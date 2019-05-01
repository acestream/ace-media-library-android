package org.videolan.medialibrary;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;
import androidx.core.content.ContextCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import android.text.TextUtils;
import android.util.Log;

import org.acestream.sdk.AceStream;
import org.acestream.sdk.controller.api.TransportFileDescriptor;
import org.acestream.sdk.controller.api.response.MediaFilesResponse;
import org.acestream.sdk.errors.TransportFileParsingException;
import org.acestream.sdk.utils.Logger;
import org.acestream.sdk.utils.MiscUtils;
import org.videolan.libvlc.LibVLC;
import org.videolan.medialibrary.interfaces.DevicesDiscoveryCb;
import org.videolan.medialibrary.interfaces.EntryPointsEventsCb;
import org.videolan.medialibrary.interfaces.MediaAddedCb;
import org.videolan.medialibrary.interfaces.MediaUpdatedCb;
import org.videolan.medialibrary.media.Album;
import org.videolan.medialibrary.media.Artist;
import org.videolan.medialibrary.media.Genre;
import org.videolan.medialibrary.media.HistoryItem;
import org.videolan.medialibrary.media.MediaSearchAggregate;
import org.videolan.medialibrary.media.MediaWrapper;
import org.videolan.medialibrary.media.Playlist;
import org.videolan.medialibrary.media.SearchAggregate;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.lang.ref.WeakReference;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;

public class Medialibrary {

    private static final String TAG = "AS/ML";

    public static final int FLAG_MEDIA_UPDATED_AUDIO        = 1 << 0;
    public static final int FLAG_MEDIA_UPDATED_AUDIO_EMPTY  = 1 << 1;
    public static final int FLAG_MEDIA_UPDATED_VIDEO        = 1 << 2;
    public static final int FLAG_MEDIA_ADDED_AUDIO          = 1 << 3;
    public static final int FLAG_MEDIA_ADDED_AUDIO_EMPTY    = 1 << 4;
    public static final int FLAG_MEDIA_ADDED_VIDEO          = 1 << 5;
    public static final int FLAG_MEDIA_ADDED_TRANSPORT_FILE = 1 << 6;

    public static final int ML_INIT_SUCCESS = 0;
    public static final int ML_INIT_ALREADY_INITIALIZED = 1;
    public static final int ML_INIT_FAILED = 2;
    public static final int ML_INIT_DB_RESET = 3;

    public static final String ACTION_IDLE = "action_idle";
    public static final String STATE_IDLE = "state_idle";

    public static final MediaWrapper[] EMPTY_COLLECTION = {};
    public static final String VLC_MEDIA_DB_NAME = "/vlc_media.db";
    public static final String THUMBS_FOLDER_NAME = "/thumbs";

    public final static long INTERNAL_TRANSPORT_FILE_PARENT_ID = -10;

    private long mInstanceID;
    private volatile boolean mIsInitiated = false;
    private volatile boolean mIsWorking = false;

    private MediaUpdatedCb mediaUpdatedCb = null;
    private MediaAddedCb mediaAddedCb = null;
    private ArtistsAddedCb mArtistsAddedCb = null;
    private ArtistsModifiedCb mArtistsModifiedCb = null;
    private AlbumsAddedCb mAlbumsAddedCb = null;
    private AlbumsModifiedCb mAlbumsModifiedCb = null;
    private final List<DevicesDiscoveryCb> devicesDiscoveryCbList = new ArrayList<>();
    private final List<EntryPointsEventsCb> entryPointsEventsCbList = new ArrayList<>();
    private static Context sContext;

    private static final Medialibrary instance = new Medialibrary();

    public static Context getContext() {
        return sContext;
    }

    public int init(Context context) {
        if (context == null)
            return ML_INIT_FAILED;
        sContext = context;
        File extFilesDir = context.getExternalFilesDir(null);
        File dbDirectory = context.getDir("db", Context.MODE_PRIVATE);
        if (extFilesDir == null || !extFilesDir.exists()
                || dbDirectory == null || !dbDirectory.canWrite())
            return ML_INIT_FAILED;
        LibVLC.loadLibraries();
        try {
            System.loadLibrary("c++_shared");
            System.loadLibrary("mla");
        } catch (UnsatisfiedLinkError ule)
        {
            Log.e(TAG, "Can't load mla: " + ule);
            return ML_INIT_FAILED;
        }
        int initCode = nativeInit(dbDirectory+ VLC_MEDIA_DB_NAME, extFilesDir+ THUMBS_FOLDER_NAME);
        mIsInitiated = initCode != ML_INIT_FAILED;
        return initCode;
    }

    public void start() {
        nativeStart();
    }

    public void banFolder(@NonNull String path) {
        if (mIsInitiated && new File(path).exists())
            nativeBanFolder(Tools.encodeVLCMrl(path));
    }

    public void unbanFolder(@NonNull String path) {
        if (mIsInitiated && new File(path).exists())
            nativeUnbanFolder(Tools.encodeVLCMrl(path));
    }

    public String[] getDevices() {
        return mIsInitiated ? nativeDevices() : new String[0];
    }

    public boolean addDevice(@NonNull String uuid, @NonNull String path, boolean removable) {
        return mIsInitiated && nativeAddDevice(Tools.encodeVLCMrl(uuid), Tools.encodeVLCMrl(path), removable);
    }

    public void discover(@NonNull String path) {
        if (mIsInitiated) nativeDiscover(Tools.encodeVLCMrl(path));
    }

    public void removeFolder(@NonNull String mrl) {
        if (!mIsInitiated) return;
        final String[] folders = getFoldersList();
        for (String folder : folders) {
            if (!folder.equals(mrl) && folder.contains(mrl))
                removeFolder(folder);
        }
        nativeRemoveEntryPoint(Tools.encodeVLCMrl(mrl));
    }

    public String[] getFoldersList() {
        if (!mIsInitiated)
            return new String[0];
        return nativeEntryPoints();
    }

    public boolean removeDevice(String uuid) {
        return mIsInitiated && !TextUtils.isEmpty(uuid) && nativeRemoveDevice(Tools.encodeVLCMrl(uuid));
    }

    @Override
    protected void finalize() throws Throwable {
        if (mIsInitiated)
            nativeRelease();
        super.finalize();
    }

    public static Medialibrary getInstance() {
        return instance;
    }

    @WorkerThread
    public MediaWrapper[] getVideos(int isP2P, int isLive) {
        return mIsInitiated ? nativeGetVideos(isP2P, isLive) : new MediaWrapper[0];
    }

    @WorkerThread
    public MediaWrapper[] getRecentVideos(int isP2P, int isLive) {
        return mIsInitiated ? nativeGetRecentVideos(isP2P, isLive) : new MediaWrapper[0];
    }

    @WorkerThread
    public MediaWrapper[] getAudio(int isP2P, int isLive) {
        return mIsInitiated ? nativeGetAudio(isP2P, isLive) : new MediaWrapper[0];
    }

    @WorkerThread
    public MediaWrapper[] getRecentAudio(int isP2P, int isLive) {
        return mIsInitiated ? nativeGetRecentAudio(isP2P, isLive) : new MediaWrapper[0];
    }

    public int getVideoCount() {
        return mIsInitiated ? nativeGetVideoCount() : 0;
    }

    public int getAudioCount() {
        return mIsInitiated ? nativeGetAudioCount() : 0;
    }

    @WorkerThread
    public Album[] getAlbums() {
        return mIsInitiated ? nativeGetAlbums() : new Album[0];
    }

    @WorkerThread
    public Album getAlbum(long albumId) {
        return mIsInitiated ? nativeGetAlbum(albumId) : null;
    }

    @WorkerThread
    public Artist[] getArtists(boolean all) {
        return mIsInitiated ? nativeGetArtists(all) : new Artist[0];
    }

    public Artist getArtist(long artistId) {
        return mIsInitiated ? nativeGetArtist(artistId) : null;
    }

    @WorkerThread
    public Genre[] getGenres() {
        return mIsInitiated ? nativeGetGenres() : new Genre[0];
    }

    public Genre getGenre(long genreId) {
        return mIsInitiated ? nativeGetGenre(genreId) : null;
    }

    @WorkerThread
    public Playlist[] getPlaylists() {
        return mIsInitiated ? nativeGetPlaylists() : new Playlist[0];
    }

    public Playlist getPlaylist(long playlistId) {
        return mIsInitiated ? nativeGetPlaylist(playlistId) : null;
    }

    public Playlist createPlaylist(String name) {
        return mIsInitiated && !TextUtils.isEmpty(name) ? nativePlaylistCreate(name) : null;
    }

    public void pauseBackgroundOperations() {
        if (mIsInitiated)
            nativePauseBackgroundOperations();
    }

    public void resumeBackgroundOperations() {
        if (mIsInitiated)
            nativeResumeBackgroundOperations();
    }

    public void reload() {
        if (mIsInitiated && !isWorking())
            nativeReload();
    }

    public void reload(String entryPoint) {
        if (mIsInitiated && !TextUtils.isEmpty(entryPoint))
            nativeReload(Tools.encodeVLCMrl(entryPoint));
    }

    public void forceParserRetry() {
        if (mIsInitiated) nativeForceParserRetry();
    }

    public void forceRescan() {
        if (mIsInitiated) nativeForceRescan();
    }

    @WorkerThread
    public MediaWrapper[] lastMediaPlayed() {
        return mIsInitiated ? nativeLastMediaPlayed() : EMPTY_COLLECTION;
    }

    @WorkerThread
    public HistoryItem[] lastStreamsPlayed() {
        return mIsInitiated ? nativeLastStreamsPlayed() : new HistoryItem[0];
    }

    public boolean clearHistory() {
        return mIsInitiated && nativeClearHistory();
    }

    public boolean addToHistory(String mrl, String title) {
        Logger.v(TAG, "addToHistory: mrl=" + mrl);
        return mIsInitiated && nativeAddToHistory(Tools.encodeVLCMrl(mrl), Tools.encodeVLCMrl(title));
    }

    @Nullable
    public MediaWrapper getMedia(long id) {
        return mIsInitiated ? nativeGetMedia(id) : null;
    }

    @Nullable
    public MediaWrapper getMedia(Uri uri) {
        if(uri == null) return null;
        return getMedia(uri.toString());
    }

    @Nullable
    public MediaWrapper getMedia(String mrl) {
        if(!mIsInitiated) return null;
        final String vlcMrl = convertMrl(Tools.encodeVLCMrl(mrl), false);
        return !TextUtils.isEmpty(vlcMrl) ? nativeGetMediaFromMrl(vlcMrl) : null;
    }

    //:ace
    @Nullable
    public MediaWrapper addMedia(MediaWrapper mw) {
        if(mw.isP2PItem()) {
            TransportFileDescriptor descriptor;
            try {
                descriptor = mw.getDescriptor();
            }
            catch(TransportFileParsingException e) {
                Log.e(TAG, "Failed to read transport file", e);
                return null;
            }

            if(mw.getMediaFile() == null) {
                Log.e(TAG, "addMedia: missing media file for p2p item");
                return null;
            }

            return addP2PMedia(mw.getParentMediaId(), descriptor, mw.getMediaFile());
        }
        else {
            return addMedia(Uri.decode(mw.getUri().toString()));
        }
    }
    ///ace

    @Nullable
    public MediaWrapper addMedia(String mrl) {
        if(!mIsInitiated) return null;
        final String vlcMrl = convertMrl(Tools.encodeVLCMrl(mrl), true);
        return !TextUtils.isEmpty(vlcMrl) ? nativeAddMedia(vlcMrl) : null;
    }

    @Nullable
    public MediaWrapper addP2PMedia(long parentMediaId, TransportFileDescriptor descriptor, MediaFilesResponse.MediaFile mediaFile) {
        if(!mIsInitiated) return null;

        if(descriptor == null) {
            if(BuildConfig.DEBUG) {
                throw new IllegalStateException("missing descriptor");
            }
            else {
                Log.e(TAG, "missing descriptor");
                return null;
            }
        }

        if(mediaFile == null) {
            if(BuildConfig.DEBUG) {
                throw new IllegalStateException("missing media file");
            }
            else {
                Log.e(TAG, "missing media file");
                return null;
            }
        }

        if(TextUtils.isEmpty(mediaFile.filename)) {
            if(BuildConfig.DEBUG) {
                throw new IllegalStateException("empty mediaFile.filename");
            }
            else {
                Log.e(TAG, "empty mediaFile.filename");
                return null;
            }
        }

        if(descriptor.getDescriptorString().startsWith("data=content%3A%2F%2F")) {
            Logger.v(TAG, "addP2PMedia: skip item: descriptor=" + descriptor);
            return null;
        }

        int type = mediaFile.mime.startsWith("audio/") ? MediaWrapper.TYPE_AUDIO : MediaWrapper.TYPE_VIDEO;
        String mrl = descriptor.getMrl(mediaFile.index).toString();
        final String vlcMrl = Tools.encodeVLCMrl(mrl);

        if(parentMediaId == 0 && descriptor.isInternal()) {
            // The means 'internal transport file' (saved in internal app storage)
            parentMediaId = INTERNAL_TRANSPORT_FILE_PARENT_ID;
        }

        Logger.v(TAG, "addP2PMedia: parent=" + parentMediaId + " mime=" + mediaFile.mime + " type=" + type + " mrl=" + mrl);

        MediaWrapper mw = nativeAddP2PMedia(parentMediaId, type, mediaFile.filename, vlcMrl);
        if(mw != null) {
            mw.setP2PLive(mediaFile.isLive() ? 1 : 0);
            mw.setP2PInfo(mediaFile.infohash, mediaFile.index);
        }
        return mw;
    }

    public long getId() {
        return mInstanceID;
    }

    public boolean isWorking() {
        return mIsWorking;
    }

    public boolean isInitiated() {
        return mIsInitiated;
    }

    public boolean increasePlayCount(long mediaId) {
        return mIsInitiated && mediaId > 0 && nativeIncreasePlayCount(mediaId);
    }

    public boolean deleteMedia(long mediaId) {
        return mIsInitiated && mediaId > 0 && nativeDeleteMedia(mediaId);
    }

    // If media is not in ML, find it with its path
    public MediaWrapper findMedia(MediaWrapper mw) {
        if (mIsInitiated && mw != null && mw.getId() == 0L && mw.getUri() != null) {
            Uri uri = mw.getUri();
            MediaWrapper libraryMedia = getMedia(uri);
            if (libraryMedia == null && TextUtils.equals("file", uri.getScheme()) &&
                    uri.getPath() != null && uri.getPath().startsWith("/sdcard")) {
                uri = Tools.convertLocalUri(uri);
                libraryMedia = getMedia(uri);
            }

            if (libraryMedia != null)
                return libraryMedia;
        }
        return mw;
    }

    @SuppressWarnings("unused")
    public void onMediaAdded(MediaWrapper[] mediaList) {
        if (mediaAddedCb != null)
            mediaAddedCb.onMediaAdded(mediaList);
    }

    @SuppressWarnings("unused")
    public void onMediaUpdated(MediaWrapper[] mediaList) {
        if (mediaUpdatedCb != null)
            mediaUpdatedCb.onMediaUpdated(mediaList);
    }

    @SuppressWarnings("unused")
    public void onMediaDeleted(long[] ids) {
        for (long id : ids)
            Log.d(TAG, "onMediaDeleted: "+id);
    }

    @SuppressWarnings("unused")
    public void onArtistsAdded() {
        if (mArtistsAddedCb != null)
            mArtistsAddedCb.onArtistsAdded();
    }

    @SuppressWarnings("unused")
    public void onArtistsModified() {
        if (mArtistsModifiedCb != null)
            mArtistsModifiedCb.onArtistsModified();
    }

    @SuppressWarnings("unused")
    public void onAlbumsAdded() {
        if (mAlbumsAddedCb != null)
            mAlbumsAddedCb.onAlbumsAdded();
    }

    @SuppressWarnings("unused")
    public void onAlbumsModified() {
        if (mAlbumsModifiedCb != null)
            mAlbumsModifiedCb.onAlbumsModified();
    }

    @SuppressWarnings("unused")
    public void onArtistsDeleted(long[] ids) {
        for (long id : ids)
            Log.d(TAG, "onArtistsDeleted: "+id);
    }

    @SuppressWarnings("unused")
    public void onAlbumsDeleted(long[] ids) {
        for (long id : ids)
            Log.d(TAG, "onAlbumsDeleted: "+id);
    }

    @SuppressWarnings("unused")
    public void onDiscoveryStarted(String entryPoint) {
        synchronized (devicesDiscoveryCbList) {
            if (!devicesDiscoveryCbList.isEmpty())
                for (DevicesDiscoveryCb cb : devicesDiscoveryCbList)
                    cb.onDiscoveryStarted(entryPoint);
        }
        synchronized (entryPointsEventsCbList) {
            if (!entryPointsEventsCbList.isEmpty())
                for (EntryPointsEventsCb cb : entryPointsEventsCbList)
                    cb.onDiscoveryStarted(entryPoint);
        }
    }

    @SuppressWarnings("unused")
    public void onDiscoveryProgress(String entryPoint) {
        synchronized (devicesDiscoveryCbList) {
            if (!devicesDiscoveryCbList.isEmpty())
                for (DevicesDiscoveryCb cb : devicesDiscoveryCbList)
                    cb.onDiscoveryProgress(entryPoint);
        }
        synchronized (entryPointsEventsCbList) {
            if (!entryPointsEventsCbList.isEmpty())
                for (EntryPointsEventsCb cb : entryPointsEventsCbList)
                    cb.onDiscoveryProgress(entryPoint);
        }
    }

    @SuppressWarnings("unused")
    public void onDiscoveryCompleted(String entryPoint) {
        synchronized (devicesDiscoveryCbList) {
            if (!devicesDiscoveryCbList.isEmpty())
                for (DevicesDiscoveryCb cb : devicesDiscoveryCbList)
                    cb.onDiscoveryCompleted(entryPoint);
        }
        synchronized (entryPointsEventsCbList) {
            if (!entryPointsEventsCbList.isEmpty())
                for (EntryPointsEventsCb cb : entryPointsEventsCbList)
                    cb.onDiscoveryCompleted(entryPoint);
        }
    }

    @SuppressWarnings("unused")
    public void onParsingStatsUpdated(int percent) {
        synchronized (devicesDiscoveryCbList) {
            if (!devicesDiscoveryCbList.isEmpty())
                for (DevicesDiscoveryCb cb : devicesDiscoveryCbList)
                    cb.onParsingStatsUpdated(percent);
        }
    }

    @SuppressWarnings("unused")
    public void onBackgroundTasksIdleChanged(boolean isIdle) {
        mIsWorking = !isIdle;
        LocalBroadcastManager.getInstance(sContext).sendBroadcast(new Intent(ACTION_IDLE).putExtra(STATE_IDLE, isIdle));
    }

    @SuppressWarnings("unused")
    void onReloadStarted(String entryPoint) {
        synchronized (devicesDiscoveryCbList) {
            if (!devicesDiscoveryCbList.isEmpty())
                for (DevicesDiscoveryCb cb : devicesDiscoveryCbList)
                    cb.onReloadStarted(entryPoint);
        }
    }

    @SuppressWarnings("unused")
    void onReloadCompleted(String entryPoint) {
        synchronized (devicesDiscoveryCbList) {
            if (!devicesDiscoveryCbList.isEmpty())
                for (DevicesDiscoveryCb cb : devicesDiscoveryCbList)
                    cb.onReloadCompleted(entryPoint);
        }
    }

    @SuppressWarnings("unused")
    void onEntryPointBanned(String entryPoint, boolean success) {
        synchronized (entryPointsEventsCbList) {
            if (!entryPointsEventsCbList.isEmpty())
                for (EntryPointsEventsCb cb : entryPointsEventsCbList)
                    cb.onEntryPointBanned(entryPoint, success);
        }
    }

    @SuppressWarnings("unused")
    void onEntryPointUnbanned(String entryPoint, boolean success) {
        synchronized (entryPointsEventsCbList) {
            if (!entryPointsEventsCbList.isEmpty())
                for (EntryPointsEventsCb cb : entryPointsEventsCbList)
                    cb.onEntryPointUnbanned(entryPoint, success);
        }
    }

    @SuppressWarnings("unused")
    void onEntryPointRemoved(String entryPoint, boolean success) {
        synchronized (entryPointsEventsCbList) {
            if (!entryPointsEventsCbList.isEmpty())
                for (EntryPointsEventsCb cb : entryPointsEventsCbList)
                    cb.onEntryPointRemoved(entryPoint, success);
        }
    }

    public void setMediaUpdatedCb(MediaUpdatedCb mediaUpdatedCb, int flags) {
        if (!mIsInitiated)
            return;
        this.mediaUpdatedCb = mediaUpdatedCb;
        nativeSetMediaUpdatedCbFlag(flags);
    }

    public void removeMediaUpdatedCb() {
        if (!mIsInitiated)
            return;
        setMediaUpdatedCb(null, 0);
    }

    public void setMediaAddedCb(MediaAddedCb mediaAddedCb, int flags) {
        if (!mIsInitiated)
            return;
        this.mediaAddedCb = mediaAddedCb;
        nativeSetMediaAddedCbFlag(flags);
    }

    public void setArtistsAddedCb(ArtistsAddedCb artistsAddedCb) {
        if (!mIsInitiated)
            return;
        this.mArtistsAddedCb = artistsAddedCb;
        nativeSetMediaAddedCbFlag(artistsAddedCb == null ? 0 : FLAG_MEDIA_ADDED_AUDIO_EMPTY);
    }

    public void setArtistsModifiedCb(ArtistsModifiedCb artistsModifiedCb) {
        if (!mIsInitiated)
            return;
        this.mArtistsModifiedCb = artistsModifiedCb;
        nativeSetMediaUpdatedCbFlag(artistsModifiedCb == null ? 0 : FLAG_MEDIA_UPDATED_AUDIO_EMPTY);
    }

    public void setAlbumsAddedCb(AlbumsAddedCb AlbumsAddedCb) {
        if (!mIsInitiated)
            return;
        this.mAlbumsAddedCb = AlbumsAddedCb;
        nativeSetMediaAddedCbFlag(AlbumsAddedCb == null ? 0 : FLAG_MEDIA_ADDED_AUDIO_EMPTY);
    }

    public void setAlbumsModifiedCb(AlbumsModifiedCb AlbumsModifiedCb) {
        if (!mIsInitiated)
            return;
        this.mAlbumsModifiedCb = AlbumsModifiedCb;
        nativeSetMediaUpdatedCbFlag(AlbumsModifiedCb == null ? 0 : FLAG_MEDIA_UPDATED_AUDIO_EMPTY);
    }

    public SearchAggregate search(String query) {
        return mIsInitiated && !TextUtils.isEmpty(query) ? nativeSearch(query) : null;
    }

    public MediaSearchAggregate searchMedia(String query) {
        return mIsInitiated && !TextUtils.isEmpty(query) ? nativeSearchMedia(query) : null;
    }

    public Artist[] searchArtist(String query) {
        return mIsInitiated && !TextUtils.isEmpty(query) ? nativeSearchArtist(query) : null;
    }

    public Album[] searchAlbum(String query) {
        return mIsInitiated && !TextUtils.isEmpty(query) ? nativeSearchAlbum(query) : null;
    }

    public Genre[] searchGenre(String query) {
        return mIsInitiated && !TextUtils.isEmpty(query) ? nativeSearchGenre(query) : null;
    }

    public Playlist[] searchPlaylist(String query) {
        return mIsInitiated && !TextUtils.isEmpty(query) ? nativeSearchPlaylist(query) : null;
    }

    public void addDeviceDiscoveryCb(DevicesDiscoveryCb cb) {
        synchronized (devicesDiscoveryCbList) {
            if (!devicesDiscoveryCbList.contains(cb))
                devicesDiscoveryCbList.add(cb);
        }
    }

    public void removeDeviceDiscoveryCb(DevicesDiscoveryCb cb) {
        synchronized (devicesDiscoveryCbList) {
            devicesDiscoveryCbList.remove(cb);
        }
    }

    public void addEntryPointsEventsCb(EntryPointsEventsCb cb) {
        synchronized (entryPointsEventsCbList) {
            if (!entryPointsEventsCbList.contains(cb))
                entryPointsEventsCbList.add(cb);
        }
    }

    public void removeEntryPointsEventsCb(EntryPointsEventsCb cb) {
        synchronized (entryPointsEventsCbList) {
            entryPointsEventsCbList.remove(cb);
        }
    }

    public void removeMediaAddedCb() {
        if (!mIsInitiated)
            return;
        setMediaAddedCb(null, 0);
    }

    public static String[] getBlackList() {
        return new String[] {
                "/Android/data/",
                "/Android/media/",
                "/Alarms/",
                "/Ringtones/",
                "/Notifications/",
                "/alarms/",
                "/ringtones/",
                "/notifications/",
                "/audio/Alarms/",
                "/audio/Ringtones/",
                "/audio/Notifications/",
                "/audio/alarms/",
                "/audio/ringtones/",
                "/audio/notifications/",
                "/WhatsApp/Media/WhatsApp Animated Gifs/",
        };
    }

    public static File[] getDefaultFolders() {
        return new File[]{
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES),
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC),
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PODCASTS),
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM),
        };
    }

    /* used only before API 13: substitute for NewWeakGlobalRef */
    @SuppressWarnings("unused") /* Used from JNI */
    private Object getWeakReference() {
        return new WeakReference<>(this);
    }


    // Native methods
    private native int nativeInit(String dbPath, String thumbsPath);
    private native void nativeStart();
    private native void nativeRelease();
    private native void nativeBanFolder(String path);
    private native void nativeUnbanFolder(String path);
    private native boolean nativeAddDevice(String uuid, String path, boolean removable);
    private native String[] nativeDevices();
    private native void nativeDiscover(String path);
    private native void nativeRemoveEntryPoint(String path);
    private native String[] nativeEntryPoints();
    private native boolean nativeRemoveDevice(String uuid);
    private native MediaWrapper[] nativeLastMediaPlayed();
    private native HistoryItem[] nativeLastStreamsPlayed();
    private native  boolean nativeAddToHistory(String mrl, String title);
    private native  boolean nativeClearHistory();
    private native MediaWrapper nativeGetMedia(long id);
    private native MediaWrapper nativeGetMediaFromMrl(String mrl);
    private native MediaWrapper nativeAddMedia(String mrl);
    private native MediaWrapper nativeAddP2PMedia(long parentMediaId, int type, String title, String mrl);
    private native MediaWrapper[] nativeGetVideos(int isP2P, int isLive);
    private native MediaWrapper[] nativeGetRecentVideos(int isP2P, int isLive);
    private native MediaWrapper[] nativeGetAudio(int isP2P, int isLive);
    private native MediaWrapper[] nativeGetRecentAudio(int isP2P, int isLive);
    private native int nativeGetVideoCount();
    private native int nativeGetAudioCount();
    private native Album[] nativeGetAlbums();
    private native Album nativeGetAlbum(long albumtId);
    private native Artist[] nativeGetArtists(boolean all);
    private native Artist nativeGetArtist(long artistId);
    private native Genre[] nativeGetGenres();
    private native Genre nativeGetGenre(long genreId);
    private native Playlist[] nativeGetPlaylists();
    private native Playlist nativeGetPlaylist(long playlistId);
    private native Playlist nativePlaylistCreate(String name);
    private native void nativePauseBackgroundOperations();
    private native void nativeResumeBackgroundOperations();
    private native void nativeReload();
    private native void nativeReload(String entryPoint);
    private native void nativeForceParserRetry();
    private native void nativeForceRescan();
    private native void nativeReinit();
    private native boolean nativeIncreasePlayCount(long mediaId);
    private native void nativeSetMediaUpdatedCbFlag(int flags);
    private native void nativeSetMediaAddedCbFlag(int flags);
    private native SearchAggregate nativeSearch(String query);
    private native MediaSearchAggregate nativeSearchMedia(String query);
    private native Artist[] nativeSearchArtist(String query);
    private native Album[] nativeSearchAlbum(String query);
    private native Genre[] nativeSearchGenre(String query);
    private native Playlist[] nativeSearchPlaylist(String query);

    private boolean canReadStorage(Context context) {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.M || ContextCompat.checkSelfPermission(context,
                Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
    }

    public interface ArtistsAddedCb {
        void onArtistsAdded();
    }

    public interface ArtistsModifiedCb {
        void onArtistsModified();
    }

    public interface AlbumsAddedCb {
        void onAlbumsAdded();
    }

    public interface AlbumsModifiedCb {
        void onAlbumsModified();
    }

    //:ace
    private native MediaWrapper[] nativeGetTransportFiles(int isParsed);
    private native boolean nativeDeleteMedia(long mediaId);
    private native MediaWrapper[] nativeFindMediaByInfohash(String infohash, int fileIndex);
    private native MediaWrapper[] nativeFindMediaByParent(long parentId);
    private native MediaWrapper[] nativeFindDuplicatesByInfohash();
    private native boolean nativeCopyMetadata(long sourceId, long destId);
    private native boolean nativeRemoveOrphanTransportFiles();

    @WorkerThread
    public MediaWrapper[] getTransportFiles(int isParsed) {
        return mIsInitiated ? nativeGetTransportFiles(isParsed) : new MediaWrapper[0];
    }

    public MediaWrapper[] getUnparsedTransportFiles() {
        return getTransportFiles(0);
    }

    @WorkerThread
    public MediaWrapper[] getRegularVideos() {
        return getVideos(0, -1);
    }

    @WorkerThread
    public MediaWrapper[] getRecentRegularVideos() {
        return getRecentVideos(0, -1);
    }

    @WorkerThread
    public MediaWrapper[] getRegularAudio() {
        return getAudio(0, -1);
    }

    @WorkerThread
    public MediaWrapper[] getP2PVideos() {
        return getVideos(1, 0);
    }

    @WorkerThread
    public MediaWrapper[] getRecentP2PVideos() {
        return getRecentVideos(1, 0);
    }

    @WorkerThread
    public MediaWrapper[] getP2PAudio() {
        return getAudio(1, 0);
    }

    @WorkerThread
    public MediaWrapper[] getP2PStreams() {
        return getVideos(1, 1);
    }

    @WorkerThread
    public MediaWrapper[] getRecentP2PStreams() {
        return getRecentVideos(1, 1);
    }

    @WorkerThread
    public MediaWrapper[] findMediaByInfohash(String infohash, int fileIndex) {
        return mIsInitiated ? nativeFindMediaByInfohash(infohash, fileIndex) : new MediaWrapper[0];
    }

    @WorkerThread
    public MediaWrapper[] findMediaByParent(long parentId) {
        return mIsInitiated ? nativeFindMediaByParent(parentId) : new MediaWrapper[0];
    }

    @WorkerThread
    public MediaWrapper[] findDuplicatesByInfohash() {
        return mIsInitiated ? nativeFindDuplicatesByInfohash() : new MediaWrapper[0];
    }

    @WorkerThread
    public boolean copyMetadata(long sourceId, long destId) {
        return mIsInitiated ? nativeCopyMetadata(sourceId, destId) : false;
    }

    @WorkerThread
    public boolean removeOrphanTransportFiles() {
        return mIsInitiated ? nativeRemoveOrphanTransportFiles() : false;
    }

    private String convertMrl(String mrl, boolean transform) {
        if(mrl == null) return null;

        Uri uri = Uri.parse(mrl);
        if(!TextUtils.equals(uri.getScheme(), "content")) {
            return mrl;
        }

        File dir = AceStream.getAppFilesDir("content_files",transform);
        String filename;
        File file;
        String ext = MiscUtils.parseExtension(uri.getLastPathSegment());

        // Need idempotent filename: the same for the same content:// URI
        try {
            filename = MiscUtils.sha1Hash(uri.toString()) + ext;
            filename = filename.toLowerCase();
        }
        catch(NoSuchAlgorithmException|UnsupportedEncodingException e) {
            filename = uri.getLastPathSegment();
        }

        if (TextUtils.isEmpty(filename)) {
            Log.e(TAG, "convertMrl: failed to make filename: uri=" + uri);
            return mrl;
        }

        file = new File(dir, filename);

        if(transform) {
            try {
                Logger.v(TAG, "convertMrl:transform: uri=" + uri + " path=" + file.getAbsolutePath() + " exists=" + file.exists());
                FileOutputStream output = new FileOutputStream(file);
                MiscUtils.readBytesFromContentUri(sContext.getContentResolver(), uri, output, 1048576);
                output.write(MiscUtils.readBytesFromContentUri(sContext.getContentResolver(), uri));
                output.close();
            } catch (IOException e) {
                Log.e(TAG, "Failed to transform content file", e);
                return mrl;
            }
        }

        return file.getAbsolutePath();
    }
    ///ace
}
