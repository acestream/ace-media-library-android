/*****************************************************************************
 * MainTvActivity.java
 *****************************************************************************
 * Copyright © 2014-2016 VLC authors, VideoLAN and VideoLabs
 * Author: Geoffrey Métais
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston MA 02110-1301, USA.
 *****************************************************************************/
package org.videolan.vlc.gui.tv;

import android.annotation.TargetApi;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.leanback.app.BackgroundManager;
import androidx.leanback.app.BrowseFragment;
import androidx.leanback.app.BrowseSupportFragment;
import androidx.leanback.widget.ArrayObjectAdapter;
import androidx.leanback.widget.HeaderItem;
import androidx.leanback.widget.ListRow;
import androidx.leanback.widget.ListRowPresenter;
import androidx.leanback.widget.OnItemViewClickedListener;
import androidx.leanback.widget.OnItemViewSelectedListener;
import androidx.leanback.widget.Presenter;
import androidx.leanback.widget.Row;
import androidx.leanback.widget.RowPresenter;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.FragmentManager;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.collection.SimpleArrayMap;
import androidx.recyclerview.widget.RecyclerView;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.ProgressBar;
import android.widget.TextView;

import org.acestream.sdk.AceStream;
import org.acestream.sdk.controller.api.response.AuthData;
import org.acestream.sdk.utils.Logger;
import org.acestream.sdk.utils.PermissionUtils;
import org.videolan.libvlc.Media;
import org.videolan.libvlc.MediaPlayer;
import org.videolan.libvlc.util.AndroidUtil;
import org.videolan.medialibrary.Medialibrary;
import org.videolan.medialibrary.Tools;
import org.videolan.medialibrary.interfaces.MediaUpdatedCb;
import org.videolan.medialibrary.media.MediaWrapper;
import org.videolan.vlc.BuildConfig;
import org.videolan.vlc.ExternalMonitor;
import org.videolan.vlc.MediaParsingService;
import org.videolan.vlc.PlaybackService;
import org.videolan.vlc.R;
import org.videolan.vlc.RecommendationsService;
import org.videolan.vlc.StartActivity;
import org.videolan.vlc.VLCApplication;
import org.videolan.vlc.gui.helpers.AudioUtil;
import org.videolan.vlc.gui.helpers.MainActivityHelper;
import org.videolan.vlc.gui.preferences.PreferencesActivity;
import org.videolan.vlc.gui.preferences.PreferencesFragment;
import org.videolan.vlc.gui.tv.audioplayer.AudioPlayerActivity;
import org.videolan.vlc.gui.tv.browser.BaseTvActivity;
import org.videolan.vlc.gui.tv.browser.MusicFragment;
import org.videolan.vlc.gui.tv.browser.VerticalGridActivity;
import org.videolan.vlc.media.MediaDatabase;
import org.videolan.vlc.media.MediaUtils;
import org.videolan.vlc.util.AndroidDevices;
import org.videolan.vlc.util.Constants;
import org.videolan.vlc.util.VLCInstance;

import java.util.List;

@TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR1)
public class MainTvActivity extends BaseTvActivity implements OnItemViewSelectedListener,
        OnItemViewClickedListener, OnClickListener, PlaybackService.Callback, MediaUpdatedCb {

    private static final int NUM_ITEMS_PREVIEW = 5;

    public static final long HEADER_VIDEO = 0;
    public static final long HEADER_CATEGORIES = 1;
    public static final long HEADER_HISTORY = 2;
    public static final long HEADER_NETWORK = 3;
    public static final long HEADER_DIRECTORIES = 4;
    public static final long HEADER_ABOUT = 5;
    public static final long HEADER_STREAM = 6;
    public static final long HEADER_PERMISSIONS = 7;

    public static final long HEADER_P2P_VIDEO = 8;
    public static final long HEADER_P2P_STREAM = 9;
    public static final long HEADER_P2P_AUDIO = 10;
    public static final long HEADER_APPLICATION = 11;

    public static final long ID_SETTINGS_MEDIA_PLAYER = 0;
    public static final long ID_ABOUT = 1;
    public static final long ID_LICENCE = 2;
    public static final long ID_AGREEMENT = 3;
    public static final long ID_PRIVACY_POLICY = 4;
    public static final long ID_PERMISSIONS = 5;
    public static final long ID_CLEAR_CACHE = 6;
    public static final long ID_REPORT_PROBLEM = 7;
    public static final long ID_EXIT = 8;
    public static final long ID_ACCOUNT = 9;
    public static final long ID_SETTINGS_ENGINE = 10;
    public static final long ID_RESTART_APP = 11;

    private static final int ACTIVITY_RESULT_PREFERENCES = 1;

    private static final int REQUEST_CODE_REQUEST_PERMISSIONS = 1;

    public static final String BROWSER_TYPE = "browser_type";

    public static final String TAG = "AS/VLC/MainTv";

    protected BrowseSupportFragment mBrowseFragment;
    private ProgressBar mProgressBar;
    private ArrayObjectAdapter mRowsAdapter = null;
    private ArrayObjectAdapter mVideoAdapter, mCategoriesAdapter, mHistoryAdapter, mBrowserAdapter;
    private final SimpleArrayMap<String, Integer> mVideoIndex = new SimpleArrayMap<>(), mHistoryIndex = new SimpleArrayMap<>();
    private Activity mContext;
    private Object mSelectedItem;
    private AsyncUpdate mUpdateTask;
    private CardPresenter.SimpleCard mNowPlayingCard;
    private BackgroundManager mBackgroundManager;

    //:ace
    private String mLastLogin;
    private TextView mInfo;
    private CardPresenter.SimpleCard mP2PNowPlayingCard;
    private ArrayObjectAdapter mPermissionsAdapter;
    private ArrayObjectAdapter mAboutAdapter;
    private ArrayObjectAdapter mApplicationAdapter;
    private ArrayObjectAdapter mP2PVideoAdapter;
    private ArrayObjectAdapter mP2PStreamsAdapter;
    private ArrayObjectAdapter mP2PAudioAdapter;
    private final SimpleArrayMap<String, Integer> mP2PVideoIndex = new SimpleArrayMap<>();
    private final SimpleArrayMap<String, Integer> mP2PStreamsIndex = new SimpleArrayMap<>();
    private MainActivityHelper mMainActivityHelper;
    private boolean mGotStorageAccess = false;

    private MainActivityHelper.Callback mMainActivityHelperCallback = new MainActivityHelper.Callback() {
        @Override
        public void showProgress(int category, String message) {
            if(mInfo != null) {
                mInfo.setText(message);
                mInfo.setVisibility(View.VISIBLE);
            }
        }

        @Override
        public void hideProgress(int category) {
            if(mInfo != null) {
                mInfo.setVisibility(View.GONE);
                mInfo.setText("");
            }
        }

        @Override
        public void onAuthUpdated(AuthData authData, String login) {
            if(login == null) {
                login = "Not signed in";
            }
            mLastLogin = login;
            if(mApplicationAdapter != null) {
                int pos = -1;
                for(int i = 0; i < mApplicationAdapter.size(); i++) {
                    Object item = mApplicationAdapter.get(i);
                    if(item instanceof CardPresenter.SimpleCard
                            && ((CardPresenter.SimpleCard) item).getId() == ID_ACCOUNT) {
                        ((CardPresenter.SimpleCard) item).description = login;
                        pos = i;
                        break;
                    }
                }

                if(pos != -1) {
                    mApplicationAdapter.notifyArrayItemRangeChanged(pos, 1);
                }
            }
        }

        @Override
        public void onBonusAdsAvailable(boolean available) {
        }
    };
    ///ace

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (!VLCInstance.testCompatibleCPU(this)) {
            finish();
            return;
        }

        mContext = this;
        setContentView(R.layout.tv_main);

        final FragmentManager fragmentManager = getSupportFragmentManager();
        mBrowseFragment = (BrowseSupportFragment) fragmentManager.findFragmentById(
                R.id.browse_fragment);
        mProgressBar = findViewById(R.id.tv_main_progress);
        //:ace
        mInfo = findViewById(R.id.tv_main_info);
        //ace

        // Set display parameters for the BrowseFragment
        mBrowseFragment.setHeadersState(BrowseFragment.HEADERS_ENABLED);
        mBrowseFragment.setTitle(getString(R.string.app_name));
        mBrowseFragment.setBadgeDrawable(ContextCompat.getDrawable(this, R.drawable.ic_acestream));

        //Enable search feature only if we detect Google Play Services.
        if (AndroidDevices.hasPlayServices) {
            mBrowseFragment.setOnSearchClickedListener(this);
            // set search icon color
            mBrowseFragment.setSearchAffordanceColor(getResources().getColor(R.color.main_accent));
        }

        mBrowseFragment.setBrandColor(ContextCompat.getColor(this, R.color.tv_brand));
        mBackgroundManager = BackgroundManager.getInstance(this);
        mBackgroundManager.setAutoReleaseOnStop(false);
        TvUtil.clearBackground(mBackgroundManager);

        //:ace
        mMainActivityHelper = new MainActivityHelper(this, mMainActivityHelperCallback);
        ///ace
    }

    @Override
    public void onConnected(PlaybackService service) {
        super.onConnected(service);
        mService.addCallback(this);
        //:ace
        mMainActivityHelper.onStart();
        ///ace
        if (!mMediaLibrary.isInitiated()) {
            Logger.v(TAG, "playback service connected: media library is not initiated");
            return;
        }

        Logger.v(TAG, "playback service connected");

        /*
         * skip browser and show directly Audio Player if a song is playing
         */
        if (!mMediaLibrary.isWorking() && (mRowsAdapter == null || mRowsAdapter.size() == 0) && PermissionUtils.hasStorageAccess()) {
            update();
        }
        else {
            updateBrowsers();
            updateNowPlayingCard();
            updateP2PNowPlayingCard();
            if (mRowsAdapter != null && mMediaLibrary.isInitiated()) {
                VLCApplication.runBackground(new Runnable() {
                    @Override
                    public void run() {
                        final MediaWrapper[] history = mMediaLibrary.lastMediaPlayed();
                        VLCApplication.runOnMainThread(new Runnable() {
                            @Override
                            public void run() {
                                updateHistory(history);
                            }
                        });
                    }
                });
            }
        }
    }

    @Override
    public void onDisconnected() {
        if (mService != null) mService.removeCallback(this);
        super.onDisconnected();

        //:ace
        mMainActivityHelper.onStop();
        ///ace
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (!mBackgroundManager.isAttached()) mBackgroundManager.attach(getWindow());
        if (mSelectedItem != null) TvUtil.updateBackground(mBackgroundManager, mSelectedItem);

        //:ace
        if(!PermissionUtils.hasStorageAccess()) {
            Log.v(TAG, "onStart: request storage access");
            PermissionUtils.requestStoragePermissions(this, REQUEST_CODE_REQUEST_PERMISSIONS);
        }
        else {
            mGotStorageAccess = true;
        }
        ///ace
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (AndroidDevices.isAndroidTv && !AndroidUtil.isOOrLater) startService(new Intent(this, RecommendationsService.class));
        TvUtil.releaseBackgroundManager(mBackgroundManager);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (mService != null) mService.addCallback(this);
        if (mMediaLibrary.isInitiated()) setmedialibraryListeners();
        else setupMediaLibraryReceiver();

        //:ace
        mMainActivityHelper.onResume();

        // Additional check on resume: access can be granted from system settings
        if(!mGotStorageAccess && PermissionUtils.hasStorageAccess()) {
            onStorageAccessGranted();
        }
        else if(mGotStorageAccess && !PermissionUtils.hasStorageAccess()) {
            onStorageAccessDenied();
        }
        ///ace
    }

    @Override
    protected void onPause() {
        if (mUpdateTask != null) mUpdateTask.cancel(true);
        super.onPause();
        if (mService != null) mService.removeCallback(this);
        mMediaLibrary.removeMediaUpdatedCb();

        //:ace
        mMainActivityHelper.onPause();
        ///ace
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == ACTIVITY_RESULT_PREFERENCES) {
            switch (resultCode) {
                case PreferencesActivity.RESULT_RESCAN:
                    startService(new Intent(Constants.ACTION_RELOAD, null,this, MediaParsingService.class));
                    break;
                case PreferencesActivity.RESULT_RESTART:
                case PreferencesActivity.RESULT_RESTART_APP:
                    Intent intent = getIntent();
                    intent.setClass(this, resultCode == PreferencesActivity.RESULT_RESTART_APP ? StartActivity.class : MainTvActivity.class);
                    finish();
                    startActivity(intent);
                    break;
            }
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if ((keyCode == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE || keyCode == KeyEvent.KEYCODE_BUTTON_Y)
                && mSelectedItem instanceof MediaWrapper) {
            MediaWrapper media = (MediaWrapper) mSelectedItem;
            if (media.getType() != MediaWrapper.TYPE_DIR) return false;
            final Intent intent = new Intent(this, DetailsActivity.class);
            // pass the item information
            intent.putExtra("media", (MediaWrapper) mSelectedItem);
            intent.putExtra("item", new MediaItemDetails(media.getTitle(), media.getArtist(), media.getAlbum(), media.getLocation(), media.getArtworkURL()));
            startActivity(intent);
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    public void update() {
        if (mUpdateTask == null || mUpdateTask.getStatus() == AsyncTask.Status.FINISHED) {
            mUpdateTask = new AsyncUpdate();
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
                mUpdateTask.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
            }
            else {
                mUpdateTask.execute();
            }
        }
    }

    @Override
    public void onMediaUpdated(final MediaWrapper[] mediaList) {
        if (mVideoAdapter == null || mVideoAdapter.size() > NUM_ITEMS_PREVIEW) return;
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                for (MediaWrapper media : mediaList) updateItem(media);
            }
        });
    }

    public void updateItem(MediaWrapper item) {
        if (item == null) return;
        if (mVideoAdapter != null) {
            if (mVideoIndex.containsKey(item.getLocation())) {
                mVideoAdapter.notifyArrayItemRangeChanged(mVideoIndex.get(item.getLocation()), 1);
            } else {
                int position = mVideoAdapter.size();
                mVideoAdapter.add(position, item);
                mVideoIndex.put(item.getLocation(), position);
            }
        }
        if (mHistoryAdapter != null) {
            if (mHistoryIndex.containsKey(item.getLocation())) {
                mHistoryAdapter.notifyArrayItemRangeChanged(mHistoryIndex.get(item.getLocation()), 1);
            }
        }
    }


    @Override
    public void onItemSelected(Presenter.ViewHolder itemViewHolder, Object item, RowPresenter.ViewHolder rowViewHolder, Row row) {
        mSelectedItem = item;
        TvUtil.updateBackground(mBackgroundManager, item);
    }

    @Override
    public void onItemClicked(Presenter.ViewHolder itemViewHolder, Object item, RowPresenter.ViewHolder rowViewHolder, Row row) {
        if (row.getId() == HEADER_CATEGORIES || row.getId() == HEADER_P2P_AUDIO) {
            if (((CardPresenter.SimpleCard)item).getId() == MusicFragment.CATEGORY_NOW_PLAYING){ //NOW PLAYING CARD
                startActivity(new Intent(this, AudioPlayerActivity.class));
                return;
            }
            final CardPresenter.SimpleCard card = (CardPresenter.SimpleCard) item;
            final Intent intent = new Intent(mContext, VerticalGridActivity.class);
            intent.putExtra(BROWSER_TYPE, row.getId());
            intent.putExtra(MusicFragment.AUDIO_CATEGORY, card.getId());
            startActivity(intent);
        } else if (row.getId() == HEADER_ABOUT) {
            long id = ((CardPresenter.SimpleCard) item).getId();
            if (id == ID_ABOUT) startActivity(new Intent(this, org.videolan.vlc.gui.tv.AboutActivity.class));
            else if (id == ID_LICENCE) startActivity(new Intent(this, org.videolan.vlc.gui.tv.LicenceActivity.class));
            else if (id == ID_AGREEMENT) startActivity(new Intent(this, org.videolan.vlc.gui.tv.AgreementActivity.class));
            else if (id == ID_PRIVACY_POLICY) startActivity(new Intent(this, org.videolan.vlc.gui.tv.PrivacyPolicyActivity.class));
        } else if (row.getId() == HEADER_APPLICATION) {
            long id = ((CardPresenter.SimpleCard) item).getId();
            if (id == ID_SETTINGS_MEDIA_PLAYER) {
                Intent intent = new Intent(this, org.videolan.vlc.gui.tv.preferences.PreferencesActivity.class);
                intent.putExtra("category", "player");
                startActivityForResult(intent, ACTIVITY_RESULT_PREFERENCES);
            }
            else if (id == ID_SETTINGS_ENGINE) {
                Intent intent = new Intent(this, org.videolan.vlc.gui.tv.preferences.PreferencesActivity.class);
                intent.putExtra("category", "engine");
                startActivityForResult(intent, ACTIVITY_RESULT_PREFERENCES);
            }
            else if (id == ID_CLEAR_CACHE) mMainActivityHelper.clearEngineCache();
            else if (id == ID_REPORT_PROBLEM) AceStream.openReportProblemActivity(this);
            else if (id == ID_RESTART_APP) mMainActivityHelper.restartApp();
            else if (id == ID_EXIT) mMainActivityHelper.shutdown();
            else if (id == ID_ACCOUNT) AceStream.openProfileActivity(this);
        } else if (row.getId() == HEADER_PERMISSIONS) {
            long id = ((CardPresenter.SimpleCard) item).getId();
            if(id == ID_PERMISSIONS) {
                PermissionUtils.grantStoragePermissions(this, REQUEST_CODE_REQUEST_PERMISSIONS);
            }
        } else TvUtil.openMedia(mContext, item, row);
    }

    @Override
    public void onClick(View v) {
        startActivity(new Intent(mContext, SearchActivity.class));
    }

    @Override
    protected void onParsingServiceStarted() {
        mHandler.sendEmptyMessageDelayed(SHOW_LOADING, 300);
    }

    @Override
    protected void onParsingServiceProgress() {
        if (mProgressBar.getVisibility() == View.GONE) mHandler.sendEmptyMessage(SHOW_LOADING);
    }

    @Override
    protected void onParsingServiceFinished() {
        Logger.v(TAG, "onParsingServiceFinished");
        update();
    }

    //:ace
    @Override
    protected void onMedialibraryUpdated() {
        update();
    }
    ///ace

    private static final int SHOW_LOADING = 0;
    private static final int HIDE_LOADING = 1;
    private Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case SHOW_LOADING:
                    mProgressBar.setVisibility(View.VISIBLE);
                    break;
                case HIDE_LOADING:
                    removeMessages(SHOW_LOADING);
                    mProgressBar.setVisibility(View.GONE);
                default:
                    super.handleMessage(msg);
            }
        }
    };

    private class AsyncUpdate extends AsyncTask<Void, Void, Void> {
        private boolean showHistory;
        private MediaWrapper[] videoList;
        private MediaWrapper[] history;
        //:ace
        private MediaWrapper[] p2pVideoList;
        private MediaWrapper[] p2pStreamsList;
        ///ace

        AsyncUpdate() {}

        @Override
        protected void onPreExecute() {
            showHistory = mSettings.getBoolean(PreferencesFragment.PLAYBACK_HISTORY, true);
            mHandler.sendEmptyMessageDelayed(SHOW_LOADING, 300);
            mHistoryAdapter = null;
        }

        @Override
        protected Void doInBackground(Void... params) {
            if (isCancelled()) return null;
            videoList = mMediaLibrary.getRecentRegularVideos();
            p2pVideoList = mMediaLibrary.getRecentP2PVideos();
            p2pStreamsList = mMediaLibrary.getRecentP2PStreams();
            if (showHistory && !isCancelled()) history = VLCApplication.getMLInstance().lastMediaPlayed();
            return null;
        }

        @Override
        protected void onCancelled() {
            mUpdateTask = null;
        }

        @Override
        protected void onPostExecute(Void result) {
            mHandler.sendEmptyMessage(HIDE_LOADING);
            if (!isVisible()) return;
            if (mRowsAdapter != null) mRowsAdapter.clear();
            else mRowsAdapter = new ArrayObjectAdapter(new ListRowPresenter());
            mHistoryIndex.clear();

            // regular video
            mVideoIndex.clear();
            mVideoAdapter = new ArrayObjectAdapter(new CardPresenter(mContext));
            mVideoAdapter.add(new CardPresenter.SimpleCard(HEADER_VIDEO, "All videos", videoList.length+" "+getString(R.string.videos), R.drawable.ic_video_collection_big));
            if (videoList.length > 0) {
                final int size = Math.min(NUM_ITEMS_PREVIEW, videoList.length);
                for (int i = 0; i < size; ++i) {
                    Tools.setMediaDescription(videoList[i]);
                    mVideoAdapter.add(videoList[i]);
                    mVideoIndex.put(videoList[i].getLocation(), i);
                }
            }
            mRowsAdapter.add(new ListRow(
                    new HeaderItem(HEADER_VIDEO, getString(R.string.video)),
                    mVideoAdapter));

            // regular audio
            mCategoriesAdapter = new ArrayObjectAdapter(new CardPresenter(mContext));
            updateNowPlayingCard();
            mCategoriesAdapter.add(new CardPresenter.SimpleCard(MusicFragment.CATEGORY_ARTISTS, getString(R.string.artists), R.drawable.ic_artist_big));
            mCategoriesAdapter.add(new CardPresenter.SimpleCard(MusicFragment.CATEGORY_ALBUMS, getString(R.string.albums), R.drawable.ic_album_big));
            mCategoriesAdapter.add(new CardPresenter.SimpleCard(MusicFragment.CATEGORY_GENRES, getString(R.string.genres), R.drawable.ic_genre_big));
            mCategoriesAdapter.add(new CardPresenter.SimpleCard(MusicFragment.CATEGORY_SONGS, getString(R.string.songs), R.drawable.ic_song_big));
            mRowsAdapter.add(new ListRow(
                    new HeaderItem(HEADER_CATEGORIES, getString(R.string.audio)),
                    mCategoriesAdapter));

            // p2p video
            mP2PVideoIndex.clear();
            mP2PVideoAdapter = new ArrayObjectAdapter(new CardPresenter(mContext));
            mP2PVideoAdapter.add(new CardPresenter.SimpleCard(HEADER_P2P_VIDEO, getString(R.string.all_torrents), p2pVideoList.length+" "+getString(R.string.videos), R.drawable.ic_video_collection_big));
            if (p2pVideoList.length > 0) {
                final int size = Math.min(NUM_ITEMS_PREVIEW, p2pVideoList.length);
                for (int i = 0; i < size; ++i) {
                    Tools.setMediaDescription(p2pVideoList[i]);
                    mP2PVideoAdapter.add(p2pVideoList[i]);
                    mP2PVideoIndex.put(p2pVideoList[i].getLocation(), i);
                }
            }
            mRowsAdapter.add(new ListRow(
                    new HeaderItem(HEADER_P2P_VIDEO, getString(R.string.video_torrents)),
                    mP2PVideoAdapter));

            // p2p streams
            mP2PStreamsIndex.clear();
            mP2PStreamsAdapter = new ArrayObjectAdapter(new CardPresenter(mContext));
            mP2PStreamsAdapter.add(new CardPresenter.SimpleCard(HEADER_P2P_STREAM, getString(R.string.all_live_streams), p2pStreamsList.length+" "+getString(R.string.streams), R.drawable.ic_video_collection_big));
            if (p2pStreamsList.length > 0) {
                final int size = Math.min(NUM_ITEMS_PREVIEW, p2pStreamsList.length);
                for (int i = 0; i < size; ++i) {
                    Tools.setMediaDescription(p2pStreamsList[i]);
                    mP2PStreamsAdapter.add(p2pStreamsList[i]);
                    mP2PStreamsIndex.put(p2pStreamsList[i].getLocation(), i);
                }
            }
            mRowsAdapter.add(new ListRow(
                    new HeaderItem(HEADER_P2P_STREAM, getString(R.string.live_streams)),
                    mP2PStreamsAdapter));

            // p2p audio
            mP2PAudioAdapter = new ArrayObjectAdapter(new CardPresenter(mContext));
            updateP2PNowPlayingCard();
            mP2PAudioAdapter.add(new CardPresenter.SimpleCard(MusicFragment.CATEGORY_P2P_SONGS, getString(R.string.songs), R.drawable.ic_song_big));
            mRowsAdapter.add(new ListRow(
                    new HeaderItem(HEADER_P2P_AUDIO, getString(R.string.audio_torrents)),
                    mP2PAudioAdapter));

            //Browser section
            mBrowserAdapter = new ArrayObjectAdapter(new CardPresenter(mContext));
            final HeaderItem browserHeader = new HeaderItem(HEADER_NETWORK, getString(R.string.browsing));
            updateBrowsers();
            mRowsAdapter.add(new ListRow(browserHeader, mBrowserAdapter));

            // application menu
            mApplicationAdapter = new ArrayObjectAdapter(new CardPresenter(mContext));
            mApplicationAdapter.add(new CardPresenter.SimpleCard(ID_SETTINGS_MEDIA_PLAYER, getString(R.string.media_player_settings), R.drawable.ic_menu_preferences_big));
            mApplicationAdapter.add(new CardPresenter.SimpleCard(ID_SETTINGS_ENGINE, getString(R.string.acestream_server_settings), R.drawable.ic_menu_preferences_big));
            //TODO: set icons
            mApplicationAdapter.add(new CardPresenter.SimpleCard(ID_ACCOUNT, getString(R.string.lbl_account), mLastLogin, 0));
            mApplicationAdapter.add(new CardPresenter.SimpleCard(ID_CLEAR_CACHE, getString(R.string.menu_clearcache), null));
            mApplicationAdapter.add(new CardPresenter.SimpleCard(ID_REPORT_PROBLEM, getString(R.string.report_problem), null));
            mApplicationAdapter.add(new CardPresenter.SimpleCard(ID_RESTART_APP, getString(R.string.restart_application), null));
            mApplicationAdapter.add(new CardPresenter.SimpleCard(ID_EXIT, getString(R.string.menu_quit), null));
            mRowsAdapter.add(new ListRow(new HeaderItem(HEADER_APPLICATION, getString(R.string.application)), mApplicationAdapter));

            // about menu
            mAboutAdapter = new ArrayObjectAdapter(new CardPresenter(mContext));
            mAboutAdapter.add(new CardPresenter.SimpleCard(ID_ABOUT, getString(R.string.about), getString(R.string.app_name)+" "+ BuildConfig.VERSION_NAME, R.drawable.ic_acestream));
            //TODO: set icons
            mAboutAdapter.add(new CardPresenter.SimpleCard(ID_LICENCE, getString(R.string.licence), null));
            mAboutAdapter.add(new CardPresenter.SimpleCard(ID_AGREEMENT, getString(R.string.agreement), null));
            mAboutAdapter.add(new CardPresenter.SimpleCard(ID_PRIVACY_POLICY, getString(R.string.privacy_policy), null));
            mRowsAdapter.add(new ListRow(new HeaderItem(HEADER_ABOUT, getString(R.string.about)), mAboutAdapter));

            //History
            if (showHistory && !Tools.isArrayEmpty(history)) updateHistory(history);

            // set adapter
            if (mBrowseFragment.getSelectedPosition() >= mRowsAdapter.size()) {
                mBrowseFragment.setSelectedPosition(0);
            }
            mBrowseFragment.setAdapter(mRowsAdapter);

            // add a listener for selected items
            mBrowseFragment.setOnItemViewClickedListener(MainTvActivity.this);
            mBrowseFragment.setOnItemViewSelectedListener(MainTvActivity.this);
        }
    }

    @MainThread
    private synchronized void updateHistory(MediaWrapper[] history) {
        if (history == null || history.length == 0 || mRowsAdapter == null) return;
        final boolean createAdapter = mHistoryAdapter == null;
        if (createAdapter) mHistoryAdapter = new ArrayObjectAdapter(new CardPresenter(mContext));
        else mHistoryAdapter.clear();
        if (!mSettings.getBoolean(PreferencesFragment.PLAYBACK_HISTORY, true)) return;
        for (int i = 0; i < history.length; ++i) {
            final MediaWrapper item = history[i];
            mHistoryAdapter.add(item);
            mHistoryIndex.put(item.getLocation(), i);
        }
        if (createAdapter) {
            final HeaderItem historyHeader = new HeaderItem(HEADER_HISTORY, getString(R.string.history));
            mRowsAdapter.add(Math.max(0, mRowsAdapter.size()-3), new ListRow(historyHeader, mHistoryAdapter));
        }
    }

    private void updateBrowsers() {
        if (mBrowserAdapter == null) return;
        mBrowserAdapter.clear();
        final List<MediaWrapper> directories = AndroidDevices.getMediaDirectoriesList();
        if (!AndroidDevices.showInternalStorage && !directories.isEmpty()) directories.remove(0);
        for (MediaWrapper directory : directories)
            mBrowserAdapter.add(new CardPresenter.SimpleCard(HEADER_DIRECTORIES, directory.getTitle(), R.drawable.ic_menu_folder_big, directory.getUri()));

        if (ExternalMonitor.isLan()) {
            try {
                final List<MediaWrapper> favs = MediaDatabase.getInstance().getAllNetworkFav();
                mBrowserAdapter.add(new CardPresenter.SimpleCard(HEADER_NETWORK, getString(R.string.network_browsing), R.drawable.ic_menu_network_big));
                mBrowserAdapter.add(new CardPresenter.SimpleCard(HEADER_STREAM, getString(R.string.open_link), R.drawable.ic_menu_stream_big));

                if (!favs.isEmpty()) {
                    for (MediaWrapper fav : favs) {
                        fav.setDescription(fav.getUri().getScheme());
                        mBrowserAdapter.add(fav);
                    }
                }
            } catch (Exception ignored) {} //SQLite can explode
        }
        mBrowserAdapter.notifyArrayItemRangeChanged(0, mBrowserAdapter.size());
    }

    protected void refresh() {
        mMediaLibrary.reload();
    }

    @Override
    public void onNetworkConnectionChanged(boolean connected) {
        updateBrowsers();
    }

    @Override
    public void updateProgress(){}

    @Override
    public
    void onMediaEvent(Media.Event event) {}

    @Override
    public
    void onMediaPlayerEvent(MediaPlayer.Event event){
        switch (event.type) {
            case MediaPlayer.Event.Opening:
                updateNowPlayingCard();
                updateP2PNowPlayingCard();
                break;
            case MediaPlayer.Event.Stopped:
                if (mNowPlayingCard != null)
                    mCategoriesAdapter.remove(mNowPlayingCard);
                if (mP2PNowPlayingCard != null)
                    mP2PAudioAdapter.remove(mP2PNowPlayingCard);
                break;
        }
    }

    public void updateNowPlayingCard () {
        if (mService == null) return;
        boolean hasmedia = mService.hasMedia();
        final boolean canSwitch = mService.canSwitchToVideo();
        final MediaWrapper mw = mService.getCurrentMediaWrapper();
        if(mw != null && mw.isP2PItem()) {
            // handle only regular items here
            hasmedia = false;
        }
        if ((!hasmedia || canSwitch) && mNowPlayingCard != null) {
            mCategoriesAdapter.removeItems(0, 1);
            mNowPlayingCard = null;
        } else if (hasmedia && !canSwitch){
            final String display = MediaUtils.getMediaTitle(mw) + " - " + MediaUtils.getMediaReferenceArtist(MainTvActivity.this, mw);
            VLCApplication.runBackground(new Runnable() {
                @Override
                public void run() {
                    final Bitmap cover = AudioUtil.readCoverBitmap(Uri.decode(mw.getArtworkMrl()), VLCApplication.getAppResources().getDimensionPixelSize(R.dimen.grid_card_thumb_width));
                    VLCApplication.runOnMainThread(new Runnable() {
                        @Override
                        public void run() {
                            if (mNowPlayingCard == null) {
                                if (cover != null) mNowPlayingCard = new CardPresenter.SimpleCard(MusicFragment.CATEGORY_NOW_PLAYING, display, cover);
                                else mNowPlayingCard = new CardPresenter.SimpleCard(MusicFragment.CATEGORY_NOW_PLAYING, display, R.drawable.ic_acestream);
                                mCategoriesAdapter.add(0, mNowPlayingCard);
                            } else {
                                mNowPlayingCard.setId(MusicFragment.CATEGORY_NOW_PLAYING);
                                mNowPlayingCard.setName(display);
                                if (cover != null) mNowPlayingCard.setImage(cover);
                                else mNowPlayingCard.setImageId(R.drawable.ic_acestream);
                            }
                            mCategoriesAdapter.notifyArrayItemRangeChanged(0,1);
                        }
                    });
                }
            });

        }
    }

    public void updateP2PNowPlayingCard () {
        if (mService == null) return;
        boolean hasmedia = mService.hasMedia();
        final boolean canSwitch = mService.canSwitchToVideo();
        final MediaWrapper mw = mService.getCurrentMediaWrapper();
        if(mw != null && !mw.isP2PItem()) {
            // handle only p2p items here
            hasmedia = false;
        }
        if ((!hasmedia || canSwitch) && mP2PNowPlayingCard != null) {
            mP2PAudioAdapter.removeItems(0, 1);
            mP2PNowPlayingCard = null;
        } else if (hasmedia && !canSwitch){
            final String display = MediaUtils.getMediaTitle(mw) + " - " + MediaUtils.getMediaReferenceArtist(MainTvActivity.this, mw);
            VLCApplication.runBackground(new Runnable() {
                @Override
                public void run() {
                    final Bitmap cover = AudioUtil.readCoverBitmap(Uri.decode(mw.getArtworkMrl()), VLCApplication.getAppResources().getDimensionPixelSize(R.dimen.grid_card_thumb_width));
                    VLCApplication.runOnMainThread(new Runnable() {
                        @Override
                        public void run() {
                            if (mP2PNowPlayingCard == null) {
                                if (cover != null) mP2PNowPlayingCard = new CardPresenter.SimpleCard(MusicFragment.CATEGORY_NOW_PLAYING, display, cover);
                                else mP2PNowPlayingCard = new CardPresenter.SimpleCard(MusicFragment.CATEGORY_NOW_PLAYING, display, R.drawable.ic_acestream);
                                mP2PAudioAdapter.add(0, mP2PNowPlayingCard);
                            } else {
                                mP2PNowPlayingCard.setId(MusicFragment.CATEGORY_NOW_PLAYING);
                                mP2PNowPlayingCard.setName(display);
                                if (cover != null) mP2PNowPlayingCard.setImage(cover);
                                else mP2PNowPlayingCard.setImageId(R.drawable.ic_acestream);
                            }
                            mP2PAudioAdapter.notifyArrayItemRangeChanged(0,1);
                        }
                    });
                }
            });

        }
    }

    private void setmedialibraryListeners() {
        mMediaLibrary.setMediaUpdatedCb(this, Medialibrary.FLAG_MEDIA_UPDATED_VIDEO);
    }

    private void setupMediaLibraryReceiver() {
        final BroadcastReceiver libraryReadyReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                Logger.v(TAG, "media library ready");
                LocalBroadcastManager.getInstance(MainTvActivity.this).unregisterReceiver(this);
                setmedialibraryListeners();
                update();
            }
        };
        LocalBroadcastManager.getInstance(this).registerReceiver(libraryReadyReceiver, new IntentFilter(VLCApplication.ACTION_MEDIALIBRARY_READY));
    }

    //:ace
    private void onStorageAccessGranted() {
        Log.v(TAG, "onStorageAccessGranted");
        boolean firstRun = false;
        boolean upgrade = false;

        Intent intent = getIntent();
        if(intent != null && intent.getBooleanExtra(Constants.EXTRA_UPGRADE, false)) {
            upgrade = true;
            firstRun = intent.getBooleanExtra(Constants.EXTRA_FIRST_RUN, false);
            intent.removeExtra(Constants.EXTRA_UPGRADE);
            intent.removeExtra(Constants.EXTRA_FIRST_RUN);
        }

        Intent serviceIntent = new Intent(Constants.ACTION_INIT, null, this, MediaParsingService.class);
        serviceIntent.putExtra(Constants.EXTRA_FIRST_RUN, firstRun);
        serviceIntent.putExtra(Constants.EXTRA_UPGRADE, upgrade);
        startService(serviceIntent);

        if (mRowsAdapter != null) {
            mRowsAdapter.clear();
        }

        AceStream.onStorageAccessGranted();
        mGotStorageAccess = true;
        mHelper.onStart();

        showUi();
        //showFragment(R.id.nav_video);

        // Show drawer
        //mDrawerLayout.openDrawer(mNavigationView);
    }

    private void onStorageAccessDenied() {
        Log.v(TAG, "onStorageAccessDenied");
        mGotStorageAccess = false;
        hideUi();

        if (mRowsAdapter != null) {
            mRowsAdapter.clear();
        }
        else {
            mRowsAdapter = new ArrayObjectAdapter(new ListRowPresenter());
        }

        mPermissionsAdapter = new ArrayObjectAdapter(new CardPresenter(mContext));
        final HeaderItem header = new HeaderItem(HEADER_PERMISSIONS, "Permissions");

        mPermissionsAdapter.add(new CardPresenter.SimpleCard(ID_PERMISSIONS, "Grant permissions", R.drawable.ic_menu_preferences_big));
        mRowsAdapter.add(new ListRow(header, mPermissionsAdapter));

        if (mBrowseFragment.getSelectedPosition() >= mRowsAdapter.size()) mBrowseFragment.setSelectedPosition(RecyclerView.NO_POSITION);
        mBrowseFragment.setAdapter(mRowsAdapter);
        mBrowseFragment.setSelectedPosition(0);

        // add a listener for selected items
        mBrowseFragment.setOnItemViewClickedListener(this);
        mBrowseFragment.setOnItemViewSelectedListener(this);
    }

    private void showUi() {
        //TODO: implement
    }

    private void hideUi() {
        //TODO: implement
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String permissions[], @NonNull int[] grantResults) {
        Logger.v(TAG, "onRequestPermissionsResult: requestCode=" + requestCode);
        if(requestCode == REQUEST_CODE_REQUEST_PERMISSIONS) {
            // If request is cancelled, the result arrays are empty.

            int i;
            for(i=0; i<permissions.length; i++) {
                Log.d(TAG, "grant: i=" + i + " permission=" + permissions[i]);
            }
            for(i=0; i<grantResults.length; i++) {
                Log.d(TAG, "grant: i=" + i + " result=" + grantResults[i]);
            }

            if (grantResults.length > 0
                    && grantResults[0] == PackageManager.PERMISSION_GRANTED) {

                Log.d(TAG, "user granted permission");
                onStorageAccessGranted();

            } else {
                Log.d(TAG, "user denied permission");
                onStorageAccessDenied();
            }
        }
    }
    ///ace
}
