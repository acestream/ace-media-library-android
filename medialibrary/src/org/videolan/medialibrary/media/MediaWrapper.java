/*****************************************************************************
 * MediaWrapper.java
 *****************************************************************************
 * Copyright © 2011-2015 VLC authors and VideoLAN
 *
 * This program is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation; either version 2.1 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston MA 02110-1301, USA.
 *****************************************************************************/

package org.videolan.medialibrary.media;

import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Parcel;
import android.os.Parcelable;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import android.text.TextUtils;
import android.util.Log;

import org.acestream.engine.controller.Callback;
import org.acestream.sdk.AceStream;
import org.acestream.sdk.AceStreamManager;
import org.acestream.sdk.controller.EngineApi;
import org.acestream.sdk.interfaces.IAceStreamManager;
import org.acestream.sdk.utils.Logger;
import org.acestream.sdk.utils.MiscUtils;

import org.acestream.sdk.EngineSession;
import org.acestream.sdk.EngineSessionStartListener;
import org.acestream.sdk.P2PItemStartListener;
import org.acestream.sdk.PlaybackData;
import org.acestream.sdk.controller.api.response.MediaFilesResponse;
import org.acestream.sdk.controller.api.TransportFileDescriptor;
import org.acestream.sdk.errors.TransportFileParsingException;
import org.json.JSONException;
import org.json.JSONObject;
import org.videolan.libvlc.Media;
import org.videolan.libvlc.Media.Meta;
import org.videolan.libvlc.Media.VideoTrack;
import org.videolan.libvlc.MediaPlayer;
import org.videolan.libvlc.util.Extensions;
import org.videolan.libvlc.util.VLCUtil;
import org.videolan.medialibrary.Medialibrary;
import org.videolan.medialibrary.Tools;

import java.io.UnsupportedEncodingException;
import java.util.Locale;

@SuppressWarnings("JniMissingFunction")
public class MediaWrapper extends MediaLibraryItem implements Parcelable {
    public final static String TAG = "AS/MW";

    public final static int TYPE_ALL = -1;
    public final static int TYPE_VIDEO = 0;
    public final static int TYPE_AUDIO = 1;
    public final static int TYPE_GROUP = 2;
    public final static int TYPE_DIR = 3;
    public final static int TYPE_SUBTITLE = 4;
    public final static int TYPE_PLAYLIST = 5;
    public final static int TYPE_STREAM = 6;
    public final static int TYPE_TRANSPORT_FILE = 7;

    public final static int MEDIA_VIDEO = 0x01;
    public final static int MEDIA_NO_HWACCEL = 0x02;
    public final static int MEDIA_PAUSED = 0x4;
    public final static int MEDIA_FORCE_AUDIO = 0x8;

    //MetaData flags
    public final static int META_RATING = 1;
    //Playback
    public final static int META_PROGRESS = 50;
    public final static int META_SPEED = 51;
    public final static int META_TITLE = 52;
    public final static int META_CHAPTER = 53;
    public final static int META_PROGRAM = 54;
    public final static int META_SEEN = 55;
    //video
    public final static int META_VIDEOTRACK = 100;
    public final static int META_ASPECT_RATIO = 101;
    public final static int META_ZOOM = 102;
    public final static int META_CROP = 103;
    public final static int META_DEINTERLACE = 104;
    public final static int META_VIDEOFILTER = 105;
    //Audio
    public final static int META_AUDIOTRACK = 150;
    public final static int META_GAIN = 151;
    public final static int META_AUDIODELAY = 152;
    //Spu
    public final static int META_SUBTITLE_TRACK = 200;
    public final static int META_SUBTITLE_DELAY = 201;
    //Various
    public final static int META_APPLICATION_SPECIFIC = 250;
    //:ace
    public final static int META_GROUP_NAME = 5000;
    public final static int META_DURATION = 5002;
    public final static int META_FILE_SIZE = 5003;
    public final static int META_TRANSPORT_FILE_PATH = 5004;
    public final static int META_LAST_MODIFIED = 5005;
    ///ace

    //:ace
    // Categories
    public final static int CATEGORY_REGULAR_VIDEO = 0;
    public final static int CATEGORY_REGULAR_AUDIO = 1;
    public final static int CATEGORY_P2P_VIDEO = 2;
    public final static int CATEGORY_P2P_AUDIO = 3;
    public final static int CATEGORY_P2P_STREAM = 4;
    ///ace

    // threshold lentgh between song and podcast ep, set to 15 minutes
    private static final long PODCAST_THRESHOLD = 900000L;

    protected String mDisplayTitle;
    private String mArtist;
    private String mGenre;
    private String mCopyright;
    private String mAlbum;
    private int mTrackNumber;
    private int mDiscNumber;
    private String mAlbumArtist;
    private String mRating;
    private String mDate;
    private String mSettings;
    private String mNowPlaying;
    private String mPublisher;
    private String mEncodedBy;
    private String mTrackID;
    private String mArtworkURL;
    private final Uri mUri;

    //:ace
    private TransportFileDescriptor mDescriptor = null;
    private MediaFilesResponse.MediaFile mMediaFile = null;
    private EngineSession mEngineSession = null;
    private boolean mIsParsed = false;
    private long mParentMediaId = 0;
    private Uri mPlaybackUri = null;
    private P2PItemStartListener mEngineSessionListener = null;
    protected String mGroupTitle = null;
    private String mUserAgent = null;
    ///ace

    private String mFilename;
    private long mTime = 0;
    /* -1 is a valid track (Disabled) */
    private int mAudioTrack = -2;
    private int mSpuTrack = -2;
    private long mLength = 0;
    private int mType;
    private int mWidth = 0;
    private int mHeight = 0;
    private Bitmap mPicture;
    private boolean mIsPictureParsed;
    private int mFlags = 0;
    private long mLastModified = 0l;
    private Media.Slave mSlaves[] = null;

    private long mSeen = 0l;

    //:ace
    private AceStreamManager.PlaybackStateCallback mPlaybackStateCallback = new AceStreamManager.PlaybackStateCallback() {
        @Override
        public void onPlaylistUpdated() {
        }

        @Override
        public void onStart(@Nullable EngineSession session) {
        }

        @Override
        public void onPrebuffering(@Nullable EngineSession session, int progress) {
        }

        @Override
        public void onPlay(@Nullable EngineSession session) {
            if(mEngineSession == null) {
                Logger.v(TAG, "pstate:play: no current engine session");
                return;
            }

            if(session == null) {
                Logger.v(TAG, "pstate:play: null engine session");
                return;
            }

            if(!TextUtils.equals(mEngineSession.playbackSessionId, session.playbackSessionId)) {
                Logger.v(TAG, "pstate:play: session id mismatch: this=" + mEngineSession.playbackSessionId + " that=" + session.playbackSessionId);
                return;
            }

            Logger.v(TAG, "pstate:play");

            if(mEngineSessionListener != null) {
                mEngineSessionListener.onPrebufferingDone();
            }
        }

        @Override
        public void onStop() {
        }
    };
    ///ace

    /**
     * Create a new MediaWrapper
     * @param mrl Should not be null.
     */
    public MediaWrapper(long id, String mrl, long time, long length, int type, String title,
                        String artist, String genre, String album, String albumArtist, int width,
                        int height, String artworkURL, int audio, int spu, int trackNumber,
                        int discNumber, long lastModified, long seen, boolean isParsed,
                        boolean isP2P, long parentMediaId, String infohash, int fileIndex,
                        int isP2PLive) {
        super();
        if (TextUtils.isEmpty(mrl))
            throw new IllegalArgumentException("uri was empty");

        if (mrl.charAt(0) == '/')
            mrl = "file://"+mrl;
        mUri = Uri.parse(mrl);
        mId = id;

        init(time, length, type, null, title, artist, genre, album, albumArtist, width, height,
                artworkURL != null ? VLCUtil.UriFromMrl(artworkURL).getPath() : null, audio, spu,
                trackNumber, discNumber, lastModified, seen, isParsed, isP2P, parentMediaId,
                infohash, fileIndex, isP2PLive, null);
        final StringBuilder sb = new StringBuilder();
        if (type == TYPE_AUDIO) {
            boolean hasArtistMeta = !TextUtils.isEmpty(artist);
            boolean hasAlbumMeta = !TextUtils.isEmpty(album);
            if (hasArtistMeta) {
                sb.append(artist);
                if (hasAlbumMeta)
                    sb.append(" - ");
            }
            if (hasAlbumMeta)
                sb.append(album);
        } else if (type == TYPE_VIDEO) {
            Tools.setMediaDescription(this);
        }

        if (sb.length() > 0)
            mDescription = sb.toString();
        defineType();
    }

    /**
     * Create a new MediaWrapper
     * @param uri Should not be null.
     */
    public MediaWrapper(Uri uri) {
        super();
        if (uri == null)
            throw new NullPointerException("uri was null");

        mUri = TransportFileDescriptor.processAceStreamUri(uri);;
        init(null);
    }

    public MediaWrapper(
            TransportFileDescriptor descriptor,
            MediaFilesResponse.MediaFile mediaFile) {
        super();
        if (mediaFile == null)
            throw new NullPointerException("mediaFile was null");

        mUri = descriptor.getMrl(mediaFile.index);
        mDescriptor = descriptor;
        mMediaFile = mediaFile;
        mTitle = mediaFile.filename;
        init(null);
    }

    /**
     * Create a new MediaWrapper
     * @param media should be parsed and not NULL
     */
    public MediaWrapper(Media media) {
        super();
        if (media == null)
            throw new NullPointerException("media was null");

        mUri = media.getUri();
        init(media);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (!(obj instanceof MediaLibraryItem) || ((MediaLibraryItem) obj).getItemType() != TYPE_MEDIA)
            return false;
        long otherId = ((MediaWrapper) obj).getId();
        if (otherId != 0 && getId() != 0 && otherId == getId())
            return true;
        Uri otherUri = ((MediaWrapper) obj).getUri();
        if (mUri == null || otherUri == null)
            return false;
        return mUri == otherUri || mUri.equals(otherUri);
    }

    private void init(Media media) {
        mType = TYPE_ALL;

        if (media != null) {
            if (media.isParsed()) {
                mLength = media.getDuration();

                for (int i = 0; i < media.getTrackCount(); ++i) {
                    final Media.Track track = media.getTrack(i);
                    if (track == null)
                        continue;
                    if (track.type == Media.Track.Type.Video) {
                        final Media.VideoTrack videoTrack = (VideoTrack) track;
                        mType = TYPE_VIDEO;
                        mWidth = videoTrack.width;
                        mHeight = videoTrack.height;
                    } else if (mType == TYPE_ALL && track.type == Media.Track.Type.Audio){
                        mType = TYPE_AUDIO;
                    }
                }
            }
            updateMeta(media);
            if (mType == TYPE_ALL)
                switch (media.getType()) {
                    case Media.Type.Directory:
                        mType = TYPE_DIR;
                        break;
                    case Media.Type.Playlist:
                        mType = TYPE_PLAYLIST;
                        break;
                }
            mSlaves = media.getSlaves();
        }

        //:ace
        if(mDescriptor != null) {
            mIsP2P = true;
        }
        else if(mUri != null && TextUtils.equals(mUri.getScheme(), "acestream")) {
            mIsP2P = true;
        }

        if(mIsP2P) {
            initP2PItem();
        }
        ///ace

        defineType();
    }

    public void defineType() {
        defineType(false);
    }

    public void defineType(boolean force) {
        if (mType != TYPE_ALL && !force)
            return;

        //:ace
        if(mMediaFile != null) {
            mType = mMediaFile.mime.startsWith("audio/") ? TYPE_AUDIO : TYPE_VIDEO;
            return;
        }
        else if(mIsP2P) {
            mType = TYPE_VIDEO;
            return;
        }
        ///ace

        String fileExt = null, filename = mUri.getLastPathSegment();
        if (TextUtils.isEmpty(filename))
            filename = mTitle;
        if (TextUtils.isEmpty(filename))
            return;
        int index = filename.indexOf('?');
        if (index != -1)
            filename = filename.substring(0, index);

        index = filename.lastIndexOf(".");

        if (index != -1)
            fileExt = filename.substring(index).toLowerCase(Locale.ENGLISH);

        if (!TextUtils.isEmpty(fileExt)) {
            if (Extensions.VIDEO.contains(fileExt)) {
                mType = TYPE_VIDEO;
            } else if (Extensions.AUDIO.contains(fileExt)) {
                mType = TYPE_AUDIO;
            } else if (Extensions.SUBTITLES.contains(fileExt)) {
                mType = TYPE_SUBTITLE;
            } else if (Extensions.PLAYLIST.contains(fileExt)) {
                mType = TYPE_PLAYLIST;
            }
        }
    }

    private void init(long time, long length, int type,
                      Bitmap picture, String title, String artist, String genre, String album, String albumArtist,
                      int width, int height, String artworkURL, int audio, int spu, int trackNumber, int discNumber, long lastModified,
                      long seen, boolean isParsed, boolean isP2P, long parentMediaId,
                      String infohash, int fileIndex, int isP2PLive,
                      Media.Slave[] slaves) {
        mFilename = null;
        mTime = time;
        mAudioTrack = audio;
        mSpuTrack = spu;
        mLength = length;
        mType = type;
        mPicture = picture;
        mWidth = width;
        mHeight = height;
        mIsParsed = isParsed;
        mIsP2P = isP2P;
        mParentMediaId = parentMediaId;
        mInfohash = infohash;
        mFileIndex = fileIndex;
        mIsLive = isP2PLive;

        mTitle = title != null ? Uri.decode(title.trim()) : null;
        mArtist = artist != null ? artist.trim() : null;
        mGenre = genre != null ? genre.trim() : null;
        mAlbum = album != null ? album.trim() : null;
        mAlbumArtist = albumArtist != null ? albumArtist.trim() : null;
        mArtworkURL = artworkURL;
        mTrackNumber = trackNumber;
        mDiscNumber = discNumber;
        mLastModified = lastModified;
        mSeen = seen;
        mSlaves = slaves;

        if(mUri != null && TextUtils.equals(mUri.getScheme(), "acestream")) {
            mIsP2P = true;
        }

        if(mIsP2P) {
            initP2PItem();
        }
    }

    private void initP2PItem() {
        mTime = getMetaLong(META_PROGRESS);
        mLength = getMetaLong(META_DURATION);
    }

    public MediaWrapper(Uri uri, long time, long length, int type,
                 Bitmap picture, String title, String artist, String genre, String album, String albumArtist,
                 int width, int height, String artworkURL, int audio, int spu, int trackNumber, int discNumber, long lastModified, long seen,
                 boolean isParsed, boolean isP2P, long parentMediaId, String infohash, int fileIndex,
                 int isP2PLive) {

        mUri = uri;
        init(time, length, type, picture, title, artist, genre, album, albumArtist,
             width, height, artworkURL, audio, spu, trackNumber, discNumber, lastModified, seen, isParsed, isP2P, parentMediaId,
             infohash, fileIndex, isP2PLive, null);
    }

    @Override
    public MediaWrapper[] getTracks() {
        return new MediaWrapper[] {this};
    }

    @Override
    public int getItemType() {
        return TYPE_MEDIA;
    }

    public long getId() {
        return mId;
    }

    public String getLocation() {
        return mUri.toString();
    }

    public Uri getUri() {
        return mUri;
    }

    public Uri getPlaybackUri() {
        return mIsP2P ? mPlaybackUri : mUri;
    }

    public void setPlaybackUri(Uri uri) {
        mPlaybackUri = uri;
    }

    private static String getMetaId(Media media, String defaultMeta, int id, boolean trim) {
        String meta = media.getMeta(id);
        return meta != null ? trim ? meta.trim() : meta : defaultMeta;
    }

    public void updateMeta(Media media) {
        mTitle = getMetaId(media, mTitle, Meta.Title, true);
        mArtist = getMetaId(media, mArtist, Meta.Artist, true);
        mAlbum = getMetaId(media, mAlbum, Meta.Album, true);
        mGenre = getMetaId(media, mGenre, Meta.Genre, true);
        mAlbumArtist = getMetaId(media, mAlbumArtist, Meta.AlbumArtist, true);
        mArtworkURL = getMetaId(media, mArtworkURL, Meta.ArtworkURL, false);
        mNowPlaying = getMetaId(media, mNowPlaying, Meta.NowPlaying, false);
        final String trackNumber = getMetaId(media, null, Meta.TrackNumber, false);
        if (!TextUtils.isEmpty(trackNumber)) {
            try {
                mTrackNumber = Integer.parseInt(trackNumber);
            } catch (NumberFormatException ignored) {}
        }
        final String discNumber = getMetaId(media, null, Meta.DiscNumber, false);
        if (!TextUtils.isEmpty(discNumber)) {
            try {
                mDiscNumber = Integer.parseInt(discNumber);
            } catch (NumberFormatException ignored) {}
        }
    }

    public void updateMeta(MediaPlayer mediaPlayer) {
        if (!TextUtils.isEmpty(mTitle) && TextUtils.isEmpty(mDisplayTitle))
            mDisplayTitle = mTitle;
        final Media media = mediaPlayer.getMedia();
        if (media == null)
            return;
        updateMeta(media);
        media.release();
    }

    public String getFileName() {
        if (mFilename == null) {
            if(mMediaFile != null) {
                mFilename = mMediaFile.filename;
            }
            else {
                mFilename = mUri.getLastPathSegment();
            }
        }
        return mFilename;
    }

    public long getTime() {
        return mTime;
    }

    public void setTime(long time) {
        mTime = time;
    }

    public int getAudioTrack() {
        return mAudioTrack;
    }

    public void setAudioTrack(int track) {
        mAudioTrack = track;
    }

    public int getSpuTrack() {
        return mSpuTrack;
    }

    public void setSpuTrack(int track) {
        mSpuTrack = track;
    }

    public long getLength() {
        return mLength;
    }

    public void setLength(long length) {
        mLength = length;
    }

    public int getType() {
        return mType;
    }

    public boolean isPodcast() {
        return mType == TYPE_AUDIO && (TextUtils.isEmpty(mAlbum) && mLength > PODCAST_THRESHOLD)
                || ("podcast".equalsIgnoreCase(mGenre))
                || ("audiobooks".equalsIgnoreCase(mGenre))
                || ("audiobook".equalsIgnoreCase(mGenre));
    }

    public void setType(int type){
        mType = type;
    }

    public int getWidth() {
        return mWidth;
    }

    public int getHeight() {
        return mHeight;
    }

    /**
     * Returns the raw picture object. Likely to be NULL in VLC for Android
     * due to lazy-loading.
     *
     * @return The raw picture or NULL
     */
    public Bitmap getPicture() {
        return mPicture;
    }

    /**
     * Sets the raw picture object.
     *
     * @param p Bitmap picture
     */
    public void setPicture(Bitmap p) {
        mPicture = p;
    }

    public boolean isPictureParsed() {
        return mIsPictureParsed;
    }

    public void setPictureParsed(boolean isParsed) {
        mIsPictureParsed = isParsed;
    }

    public void setDisplayTitle(String title){
        mDisplayTitle = title;
    }

    public void setTitle(String title){
        mTitle = title;
    }

    public void setArtist(String artist){
        mArtist = artist;
    }

    public String getTitle() {
        if (!TextUtils.isEmpty(mDisplayTitle))
            return mDisplayTitle;
        if (!TextUtils.isEmpty(mTitle))
            return mTitle;
        String fileName = getFileName();
        if (fileName == null)
            return "";
        int end = fileName.lastIndexOf(".");
        if (end <= 0)
            return fileName;
        return fileName.substring(0, end);
    }

    public String getReferenceArtist() {
        return mAlbumArtist == null ? mArtist : mAlbumArtist;
    }

    public String getArtist() {
        return mArtist;
    }

    public Boolean isArtistUnknown() {
        return mArtist == null;
    }

    public String getGenre() {
        if (mGenre == null)
            return null;
        else if (mGenre.length() > 1)/* Make genres case insensitive via normalisation */
            return Character.toUpperCase(mGenre.charAt(0)) + mGenre.substring(1).toLowerCase(Locale.getDefault());
        else
            return mGenre;
    }

    public String getCopyright() {
        return mCopyright;
    }

    public String getAlbum() {
        return mAlbum;
    }

    public String getAlbumArtist() {
        return mAlbumArtist;
    }

    public Boolean isAlbumUnknown() {
        return mAlbum == null;
    }

    public int getTrackNumber() {
        return mTrackNumber;
    }

    public int getDiscNumber() {
        return mDiscNumber;
    }

    public String getRating() {
        return mRating;
    }

    public String getDate() {
        return mDate;
    }

    public String getSettings() {
        return mSettings;
    }

    public String getNowPlaying() {
        return mNowPlaying;
    }

    public String getPublisher() {
        return mPublisher;
    }

    public String getEncodedBy() {
        return mEncodedBy;
    }

    public String getTrackID() {
        return mTrackID;
    }

    public String getArtworkURL() {
        return mArtworkURL;
    }

    public String getArtworkMrl() {
        return mArtworkURL;
    }

    public void setArtworkURL(String url) {
        mArtworkURL = url;
    }

    public long getLastModified() {
        if(mLastModified != 0) {
            return mLastModified;
        }
        else {
            // For p2p items last modified is stored in metadata
            return getMetaLong(META_LAST_MODIFIED);
        }
    }

    public void setLastModified(long mLastModified) {
        this.mLastModified = mLastModified;
    }

    public long getSeen() {
        return mSeen;
    }

    public void setSeen(long seen) {
        mSeen = seen;
    }

    public void addFlags(int flags) {
        mFlags |= flags;
    }
    public void setFlags(int flags) {
        mFlags = flags;
    }
    public int getFlags() {
        return mFlags;
    }
    public boolean hasFlag(int flag) {
        return (mFlags & flag) != 0;
    }
    public void removeFlags(int flags) {
        mFlags &= ~flags;
    }

    public long getMetaLong(int metaDataType) {
        Medialibrary ml = Medialibrary.getInstance();
        return mId == 0 || !ml.isInitiated() ? 0L : nativeGetMediaLongMetadata(ml, mId, metaDataType);
    }
    public String getMetaString(int metaDataType) {
        Medialibrary ml = Medialibrary.getInstance();

        return mId == 0 || !ml.isInitiated() ? null : nativeGetMediaStringMetadata(ml, mId, metaDataType);
    }

    public boolean setLongMeta(int metaDataType, long metadataValue) {
        Medialibrary ml = Medialibrary.getInstance();
        if (mId != 0 && ml.isInitiated())
            nativeSetMediaLongMetadata(ml, mId, metaDataType, metadataValue);
        return mId != 0;
    }

    public boolean setStringMeta(int metaDataType, String metadataValue) {
        Medialibrary ml = Medialibrary.getInstance();
        if (mId != 0 && ml.isInitiated())
            nativeSetMediaStringMetadata(ml, mId, metaDataType, metadataValue);
        return mId != 0;
    }

    private native long nativeGetMediaLongMetadata(Medialibrary ml, long id, int metaDataType);
    private native String nativeGetMediaStringMetadata(Medialibrary ml, long id, int metaDataType);
    private native void nativeSetMediaStringMetadata(Medialibrary ml, long id, int metaDataType, String metadataValue);
    private native void nativeSetMediaLongMetadata(Medialibrary ml, long id, int metaDataType, long metadataValue);
    private native void nativeSetMediaType(Medialibrary ml, long id, int mediaType);
    //:ace
    private native void nativeSetParsed(Medialibrary ml, long id, boolean parsed);
    private native void nativeSetP2PInfo(Medialibrary ml, long id, String infohash, int fileIndex);
    private native void nativeSetP2PLive(Medialibrary ml, long id, int value);
    ///ace

    @Nullable
    public Media.Slave[] getSlaves() {
        return mSlaves;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public MediaWrapper(Parcel in) {
        super(in);
        mUri = in.readParcelable(Uri.class.getClassLoader());
        init(in.readLong(),
                in.readLong(),
                in.readInt(),
                (Bitmap) in.readParcelable(Bitmap.class.getClassLoader()),
                in.readString(),
                in.readString(),
                in.readString(),
                in.readString(),
                in.readString(),
                in.readInt(),
                in.readInt(),
                in.readString(),
                in.readInt(),
                in.readInt(),
                in.readInt(),
                in.readInt(),
                in.readLong(),
                in.readLong(),
                int2bool(in.readInt()),
                int2bool(in.readInt()),
                in.readLong(),
                in.readString(),
                in.readInt(),
                in.readInt(),
                in.createTypedArray(PSlave.CREATOR));
    }

    private static int bool2int(boolean value) {
        return value ? 1 : 0;
    }

    private static boolean int2bool(int value) {
        return value == 1;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        super.writeToParcel(dest, flags);
        dest.writeParcelable(mUri, flags);
        dest.writeLong(getTime());
        dest.writeLong(getLength());
        dest.writeInt(getType());
        dest.writeParcelable(getPicture(), flags);
        dest.writeString(getTitle());
        dest.writeString(getArtist());
        dest.writeString(getGenre());
        dest.writeString(getAlbum());
        dest.writeString(getAlbumArtist());
        dest.writeInt(getWidth());
        dest.writeInt(getHeight());
        dest.writeString(getArtworkURL());
        dest.writeInt(getAudioTrack());
        dest.writeInt(getSpuTrack());
        dest.writeInt(getTrackNumber());
        dest.writeInt(getDiscNumber());
        dest.writeLong(getLastModified());
        dest.writeLong(getSeen());
        dest.writeInt(bool2int(mIsParsed));
        dest.writeInt(bool2int(mIsP2P));
        dest.writeLong(mParentMediaId);
        dest.writeString(mInfohash);
        dest.writeInt(mFileIndex);
        dest.writeInt(mIsLive);

        if (mSlaves != null) {
            PSlave pslaves[] = new PSlave[mSlaves.length];
            for (int i = 0; i < mSlaves.length; ++i) {
                pslaves[i] = new PSlave(mSlaves[i]);
            }
            dest.writeTypedArray(pslaves, flags);
        }
        else
            dest.writeTypedArray(null, flags);
    }

    public static final Parcelable.Creator<MediaWrapper> CREATOR = new Parcelable.Creator<MediaWrapper>() {
        public MediaWrapper createFromParcel(Parcel in) {
            return new MediaWrapper(in);
        }
        public MediaWrapper[] newArray(int size) {
            return new MediaWrapper[size];
        }
    };

    private static class PSlave extends Media.Slave implements Parcelable {

        protected PSlave(Media.Slave slave) {
            super(slave.type, slave.priority, slave.uri);
        }

        protected PSlave(Parcel in) {
            super(in.readInt(), in.readInt(), in.readString());
        }

        public static final Creator<PSlave> CREATOR = new Creator<PSlave>() {
            @Override
            public PSlave createFromParcel(Parcel in) {
                return new PSlave(in);
            }

            @Override
            public PSlave[] newArray(int size) {
                return new PSlave[size];
            }
        };

        @Override
        public int describeContents() {
            return 0;
        }

        @Override
        public void writeToParcel(Parcel parcel, int flags) {
            parcel.writeInt(type);
            parcel.writeInt(priority);
            parcel.writeString(uri);
        }
    }

    //:ace
    @NonNull
    public String getGroupTitle() {
        if(!TextUtils.isEmpty(mGroupTitle)) {
            return mGroupTitle;
        }
        else {
            return getMetaString(META_GROUP_NAME);
        }
    }

    public void updateType(int type) {
        // Set type and update type in media library
        setType(type);
        Medialibrary ml = Medialibrary.getInstance();
        if (mId != 0 && ml.isInitiated())
            nativeSetMediaType(ml, mId, type);
    }

    public void setParsed(boolean parsed) {
        mIsParsed = parsed;
        Medialibrary ml = Medialibrary.getInstance();
        if (mId != 0 && ml.isInitiated())
            nativeSetParsed(ml, mId, parsed);
    }

    public void setP2PLive(int value) {
        mIsLive = value;
        Medialibrary ml = Medialibrary.getInstance();
        if (mId != 0 && ml.isInitiated())
            nativeSetP2PLive(ml, mId, value);
    }

    public void setP2PInfo(String infohash, int fileIndex) {
        mInfohash = infohash;
        mFileIndex = fileIndex;
        Medialibrary ml = Medialibrary.getInstance();
        if (mId != 0 && ml.isInitiated())
            nativeSetP2PInfo(ml, mId, infohash, fileIndex);
    }

    public void resetP2PItem(AceStreamManager playbackManager) {
        mPlaybackUri = null;
        mEngineSession = null;
        if(playbackManager != null) {
            playbackManager.removePlaybackStateCallback(mPlaybackStateCallback);
        }
    }

    public void startP2P(
            @NonNull AceStreamManager manager,
            @NonNull final P2PItemStartListener listener,
            int[] nextFileIndexes) {
        startP2P(manager, listener, nextFileIndexes, -1);
    }

    public void setMediaFile(MediaFilesResponse.MediaFile mediaFile) {
        mMediaFile = mediaFile;
        defineType(true);
    }

    public void startP2P(
            @NonNull final IAceStreamManager manager,
            @NonNull final P2PItemStartListener listener,
            final int[] nextFileIndexes,
            final int streamIndex) {
        mEngineSessionListener = listener;

        final TransportFileDescriptor descriptor;
        try {
            descriptor = getDescriptor();
        }
        catch(TransportFileParsingException e) {
            Log.e(TAG, "Failed to read transport file", e);
            listener.onError(e.getMessage());
            return;
        }

        if(mMediaFile == null) {
            Log.v(TAG, "startP2P: no media file, get from engine");
            if(mUri == null) {
                throw new IllegalStateException("missing descriptor and MRL");
            }

            final int fileIndex = getP2PFileIndex();

            manager.getEngine(new IAceStreamManager.EngineStateCallback() {
                @Override
                public void onEngineConnected(final @NonNull IAceStreamManager manager, @NonNull EngineApi engineApi) {
                    engineApi.getMediaFiles(descriptor, new Callback<MediaFilesResponse>() {
                        @Override
                        public void onSuccess(MediaFilesResponse result) {
                            for(MediaFilesResponse.MediaFile mf: result.files) {
                                if(mf.index == fileIndex) {
                                    mMediaFile = mf;
                                    startP2P(manager, listener, nextFileIndexes, streamIndex);
                                    return;
                                }
                            }
                            Log.e(TAG, "Bad file index: index=" + fileIndex);
                        }

                        @Override
                        public void onError(String err) {
                            listener.onError(err);
                        }
                    });
                }
            });

            return;
        }

        PlaybackData pb = new PlaybackData();
        pb.mediaFile = mMediaFile;
        pb.descriptor = descriptor;
        pb.streamIndex = streamIndex;
        pb.nextFileIndexes = nextFileIndexes;

        pb.outputFormat = manager.getOutputFormatForContent(
                mMediaFile.type,
                mMediaFile.mime,
                null,
                false,
                true);
        pb.useFixedSid = true;
        pb.stopPrevReadThread = 1;
        pb.resumePlayback = false;
        pb.useTimeshift = true;

        manager.addPlaybackStateCallback(mPlaybackStateCallback);
        manager.initEngineSession(pb, new EngineSessionStartListener() {
            @Override
            public void onSuccess(EngineSession session) {
                mPlaybackUri = Uri.parse(session.playbackUrl);
                mEngineSession = session;
                listener.onSessionStarted(session);
            }

            @Override
            public void onError(String error) {
                listener.onError(error);
            }
        });
    }

    public Uri getMetaUri() {
        if(mMediaFile != null) {
            return Uri.parse("acestream:?infohash=" + mMediaFile.infohash + "&file_index=" + mMediaFile.index);
        }
        else {
            return mUri;
        }
    }

    public TransportFileDescriptor getDescriptor() throws TransportFileParsingException {
        if(mDescriptor == null) {
            Log.v(TAG, "getDescriptor: no descriptor, parse from MRL");
            if(mUri == null) {
                throw new TransportFileParsingException("missing descriptor and MRL");
            }
            mDescriptor = TransportFileDescriptor.fromMrl(AceStream.context().getContentResolver(), mUri);
        }
        return mDescriptor;
    }

    public MediaFilesResponse.MediaFile getMediaFile() {
        return mMediaFile;
    }

    public String getMime(){
        return mMediaFile == null ? null : mMediaFile.mime;
    }

    public static MediaWrapper fromJson(String data) {
        try {
            JSONObject root = new JSONObject(data);
            MediaWrapper mw;
            if(root.has("uri")) {
                mw = new MediaWrapper(Uri.parse(root.getString("uri")));
            }
            else if(root.has("mediaFile") && root.has("transportDescriptor")) {
                mw = new MediaWrapper(
                        TransportFileDescriptor.fromJson(root.getString("transportDescriptor")),
                        MediaFilesResponse.MediaFile.fromJson(root.getString("mediaFile")));
            }
            else {
                throw new IllegalStateException("malformed encoded data");
            }
            if(root.has("title")) {
                mw.setTitle(root.getString("title"));
            }
            return mw;
        }
        catch(JSONException e) {
            throw new IllegalStateException("failed to decode from JSON", e);
        }
    }

    public String toJson() {
        JSONObject root = new JSONObject();
        try {
            if (mMediaFile != null) {
                root.put("mediaFile", mMediaFile.toJson());
                root.put("transportDescriptor", mDescriptor.toJson());
            } else {
                root.put("uri", mUri.toString());
            }
            root.put("title", getTitle());
        }
        catch(JSONException e) {
            throw new IllegalStateException("failed to encode to JSON", e);
        }
        return root.toString();
    }

    public boolean isParsed() {
        return mIsParsed;
    }

    public long getParentMediaId() {
        return mParentMediaId;
    }

    public boolean isLive() {
        if(mIsLive == -1) {
            // init
            int live;
            if(mMediaFile != null) {
                live = mMediaFile.isLive() ? 1 : 0;
            }
            else {
                live = 0;
            }
            setP2PLive(live);
        }

        // return cached value
        return mIsLive == 1;
    }

    public boolean isVideo() {
        return getType() == TYPE_VIDEO;
    }

    /**
     * Get P2P item without "index" query param
     *
     * @return Uri
     */
    public Uri getP2PBaseUri() {
        if(!isP2PItem()) {
            return null;
        }

        if(mUri == null) {
            return null;
        }

        try {
            return MiscUtils.removeQueryParameter(mUri, "index");
        }
        catch(UnsupportedEncodingException e) {
            return mUri;
        }
    }

    public int getP2PFileIndex() {
        if(mMediaFile != null) {
            return mMediaFile.index;
        }

        if(mUri == null) {
            return 0;
        }

        int index;
        try {
            index = Integer.parseInt(MiscUtils.getQueryParameter(mUri, "index"));
        }
        catch(NumberFormatException|UnsupportedEncodingException e) {
            index = 0;
        }
        return index;
    }

    public void setUserAgent(String value) {
        mUserAgent = value;
    }

    public String getUserAgent() {
        return mUserAgent;
    }

    public boolean isRemote() {
        return mUri != null
                && !TextUtils.equals(mUri.getScheme(), "file")
                && !TextUtils.equals(mUri.getScheme(), "content")
                && !TextUtils.equals(mUri.getScheme(), "acestream");
    }

    @Override
    public String toString() {
        return String.format(Locale.getDefault(),
                "<MediaWrapper: type=%d id=%d parent=%d p2p=%b uri=%s this=%d title=%s>",
                mType,
                mId,
                mParentMediaId,
                mIsP2P,
                mUri,
                this.hashCode(),
                getTitle());
    }

    public String getInfohash() {
        return mInfohash;
    }

    public int getFileIndex() {
        return mFileIndex;
    }

    /**
     * Return length to use for sorting.
     *
     * @return duration (for reguar items) or file length (for p2p items)
     */
    public long getComparableLength() {
        if(isP2PItem()) {
            return getMetaLong(META_FILE_SIZE);
        }
        else {
            return mLength;
        }
    }

    public void updateFromMediaLibrary() {
        if(mId != 0) {
            // Already from ML
            return;
        }

        Medialibrary ml = Medialibrary.getInstance();
        if(ml == null || !ml.isInitiated()) {
            return;
        }

        MediaWrapper mediaFromLibrary = ml.findMedia(this);
        if(mediaFromLibrary.getId() == 0) {
            return;
        }

        //TODO: copy all metadata
        mId = mediaFromLibrary.getId();
        mParentMediaId = mediaFromLibrary.getParentMediaId();
        mIsParsed = mediaFromLibrary.isParsed();
        mInfohash = mediaFromLibrary.getInfohash();
        mFileIndex = mediaFromLibrary.getP2PFileIndex();
        mType = mediaFromLibrary.getType();
        mTime = mediaFromLibrary.getTime();
        mLength = mediaFromLibrary.getLength();
    }
    ///ace
}