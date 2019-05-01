/*****************************************************************************
 * VideoPlayerActivity.java
 *****************************************************************************
 * Copyright © 2011-2017 VLC authors and VideoLAN
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

package org.videolan.vlc.gui.video;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.Activity;
import android.app.KeyguardManager;
import android.app.PictureInPictureParams;
import android.bluetooth.BluetoothA2dp;
import android.bluetooth.BluetoothHeadset;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import androidx.databinding.BindingAdapter;
import androidx.databinding.DataBindingUtil;
import androidx.databinding.ObservableField;
import androidx.databinding.ObservableInt;
import androidx.databinding.ObservableLong;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.media.AudioManager;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.PowerManager;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.provider.Settings.SettingNotFoundException;
import androidx.annotation.MainThread;
import androidx.annotation.NonNull;

import com.google.android.material.snackbar.Snackbar;
import androidx.fragment.app.FragmentManager;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import android.support.v4.media.session.PlaybackStateCompat;
import androidx.core.view.GestureDetectorCompat;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.appcompat.widget.PopupMenu;
import androidx.recyclerview.widget.RecyclerView;
import androidx.appcompat.widget.ViewStubCompat;
import androidx.recyclerview.widget.ItemTouchHelper;
import android.text.TextUtils;
import android.text.format.DateFormat;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.Pair;
import android.util.Rational;
import android.view.Display;
import android.view.GestureDetector;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.Surface;
import android.view.SurfaceView;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnLayoutChangeListener;
import android.view.ViewGroup;
import android.view.ViewGroup.LayoutParams;
import android.view.WindowManager;
import android.view.animation.AnimationUtils;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.SeekBar;
import android.widget.SeekBar.OnSeekBarChangeListener;
import android.widget.TextView;
import android.widget.Toast;

import org.acestream.engine.controller.Callback;
import org.acestream.sdk.AceStream;
import org.acestream.sdk.AceStreamManager;
import org.acestream.sdk.AceStreamManagerActivityHelper;
import org.acestream.sdk.EngineStatus;
import org.acestream.sdk.MediaItem;
import org.acestream.sdk.RemoteDevice;
import org.acestream.sdk.SelectedPlayer;
import org.acestream.sdk.SystemUsageInfo;
import org.acestream.sdk.controller.EngineApi;
import org.acestream.sdk.controller.api.TransportFileDescriptor;
import org.acestream.sdk.controller.api.response.MediaFilesResponse;
import org.acestream.sdk.errors.TransportFileParsingException;
import org.acestream.sdk.interfaces.EngineCallbackListener;
import org.acestream.sdk.interfaces.EngineStatusListener;
import org.acestream.sdk.interfaces.IRemoteDevice;
import org.acestream.sdk.player.api.AceStreamPlayer;
import org.acestream.sdk.utils.Logger;
import org.acestream.sdk.utils.MiscUtils;
import org.jetbrains.annotations.Nullable;
import org.json.JSONException;
import org.videolan.libvlc.IVLCVout;
import org.videolan.libvlc.Media;
import org.videolan.libvlc.MediaPlayer;
import org.videolan.libvlc.util.AndroidUtil;
import org.videolan.medialibrary.Medialibrary;
import org.videolan.medialibrary.Tools;
import org.videolan.medialibrary.media.MediaWrapper;
import org.videolan.medialibrary.media.TrackDescription;
import org.videolan.vlc.BuildConfig;
import org.videolan.vlc.PlaybackService;
import org.videolan.vlc.R;
import org.videolan.vlc.RendererDelegate;
import org.videolan.vlc.VLCApplication;
import org.videolan.vlc.databinding.PlayerHudBinding;
import org.videolan.vlc.gui.MainActivity;
import org.videolan.vlc.gui.PlaybackServiceActivity;
import org.videolan.vlc.gui.audio.PlaylistAdapter;
import org.videolan.vlc.gui.browser.FilePickerActivity;
import org.videolan.vlc.gui.browser.FilePickerFragment;
import org.videolan.vlc.gui.dialogs.AdvOptionsDialog;
import org.videolan.vlc.gui.dialogs.RenderersDialog;
import org.videolan.vlc.gui.helpers.OnRepeatListener;
import org.videolan.vlc.gui.helpers.SwipeDragItemTouchHelperCallback;
import org.videolan.vlc.gui.helpers.UiTools;
import org.videolan.vlc.gui.helpers.hf.StoragePermissionsDelegate;
import org.videolan.vlc.gui.preferences.PreferencesActivity;
import org.videolan.vlc.gui.tv.audioplayer.AudioPlayerActivity;
import org.videolan.vlc.interfaces.IPlaybackSettingsController;
import org.videolan.vlc.media.MediaDatabase;
import org.videolan.vlc.media.MediaPlayerEvent;
import org.videolan.vlc.media.MediaUtils;
import org.videolan.vlc.util.AceStreamUtils;
import org.videolan.vlc.util.AndroidDevices;
import org.videolan.vlc.util.Constants;
import org.videolan.vlc.util.FileUtils;
import org.videolan.vlc.util.Permissions;
import org.videolan.vlc.util.RendererItemWrapper;
import org.videolan.vlc.util.Strings;
import org.videolan.vlc.util.SubtitlesDownloader;
import org.videolan.vlc.util.Util;
import org.videolan.vlc.util.VLCInstance;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import org.videolan.vlc.util.VLCOptions;

import static org.acestream.sdk.Constants.DEINTERLACE_MODE_DISABLED;
import static org.acestream.sdk.Constants.EXTRA_ERROR_MESSAGE;
import static org.acestream.sdk.Constants.EXTRA_FILE_INDEX;
import static org.acestream.sdk.Constants.EXTRA_SEEK_ON_START;
import static org.acestream.sdk.Constants.EXTRA_TRANSPORT_DESCRIPTOR;

public class VideoPlayerActivity extends AppCompatActivity
        implements
        IVLCVout.Callback,
        PlaybackService.Client.Callback,
        IVLCVout.OnNewVideoLayoutListener, IPlaybackSettingsController,
        PlaybackService.Callback,PlaylistAdapter.IPlayer,
        OnClickListener, StoragePermissionsDelegate.CustomActionController,
        ScaleGestureDetector.OnScaleGestureListener, RendererDelegate.RendererListener,
        RendererDelegate.RendererPlayer, EngineCallbackListener
{
    private final static String TAG = "VLC/Player";

    private final static String ACTION_RESULT = Strings.buildPkgString("player.result");
    private final static String EXTRA_POSITION = "extra_position";
    private final static String EXTRA_DURATION = "extra_duration";
    private final static String EXTRA_URI = "extra_uri";
    private final static int RESULT_CONNECTION_FAILED = RESULT_FIRST_USER + 1;
    private final static int RESULT_PLAYBACK_ERROR = RESULT_FIRST_USER + 2;
    private final static int RESULT_VIDEO_TRACK_LOST = RESULT_FIRST_USER + 3;
    private static final float DEFAULT_FOV = 80f;
    private static final float MIN_FOV = 20f;
    private static final float MAX_FOV = 150f;

    private final static int REQUEST_CODE_SELECT_PLAYER = 1;
    private final static int REQUEST_CODE_SELECT_SUBTITLES = 2;

    // hardcoded debug flags
    private static final boolean DEBUG_LOG_ENGINE_STATUS = false;
    private static final boolean DEBUG_TIPS = false;
    private static final boolean DEBUG_AUDIO_OUTPUT_SWITCHER = false;

    private final PlaybackServiceActivity.Helper mHelper = new PlaybackServiceActivity.Helper(this, this);
    protected PlaybackService mService;
    private Medialibrary mMedialibrary;
    private SurfaceView mSurfaceView = null;
    private SurfaceView mSubtitlesSurfaceView = null;
    protected DisplayManager mDisplayManager;
    private View mRootView;
    private FrameLayout mSurfaceFrame;
    private Uri mUri;
    //:ace
    private String mLastRemoteDeviceId = null;
    private final List<Runnable> mPlaybackManagerOnReadyQueue = new ArrayList<>();
    //
    protected EngineApi mEngineService = null;

    private PowerManager.WakeLock mWakeLock;
    protected EngineStatus mLastEngineStatus = null;
    protected boolean mIsLive;
    protected long freezeEngineStatusAt = 0;
    protected long freezeEngineStatusFor = 0;
    protected long freezeLiveStatusAt = 0;
    protected long freezeLivePosAt = 0;
    protected EngineStatus.LivePosition mLastLivePos = null;
    protected AceStreamManager mAceStreamManager = null;
    protected boolean mPictureInPictureMode = false;
    protected boolean mSwitchingToAnotherPlayer = false;
    protected boolean mSwitchingToAnotherRenderer = false;
    protected boolean mStoppingOnDeviceDisconnect = false;
    // exit activity when player stops
    protected boolean mExitOnStop = true;
    // we have stopped to restart player
    protected boolean mRestartingPlayer = false;
    protected boolean mWasStopped = false;
    protected TextView mEngineStatus;
    protected TextView mDebugInfo;

    protected boolean mIsStarted = false;
    protected boolean mIsPaused = true;

    // player ui
    private RelativeLayout mPlayerUiContainer;

    // casting info message
    private TextView mCastingInfo;

    private final static int FREEZE_LIVE_STATUS_FOR = 5000;
    private final static int FREEZE_LIVE_POS_FOR = 5000;
    // remove this later and avoid using global vars for this purpose
    protected long mSeekOnStart = -1;
    private TransportFileDescriptor mDescriptor = null;
    private MediaFilesResponse mMediaFiles = null;
    ///ace
    private boolean mAskResume = true;
    private boolean mIsRtl;
    private ScaleGestureDetector mScaleGestureDetector;
    private GestureDetectorCompat mDetector = null;

    private ImageView mPlaylistToggle;
    private ImageView mPipToggle;
    protected ImageView mSwitchPlayer;
    private ImageView mAdvOptionsButton;
    private RecyclerView mPlaylist;
    private PlaylistAdapter mPlaylistAdapter;

    public static final int SURFACE_BEST_FIT = 0;
    public static final int SURFACE_FIT_SCREEN = 1;
    public static final int SURFACE_FILL = 2;
    public static final int SURFACE_16_9 = 3;
    public static final int SURFACE_4_3 = 4;
    public static final int SURFACE_ORIGINAL = 5;
    protected int mCurrentSize;

    //:ace
    public static class VlcState {
		public static final int IDLE = 0;
		public static final int OPENING = 1;
		public static final int PLAYING = 3;
		public static final int PAUSED = 4;
		public static final int STOPPING = 5;
		public static final int ENDED = 6;
		public static final int ERROR = 7;
	}
    ///ace

    protected SharedPreferences mSettings;

    private static final int TOUCH_FLAG_AUDIO_VOLUME = 1;
    private static final int TOUCH_FLAG_BRIGHTNESS = 1 << 1;
    private static final int TOUCH_FLAG_SEEK = 1 << 2;
    private int mTouchControls = 0;

    /** Overlay */
    private ActionBar mActionBar;
    private ViewGroup mActionBarView;
    private View mOverlayBackground;
    private static final int OVERLAY_TIMEOUT = 4000;
    protected static final int OVERLAY_INFINITE = -1;
    private static final int FADE_OUT = 1;
    private static final int SHOW_PROGRESS = 2;
    private static final int FADE_OUT_INFO = 3;
    private static final int START_PLAYBACK = 4;
    private static final int AUDIO_SERVICE_CONNECTION_FAILED = 5;
    private static final int RESET_BACK_LOCK = 6;
    private static final int CHECK_VIDEO_TRACKS = 7;
    private static final int SHOW_INFO = 9;
    private static final int HIDE_INFO = 10;
    private static final int START_PLAYBACK_NO_CHECK = 11;

    private boolean mDragging;
    private boolean mShowing;
    private boolean mShowingDialog;
    private DelayState mPlaybackSetting = DelayState.OFF;
    private TextView mSysTime;
    private TextView mBattery;
    private TextView mInfo;
    private View mOverlayInfo;
    private View mVerticalBar;
    private View mVerticalBarProgress;
    private View mVerticalBarBoostProgress;
    private boolean mIsLoading;
    private boolean mIsPlaying = false;
    //:ace
    private boolean mIsBuffering = false;
    private boolean mMediaStartedPlaying = false;
    ///ace
    private ProgressBar mLoading;
    private ImageView mNavMenu;
    private ImageView mRendererBtn;
    private ImageView mPlaybackSettingPlus;
    private ImageView mPlaybackSettingMinus;
    protected boolean mEnableCloneMode;
    private static volatile boolean sDisplayRemainingTime;
    private int mScreenOrientation;
    private int mScreenOrientationLock;
    private int mCurrentScreenOrientation;
    private String KEY_REMAINING_TIME_DISPLAY = "remaining_time_display";
    private String KEY_BLUETOOTH_DELAY = "key_bluetooth_delay";
    private long mSpuDelay = 0L;
    private long mAudioDelay = 0L;
    private boolean mRateHasChanged = false;
    private int mCurrentAudioTrack = -2, mCurrentSpuTrack = -2;

    private boolean mIsLocked = false;
    /* -1 is a valid track (Disable) */
    private int mLastAudioTrack = -2;
    private int mLastSpuTrack = -2;
    private int mOverlayTimeout = 0;
    private boolean mLockBackButton = false;
    boolean mWasPaused = false;
    private long mSavedTime = -1;
    private float mSavedRate = 1.f;

    /**
     * For uninterrupted switching between audio and video mode
     */
    private boolean mSwitchingView;
    private boolean mHasSubItems = false;

    // size of the video
    private int mVideoHeight;
    private int mVideoWidth;
    private int mVideoVisibleHeight;
    private int mVideoVisibleWidth;
    private int mSarNum;
    private int mSarDen;

    //Volume
    private AudioManager mAudioManager;
    private int mAudioMax;
    private boolean audioBoostEnabled;
    private boolean mMute = false;
    private int mVolSave;
    private float mVol;
    private float mOriginalVol;
    private Toast warningToast;

    //Touch Events
    private static final int TOUCH_NONE = 0;
    private static final int TOUCH_VOLUME = 1;
    private static final int TOUCH_BRIGHTNESS = 2;
    private static final int TOUCH_MOVE = 3;
    private static final int TOUCH_SEEK = 4;
    private int mTouchAction = TOUCH_NONE;
    private int mSurfaceYDisplayRange, mSurfaceXDisplayRange;
    private float mFov;
    private float mInitTouchY, mTouchY =-1f, mTouchX=-1f;

    //stick event
    private static final int JOYSTICK_INPUT_DELAY = 300;
    private long mLastMove;

    // Brightness
    private boolean mIsFirstBrightnessGesture = true;
    private float mRestoreAutoBrightness = -1f;

    // Tracks & Subtitles
    private TrackDescription[] mAudioTracksList;
    private TrackDescription[] mVideoTracksList;
    private TrackDescription[] mSubtitleTracksList;
    /**
     * Used to store a selected subtitle; see onActivityResult.
     * It is possible to have multiple custom subs in one session
     * (just like desktop VLC allows you as well.)
     */
    private final ArrayList<String> mSubtitleSelectedFiles = new ArrayList<>();

    /**
     * Flag to indicate whether the media should be paused once loaded
     * (e.g. lock screen, or to restore the pause state)
     */
    private boolean mPlaybackStarted = false;

    // Tips
    private View mOverlayTips;
    private static final String PREF_TIPS_SHOWN = "video_player_tips_shown";

    // Navigation handling (DVD, Blu-Ray...)
    private int mMenuIdx = -1;
    private boolean mIsNavMenu = false;

    /* for getTime and seek */
    private long mForcedTime = -1;
    private long mLastTime = -1;

    private OnLayoutChangeListener mOnLayoutChangeListener;
    private AlertDialog mAlertDialog;

    private final DisplayMetrics mScreen = new DisplayMetrics();

    protected boolean mIsBenchmark = false;

    //:ace
    private AceStreamManager.CastResultListener mCastResultListener = new AceStreamManager.CastResultListener() {
        private boolean mCancelled = false;
        private boolean mWaiting = true;

        @Override
        public void onSuccess() {
            Log.d(TAG, "castResult:success: cancelled=" + mCancelled + " this=" + this);
            mWaiting = false;
        }

        @Override
        public void onSuccess(RemoteDevice device, SelectedPlayer selectedPlayer) {
            onSuccess();
        }

        @Override
        public void onError(final String error) {
            Log.d(TAG, "castResult:error: cancelled=" + mCancelled + " this=" + this + " error=" + error);
            mWaiting = false;
            if (!mCancelled) {
                if(mAceStreamManager != null) {
                    mAceStreamManager.stopEngineSession(false);
                }
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        showStatusOverlay(true, error);
                    }
                });
            }
        }

        @Override
        public boolean isWaiting() {
            return mWaiting;
        }

        @Override
        public void onDeviceConnected(@NonNull RemoteDevice device) {
            Log.d(TAG, "castResult:device connected: this=" + this + " device=" + device);
            if(mCastingInfo != null) {
                mCastingInfo.setText("Casting to " + device.getName());
                updateStopButton();
            }
        }

        @Override
        public void onDeviceDisconnected(@NonNull RemoteDevice device) {
            Logger.v(TAG, "castResult:device disconnected: this=" + this + " device=" + device);
        }

        @Override
        public void onCancel() {
            Log.d(TAG, "castResult:cancelled: this=" + this);
            mWaiting = false;
            mCancelled = true;
        }
    };

    private AceStreamManagerActivityHelper.ActivityCallback mActivityCallback = new AceStreamManagerActivityHelper.ActivityCallback() {
        @Override
        public void onResumeConnected() {
            Log.v(TAG, "onResumeConnected");

            mAceStreamManager.addEngineStatusListener(mEngineStatusListener);
            mAceStreamManager.addEngineCallbackListener(VideoPlayerActivity.this);
        }

        @Override
        public void onConnected(AceStreamManager pm) {
            Log.v(TAG, "connected playback manager");
            mAceStreamManager = pm;
            mAceStreamManager.addCallback(mPlaybackManagerCallback);
            mAceStreamManager.startEngine();
            mAceStreamManager.setOurPlayerActive(true);

            for(Runnable runnable: mPlaybackManagerOnReadyQueue) {
                runnable.run();
            }
            mPlaybackManagerOnReadyQueue.clear();
        }

        @Override
        public void onDisconnected() {
            Log.v(TAG, "disconnected playback manager");
            if(mAceStreamManager != null) {
                mAceStreamManager.removeCallback(mPlaybackManagerCallback);
                mAceStreamManager.setOurPlayerActive(false);
                mAceStreamManager = null;
            }
        }
    };

    final protected AceStreamManagerActivityHelper mActivityHelper = new AceStreamManagerActivityHelper(this, mActivityCallback);

    private AceStreamManager.Callback mPlaybackManagerCallback = new AceStreamManager.Callback() {
        @Override
        public void onEngineConnected(EngineApi service) {
            Logger.v(TAG, "onEngineConnected: paused=" + mIsPaused + " service=" + mEngineService);
            if(mEngineService == null) {
                mEngineService = service;
                if(!mIsPaused) {
                    handleIntent(getIntent());
                }
            }
        }

        @Override
        public void onEngineFailed() {
            Logger.v(TAG, "onEngineFailed");
            setEngineStatus(EngineStatus.fromString("engine_failed"));
        }

        @Override
        public void onEngineUnpacking() {
            Logger.v(TAG, "onEngineUnpacking");
            setEngineStatus(EngineStatus.fromString("engine_unpacking"));
        }

        @Override
        public void onEngineStarting() {
            Logger.v(TAG, "onEngineStarting");
            setEngineStatus(EngineStatus.fromString("engine_starting"));
        }

        @Override
        public void onEngineStopped() {
            Logger.v(TAG, "onEngineStopped");
            finish();
        }

        @Override
        public void onBonusAdsAvailable(boolean available) {
        }
    };

    private EngineStatusListener mEngineStatusListener = new EngineStatusListener() {
        @Override
        public void onEngineStatus(final EngineStatus status, final IRemoteDevice remoteDevice) {
            boolean freeze = false;
            if(freezeEngineStatusAt > 0 && freezeEngineStatusFor > 0) {
                long age = System.currentTimeMillis() - freezeEngineStatusAt;
                if (age < freezeEngineStatusFor) {
                    freeze = true;
                }
            }

            if(DEBUG_LOG_ENGINE_STATUS) {
                Log.v(TAG, "engine_status:"
                        + " freeze=" + freeze
                        + " this=" + status.playbackSessionId
                        + " curr=" + (mAceStreamManager.getEngineSession() == null ? "null" : mAceStreamManager.getEngineSession().playbackSessionId)
                        + " status=" + status.toString()
                );
            }

            if(!freeze) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            processEngineStatus(status);
                        } catch (Exception e) {
                            Log.e(TAG, "Failed to process engine status");
                        }
                    }
                });
            }
        }

        @Override
        public boolean updatePlayerActivity() {
            return !mIsPaused;
        }
    };

    private Runnable mEnsurePlayerIsPlayingTask = new Runnable() {
        @Override
        public void run() {
            if(mService != null) {
                int state = mService.getPlaybackState();
                Log.v(TAG, "ensure playing: state=" + state);
                if (state != PlaybackStateCompat.STATE_PLAYING && state != PlaybackStateCompat.STATE_PAUSED) {
                    Log.d(TAG, "ensure playing: do play");
                    mService.play();
                }
            }
        }
    };

    @SuppressLint("RestrictedApi")
    @Override
    @TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR1)
    protected void onCreate(Bundle savedInstanceState) {
        boolean launchedFromRecents = (getIntent().getFlags() & Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY) != 0;
        Log.v(TAG, "onCreate: launchedFromRecents=" + launchedFromRecents);
        super.onCreate(savedInstanceState);

        if(launchedFromRecents) {
            // Reset intent when launched from recents
            setIntent(new Intent());
        }

        if (!VLCInstance.testCompatibleCPU(this)) {
            exit(RESULT_CANCELED);
            return;
        }

        mSettings = PreferenceManager.getDefaultSharedPreferences(this);

        if (!VLCApplication.showTvUi()) {
            mTouchControls = (mSettings.getBoolean("enable_volume_gesture", true) ? TOUCH_FLAG_AUDIO_VOLUME : 0)
                    + (mSettings.getBoolean("enable_brightness_gesture", true) ? TOUCH_FLAG_BRIGHTNESS : 0)
                    + (mSettings.getBoolean("enable_double_tap_seek", true) ? TOUCH_FLAG_SEEK : 0);
        }

        /* Services and miscellaneous */
        mAudioManager = (AudioManager) getApplicationContext().getSystemService(AUDIO_SERVICE);
        mAudioMax = mAudioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
        audioBoostEnabled = mSettings.getBoolean("audio_boost", false);

        mEnableCloneMode = mSettings.getBoolean("enable_clone_mode", false);
        mDisplayManager = new DisplayManager(this, mEnableCloneMode);
        setContentView(mDisplayManager.isPrimary() ? R.layout.player : R.layout.player_remote_control);

        /** initialize Views an their Events */
        mActionBar = getSupportActionBar();
        mActionBar.setDisplayShowHomeEnabled(false);
        mActionBar.setDisplayShowTitleEnabled(false);
        mActionBar.setBackgroundDrawable(null);
        mActionBar.setDisplayShowCustomEnabled(true);
        mActionBar.setCustomView(R.layout.player_action_bar);

        mRootView = findViewById(R.id.player_root);
        mActionBarView = (ViewGroup) mActionBar.getCustomView();

        mPlaylistToggle = (ImageView) findViewById(R.id.playlist_toggle);
        mPlaylist = (RecyclerView) findViewById(R.id.video_playlist);
        mPipToggle = findViewById(R.id.pip_toggle);

        mSwitchPlayer = findViewById(R.id.switch_player);
        mSwitchPlayer.setOnClickListener(this);

        mAdvOptionsButton = findViewById(R.id.player_overlay_adv_function);
        mAdvOptionsButton.setOnClickListener(this);

        if (mDisplayManager.isPrimary() && AndroidDevices.hasPiP && !AndroidDevices.isDex(this)) {
            mPipToggle.setOnClickListener(this);
            mPipToggle.setVisibility(View.VISIBLE);
        }

        mEngineStatus = findViewById(R.id.engine_status);
        mDebugInfo = findViewById(R.id.debug_info);

        // player UI
        mPlayerUiContainer = findViewById(R.id.player_ui_container);

        mScreenOrientation = Integer.valueOf(
                mSettings.getString("screen_orientation", "99" /*SCREEN ORIENTATION SENSOR*/));

        mSurfaceView = (SurfaceView) findViewById(R.id.player_surface);
        mSubtitlesSurfaceView = (SurfaceView) findViewById(R.id.subtitles_surface);

        mSubtitlesSurfaceView.setZOrderMediaOverlay(true);
        mSubtitlesSurfaceView.getHolder().setFormat(PixelFormat.TRANSLUCENT);

        mSurfaceFrame = (FrameLayout) findViewById(R.id.player_surface_frame);

        /* Loading view */
        mLoading = findViewById(R.id.player_overlay_loading);
        dimStatusBar(true);

        mSwitchingView = false;

        mAskResume = mSettings.getBoolean("dialog_confirm_resume", false);
        sDisplayRemainingTime = mSettings.getBoolean(KEY_REMAINING_TIME_DISPLAY, false);
        // Clear the resume time, since it is only used for resumes in external
        // videos.
        final SharedPreferences.Editor editor = mSettings.edit();
        editor.putLong(PreferencesActivity.VIDEO_RESUME_TIME, -1);
        // Also clear the subs list, because it is supposed to be per session
        // only (like desktop VLC). We don't want the custom subtitle files
        // to persist forever with this video.
        editor.putString(PreferencesActivity.VIDEO_SUBTITLE_FILES, null);
        // Paused flag - per session too, like the subs list.
        editor.remove(PreferencesActivity.VIDEO_PAUSED);
        editor.apply();

        final IntentFilter filter = new IntentFilter();
        if (mBattery != null)
            filter.addAction(Intent.ACTION_BATTERY_CHANGED);
        filter.addAction(VLCApplication.SLEEP_INTENT);
        registerReceiver(mReceiver, filter);

        this.setVolumeControlStream(AudioManager.STREAM_MUSIC);

        // 100 is the value for screen_orientation_start_lock
        setRequestedOrientation(getScreenOrientation(mScreenOrientation));
        // Extra initialization when no secondary display is detected
        if (mDisplayManager.isPrimary()) {
            if (DEBUG_TIPS
                    || (!VLCApplication.showTvUi()
                        && !mSettings.getBoolean(PREF_TIPS_SHOWN, false))) {
                ((ViewStubCompat) findViewById(R.id.player_overlay_tips)).inflate();
                mOverlayTips = findViewById(R.id.overlay_tips_layout);
            }

            //Set margins for TV overscan
            if (VLCApplication.showTvUi()) {
                int hm = getResources().getDimensionPixelSize(R.dimen.tv_overscan_horizontal);
                int vm = getResources().getDimensionPixelSize(R.dimen.tv_overscan_vertical);

                final RelativeLayout.LayoutParams lp = (RelativeLayout.LayoutParams) mPlayerUiContainer.getLayoutParams();
                lp.setMargins(hm, 0, hm, vm);
                mPlayerUiContainer.setLayoutParams(lp);
            }
        }
        else {
            //:ace
            mCastingInfo = findViewById(R.id.casting_info);
            ///ace
        }

        getWindowManager().getDefaultDisplay().getMetrics(mScreen);
        mSurfaceYDisplayRange = Math.min(mScreen.widthPixels, mScreen.heightPixels);
        mSurfaceXDisplayRange = Math.max(mScreen.widthPixels, mScreen.heightPixels);
        mCurrentScreenOrientation = getResources().getConfiguration().orientation;
        if (mIsBenchmark) {
            mCurrentSize = SURFACE_FIT_SCREEN;
        } else {
            mCurrentSize = mSettings.getInt(PreferencesActivity.VIDEO_RATIO, SURFACE_BEST_FIT);
        }
        mMedialibrary = VLCApplication.getMLInstance();
        mIsRtl = TextUtils.getLayoutDirectionFromLocale(Locale.getDefault()) == View.LAYOUT_DIRECTION_RTL;

        //:ace
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        PowerManager powerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);
        if(powerManager != null) {
            mWakeLock = powerManager.newWakeLock(
                    PowerManager.FULL_WAKE_LOCK | PowerManager.ACQUIRE_CAUSES_WAKEUP,
                    "acestream:player_wake_lock");
            if (mWakeLock != null) {
                // Acquire lock for 5 seconds
                // Need this to wake up.
                // After wake up FLAG_KEEP_SCREEN_ON will prevent sleeping.
                mWakeLock.acquire(5000);
            }
        }

        if(showDebugInfo()) {
            mDebugInfo.setVisibility(View.VISIBLE);
        }
        ///ace
    }

    @Override
    protected void onResume() {
        Log.v(TAG, "onResume");
        overridePendingTransition(0,0);
        super.onResume();
        mShowingDialog = false;
        /*
         * Set listeners here to avoid NPE when activity is closing
         */
        setListeners(true);

        if (mIsLocked && mScreenOrientation == 99)
            setRequestedOrientation(mScreenOrientationLock);

        //:ace
        mIsPaused = false;
        mActivityHelper.onResume();

        // Handle intent if already connected to engine.
        // Otherwise do nothing - intent will be handled when connected.
        if(mEngineService != null) {
            handleIntent(getIntent());
        }

        checkRemotePlaybackIntent();
        ///ace
    }

    private void checkRemotePlaybackIntent() {
        if(TextUtils.equals(getIntent().getAction(), Constants.PLAY_REMOTE_DEVICE)) {
            try {
                final MediaWrapper media = getIntent().getParcelableExtra(Constants.PLAY_EXTRA_MEDIA_WRAPPER);
                final SelectedPlayer selectedPlayer = SelectedPlayer.fromJson(getIntent().getStringExtra(Constants.PLAY_EXTRA_SELECTED_PLAYER));
                final boolean fromStart = getIntent().getBooleanExtra(Constants.PLAY_EXTRA_FROM_START, false);

                media.updateFromMediaLibrary();
                Logger.v(TAG, "checkRemotePlaybackIntent: start playback on remote device: media=" + media + " selectedPlayer=" + selectedPlayer + " fromStart=" + fromStart);

                // Reset intent because we want to handle it only once.
                setIntent(new Intent());

                if(mAskResume && !fromStart && media.getTime() > 0) {
                    Runnable resumeYes = new Runnable() {
                        @Override
                        public void run() {
                            startRemotePlayback(media, selectedPlayer, false);
                        }
                    };
                    Runnable resumeNo = new Runnable() {
                        @Override
                        public void run() {
                            startRemotePlayback(media, selectedPlayer, true);
                        }
                    };
                    showConfirmResumeDialog(resumeYes, resumeNo);
                }
                else {
                    startRemotePlayback(media, selectedPlayer, fromStart);
                }


            }
            catch(JSONException e) {
                Log.e(TAG, "error", e);
            }
        }
    }

    private void startRemotePlayback(final MediaWrapper media, final SelectedPlayer selectedPlayer, final boolean fromStart) {
        // Start remote session (acecast or chromecast)
        runWhenPlaybackManagerReady(new Runnable() {
            @Override
            public void run() {
                Logger.v(TAG, "pm-ready: start playback on remote device: media=" + media + " selectedPlayer=" + selectedPlayer);

                if(!mService.hasRenderer()) {
                    Logger.v(TAG, "no renderer, resume local playback");
                    return;
                }

                // Need to call this to init callbacks etc
                startPlayback(1);

                // init UI
                newItemSelected(media);

                // start remote player
                final int forceResume = fromStart ? 0 : 1;
                final long savedTime = media.getTime();
                Logger.v(TAG, "pm-ready: start playback on remote device: fromStart=" + fromStart + " savedTime=" + savedTime);

                try {
                    MediaItem mediaItem = new MediaItem(
                            VideoPlayerActivity.this,
                            media.getUri(),
                            media.getTitle(),
                            media.getId(),
                            media.getDescriptor(),
                            media.getMediaFile(),
                            new MediaItem.UpdateListener() {
                                @Override
                                public void onTitleChange(MediaItem item, String title) {
                                }

                                @Override
                                public void onP2PInfoChanged(MediaItem item, String infohash, int fileIndex) {
                                    media.setP2PInfo(infohash, fileIndex);
                                }

                                @Override
                                public void onLiveChanged(MediaItem item, int live) {
                                    media.setP2PLive(live);
                                    mIsLive = media.isLive();
                                }
                            });
                    mediaItem.setSavedTime(savedTime);
                    mAceStreamManager.startPlayer(
                            VideoPlayerActivity.this,
                            selectedPlayer,
                            mediaItem,
                            -1,
                            mCastResultListener,
                            forceResume
                    );
                }
                catch(TransportFileParsingException e) {
                    Log.e(TAG, "failed to get descriptor", e);
                }
            }
        });
    }

    private void setListeners(boolean enabled) {
        if (mHudBinding != null) mHudBinding.playerOverlaySeekbar.setOnSeekBarChangeListener(enabled ? mSeekListener : null);
        if (mNavMenu != null) mNavMenu.setOnClickListener(enabled ? this : null);
        if (mRendererBtn != null) {
            if (enabled) {
                RendererDelegate.INSTANCE.addListener(this);
                RendererDelegate.INSTANCE.addPlayerListener(this);
            } else {
                RendererDelegate.INSTANCE.removeListener(this);
                RendererDelegate.INSTANCE.removePlayerListener(this);
            }
            mRendererBtn.setOnClickListener(enabled ? this : null);
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        Logger.v(TAG, "onNewIntent: action=" + intent.getAction());
        setIntent(intent);
        // Reset last remote device id when player is restored by intent. This means that new
        // playback is started by user rather then player is just restored from recent apps.
        mLastRemoteDeviceId = null;
        if (mService != null && mService.getCurrentMediaWrapper() != null) {
            Uri uri = intent.hasExtra(Constants.PLAY_EXTRA_ITEM_LOCATION) ?
                    (Uri) intent.getExtras().getParcelable(Constants.PLAY_EXTRA_ITEM_LOCATION) : intent.getData();

            if (uri == null) {
                Logger.v(TAG, "onNewIntent: empty uri");
                return;
            }

            if(uri.equals(mUri) && mPlaybackStarted) {
                Logger.v(TAG, "onNewIntent: same uri: uri=" + uri);
                return;
            }

            Logger.v(TAG, "onNewIntent: playing=" + mPlaybackStarted + " uri=" + uri + " mUri=" + mUri);

            if (TextUtils.equals("file", uri.getScheme()) && uri.getPath() != null && uri.getPath().startsWith("/sdcard")) {
                Uri convertedUri = FileUtils.convertLocalUri(uri);
                if (convertedUri == null)
                    return;
                else if (convertedUri.equals(mUri) && mPlaybackStarted)
                    return;
                else
                    uri = convertedUri;
            }
            //:ace
            newPlayback(uri);
            ///ace
        }
        else {
            Logger.v(TAG, "onNewIntent: no service or current mw");
        }
    }

    protected void newItemSelected() {
        newItemSelected(null);
    }

    protected void newItemSelected(@Nullable MediaWrapper media) {
        Logger.vv(TAG, "newItemSelected: media=" + media);

        if(media == null && mService != null) {
            media = mService.getCurrentMediaWrapper();
        }

        if(media != null) {
            mTitle.set(media.getTitle());
            mIsLive = media.isLive();
        }

        if (mPlaylist.getVisibility() == View.VISIBLE) {
            mPlaylistAdapter.setCurrentIndex(mService.getCurrentMediaPosition());
            mPlaylist.setVisibility(View.GONE);
        }
        showOverlay();
        initUI();
    }

    protected void newPlayback(Uri uri) {
        boolean viewsAttached = areViewsAttached();
        Log.v(TAG, "newPlayback: uri=" + uri + " viewsAttached=" + viewsAttached);

        mExitOnStop = true;
        mMediaStartedPlaying = false;

        mUri = uri;
        newItemSelected();
        setPlaybackParameters();
        mForcedTime = mLastTime = -1;
        updateTimeValues();
        enableSubs();

        if(!viewsAttached) {
            startPlayback();
        }
    }

    private void updateTimeValues() {
        if(mService == null) return;
        MediaWrapper media = mService.getCurrentMediaWrapper();
        boolean isLive = media != null && media.isLive();
        if(!isLive) {
            int time = (int) getTime();
            mProgress.set(time);
            mCurrentTime.set(time);
            mMediaLength.set(mService.getLength());
        }
    }

    @SuppressLint("NewApi")
    @Override
    protected void onPause() {
        Log.v(TAG, "onPause: finishing=" + isFinishing());
        if (isFinishing())
            overridePendingTransition(0, 0);
        else
            hideOverlay(true);

        super.onPause();
        setListeners(false);

        /* Stop the earliest possible to avoid vout error */
        if (!isInPictureInPictureMode()) {
            if (isFinishing() ||
                    (AndroidUtil.isNougatOrLater && !AndroidUtil.isOOrLater //Video on background on Nougat Android TVs
                            && AndroidDevices.isAndroidTv && !requestVisibleBehind(true)))
                stopPlayback(true);
            else if (!isFinishing() && !mShowingDialog && "2".equals(mSettings.getString(PreferencesActivity.KEY_VIDEO_APP_SWITCH, "0")) && isInteractive()) {
                switchToPopup();
            }
        }

        //:ace
        if(!isInPictureInPictureMode()) {
            mIsPaused = true;
            mActivityHelper.onPause();
            if(mAceStreamManager != null) {
                mAceStreamManager.removeEngineStatusListener(mEngineStatusListener);
                mAceStreamManager.removeEngineCallbackListener(this);
            }
        }
        ///ace
    }

    @SuppressLint("NewApi")
    public void switchToPopup() {
        Logger.v(TAG, "switchToPopup");
        final MediaWrapper mw = mService != null ? mService.getCurrentMediaWrapper() : null;
        if (mw == null) return;
        if (AndroidDevices.hasPiP) {
            try {
                if (AndroidUtil.isOOrLater)
                    try {
                        final int height = mVideoHeight != 0 ? mVideoHeight : mw.getHeight();
                        final int width = Math.min(mVideoWidth != 0 ? mVideoWidth : mw.getWidth(), (int) (height * 2.39f));
                        enterPictureInPictureMode(new PictureInPictureParams.Builder().setAspectRatio(new Rational(width, height)).build());
                    } catch (IllegalArgumentException e) { // Fallback with default parameters
                        //noinspection deprecation
                        enterPictureInPictureMode();
                    }
                else {
                    //noinspection deprecation
                    enterPictureInPictureMode();
                }
            }
            catch(IllegalStateException e) {
                // Some devices throw IllegalStateException("enterPictureInPictureMode: Device doesn't support picture-in-picture mode.")
                Logger.wtf(TAG, "Failed to enter pip", e);
                AceStream.toast("Failed to enter PiP");
            }
        } else {
            Logger.v(TAG, "switchToPopup: pip is not supported");
        }
    }

    @TargetApi(Build.VERSION_CODES.KITKAT_WATCH)
    private boolean isInteractive() {
        final PowerManager pm = (PowerManager) getApplicationContext().getSystemService(Context.POWER_SERVICE);
        return pm != null && (AndroidUtil.isLolliPopOrLater ? pm.isInteractive() : pm.isScreenOn());
    }

    @Override
    public void onVisibleBehindCanceled() {
        super.onVisibleBehindCanceled();
        stopPlayback(true);
        exitOK();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        getWindowManager().getDefaultDisplay().getMetrics(mScreen);
        mCurrentScreenOrientation = newConfig.orientation;
        mSurfaceYDisplayRange = Math.min(mScreen.widthPixels, mScreen.heightPixels);
        mSurfaceXDisplayRange = Math.max(mScreen.widthPixels, mScreen.heightPixels);
        resetHudLayout();
    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR1)
    public void resetHudLayout() {
        if (mHudBinding == null) return;
        final RelativeLayout.LayoutParams layoutParams = (RelativeLayout.LayoutParams)mHudBinding.playerOverlayButtons.getLayoutParams();
        final int orientation = getScreenOrientation(100);
        final boolean portrait = orientation == ActivityInfo.SCREEN_ORIENTATION_PORTRAIT ||
                orientation == ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT;
        final int endOf = RelativeLayout.END_OF;
        final int startOf = RelativeLayout.START_OF;
        final int endAlign = RelativeLayout.ALIGN_PARENT_END;
        final int startAlign = RelativeLayout.ALIGN_PARENT_START;
        layoutParams.addRule(startAlign, portrait ? 1 : 0);
        layoutParams.addRule(endAlign, portrait ? 1 : 0);
        layoutParams.addRule(RelativeLayout.BELOW, portrait ? R.id.player_overlay_length : R.id.progress_container);
        layoutParams.addRule(endOf, portrait ? 0 : R.id.player_overlay_time);
        layoutParams.addRule(startOf, portrait ? 0 : R.id.player_overlay_length);
        mHudBinding.playerOverlayButtons.setLayoutParams(layoutParams);
    }

    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    @Override
    protected void onStart() {
        Log.v(TAG, "onStart: started=" + mIsStarted + " wasPaused=" + mWasPaused);
        super.onStart();
        mHandler.removeCallbacks(mDelayedInteralStop);

        if(!mIsStarted) {
            internalStart();
        }
        else {
            attachViews();

            if (mService != null && !mWasPaused) {
                mService.play();
            } else {
                showOverlay();
            }
        }
    }

    private void internalStart() {
        Logger.v(TAG, "internalStart");
        mIsStarted = true;
        mHelper.onStart();
        if (mSettings.getBoolean("save_brightness", false)) {
            float brightness = mSettings.getFloat("brightness_value", -1f);
            if (brightness != -1f)
                setWindowBrightness(brightness);
        }

        final IntentFilter filter = new IntentFilter(Constants.PLAY_FROM_SERVICE);
        filter.addAction(Constants.EXIT_PLAYER);
        filter.addAction(Constants.ACTION_P2P_STARTING);
        filter.addAction(Constants.ACTION_P2P_STARTED);
        filter.addAction(Constants.ACTION_P2P_SESSION_STARTED);
        filter.addAction(Constants.ACTION_P2P_FAILED);
        LocalBroadcastManager.getInstance(this).registerReceiver(
                mServiceReceiver, filter);

        if (mBtReceiver != null) {
            final IntentFilter btFilter = new IntentFilter(BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED);
            btFilter.addAction(BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED);
            registerReceiver(mBtReceiver, btFilter);
        }
        UiTools.setViewVisibility(mOverlayInfo, View.INVISIBLE);
    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR1)
    @Override
    protected void onStop() {
        boolean isPlaying = false;
        if(mPlaybackStarted) {
            isPlaying = true;
        }
        else if(mService != null) {
            isPlaying = mService.isVideoPlaying();
        }
        Log.v(TAG, "onStop: finishing=" + isFinishing() + " pip=" + mPictureInPictureMode + " playing=" + isPlaying);

        super.onStop();

        if(mWakeLock != null && mWakeLock.isHeld()) {
            mWakeLock.release();
        }

        boolean isFinishing = isFinishing();
        if(!isFinishing) {
            if(mPictureInPictureMode) {
                // onStop on PIP mode means that user has pressed "close" button in PiP window.
                // Finish activity in such case.
                isFinishing = true;
                finish();
            }
        }

        if(mService != null) {
            if(mService.isVideoPlaying()) {
                mWasPaused = !mService.isPlaying();
            }
            Logger.v(TAG, "onStop: mWasPaused=" + mWasPaused);
        }

        if (mAlertDialog != null && mAlertDialog.isShowing())
            mAlertDialog.dismiss();

        restoreBrightness();
        cleanUI();

        if (mSubtitlesGetTask != null)
            mSubtitlesGetTask.cancel(true);

        int delayInterval = 0;
        if(!isFinishing && mService != null && mService.isRemoteDeviceConnected()) {
            RemoteDevice device = mService.getCurrentRemoteDevice();
            if(device != null) {
                mLastRemoteDeviceId = device.getId();
            }
        }

        if(delayInterval > 0) {
            Logger.v(TAG, "schedule delayed internal stop in " + delayInterval + " ms");
            mHandler.postDelayed(mDelayedInteralStop, delayInterval);
        }
        else {
            if (mDisplayManager.isPrimary() && !isFinishing && mService != null && mService.isPlaying()
                    && "1".equals(mSettings.getString(PreferencesActivity.KEY_VIDEO_APP_SWITCH, "0"))) {
                switchToAudioMode(false);
            }

            internalStop();
        }
    }

    private Runnable mDelayedInteralStop = new Runnable() {
        @Override
        public void run() {
            Logger.v(TAG, "run delayed internal stop");
            internalStop();
        }
    };

    private void internalStop() {
        boolean isFinishing = isFinishing();
        Logger.v(TAG, "internalStop: started=" + mIsStarted + " isFinishing=" + isFinishing);
        if(!mIsStarted) {
            return;
        }

        mIsStarted = false;

        LocalBroadcastManager.getInstance(this).unregisterReceiver(mServiceReceiver);

        if (mBtReceiver != null)
            unregisterReceiver(mBtReceiver);

        //:ace
        mWasStopped = !isFinishing;
        boolean stopEngineSession = true;
        if(!isFinishing) {
            stopEngineSession = false;
            if(mAceStreamManager != null) {
                mAceStreamManager.setPlayerActivityTimeout(60);
            }
        }
        ///ace

        stopPlayback(stopEngineSession);

        final SharedPreferences.Editor editor = mSettings.edit();
        if (mSavedTime != -1) editor.putLong(PreferencesActivity.VIDEO_RESUME_TIME, mSavedTime);

        // Save selected subtitles
        String subtitleList_serialized = null;
        synchronized (mSubtitleSelectedFiles) {
            if(mSubtitleSelectedFiles.size() > 0) {
                if (BuildConfig.DEBUG) Log.d(TAG, "Saving selected subtitle files");
                ByteArrayOutputStream bos = new ByteArrayOutputStream();
                try {
                    ObjectOutputStream oos = new ObjectOutputStream(bos);
                    oos.writeObject(mSubtitleSelectedFiles);
                    subtitleList_serialized = bos.toString();
                } catch(IOException ignored) {}
            }
        }
        editor.putString(PreferencesActivity.VIDEO_SUBTITLE_FILES, subtitleList_serialized);
        editor.apply();

        if(!intentHasTransportDescriptor()) {
            // Clear Intent to restore playlist on activity restart
            // But don't clear engine intent (with transport descriptor).
            setIntent(new Intent());
        }

        //:ace
        if(mAceStreamManager != null) {
            mAceStreamManager.setOurPlayerActive(false);
            mAceStreamManager.unregisterCastResultListener(mCastResultListener);
            mAceStreamManager.removeEngineStatusListener(mEngineStatusListener);
            mAceStreamManager.removeEngineCallbackListener(this);
        }

        // Need to reset to correctly reconnect after stop
        mEngineService = null;
        ///ace

        if (mService != null) mService.removeCallback(this);
        mHelper.onStop();
    }

    private void restoreBrightness() {
        if (mRestoreAutoBrightness != -1f) {
            int brightness = (int) (mRestoreAutoBrightness*255f);
            Settings.System.putInt(getContentResolver(),
                    Settings.System.SCREEN_BRIGHTNESS,
                    brightness);
            Settings.System.putInt(getContentResolver(),
                    Settings.System.SCREEN_BRIGHTNESS_MODE,
                    Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC);
        }
        // Save brightness if user wants to
        if (mSettings.getBoolean("save_brightness", false)) {
            float brightness = getWindow().getAttributes().screenBrightness;
            if (brightness != -1f) {
                SharedPreferences.Editor editor = mSettings.edit();
                editor.putFloat("brightness_value", brightness);
                editor.apply();
            }
        }
    }

    @Override
    protected void onDestroy() {
        Log.v(TAG, "onDestroy: started=" + mIsStarted);
        super.onDestroy();
        unregisterReceiver(mReceiver);

        // Dismiss the presentation when the activity is not visible.
        mDisplayManager.release();
        mAudioManager = null;

        // Ensure that activity is stopped.
        if(mIsStarted) {
            internalStop();
        }
    }

    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    private void surfaceFrameAddLayoutListener(boolean add) {
        if (mSurfaceFrame == null
                || add == (mOnLayoutChangeListener != null))
            return;

        if (add) {
            mOnLayoutChangeListener = new View.OnLayoutChangeListener() {
                private final Runnable mRunnable = new Runnable() {
                    @Override
                    public void run() {
                        changeSurfaceLayout();
                    }
                };
                @Override
                public void onLayoutChange(View v, int left, int top, int right,
                                           int bottom, int oldLeft, int oldTop, int oldRight, int oldBottom) {
                    if (left != oldLeft || top != oldTop || right != oldRight || bottom != oldBottom) {
                        /* changeSurfaceLayout need to be called after the layout changed */
                        mHandler.removeCallbacks(mRunnable);
                        mHandler.post(mRunnable);
                    }
                }
            };
            mSurfaceFrame.addOnLayoutChangeListener(mOnLayoutChangeListener);
            changeSurfaceLayout();
        }
        else {
            mSurfaceFrame.removeOnLayoutChangeListener(mOnLayoutChangeListener);
            mOnLayoutChangeListener = null;
        }
    }

    protected void startPlayback() {
        startPlayback(-1);
    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR1)
    protected void startPlayback(int fromStart) {
        Log.v(TAG, "startPlayback: started=" + mPlaybackStarted + " service=" + mService);
        /* start playback only when audio service and both surfaces are ready */
        if (mPlaybackStarted || mService == null)
            return;

        mSavedRate = 1.0f;
        mSavedTime = -1;
        mPlaybackStarted = true;

        attachViews();
        initUI();

        if(fromStart == -1) {
            loadMedia();
        }
        else {
            loadMedia(fromStart == 1);
        }
    }

    protected void attachViews() {
        if (mService == null)
            return;

        IVLCVout vlcVout = mService.getVLCVout();
        if (vlcVout.areViewsAttached()) {
            if (mService.isPlayingPopup()) {
                Logger.v(TAG, "attachViews: already attached and playing popup");
                mService.stop();
                vlcVout = mService.getVLCVout();
            } else {
                Logger.v(TAG, "attachViews: already attached, detach before reattaching");
                vlcVout.detachViews();
            }
        }
        else {
            Logger.v(TAG, "attachViews: currently not attached");
        }
        final DisplayManager.SecondaryDisplay sd = mDisplayManager.getPresentation();
        vlcVout.setVideoView(sd != null ? sd.getSurfaceView() : mSurfaceView);
        vlcVout.setSubtitlesView(sd != null ? sd.getSubtitlesSurfaceView() : mSubtitlesSurfaceView);
        vlcVout.addCallback(this);
        vlcVout.attachViews(this);
        mService.setVideoTrackEnabled(true);
    }

    private void initPlaylistUi() {
        if (mService.hasPlaylist()) {
            mHasPlaylist = true;
            mPlaylistAdapter = new PlaylistAdapter(this);
            mPlaylistAdapter.setService(mService);
            final LinearLayoutManager layoutManager = new LinearLayoutManager(this);
            layoutManager.setOrientation(RecyclerView.VERTICAL);
            mPlaylist.setLayoutManager(layoutManager);
            mPlaylistToggle.setVisibility(View.VISIBLE);
            mHudBinding.playlistPrevious.setVisibility(View.VISIBLE);
            mHudBinding.playlistNext.setVisibility(View.VISIBLE);
            mPlaylistToggle.setOnClickListener(VideoPlayerActivity.this);

            final ItemTouchHelper.Callback callback =  new SwipeDragItemTouchHelperCallback(mPlaylistAdapter);
            final ItemTouchHelper touchHelper = new ItemTouchHelper(callback);
            touchHelper.attachToRecyclerView(mPlaylist);
        }
    }

    private void initUI() {

        /* Dispatch ActionBar touch events to the Activity */
        mActionBarView.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                onTouchEvent(event);
                return true;
            }
        });

        surfaceFrameAddLayoutListener(true);

        /* Listen for changes to media routes. */
        mDisplayManager.mediaRouterAddCallback(true);

        if (mRootView != null) mRootView.setKeepScreenOn(true);
    }

    private void setPlaybackParameters() {
        if (mAudioDelay != 0L && mAudioDelay != mService.getAudioDelay())
            mService.setAudioDelay(mAudioDelay);
        else if (mBtReceiver != null && (mAudioManager.isBluetoothA2dpOn() || mAudioManager.isBluetoothScoOn()))
            toggleBtDelay(true);
        if (mSpuDelay != 0L && mSpuDelay != mService.getSpuDelay())
            mService.setSpuDelay(mSpuDelay);
    }

    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    private void stopPlayback(boolean stopEngineSession) {
        stopPlayback(stopEngineSession,  false);
    }

    private void stopPlayback(boolean stopEngineSession, boolean stopRemoteDevice) {
        Log.v(TAG, "stopPlayback: started=" + mPlaybackStarted
                + " stopEngineSession=" + stopEngineSession
                + " stopRemoteDevice=" + stopRemoteDevice
                + " switchingToAnotherPlayer=" + mSwitchingToAnotherPlayer
                + " switchingToAnotherRenderer=" + mSwitchingToAnotherRenderer
                + " stoppingOnDeviceDisconnect=" + mStoppingOnDeviceDisconnect
        );

        //:ace
        setPlaying(false);

        if(stopEngineSession) {
            RemoteDevice device = (mService == null) ? null : mService.getCurrentRemoteDevice();
            if(mSwitchingToAnotherPlayer) {
                mSwitchingToAnotherPlayer = false;
                Log.v(TAG, "stopPlayback: skip stop engine session, switching to another player");
            }
            else if(mStoppingOnDeviceDisconnect) {
                mStoppingOnDeviceDisconnect = false;
                Log.v(TAG, "stopPlayback: skip stop engine session, stopping after device disconnect");
            }
            else if(mAceStreamManager == null) {
                Log.v(TAG, "stopPlayback: skip stop engine session, no PM");
            }
            else if(device != null && !device.isAceCast()) {
                Log.v(TAG, "stopPlayback: skip stop engine session, on renderer");
            }
            else {
                Log.v(TAG, "stopPlayback: stop engine session");
                mAceStreamManager.stopEngineSession(true);
            }
        }
        ///ace

        if (mPlaybackStarted && mDisplayManager.isOnRenderer() && !isFinishing()) {
            Log.v(TAG, "stopPlayback: on renderer and not finishing");
            mPlaybackStarted = false;
        }

        if(mPlaybackStarted) {
            if (!isFinishing()) {
                mCurrentAudioTrack = mService.getAudioTrack();
                mCurrentSpuTrack = mService.getSpuTrack();
            }

            if (mMute) mute(false);

            mPlaybackStarted = false;

            mService.setVideoTrackEnabled(false);
            mService.removeCallback(this);

            mHandler.removeCallbacksAndMessages(null);

            final IVLCVout vlcVout = mService.getVLCVout();
            vlcVout.removeCallback(this);
            if (vlcVout.areViewsAttached()) {
                Logger.v(TAG, "stopPlayback: view are attached, detach now");
                vlcVout.detachViews();
            }
            else {
                Logger.v(TAG, "stopPlayback: view are not attached");
            }
            if (mService.hasMedia() && mSwitchingView) {
                Log.v(TAG, "stopPlayback: switching view: uri=" + mUri);
                mService.getCurrentMediaWrapper().addFlags(MediaWrapper.MEDIA_FORCE_AUDIO);
                mService.showWithoutParse(mService.getCurrentMediaPosition());
                return;
            }

            if (isSeekable()) {
                mSavedTime = getTime();
                long length = mService.getLength();
                //remove saved position if in the last 5 seconds
                if (length - mSavedTime < 5000)
                    mSavedTime = 0;
                else
                    mSavedTime -= 2000; // go back 2 seconds, to compensate loading time
            }

            mSavedRate = mService.getRate();
            mRateHasChanged = mSavedRate != 1.0f;
        }

        if(mService != null) {
            if (!stopRemoteDevice && mService.isRemoteDeviceConnected()) {
                Logger.v(TAG, "stopPlayback: skip stopping remote device");
            } else {
                mService.setRate(1.0f, false);
                mService.stop(false, true, !mSwitchingToAnotherRenderer, mSwitchingToAnotherRenderer);
            }
        }
    }

    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    private void cleanUI() {

        if (mRootView != null) mRootView.setKeepScreenOn(false);

        if (mDetector != null) {
            mDetector.setOnDoubleTapListener(null);
            mDetector = null;
        }

        /* Stop listening for changes to media routes. */
        mDisplayManager.mediaRouterAddCallback(false);

        surfaceFrameAddLayoutListener(false);

        mActionBarView.setOnTouchListener(null);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, final Intent data) {
        Logger.vv(TAG, "onActivityResult: requestCode=" + requestCode + " resultCode=" + resultCode + " data=" + data);

        if (requestCode == REQUEST_CODE_SELECT_PLAYER) {
            if(resultCode == Activity.RESULT_OK){
                if(mService == null) {
                    Log.e(TAG, "onActivityResult: missing playback service");
                    return;
                }

                SelectedPlayer player = SelectedPlayer.fromIntentExtra(data);

                // Check for same player
                boolean sameDevice = false;
                if (player.type == SelectedPlayer.OUR_PLAYER) {
                    if(!mService.hasRenderer()) {
                        sameDevice = true;
                    }
                }
                else if (player.type == SelectedPlayer.ACESTREAM_DEVICE) {
                    RemoteDevice device = mService.getCurrentRemoteDevice();
                    if (device != null && device.equals(player)) {
                        sameDevice = true;
                    }
                }

                if(sameDevice) {
                    Logger.v(TAG, "onActivityResult: skip same device");
                    return;
                }

                // Save current media metadata before disconnecting remote device
                mService.saveMediaMeta();

                // Need to stop remote playback and reset engine session (remote) before
                // new session is started, otherwise PlaybackManager.EngineStatusHandler
                // may be confused (session can be stopped right after start because handler
                // understands only one session and don't distinguish local and remote session).
                if(mAceStreamManager != null) {
                    mAceStreamManager.stopRemotePlayback(true);
                }

                if(player.type == SelectedPlayer.OUR_PLAYER) {
                    Logger.v(TAG, "onActivityResult: start our player");

                    mDisplayManager.setRecreateOnDisplayChange(false);

                    mService.setRenderer(null, "video-player-resolver");
                    RendererDelegate.INSTANCE.selectRenderer(true, null, false);
                    mSwitchingToAnotherRenderer = true;

                    if(AceStreamUtils.shouldStartAceStreamPlayer(mService.getCurrentMediaWrapper())) {
                        // Don't ask for resume when switching between renderers
                        Bundle extras = new Bundle(1);
                        extras.putBoolean("askResume", false);
                        mService.startCurrentPlaylistInAceStreamPlayer(extras);
                        finish();
                    }
                    else {
                        mSwitchingToAnotherPlayer = true;
                        recreate();
                    }
                }
                else if (player.isRemote()) {
                    if (mAceStreamManager == null) {
                        Log.e(TAG, "onActivityResult: missing pm");
                        return;
                    }

                    RemoteDevice device = mAceStreamManager.findRemoteDevice(player);

                    Logger.v(TAG, "onActivityResult: set renderer: " + device);
                    if (device == null) {
                        Log.e(TAG, "onActivityResult: device not found: id=" + player.id1);
                        return;
                    }

                    if(mAceStreamManager == null) {
                        Log.e(TAG, "onActivityResult: missing playback manager");
                        return;
                    }

                    // reset p2p item to avoid restoring playback URI
                    MediaWrapper media = mService.getCurrentMediaWrapper();
                    if(media != null && media.isP2PItem()) {
                        media.resetP2PItem(mAceStreamManager);

                        // Update intent to ensure that actual item URI will be used, not playback URI.
                        // Playback URI for p2p items has no sense when switching to AceCast.
                        // URI from intent (Constants.PLAY_EXTRA_ITEM_LOCATION) will be used after
                        // this activity is restarted.
                        Logger.v(TAG, "onActivityResult: update intent: current=" + getIntent().getParcelableExtra(Constants.PLAY_EXTRA_ITEM_LOCATION) + " new=" + media.getUri());
                        getIntent().putExtra(Constants.PLAY_EXTRA_ITEM_LOCATION, media.getUri());
                    }

                    // stop local session
                    mAceStreamManager.stopEngineSession(true);

                    RendererItemWrapper renderer = new RendererItemWrapper(device);
                    boolean wasOnRenderer = mService.hasRenderer();
                    mService.setRenderer(renderer, "video-player-resolver");

                    // This will recreate VideoPlayerActivityOld
                    mSwitchingToAnotherRenderer = true;
                    RendererDelegate.INSTANCE.selectRenderer(true, renderer, false);
                    if(wasOnRenderer) {
                        // If was on renderer then need to recreate activity manually, because
                        // automatic recreate is triggered by DisplayManager only when
                        // primary/non-primary status changes, which doesn't happen when one
                        // renderer is changed to another.
                        Logger.v(TAG, "onActivityResult: recreate activity");
                        recreate();
                    }
                }
                else {
                    TransportFileDescriptor descriptor = mDescriptor;
                    if(descriptor == null) {
                        if(mService == null) {
                            Log.e(TAG, "onActivityResult: missing playback service");
                            return;
                        }
                        MediaWrapper media = mService.getCurrentMediaWrapper();
                        if(media == null) {
                            Log.e(TAG, "onActivityResult: missing current media");
                            return;
                        }

                        try {
                            descriptor = media.getDescriptor();
                        }
                        catch(TransportFileParsingException e) {
                            Log.e(TAG, "onActivityResult: failed to get descriptor: " + e.getMessage());
                            return;
                        }
                    }

                    startActivity(AceStream.makeIntentFromDescriptor(descriptor, player));
                    mSwitchingToAnotherPlayer = true;
                    finish();
                }
            }
            else if(resultCode == AceStream.Resolver.RESULT_CLOSE_CALLER) {
                finish();
            }
        }
        else if (requestCode == REQUEST_CODE_SELECT_SUBTITLES) {
            if(data != null) {
                if(data.hasExtra(FilePickerFragment.EXTRA_MRL)) {
                    mService.addSubtitleTrack(Uri.parse(data.getStringExtra(FilePickerFragment.EXTRA_MRL)), true);
                    VLCApplication.runBackground(new Runnable() {
                        @Override
                        public void run() {
                            MediaDatabase.getInstance().saveSlave(mService.getCurrentMediaLocation(), Media.Slave.Type.Subtitle, 2, data.getStringExtra(FilePickerFragment.EXTRA_MRL));
                        }
                    });
                } else if (BuildConfig.DEBUG) Log.d(TAG, "Subtitle selection dialog was cancelled");
            }
        }
    }

    public static void start(Context context, Uri uri) {
        start(context, uri, null, false, -1);
    }

    public static void start(Context context, Uri uri, boolean fromStart) {
        start(context, uri, null, fromStart, -1);
    }

    public static void start(Context context, Uri uri, String title, boolean fromStart) {
        start(context, uri, title, fromStart, -1);
    }

    public static void start(Context context, Uri uri, String title) {
        start(context, uri, title, false, -1);
    }
    public static void startOpened(Context context, Uri uri, int openedPosition) {
        start(context, uri, null, false, openedPosition);
    }

    //:ace
    public static void closePlayer() {
        closePlayer(false);
    }

    public static void closePlayer(boolean stopAfterDeviceDisconnect) {
        Logger.v(TAG, "closePlayer: stopAfterDeviceDisconnect=" + stopAfterDeviceDisconnect);
        Intent intent  = new Intent(Constants.EXIT_PLAYER);
        if(stopAfterDeviceDisconnect) {
            intent.putExtra(Constants.PLAY_EXTRA_STOP_AFTER_DEVICE_DISCONNECT, true);
        }
        LocalBroadcastManager
                .getInstance(VLCApplication.getAppContext())
                .sendBroadcast(intent);
    }

    public static void startRemoteDevice(
            @NonNull Context context,
            @NonNull MediaWrapper media,
            @NonNull RemoteDevice device,
            boolean fromStart) {
        Logger.v(TAG, "startRemoteDevice: fromStart=" + fromStart);
        Intent intent = new Intent(context, VideoPlayerActivity.class);
        intent.setAction(Constants.PLAY_REMOTE_DEVICE);
        intent.putExtra(Constants.PLAY_EXTRA_MEDIA_WRAPPER, media);
        intent.putExtra(Constants.PLAY_EXTRA_SELECTED_PLAYER, SelectedPlayer.fromDevice(device).toJson());
        intent.putExtra(Constants.PLAY_EXTRA_FROM_START, fromStart);

        if (!(context instanceof Activity)) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        }

        context.startActivity(intent);
    }
    ///ace

    private static void start(Context context, Uri uri, String title, boolean fromStart, int openedPosition) {
        final Intent intent = getIntent(context, uri, title, fromStart, openedPosition);
        context.startActivity(intent);
    }

    public static Intent getIntent(String action, MediaWrapper mw, boolean fromStart, int openedPosition) {
        return getIntent(action, VLCApplication.getAppContext(), mw.getPlaybackUri(), mw.getTitle(), fromStart, openedPosition);
    }

    @NonNull
    public static Intent getIntent(Context context, Uri uri, String title, boolean fromStart, int openedPosition) {
        return getIntent(Constants.PLAY_FROM_VIDEOGRID, context, uri, title, fromStart, openedPosition);
    }

    @NonNull
    public static Intent getIntent(String action, Context context, Uri uri, String title, boolean fromStart, int openedPosition) {
        Intent intent;
        if(AceStreamUtils.shouldStartAceStreamPlayer(uri)) {

            SelectedPlayer selectedPlayer = null;
            AceStreamManager manager = AceStreamManager.getInstance();
            if(manager != null) {
                selectedPlayer = manager.getSelectedPlayer();
            }

            // Use AceStream player as default
            if(selectedPlayer == null || selectedPlayer.isOurPlayer()) {
                intent = AceStreamUtils.getPlayerIntent();
                intent.putExtra(AceStreamPlayer.EXTRA_PLAYLIST, AceStreamPlayer.Playlist.fromSingleItem(uri.toString(), title, 0));
                intent.putExtra(AceStreamPlayer.EXTRA_PLAY_FROM_START, fromStart);
            }
            else {
                intent = AceStream.makeIntentFromUri(
                        context,
                        uri,
                        null,
                        false,
                        false);
            }
        }
        else {
            intent = new Intent(context, VideoPlayerActivity.class);
            intent.setAction(action);
            intent.putExtra(Constants.PLAY_EXTRA_ITEM_LOCATION, uri);
            intent.putExtra(Constants.PLAY_EXTRA_ITEM_TITLE, title);
            intent.putExtra(Constants.PLAY_EXTRA_FROM_START, fromStart);
        }

        if (openedPosition != -1 || !(context instanceof Activity)) {
            if (openedPosition != -1)
                intent.putExtra(Constants.PLAY_EXTRA_OPENED_POSITION, openedPosition);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        }
        return intent;
    }

    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent)
        {
            final String action = intent.getAction();
            if (Intent.ACTION_BATTERY_CHANGED.equalsIgnoreCase(action)) {
                if (mBattery == null) return;
                int batteryLevel = intent.getIntExtra("level", 0);
                if (batteryLevel >= 50)
                    mBattery.setTextColor(Color.GREEN);
                else if (batteryLevel >= 30)
                    mBattery.setTextColor(Color.YELLOW);
                else
                    mBattery.setTextColor(Color.RED);
                mBattery.setText(String.format("%d%%", batteryLevel));
            } else if (VLCApplication.SLEEP_INTENT.equalsIgnoreCase(action)) {
                exitOK();
            }
        }
    };

    protected void exit(int resultCode){
        if (isFinishing())
            return;
        Intent resultIntent = new Intent(ACTION_RESULT);
        if (mUri != null && mService != null) {
            if (AndroidUtil.isNougatOrLater)
                resultIntent.putExtra(EXTRA_URI, mUri.toString());
            else
                resultIntent.setData(mUri);
            resultIntent.putExtra(EXTRA_POSITION, mService.getTime());
            resultIntent.putExtra(EXTRA_DURATION, mService.getLength());
        }
        setResult(resultCode, resultIntent);
        finish();
    }

    protected void exitOK() {
        exit(RESULT_OK);
    }

    @Override
    public boolean onTrackballEvent(MotionEvent event) {
        if (mIsLoading)
            return false;
        showOverlay();
        return true;
    }

    @TargetApi(12) //only active for Android 3.1+
    public boolean dispatchGenericMotionEvent(MotionEvent event){
        if (mIsLoading)
            return  false;
        //Check for a joystick event
        if ((event.getSource() & InputDevice.SOURCE_JOYSTICK) !=
                InputDevice.SOURCE_JOYSTICK ||
                event.getAction() != MotionEvent.ACTION_MOVE)
            return false;

        InputDevice mInputDevice = event.getDevice();

        float dpadx = event.getAxisValue(MotionEvent.AXIS_HAT_X);
        float dpady = event.getAxisValue(MotionEvent.AXIS_HAT_Y);
        if (mInputDevice == null || Math.abs(dpadx) == 1.0f || Math.abs(dpady) == 1.0f)
            return false;

        float x = AndroidDevices.getCenteredAxis(event, mInputDevice,
                MotionEvent.AXIS_X);
        float y = AndroidDevices.getCenteredAxis(event, mInputDevice,
                MotionEvent.AXIS_Y);
        float rz = AndroidDevices.getCenteredAxis(event, mInputDevice,
                MotionEvent.AXIS_RZ);

        if (System.currentTimeMillis() - mLastMove > JOYSTICK_INPUT_DELAY){
            if (Math.abs(x) > 0.3){
                if (VLCApplication.showTvUi()) {
                    navigateDvdMenu(x > 0.0f ? KeyEvent.KEYCODE_DPAD_RIGHT : KeyEvent.KEYCODE_DPAD_LEFT);
                } else
                    seekDelta(x > 0.0f ? 10000 : -10000);
            } else if (Math.abs(y) > 0.3){
                if (VLCApplication.showTvUi())
                    navigateDvdMenu(x > 0.0f ? KeyEvent.KEYCODE_DPAD_UP : KeyEvent.KEYCODE_DPAD_DOWN);
                else {
                    if (mIsFirstBrightnessGesture)
                        initBrightnessTouch();
                    changeBrightness(-y / 10f);
                }
            } else if (Math.abs(rz) > 0.3){
                mVol = mAudioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
                int delta = -(int) ((rz / 7) * mAudioMax);
                int vol = (int) Math.min(Math.max(mVol + delta, 0), mAudioMax);
                setAudioVolume(vol);
            }
            mLastMove = System.currentTimeMillis();
        }
        return true;
    }

    @Override
    public void onBackPressed() {
        if (mLockBackButton) {
            mLockBackButton = false;
            mHandler.sendEmptyMessageDelayed(RESET_BACK_LOCK, 2000);
            Toast.makeText(getApplicationContext(), getString(R.string.back_quit_lock), Toast.LENGTH_SHORT).show();
        } else if(mPlaylist.getVisibility() == View.VISIBLE) {
            togglePlaylist();
        } else if (mPlaybackSetting != DelayState.OFF){
            endPlaybackSetting();
        } else if (VLCApplication.showTvUi() && mShowing && !mIsLocked) {
            hideOverlay(true);
        } else {
            exitOK();
            super.onBackPressed();
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (mService == null
                || keyCode == KeyEvent.KEYCODE_BACK
                || keyCode == KeyEvent.KEYCODE_BUTTON_B)
            return super.onKeyDown(keyCode, event);
        if (mPlaybackSetting != DelayState.OFF)
            return false;
        if (mIsLoading) {
            switch (keyCode) {
                case KeyEvent.KEYCODE_S:
                case KeyEvent.KEYCODE_MEDIA_STOP:
                    exitOK();
                    return true;
            }
            return false;
        }

        //Handle playlist d-pad navigation
        if (mPlaylist.hasFocus()) {
            Log.v(TAG, "playIndex:keydown: code=" + keyCode);
            switch (keyCode) {
                case KeyEvent.KEYCODE_DPAD_UP:
                case KeyEvent.KEYCODE_DPAD_LEFT:
                    //:ace
                    mPlaylistAdapter.setDpadNavigation(true);
                    ///ace
                    mPlaylistAdapter.setCurrentIndex(mPlaylistAdapter.getCurrentIndex() - 1);
                    break;
                case KeyEvent.KEYCODE_DPAD_DOWN:
                case KeyEvent.KEYCODE_DPAD_RIGHT:
                    //:ace
                    mPlaylistAdapter.setDpadNavigation(true);
                    ///ace
                    mPlaylistAdapter.setCurrentIndex(mPlaylistAdapter.getCurrentIndex() + 1);
                    break;
                case KeyEvent.KEYCODE_ENTER:
                case KeyEvent.KEYCODE_DPAD_CENTER:
                case KeyEvent.KEYCODE_BUTTON_A:
                    mService.playIndex(mPlaylistAdapter.getCurrentIndex());
                    break;
            }
            return true;
        }
        if (mShowing || (mFov == 0f && keyCode == KeyEvent.KEYCODE_DPAD_DOWN)) {
            showOverlay();
        }

        // Progress bar d-pad navigation
        if(mHudBinding != null && mHudBinding.playerOverlaySeekbar.hasFocus()) {
            if(keyCode == KeyEvent.KEYCODE_DPAD_CENTER) {
                doSeek(mHudBinding.playerOverlaySeekbar.getProgress());
                return true;
            }
        }

        switch (keyCode) {
            case KeyEvent.KEYCODE_F:
            case KeyEvent.KEYCODE_MEDIA_FAST_FORWARD:
                seekDelta(10000);
                return true;
            case KeyEvent.KEYCODE_R:
            case KeyEvent.KEYCODE_MEDIA_REWIND:
                seekDelta(-10000);
                return true;
            case KeyEvent.KEYCODE_BUTTON_R1:
                seekDelta(60000);
                return true;
            case KeyEvent.KEYCODE_BUTTON_L1:
                seekDelta(-60000);
                return true;
            case KeyEvent.KEYCODE_BUTTON_A:
                if (mHudBinding != null && mHudBinding.progressOverlay.getVisibility() == View.VISIBLE)
                    return false;
            case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
            case KeyEvent.KEYCODE_MEDIA_PLAY:
            case KeyEvent.KEYCODE_MEDIA_PAUSE:
            case KeyEvent.KEYCODE_SPACE:
                if (mIsNavMenu)
                    return navigateDvdMenu(keyCode);
                else if (keyCode == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE) //prevent conflict with remote control
                    return super.onKeyDown(keyCode, event);
                else
                    doPlayPause();
                return true;
            case KeyEvent.KEYCODE_O:
            case KeyEvent.KEYCODE_BUTTON_Y:
            case KeyEvent.KEYCODE_MENU:
                showAdvancedOptions();
                return true;
            case KeyEvent.KEYCODE_V:
            case KeyEvent.KEYCODE_MEDIA_AUDIO_TRACK:
            case KeyEvent.KEYCODE_BUTTON_X:
                onAudioSubClick(mHudBinding != null ? mHudBinding.playerOverlayTracks : null);
                return true;
            case KeyEvent.KEYCODE_N:
                showNavMenu();
                return true;
            case KeyEvent.KEYCODE_A:
                resizeVideo();
                return true;
            case KeyEvent.KEYCODE_M:
            case KeyEvent.KEYCODE_VOLUME_MUTE:
                updateMute();
                return true;
            case KeyEvent.KEYCODE_S:
            case KeyEvent.KEYCODE_MEDIA_STOP:
                exitOK();
                return true;
            case KeyEvent.KEYCODE_DPAD_LEFT:
                if (mIsNavMenu)
                    return navigateDvdMenu(keyCode);
                else if (!mShowing) {
                    if (mFov == 0f)
                        seekDelta(-10000);
                    else
                        mService.updateViewpoint(-5f, 0f, 0f, 0f, false);
                    return true;
                }
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                if (mIsNavMenu)
                    return navigateDvdMenu(keyCode);
                else if (!mShowing) {
                    if (mFov == 0f)
                        seekDelta(10000);
                    else
                        mService.updateViewpoint(5f, 0f, 0f, 0f, false);
                    return true;
                }
            case KeyEvent.KEYCODE_DPAD_UP:
                if (mIsNavMenu)
                    return navigateDvdMenu(keyCode);
                else if (event.isCtrlPressed()) {
                    volumeUp();
                    return true;
                } else if (!mShowing) {
                    if (mFov == 0f)
                        showAdvancedOptions();
                    else
                        mService.updateViewpoint(0f, -5f, 0f, 0f, false);
                    return true;
                }
            case KeyEvent.KEYCODE_DPAD_DOWN:
                if (mIsNavMenu)
                    return navigateDvdMenu(keyCode);
                else if (event.isCtrlPressed()) {
                    volumeDown();
                    return true;
                } else if (!mShowing && mFov != 0f) {
                    mService.updateViewpoint(0f, 5f, 0f, 0f, false);
                    return true;
                }
            case KeyEvent.KEYCODE_DPAD_CENTER:
                if (mIsNavMenu)
                    return navigateDvdMenu(keyCode);
                else if (!mShowing) {
                    doPlayPause();
                    return true;
                }
            case KeyEvent.KEYCODE_ENTER:
                if (mIsNavMenu)
                    return navigateDvdMenu(keyCode);
                else
                    return super.onKeyDown(keyCode, event);
            case KeyEvent.KEYCODE_J:
                delayAudio(-50000L);
                return true;
            case KeyEvent.KEYCODE_K:
                delayAudio(50000L);
                return true;
            case KeyEvent.KEYCODE_G:
                delaySubs(-50000L);
                return true;
            case KeyEvent.KEYCODE_H:
                delaySubs(50000L);
                return true;
            case KeyEvent.KEYCODE_VOLUME_DOWN:
                volumeDown();
                return true;
            case KeyEvent.KEYCODE_VOLUME_UP:
                volumeUp();
                return true;
            case KeyEvent.KEYCODE_CAPTIONS:
                selectSubtitles();
                return true;
            case KeyEvent.KEYCODE_PLUS:
                mService.setRate(mService.getRate()*1.2f, true);
                return true;
            case KeyEvent.KEYCODE_EQUALS:
                if (event.isShiftPressed()) {
                    mService.setRate(mService.getRate() * 1.2f, true);
                    return true;
                }
                return false;
            case KeyEvent.KEYCODE_MINUS:
                mService.setRate(mService.getRate()/1.2f, true);
                return true;
            case KeyEvent.KEYCODE_C:
                resizeVideo();
                return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    private void volumeUp() {
        if (mMute) {
            updateMute();
        } else {
            int volume;
            if (mAudioManager.getStreamVolume(AudioManager.STREAM_MUSIC) < mAudioMax)
                volume = mAudioManager.getStreamVolume(AudioManager.STREAM_MUSIC) + 1;
            else
                volume = Math.round(((float)mService.getVolume())*mAudioMax/100 + 1);
            volume = Math.min(Math.max(volume, 0), mAudioMax * (audioBoostEnabled ? 2 : 1));
            setAudioVolume(volume);
        }
    }

    private void volumeDown() {
        int vol;
        if (mService.getVolume() > 100)
            vol = Math.round(((float)mService.getVolume())*mAudioMax/100 - 1);
        else
            vol = mAudioManager.getStreamVolume(AudioManager.STREAM_MUSIC) - 1;
        vol = Math.min(Math.max(vol, 0), mAudioMax * (audioBoostEnabled ? 2 : 1));
        mOriginalVol = vol;
        setAudioVolume(vol);
    }

    private boolean navigateDvdMenu(int keyCode) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_UP:
                mService.navigate(MediaPlayer.Navigate.Up);
                return true;
            case KeyEvent.KEYCODE_DPAD_DOWN:
                mService.navigate(MediaPlayer.Navigate.Down);
                return true;
            case KeyEvent.KEYCODE_DPAD_LEFT:
                mService.navigate(MediaPlayer.Navigate.Left);
                return true;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                mService.navigate(MediaPlayer.Navigate.Right);
                return true;
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_BUTTON_X:
            case KeyEvent.KEYCODE_BUTTON_A:
                mService.navigate(MediaPlayer.Navigate.Activate);
                return true;
            default:
                return false;
        }
    }

    @Override
    public void showAudioDelaySetting() {
        mPlaybackSetting = DelayState.AUDIO;
        showDelayControls();
    }

    @Override
    public void showSubsDelaySetting() {
        mPlaybackSetting = DelayState.SUBS;
        showDelayControls();
    }

    @SuppressLint("RestrictedApi")
    public void showDelayControls(){
        mTouchAction = TOUCH_NONE;
        if (!mDisplayManager.isPrimary()) showOverlayTimeout(OVERLAY_INFINITE);
        ViewStubCompat vsc = (ViewStubCompat) findViewById(R.id.player_overlay_settings_stub);
        if (vsc != null) {
            vsc.inflate();
            mPlaybackSettingPlus = (ImageView) findViewById(R.id.player_delay_plus);
            mPlaybackSettingMinus = (ImageView) findViewById(R.id.player_delay_minus);

        }
        mPlaybackSettingMinus.setOnClickListener(this);
        mPlaybackSettingPlus.setOnClickListener(this);
        mPlaybackSettingMinus.setOnTouchListener(new OnRepeatListener(this));
        mPlaybackSettingPlus.setOnTouchListener(new OnRepeatListener(this));
        mPlaybackSettingMinus.setVisibility(View.VISIBLE);
        mPlaybackSettingPlus.setVisibility(View.VISIBLE);
        mPlaybackSettingPlus.requestFocus();
        initPlaybackSettingInfo();
    }

    private void initPlaybackSettingInfo() {
        initInfoOverlay();
        UiTools.setViewVisibility(mVerticalBar, View.GONE);
        UiTools.setViewVisibility(mOverlayInfo, View.VISIBLE);
        String text = "";
        if (mPlaybackSetting == DelayState.AUDIO) {
            text += getString(R.string.audio_delay)+"\n";
            text += mService.getAudioDelay() / 1000L;
            text += " ms";
        } else if (mPlaybackSetting == DelayState.SUBS) {
            text += getString(R.string.spu_delay)+"\n";
            text += mService.getSpuDelay() / 1000L;
            text += " ms";
        } else
            text += "0";
        mInfo.setText(text);
    }

    @Override
    public void endPlaybackSetting() {
        mTouchAction = TOUCH_NONE;
        mService.saveMediaMeta();
        if (mBtReceiver != null && mPlaybackSetting == DelayState.AUDIO
                && (mAudioManager.isBluetoothA2dpOn() || mAudioManager.isBluetoothScoOn())) {
            String msg = getString(R.string.audio_delay) + "\n"
                    + mService.getAudioDelay() / 1000L
                    + " ms";
            Snackbar sb = Snackbar.make(mInfo, msg, Snackbar.LENGTH_LONG);
            sb.setAction(R.string.save_bluetooth_delay, mBtSaveListener);
            sb.show();
        }
        mPlaybackSetting = DelayState.OFF;
        if (mPlaybackSettingMinus != null) {
            mPlaybackSettingMinus.setOnClickListener(null);
            mPlaybackSettingMinus.setVisibility(View.INVISIBLE);
        }
        if (mPlaybackSettingPlus != null) {
            mPlaybackSettingPlus.setOnClickListener(null);
            mPlaybackSettingPlus.setVisibility(View.INVISIBLE);
        }
        UiTools.setViewVisibility(mOverlayInfo, View.INVISIBLE);
        mInfo.setText("");
        if (mHudBinding != null)
            mHudBinding.playerOverlayPlay.requestFocus();
    }

    public void delayAudio(long delta) {
        initInfoOverlay();
        long delay = mService.getAudioDelay()+delta;
        mService.setAudioDelay(delay);
        mInfo.setText(getString(R.string.audio_delay)+"\n"+(delay/1000L)+" ms");
        mAudioDelay = delay;
        if (mPlaybackSetting == DelayState.OFF) {
            mPlaybackSetting = DelayState.AUDIO;
            initPlaybackSettingInfo();
        }
    }

    public void delaySubs(long delta) {
        initInfoOverlay();
        long delay = mService.getSpuDelay()+delta;
        mService.setSpuDelay(delay);
        mInfo.setText(getString(R.string.spu_delay) + "\n" + (delay / 1000L) + " ms");
        mSpuDelay = delay;
        if (mPlaybackSetting == DelayState.OFF) {
            mPlaybackSetting = DelayState.SUBS;
            initPlaybackSettingInfo();
        }
    }

    /**
     * Lock screen rotation
     */
    private void lockScreen() {
        if (mScreenOrientation != 100) {
            mScreenOrientationLock = getRequestedOrientation();
            if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2)
                setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LOCKED);
            else
                setRequestedOrientation(getScreenOrientation(100));
        }
        showInfo(R.string.locked, 1000);
        if (mHudBinding != null) {
            mHudBinding.lockOverlayButton.setImageResource(R.drawable.rci_lock_selector);
            mHudBinding.playerOverlayTime.setEnabled(false);
            mHudBinding.playerOverlaySeekbar.setEnabled(false);
            mHudBinding.playerOverlayLength.setEnabled(false);
            mHudBinding.playerOverlaySize.setEnabled(false);
            mHudBinding.playlistNext.setEnabled(false);
            mHudBinding.playlistPrevious.setEnabled(false);
            mHudBinding.selectAudioTrack.setVisibility(View.GONE);
        }
        hideOverlay(true);
        mLockBackButton = true;
        mIsLocked = true;
    }

    /**
     * Remove screen lock
     */
    private void unlockScreen() {
        if(mScreenOrientation != 100)
            setRequestedOrientation(mScreenOrientationLock);
        showInfo(R.string.unlocked, 1000);
        if (mHudBinding != null) {
            mHudBinding.lockOverlayButton.setImageResource(R.drawable.rci_lock_open_selector);
            mHudBinding.playerOverlayTime.setEnabled(true);
            mHudBinding.playerOverlaySeekbar.setEnabled(mService == null || isSeekable());
            mHudBinding.playerOverlayLength.setEnabled(true);
            mHudBinding.playerOverlaySize.setEnabled(true);
            mHudBinding.playlistNext.setEnabled(true);
            mHudBinding.playlistPrevious.setEnabled(true);
        }
        mShowing = false;
        mIsLocked = false;
        showOverlay();
        updateTracksSelectors();
        mLockBackButton = false;
    }

    /**
     * Show text in the info view and vertical progress bar for "duration" milliseconds
     * @param text
     * @param duration
     * @param barNewValue new volume/brightness value (range: 0 - 15)
     */
    private void showInfoWithVerticalBar(String text, int duration, int barNewValue, int max) {
        showInfo(text, duration);
        if (mVerticalBarProgress == null)
            return;
        LinearLayout.LayoutParams layoutParams;
        if (barNewValue <= 100) {
            layoutParams = (LinearLayout.LayoutParams) mVerticalBarProgress.getLayoutParams();
            layoutParams.weight = barNewValue * 100 / max;
            mVerticalBarProgress.setLayoutParams(layoutParams);
            layoutParams = (LinearLayout.LayoutParams) mVerticalBarBoostProgress.getLayoutParams();
            layoutParams.weight = 0;
            mVerticalBarBoostProgress.setLayoutParams(layoutParams);
        } else {
            layoutParams = (LinearLayout.LayoutParams) mVerticalBarProgress.getLayoutParams();
            layoutParams.weight = 100 * 100 / max;
            mVerticalBarProgress.setLayoutParams(layoutParams);
            layoutParams = (LinearLayout.LayoutParams) mVerticalBarBoostProgress.getLayoutParams();
            layoutParams.weight = (barNewValue - 100) * 100 / max;
            mVerticalBarBoostProgress.setLayoutParams(layoutParams);
        }
        mVerticalBar.setVisibility(View.VISIBLE);
    }

    /**
     * Show text in the info view for "duration" milliseconds
     * @param text
     * @param duration
     */
    private void showInfo(String text, int duration) {
        initInfoOverlay();
        UiTools.setViewVisibility(mVerticalBar, View.GONE);
        UiTools.setViewVisibility(mOverlayInfo, View.VISIBLE);
        mInfo.setText(text);
        mHandler.removeMessages(FADE_OUT_INFO);
        mHandler.sendEmptyMessageDelayed(FADE_OUT_INFO, duration);
    }

    @SuppressLint("RestrictedApi")
    private void initInfoOverlay() {
        ViewStubCompat vsc = (ViewStubCompat) findViewById(R.id.player_info_stub);
        if (vsc != null) {
            vsc.inflate();
            // the info textView is not on the overlay
            mInfo = (TextView) findViewById(R.id.player_overlay_textinfo);
            mOverlayInfo = findViewById(R.id.player_overlay_info);
            mVerticalBar = findViewById(R.id.verticalbar);
            mVerticalBarProgress = findViewById(R.id.verticalbar_progress);
            mVerticalBarBoostProgress = findViewById(R.id.verticalbar_boost_progress);
        }
    }

    private void showInfo(int textid, int duration) {
        initInfoOverlay();
        UiTools.setViewVisibility(mVerticalBar, View.GONE);
        UiTools.setViewVisibility(mOverlayInfo, View.VISIBLE);
        mInfo.setText(textid);
        mHandler.removeMessages(FADE_OUT_INFO);
        mHandler.sendEmptyMessageDelayed(FADE_OUT_INFO, duration);
    }

    /**
     * hide the info view with "delay" milliseconds delay
     * @param delay
     */
    private void hideInfo(int delay) {
        mHandler.sendEmptyMessageDelayed(FADE_OUT_INFO, delay);
    }

    /**
     * hide the info view
     */
    private void hideInfo() {
        hideInfo(0);
    }

    private void fadeOutInfo() {
        if (mOverlayInfo != null && mOverlayInfo.getVisibility() == View.VISIBLE) {
            mOverlayInfo.startAnimation(AnimationUtils.loadAnimation(
                    VideoPlayerActivity.this, android.R.anim.fade_out));
            UiTools.setViewVisibility(mOverlayInfo, View.INVISIBLE);
        }
    }

    /* PlaybackService.Callback */

    @Override
    public void update() {
        updateList();
    }

    @Override
    public void updateProgress() {
    }

    @Override
    public void onMediaEvent(Media.Event event) {
        switch (event.type) {
            case Media.Event.ParsedChanged:
                updateNavStatus();
                break;
            case Media.Event.MetaChanged:
                break;
            case Media.Event.SubItemTreeAdded:
                mHasSubItems = true;
                break;
        }
    }

    @Override
    public void onMediaPlayerEvent(MediaPlayer.Event event) {
        MediaPlayerEvent upgradedEvent = null;
        if(event instanceof MediaPlayerEvent) {
            upgradedEvent = (MediaPlayerEvent)event;
        }

        switch (event.type) {
            case MediaPlayer.Event.Opening:
                Logger.v(TAG, "vlc:event: opening");
                mHasSubItems = false;
                break;
            case MediaPlayer.Event.Playing:
                Logger.v(TAG, "vlc:event: playing");
                setBuffering(false);
                onPlaying();
                updateStopButton();
                updateVideoSizeButton();
                break;
            case MediaPlayer.Event.Paused:
                Logger.v(TAG, "vlc:event: paused");
                updateOverlayPausePlay();
                break;
            case MediaPlayer.Event.Stopped:
                Logger.v(TAG, "vlc:event: stopped");
                //r
                //exitOK();
                //--
                Logger.v(TAG, "vlc:event:Stopped: restarting=" + mRestartingPlayer + " exitOnStop=" + mExitOnStop);
                if(mRestartingPlayer) {
                    mRestartingPlayer = false;
                    if(mService != null) {
                        mService.play();
                    }
                }
                //<<
                break;
            case MediaPlayer.Event.EndReached:
                /* Don't end the activity if the media has subitems since the next child will be
                 * loaded by the PlaybackService */
                //r
                //if (!mHasSubItems) endReached();
                //--
                Logger.v(TAG, "vlc:event:EndReached: exitOnStop=" + mExitOnStop + " hasSubItems=" + mHasSubItems);
                if(mExitOnStop && !mHasSubItems) {
                    endReached();
                }
                //<<
                break;
            case MediaPlayerEvent.PlayerClosed:
                Logger.v(TAG, "vlc:event:PlayerClosed");
                remotePlayerClosed();
                break;
            case MediaPlayerEvent.VolumeChanged:
                Logger.v(TAG, "vlc:event:VolumeChanged: value=" + upgradedEvent.getVolume());
                break;
            case MediaPlayerEvent.VideoSizeChanged:
                Logger.v(TAG, "vlc:event:VideoSizeChanged: value=" + upgradedEvent.getVideoSize());
                break;
            case MediaPlayer.Event.EncounteredError:
                encounteredError();
                break;
            case MediaPlayer.Event.TimeChanged:
                if(!mIsLive) {
                    boolean dragging = false;
                    if(mDragging) {
                        dragging = true;
                    }
                    else if(mHudBinding != null && mHudBinding.playerOverlaySeekbar.hasFocus()) {
                        dragging = true;
                    }
                    if(!dragging) {
                        mProgress.set((int) event.getTimeChanged());
                    }
                    mCurrentTime.set((int) event.getTimeChanged());
                }
                break;
            case MediaPlayer.Event.LengthChanged:
                if(!mIsLive) {
                    mMediaLength.set(event.getLengthChanged());
                }
                break;
            case MediaPlayer.Event.Vout:
                updateNavStatus();
                if (mMenuIdx == -1)
                    handleVout(event.getVoutCount());
                break;
            case MediaPlayer.Event.ESAdded:
                if (mMenuIdx == -1) {
                    MediaWrapper media = mMedialibrary.findMedia(mService.getCurrentMediaWrapper());
                    if (media == null)
                        return;
                    if (event.getEsChangedType() == Media.Track.Type.Audio) {
                        setESTrackLists();
                        int audioTrack = (int) media.getMetaLong(MediaWrapper.META_AUDIOTRACK);
                        if (audioTrack != 0 || mCurrentAudioTrack != -2)
                            mService.setAudioTrack(media.getId() == 0L ? mCurrentAudioTrack : audioTrack);
                    } else if (event.getEsChangedType() == Media.Track.Type.Text) {
                        setESTrackLists();
                        int spuTrack = (int) media.getMetaLong(MediaWrapper.META_SUBTITLE_TRACK);
                        if (spuTrack != 0 || mCurrentSpuTrack != -2)
                            mService.setSpuTrack(media.getId() == 0L ? mCurrentAudioTrack : spuTrack);
                    }
                }
            case MediaPlayer.Event.ESDeleted:
                if (mMenuIdx == -1 && event.getEsChangedType() == Media.Track.Type.Video) {
                    mHandler.removeMessages(CHECK_VIDEO_TRACKS);
                    mHandler.sendEmptyMessageDelayed(CHECK_VIDEO_TRACKS, 1000);
                }
                invalidateESTracks(event.getEsChangedType());
                break;
            case MediaPlayer.Event.ESSelected:
                if (event.getEsChangedType() == Media.VideoTrack.Type.Video) {
                    Media.VideoTrack vt = mService.getCurrentVideoTrack();
                    changeSurfaceLayout();
                    if (vt != null)
                        mFov = vt.projection == Media.VideoTrack.Projection.Rectangular ? 0f : DEFAULT_FOV;
                }
                break;
            case MediaPlayer.Event.SeekableChanged:
                if(!mIsLive) {
                    updateSeekable(event.getSeekable());
                }
                break;
            case MediaPlayer.Event.PausableChanged:
                updatePausable(event.getPausable());
                break;
            case MediaPlayer.Event.Buffering:
                if (!mIsPlaying)
                    break;
                if (event.getBuffering() == 100f) {
                    setBuffering(false);
                }
                else {
                    setBuffering(true);
                }
                break;
        }
    }

    /**
     * Handle resize of the surface and the overlay
     */
    private final Handler mHandler = new Handler(Looper.getMainLooper(), new Handler.Callback() {
        @Override
        public boolean handleMessage(Message msg) {
            if (mService == null)
                return true;

            switch (msg.what) {
                case FADE_OUT:
                    hideOverlay(false);
                    break;
                case SHOW_PROGRESS:
                    if (mSysTime != null && canShowProgress()) {
                        mSysTime.setText(DateFormat.getTimeFormat(VideoPlayerActivity.this).format(new Date(System.currentTimeMillis())));
                        mHandler.sendEmptyMessageDelayed(SHOW_PROGRESS, 10000L);
                    }
                    break;
                case FADE_OUT_INFO:
                    fadeOutInfo();
                    break;
                case START_PLAYBACK:
                    startPlayback();
                    break;
                case START_PLAYBACK_NO_CHECK:
                    int fromStart = msg.arg1;
                    startPlayback(fromStart);
                    break;
                case AUDIO_SERVICE_CONNECTION_FAILED:
                    exit(RESULT_CONNECTION_FAILED);
                    break;
                case RESET_BACK_LOCK:
                    mLockBackButton = true;
                    break;
                case CHECK_VIDEO_TRACKS:
                    if (mService.getVideoTracksCount() < 1 && mService.getAudioTracksCount() > 0) {
                        Log.i(TAG, "No video track, open in audio mode");
                        switchToAudioMode(true);
                    }
                    break;
                //case LOADING_ANIMATION:
                //    startLoading();
                //    break;
                case HIDE_INFO:
                    hideOverlay(true);
                    break;
                case SHOW_INFO:
                    showOverlay();
                    break;
            }
            return true;
        }
    });

    private boolean canShowProgress() {
        return !mDragging && mShowing && mService != null &&  mService.isPlaying();
    }

    private void onPlaying() {
        setPlaying(true);
        setPlaybackParameters();
        stopLoading();
        updateOverlayPausePlay();
        updateNavStatus();
        final MediaWrapper mw = mService.getCurrentMediaWrapper();
        if(mw == null) {
            return;
        }
        //r
        //mMediaLength.set(mService.getLength());
        //--
        if(!mIsLive) {
            mMediaLength.set(mService.getLength());
        }
        //<<

        if (!mw.hasFlag(MediaWrapper.MEDIA_PAUSED))
            mHandler.sendEmptyMessageDelayed(FADE_OUT, OVERLAY_TIMEOUT);
        else {
            mw.removeFlags(MediaWrapper.MEDIA_PAUSED);
            mWasPaused = false;
        }
        setESTracks();
        updateTitle();

        if(!mMediaStartedPlaying) {
            mMediaStartedPlaying = true;
        }
    }

    private void endReached() {
        if (mService == null)
            return;
        if (mService.getRepeatType() == Constants.REPEAT_ONE){
            seek(0);
            return;
        }
//        if (mService.expand(false) == 0) {
//            mHandler.removeMessages(LOADING_ANIMATION);
//            mHandler.sendEmptyMessageDelayed(LOADING_ANIMATION, LOADING_ANIMATION_DELAY);
//            Log.d(TAG, "Found a video playlist, expanding it");
//            mHandler.post(new Runnable() {
//                @Override
//                public void run() {
//                    loadMedia();
//                }
//            });
//        }
        //Ignore repeat
        if (mService.getRepeatType() == Constants.REPEAT_ALL && mService.getMediaListSize() == 1)
            exitOK();
    }

    private void encounteredError() {
        if (isFinishing() || mService.hasNext()) return;
        /* Encountered Error, exit player with a message */
        mAlertDialog = new AlertDialog.Builder(VideoPlayerActivity.this)
                .setOnCancelListener(new DialogInterface.OnCancelListener() {
                    @Override
                    public void onCancel(DialogInterface dialog) {
                        exit(RESULT_PLAYBACK_ERROR);
                    }
                })
                .setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int id) {
                        exit(RESULT_PLAYBACK_ERROR);
                    }
                })
                .setTitle(R.string.encountered_error_title)
                .setMessage(R.string.encountered_error_message)
                .create();
        mAlertDialog.show();
    }

    private final Runnable mSwitchAudioRunnable = new Runnable() {
        @Override
        public void run() {
            if (mService.hasMedia()) {
                Log.i(TAG, "Video track lost, switching to audio");
                mSwitchingView = true;
            }
            exit(RESULT_VIDEO_TRACK_LOST);
        }
    };

    private void handleVout(int voutCount) {
        mHandler.removeCallbacks(mSwitchAudioRunnable);

        final IVLCVout vlcVout = mService.getVLCVout();
        if (vlcVout.areViewsAttached() && voutCount == 0) {
            mHandler.postDelayed(mSwitchAudioRunnable, 4000);
        }
    }

    public void switchToAudioMode(boolean showUI) {
        if (mService == null) return;
        mSwitchingView = true;
        // Show the MainActivity if it is not in background.
        if (showUI) {
            Intent i = new Intent(this, VLCApplication.showTvUi() ? AudioPlayerActivity.class : MainActivity.class);
            startActivity(i);
        } else
            mSettings.edit().putBoolean(PreferencesActivity.VIDEO_RESTORE, true).apply();
        exitOK();
    }

    private void changeMediaPlayerLayout(int displayW, int displayH) {
        /* Change the video placement using MediaPlayer API */
        switch (mCurrentSize) {
            case SURFACE_BEST_FIT:
                mService.setVideoAspectRatio(null);
                mService.setVideoScale(0);
                break;
            case SURFACE_FIT_SCREEN:
            case SURFACE_FILL: {
                Media.VideoTrack vtrack = mService.getCurrentVideoTrack();
                if (vtrack == null)
                    return;
                final boolean videoSwapped = vtrack.orientation == Media.VideoTrack.Orientation.LeftBottom
                        || vtrack.orientation == Media.VideoTrack.Orientation.RightTop;
                if (mCurrentSize == SURFACE_FIT_SCREEN) {
                    int videoW = vtrack.width;
                    int videoH = vtrack.height;

                    if (videoSwapped) {
                        int swap = videoW;
                        videoW = videoH;
                        videoH = swap;
                    }
                    if (vtrack.sarNum != vtrack.sarDen)
                        videoW = videoW * vtrack.sarNum / vtrack.sarDen;

                    float ar = videoW / (float) videoH;
                    float dar = displayW / (float) displayH;

                    float scale;
                    if (dar >= ar)
                        scale = displayW / (float) videoW; /* horizontal */
                    else
                        scale = displayH / (float) videoH; /* vertical */
                    mService.setVideoScale(scale);
                    mService.setVideoAspectRatio(null);
                } else {
                    mService.setVideoScale(0);
                    mService.setVideoAspectRatio(!videoSwapped ? ""+displayW+":"+displayH
                            : ""+displayH+":"+displayW);
                }
                break;
            }
            case SURFACE_16_9:
                mService.setVideoAspectRatio("16:9");
                mService.setVideoScale(0);
                break;
            case SURFACE_4_3:
                mService.setVideoAspectRatio("4:3");
                mService.setVideoScale(0);
                break;
            case SURFACE_ORIGINAL:
                mService.setVideoAspectRatio(null);
                mService.setVideoScale(1);
                break;
        }
    }

    @Override
    public boolean isInPictureInPictureMode() {
        return AndroidUtil.isNougatOrLater && super.isInPictureInPictureMode();
    }

    @Override
    public void onPictureInPictureModeChanged(boolean isInPictureInPictureMode) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode);
        Log.v(TAG, "onPictureInPictureModeChanged: is_pip=" + isInPictureInPictureMode);
        mPictureInPictureMode = isInPictureInPictureMode;
        changeSurfaceLayout();
    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR1)
    private void changeSurfaceLayout() {
        int sw;
        int sh;

        // get screen size
        if (mDisplayManager.isPrimary()) {
            sw = getWindow().getDecorView().getWidth();
            sh = getWindow().getDecorView().getHeight();
        } else if (mDisplayManager.getPresentation() != null) {
            sw = mDisplayManager.getPresentation().getWindow().getDecorView().getWidth();
            sh = mDisplayManager.getPresentation().getWindow().getDecorView().getHeight();
        } else return;

        // sanity check
        if (sw * sh == 0) {
            Log.e(TAG, "Invalid surface size");
            return;
        }

        if (mService != null) {
            final IVLCVout vlcVout = mService.getVLCVout();
            vlcVout.setWindowSize(sw, sh);
        }

        SurfaceView surface;
        SurfaceView subtitlesSurface;
        FrameLayout surfaceFrame;
        if (mDisplayManager.isPrimary()) {
            surface = mSurfaceView;
            subtitlesSurface = mSubtitlesSurfaceView;
            surfaceFrame = mSurfaceFrame;
        } else if (mDisplayManager.getDisplayType() == DisplayManager.DisplayType.PRESENTATION) {
            surface = mDisplayManager.getPresentation().getSurfaceView();
            subtitlesSurface = mDisplayManager.getPresentation().getSubtitlesSurfaceView();
            surfaceFrame = mDisplayManager.getPresentation().getSurfaceFrame();
        } else return;
        LayoutParams lp = surface.getLayoutParams();

        if (mVideoWidth * mVideoHeight == 0 || isInPictureInPictureMode()) {
            /* Case of OpenGL vouts: handles the placement of the video using MediaPlayer API */
            lp.width = LayoutParams.MATCH_PARENT;
            lp.height = LayoutParams.MATCH_PARENT;
            surface.setLayoutParams(lp);
            lp = surfaceFrame.getLayoutParams();
            lp.width = LayoutParams.MATCH_PARENT;
            lp.height = LayoutParams.MATCH_PARENT;
            surfaceFrame.setLayoutParams(lp);
            if (mService != null && mVideoWidth * mVideoHeight == 0)
                changeMediaPlayerLayout(sw, sh);
            return;
        }

        if (mService != null && lp.width == lp.height && lp.width == LayoutParams.MATCH_PARENT) {
            /* We handle the placement of the video using Android View LayoutParams */
            mService.setVideoAspectRatio(null);
            mService.setVideoScale(0);
        }

        double dw = sw, dh = sh;
        boolean isPortrait;

        // getWindow().getDecorView() doesn't always take orientation into account, we have to correct the values
        isPortrait = mDisplayManager.isPrimary() && mCurrentScreenOrientation == Configuration.ORIENTATION_PORTRAIT;

        if (sw > sh && isPortrait || sw < sh && !isPortrait) {
            dw = sh;
            dh = sw;
        }

        // compute the aspect ratio
        double ar, vw;
        if (mSarDen == mSarNum) {
            /* No indication about the density, assuming 1:1 */
            vw = mVideoVisibleWidth;
            ar = (double) mVideoVisibleWidth / (double) mVideoVisibleHeight;
        } else {
            /* Use the specified aspect ratio */
            vw = mVideoVisibleWidth * (double) mSarNum / mSarDen;
            ar = vw / mVideoVisibleHeight;
        }

        // compute the display aspect ratio
        double dar = dw / dh;

        switch (mCurrentSize) {
            case SURFACE_BEST_FIT:
                if (dar < ar)
                    dh = dw / ar;
                else
                    dw = dh * ar;
                break;
            case SURFACE_FIT_SCREEN:
                if (dar >= ar)
                    dh = dw / ar; /* horizontal */
                else
                    dw = dh * ar; /* vertical */
                break;
            case SURFACE_FILL:
                break;
            case SURFACE_16_9:
                ar = 16.0 / 9.0;
                if (dar < ar)
                    dh = dw / ar;
                else
                    dw = dh * ar;
                break;
            case SURFACE_4_3:
                ar = 4.0 / 3.0;
                if (dar < ar)
                    dh = dw / ar;
                else
                    dw = dh * ar;
                break;
            case SURFACE_ORIGINAL:
                dh = mVideoVisibleHeight;
                dw = vw;
                break;
        }

        // set display size
        lp.width = (int) Math.ceil(dw * mVideoWidth / mVideoVisibleWidth);
        lp.height = (int) Math.ceil(dh * mVideoHeight / mVideoVisibleHeight);
        surface.setLayoutParams(lp);
        subtitlesSurface.setLayoutParams(lp);

        // set frame size (crop if necessary)
        lp = surfaceFrame.getLayoutParams();
        lp.width = (int) Math.floor(dw);
        lp.height = (int) Math.floor(dh);
        surfaceFrame.setLayoutParams(lp);

        surface.invalidate();
        subtitlesSurface.invalidate();
    }

    private void sendMouseEvent(int action, int x, int y) {
        if (mService == null)
            return;
        final IVLCVout vlcVout = mService.getVLCVout();
        vlcVout.sendMouseEvent(action, 0, x, y);
    }

    /**
     * show/hide the overlay
     */

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (mService == null)
            return false;
        if (mDetector == null) {
            mDetector = new GestureDetectorCompat(this, mGestureListener);
            mDetector.setOnDoubleTapListener(mGestureListener);
        }
        if (mFov != 0f && mScaleGestureDetector == null)
            mScaleGestureDetector = new ScaleGestureDetector(this, this);
        if (mPlaybackSetting != DelayState.OFF) {
            if (event.getAction() == MotionEvent.ACTION_UP)
                endPlaybackSetting();
            return true;
        } else if (mPlaylist.getVisibility() == View.VISIBLE) {
            togglePlaylist();
            return true;
        }
        if (mTouchControls == 0 || mIsLocked) {
            // locked or swipe disabled, only handle show/hide & ignore all actions
            if (event.getAction() == MotionEvent.ACTION_UP) {
                if (!mShowing) {
                    showOverlay();
                } else {
                    hideOverlay(true);
                }
            }
            return false;
        }
        if (mFov != 0f && mScaleGestureDetector != null)
            mScaleGestureDetector.onTouchEvent(event);
        if ((mScaleGestureDetector != null && mScaleGestureDetector.isInProgress()) ||
                (mDetector != null && mDetector.onTouchEvent(event)))
            return true;

        final float x_changed = mTouchX != -1f && mTouchY != -1f ? event.getRawX() - mTouchX : 0f;
        final float y_changed = x_changed != 0f ? event.getRawY() - mTouchY : 0f;

        // coef is the gradient's move to determine a neutral zone
        final float coef = Math.abs (y_changed / x_changed);
        final float xgesturesize = ((x_changed / mScreen.xdpi) * 2.54f);
        final float delta_y = Math.max(1f, (Math.abs(mInitTouchY - event.getRawY()) / mScreen.xdpi + 0.5f) * 2f);

        final int xTouch = Math.round(event.getRawX());
        final int yTouch = Math.round(event.getRawY());

        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                // Audio
                mTouchY = mInitTouchY = event.getRawY();
                if (mService.getVolume() <= 100) {
                    mVol = mAudioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
                    mOriginalVol = mAudioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
                }
                else {
                    mVol = ((float)mService.getVolume()) * mAudioMax / 100;
                }
                mTouchAction = TOUCH_NONE;
                // Seek
                mTouchX = event.getRawX();
                // Mouse events for the core
                sendMouseEvent(MotionEvent.ACTION_DOWN, xTouch, yTouch);
                break;
            case MotionEvent.ACTION_MOVE:
                // Mouse events for the core
                sendMouseEvent(MotionEvent.ACTION_MOVE, xTouch, yTouch);

                if (mFov == 0f) {
                    // No volume/brightness action if coef < 2 or a secondary display is connected
                    //TODO : Volume action when a secondary display is connected
                    if (mTouchAction != TOUCH_SEEK && coef > 2 && mDisplayManager.isPrimary()) {
                        if (Math.abs(y_changed/mSurfaceYDisplayRange) < 0.05)
                            return false;
                        mTouchY = event.getRawY();
                        mTouchX = event.getRawX();
                        doVerticalTouchAction(y_changed);
                    } else {
                        // Seek (Right or Left move)
                        doSeekTouch(Math.round(delta_y), mIsRtl ? -xgesturesize : xgesturesize , false);
                    }
                } else {
                    mTouchY = event.getRawY();
                    mTouchX = event.getRawX();
                    mTouchAction = TOUCH_MOVE;
                    final float yaw = mFov * -x_changed/(float)mSurfaceXDisplayRange;
                    final float pitch = mFov * -y_changed/(float)mSurfaceXDisplayRange;
                    mService.updateViewpoint(yaw, pitch, 0, 0, false);
                }
                break;
            case MotionEvent.ACTION_UP:
                // Mouse events for the core
                sendMouseEvent(MotionEvent.ACTION_UP, xTouch, yTouch);
                // Seek
                if (mTouchAction == TOUCH_SEEK)
                    doSeekTouch(Math.round(delta_y), mIsRtl ? -xgesturesize : xgesturesize , true);
                mTouchX = -1f;
                mTouchY = -1f;
                break;
        }
        return mTouchAction != TOUCH_NONE;
    }

    private void doVerticalTouchAction(float y_changed) {
        final boolean rightAction = (int) mTouchX > (4 * mScreen.widthPixels / 7f);
        final boolean leftAction = !rightAction && (int) mTouchX < (3 * mScreen.widthPixels / 7f);
        if (!leftAction && !rightAction)
            return;
        final boolean audio = (mTouchControls & TOUCH_FLAG_AUDIO_VOLUME) != 0;
        final boolean brightness = (mTouchControls & TOUCH_FLAG_BRIGHTNESS) != 0;
        if (!audio && !brightness)
            return;
        if (rightAction ^ mIsRtl) {
            if (audio)
                doVolumeTouch(y_changed);
            else
                doBrightnessTouch(y_changed);
        } else {
            if (brightness)
                doBrightnessTouch(y_changed);
            else
                doVolumeTouch(y_changed);
        }
        hideOverlay(true);
    }

    private void doSeekTouch(int coef, float gesturesize, boolean seek) {
        if (coef == 0)
            coef = 1;
        // No seek action if coef > 0.5 and gesturesize < 1cm
        if (Math.abs(gesturesize) < 1 || !mService.isSeekable())
            return;

        if (mTouchAction != TOUCH_NONE && mTouchAction != TOUCH_SEEK)
            return;
        mTouchAction = TOUCH_SEEK;

        long length = mService.getLength();
        long time = getTime();

        // Size of the jump, 10 minutes max (600000), with a bi-cubic progression, for a 8cm gesture
        int jump = (int) ((Math.signum(gesturesize) * ((600000 * Math.pow((gesturesize / 8), 4)) + 3000)) / coef);

        // Adjust the jump
        if ((jump > 0) && ((time + jump) > length))
            jump = (int) (length - time);
        if ((jump < 0) && ((time + jump) < 0))
            jump = (int) -time;

        //Jump !
        if (seek && length > 0)
            seek(time + jump, length);

        if (length > 0)
            //Show the jump's size
            showInfo(String.format("%s%s (%s)%s",
                    jump >= 0 ? "+" : "",
                    Tools.millisToString(jump),
                    Tools.millisToString(time + jump),
                    coef > 1 ? String.format(" x%.1g", 1.0/coef) : ""), 50);
        else
            showInfo(R.string.unseekable_stream, 1000);
    }

    private void doVolumeTouch(float y_changed) {
        if (mTouchAction != TOUCH_NONE && mTouchAction != TOUCH_VOLUME)
            return;
        float delta = - ((y_changed / (float) mScreen.heightPixels) * mAudioMax);
        mVol += delta;
        int vol = (int) Math.min(Math.max(mVol, 0), mAudioMax * (audioBoostEnabled ? 2 : 1));
        if (delta < 0)
            mOriginalVol = vol;
        if (delta != 0f) {
            if (vol > mAudioMax) {
                if (audioBoostEnabled) {
                    if (mOriginalVol < mAudioMax) {
                        displayWarningToast();
                        setAudioVolume(mAudioMax);
                    } else {
                        setAudioVolume(vol);
                    }
                }
            } else {
                setAudioVolume(vol);
            }
        }
    }

    //Toast that appears only once
    public void displayWarningToast() {
        if(warningToast != null)
            warningToast.cancel();
        warningToast = Toast.makeText(getApplication(), R.string.audio_boost_warning, Toast.LENGTH_SHORT);
        warningToast.show();
    }

    private void setAudioVolume(int vol) {
        if (AndroidUtil.isNougatOrLater && (vol <= 0 ^ mMute)) {
            mute(!mMute);
            return; //Android N+ throws "SecurityException: Not allowed to change Do Not Disturb state"
        }

        /* Since android 4.3, the safe volume warning dialog is displayed only with the FLAG_SHOW_UI flag.
         * We don't want to always show the default UI volume, so show it only when volume is not set. */
        if (vol <= mAudioMax) {
            mService.setVolume(100);
            if (vol !=  mAudioManager.getStreamVolume(AudioManager.STREAM_MUSIC)) {
                try {
                    mAudioManager.setStreamVolume(AudioManager.STREAM_MUSIC, vol, 0);
                    // High Volume warning can block volume setting
                    if (mAudioManager.getStreamVolume(AudioManager.STREAM_MUSIC) != vol)
                        mAudioManager.setStreamVolume(AudioManager.STREAM_MUSIC, vol, AudioManager.FLAG_SHOW_UI);
                } catch (SecurityException ignored) {} //Some device won't allow us to change volume
            }
            vol = Math.round(vol * 100 / mAudioMax);
        } else {
            vol = Math.round(vol * 100 / mAudioMax);
            mService.setVolume(Math.round(vol));
        }
        mTouchAction = TOUCH_VOLUME;
        showInfoWithVerticalBar(getString(R.string.volume) + "\n" + Integer.toString(vol) + '%', 1000, vol, audioBoostEnabled ? 200 : 100);
    }

    private void mute(boolean mute) {
        //:ace
        if(mMute == mute) return;
        ///ace

        mMute = mute;
        if (mMute)
            mVolSave = mService.getVolume();
        mService.setVolume(mMute ? 0 : mVolSave);
    }

    private void updateMute () {
        mute(!mMute);
        showInfo(mMute ? R.string.sound_off : R.string.sound_on, 1000);
    }

    private void initBrightnessTouch() {
        WindowManager.LayoutParams lp = getWindow().getAttributes();
        float brightnesstemp = lp.screenBrightness != -1f ? lp.screenBrightness : 0.6f;
        // Initialize the layoutParams screen brightness
        try {
            if (Settings.System.getInt(getContentResolver(), Settings.System.SCREEN_BRIGHTNESS_MODE) == Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC) {
                if (!Permissions.canWriteSettings(this)) {
                    Permissions.checkWriteSettingsPermission(this, Permissions.PERMISSION_SYSTEM_BRIGHTNESS);
                    return;
                }
                Settings.System.putInt(getContentResolver(),
                        Settings.System.SCREEN_BRIGHTNESS_MODE,
                        Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL);
                mRestoreAutoBrightness = android.provider.Settings.System.getInt(getContentResolver(),
                        android.provider.Settings.System.SCREEN_BRIGHTNESS) / 255.0f;
            } else if (brightnesstemp == 0.6f) {
                brightnesstemp = android.provider.Settings.System.getInt(getContentResolver(),
                        android.provider.Settings.System.SCREEN_BRIGHTNESS) / 255.0f;
            }
        } catch (SettingNotFoundException e) {
            e.printStackTrace();
        }
        lp.screenBrightness = brightnesstemp;
        getWindow().setAttributes(lp);
        mIsFirstBrightnessGesture = false;
    }

    private void doBrightnessTouch(float y_changed) {
        if (mTouchAction != TOUCH_NONE && mTouchAction != TOUCH_BRIGHTNESS)
            return;
        if (mIsFirstBrightnessGesture) initBrightnessTouch();
        mTouchAction = TOUCH_BRIGHTNESS;

        // Set delta : 2f is arbitrary for now, it possibly will change in the future
        float delta = - y_changed / mSurfaceYDisplayRange;

        changeBrightness(delta);
    }

    private void changeBrightness(float delta) {
        // Estimate and adjust Brightness
        WindowManager.LayoutParams lp = getWindow().getAttributes();
        float brightness =  Math.min(Math.max(lp.screenBrightness + delta, 0.01f), 1f);
        setWindowBrightness(brightness);
        brightness = Math.round(brightness * 100);
        showInfoWithVerticalBar(getString(R.string.brightness) + "\n" + (int) brightness + '%', 1000, (int) brightness, 100);
    }

    private void setWindowBrightness(float brightness) {
        WindowManager.LayoutParams lp = getWindow().getAttributes();
        lp.screenBrightness =  brightness;
        // Set Brightness
        getWindow().setAttributes(lp);
    }

    /**
     * handle changes of the seekbar (slicer)
     */
    private final OnSeekBarChangeListener mSeekListener = new OnSeekBarChangeListener() {
        int seekValue = -1;

        @Override
        public void onStartTrackingTouch(SeekBar seekBar) {
            mDragging = true;
            showOverlayTimeout(OVERLAY_INFINITE);
        }

        @Override
        public void onStopTrackingTouch(SeekBar seekBar) {
            mDragging = false;
            showOverlay(true);
            try {
                Logger.vv(TAG, "onStopTrackingTouch: live=" + mIsLive + " length=" + mMediaLength.get());
                doSeek(seekValue);
            } catch (Exception e) {
                Log.e(TAG, "progress seek error", e);
            }
        }

        @Override
        public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
            //TODO: seek now if playing direct content (vlc is seeking now, but we cannot do it for p2p sessions)
            seekValue = progress;
            int currentTime = 0;

            //:ace
            if(fromUser) {
                showOverlay();
            }
            ///ace

            if (!isFinishing() && fromUser && isSeekable() && mMediaLength.get() > 0) {
                //String timeString = "00:00";

                if(mIsLive) {
                    // live
                    if(mLastLivePos != null) {
                        int duration = mLastLivePos.lastTimestamp - mLastLivePos.firstTimestamp;
                        int pieces = mLastLivePos.last - mLastLivePos.first;

                        if (duration > 0 && pieces > 0) {
                            float secondsPerPiece = duration / pieces;
                            currentTime = Math.round((mMediaLength.get() - seekValue) * secondsPerPiece * 1000);
                        }
                    }
                }
                else {
                    // vod
                    currentTime = progress;
                }

                showInfo(Tools.millisToString(currentTime), 1000);
            }
        }
    };

    //:ace
    @MainThread
    private void doSeek(int seekValue) {
        Log.v(TAG, "doSeek: seekTo=" + seekValue + " length=" + mMediaLength.get());
        if(mMediaLength.get() == 0) return;

        int seekTo;

        if(mIsLive) {
            // live
            if(mLastLivePos != null) {
                boolean skipSeek = false;

                seekTo = mLastLivePos.first + seekValue;
                int len = mLastLivePos.last - mLastLivePos.first;
                if(len > 0) {
                    float percentToCurrent = Math.abs(seekTo - mLastLivePos.pos) / (float)len;
                    float percentToLive = Math.abs(seekTo - mLastLivePos.last) / (float)len;

                    if(percentToCurrent < 0.05) {
                        skipSeek = true;
                    }

                    if(percentToLive < 0.05) {
                        seekTo = -1;
                    }
                }

                if(!skipSeek) {
                    Log.d(TAG, "progress:live: seek to: " + seekTo + " (value=" + seekValue + " first=" + mLastLivePos.first + " last=" + mLastLivePos.last + " pos=" + mLastLivePos.pos + ")");

                    if (mAceStreamManager != null) {
                        mAceStreamManager.liveSeek(seekTo);
                    }

                    if (mHudBinding != null) {

                        if(seekTo == -1) {
                            UiTools.setBackgroundWithPadding(mHudBinding.goLiveButton, R.drawable.button_live_blue);
                            mHudBinding.goLiveButton.setTextColor(getResources().getColor(R.color.live_status_yes));
                        }
                        else {
                            UiTools.setBackgroundWithPadding(mHudBinding.goLiveButton, R.drawable.button_live_yellow);
                            mHudBinding.goLiveButton.setTextColor(getResources().getColor(R.color.live_status_no));
                        }
                    }

                    // to freeze live pos for some time
                    freezeLiveStatusAt = new Date().getTime();
                    freezeLivePosAt = new Date().getTime();
                }
            }
        }
        else {
            // vod
            seekTo = seekValue;
            Log.d(TAG, "progress:vod: seek to: " + seekTo);
            seek(seekTo);
        }
    }
    ///ace

    public void onAudioSubClick(View anchor){
        if (anchor == null) {
            initOverlay();
            anchor = mHudBinding.playerOverlayTracks;
        }
        final AppCompatActivity context = this;
        final PopupMenu popupMenu = new PopupMenu(this, anchor);
        final Menu menu = popupMenu.getMenu();
        popupMenu.getMenuInflater().inflate(R.menu.audiosub_tracks, menu);
        //FIXME network subs cannot be enabled & screen cast display is broken with picker
        //r
        //menu.findItem(R.id.video_menu_subtitles_picker).setEnabled(mDisplayManager.isPrimary() && enableSubs);
        //menu.findItem(R.id.video_menu_subtitles_download).setEnabled(enableSubs);
        //--
        // hide subtitle "choose file" and "search"
        menu.findItem(R.id.video_menu_subtitles_picker).setVisible(false);
        menu.findItem(R.id.video_menu_subtitles_download).setVisible(false);
        //<<
        menu.findItem(R.id.video_menu_video_track).setVisible(mService.getVideoTracksCount() > 2);
        menu.findItem(R.id.video_menu_audio_track).setEnabled(mService.getAudioTracksCount() > 0);
        menu.findItem(R.id.video_menu_subtitles).setEnabled(mService.getSpuTracksCount() > 0);
        popupMenu.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(MenuItem item) {
                if (item.getItemId() == R.id.video_menu_audio_track) {
                    selectAudioTrack();
                    return true;
                } else if (item.getItemId() == R.id.video_menu_video_track) {
                    selectVideoTrack();
                    return true;
                } else if (item.getItemId() == R.id.video_menu_subtitles) {
                    selectSubtitles();
                    return true;
                } else if (item.getItemId() == R.id.video_menu_subtitles_picker) {
                    if (mUri == null)
                        return false;
                    mShowingDialog = true;
                    final Intent filePickerIntent = new Intent(context, FilePickerActivity.class);
                    filePickerIntent.setData(Uri.parse(FileUtils.getParent(mUri.toString())));
                    context.startActivityForResult(filePickerIntent, REQUEST_CODE_SELECT_SUBTITLES);
                    return true;
                } else if (item.getItemId() == R.id.video_menu_subtitles_download) {
                    if (mUri == null)
                        return false;
                    MediaUtils.getSubs(VideoPlayerActivity.this, mService.getCurrentMediaWrapper(), new SubtitlesDownloader.Callback() {
                        @Override
                        public void onRequestEnded(boolean success) {
                            if (success)
                                getSubtitles();
                        }
                    });
                }
                hideOverlay(true);
                return false;
            }
        });
        popupMenu.show();
        showOverlay();
    }

    @SuppressWarnings("unused")
    public void onAudioOptionsClick(View anchor){
        if (anchor == null) {
            initOverlay();
            anchor = mHudBinding.selectAudioTrack;
        }
        final AppCompatActivity context = this;
        final PopupMenu popupMenu = new PopupMenu(this, anchor);
        final Menu menu = popupMenu.getMenu();
        popupMenu.getMenuInflater().inflate(R.menu.audio_options, menu);
        menu.findItem(R.id.menu_audio_track).setEnabled(mService.getAudioTracksCount() > 0);
        popupMenu.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(MenuItem item) {
                if (item.getItemId() == R.id.menu_audio_track) {
                    selectAudioTrack();
                    return true;
                } else if (item.getItemId() == R.id.menu_audio_output) {
                    selectAudioOutput();
                    return true;
                }
                hideOverlay(true);
                return false;
            }
        });
        popupMenu.show();
        showOverlay();
    }

    @Override
    public void onPopupMenu(View anchor, final int position) {
        //r
//        final PopupMenu popupMenu = new PopupMenu(this, anchor);
//        popupMenu.getMenuInflater().inflate(R.menu.audio_player, popupMenu.getMenu());
//
//        popupMenu.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
//            @Override
//            public boolean onMenuItemClick(MenuItem item) {
//                if(item.getItemId() == R.id.audio_player_mini_remove) {
//                    if (mService != null) {
//                        mPlaylistAdapter.remove(position);
//                        mService.remove(position);
//                        return true;
//                    }
//                }
//                return false;
//            }
//        });
//        popupMenu.show();
        //--
        //<<
    }

    @Override
    public void updateList() {
        if (mService == null || mPlaylistAdapter == null) return;
        mPlaylistAdapter.update(mService.getMedias());
    }

    @Override
    public void onSelectionSet(int position) {
        mPlaylist.scrollToPosition(position);
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.playlist_toggle:
                togglePlaylist();
                break;
            case R.id.pip_toggle:
                switchToPopup();
                break;
            case R.id.switch_player:
                showResolver();
                break;
            case R.id.player_overlay_adv_function:
                showAdvancedOptions();
                break;
            case R.id.player_overlay_forward:
                seekDelta(10000);
                break;
            case R.id.player_overlay_rewind:
                seekDelta(-10000);
                break;
            case R.id.player_overlay_navmenu:
                showNavMenu();
                break;
            case R.id.player_overlay_length:
            case R.id.player_overlay_time:
                toggleTimeDisplay();
                break;
            case R.id.player_delay_minus:
                if (mPlaybackSetting == DelayState.AUDIO)
                    delayAudio(-50000);
                else if (mPlaybackSetting == DelayState.SUBS)
                    delaySubs(-50000);
                break;
            case R.id.player_delay_plus:
                if (mPlaybackSetting == DelayState.AUDIO)
                    delayAudio(50000);
                else if (mPlaybackSetting == DelayState.SUBS)
                    delaySubs(50000);
                break;
            case R.id.video_renderer:
                if (getSupportFragmentManager().findFragmentByTag("renderers") == null)
                    new RenderersDialog().show(getSupportFragmentManager(), "renderers");
                break;
        }
    }

    //:ace
    private void showResolver() {
        Log.d(TAG, "showResolver");

        if (mService == null) {
            Logger.v(TAG, "showResolver: no service");
            return;
        }

        final MediaWrapper mw = mService.getCurrentMediaWrapper();
        if (mw == null) {
            Logger.v(TAG, "showResolver: no current media");
            return;
        }

        if (!mw.isP2PItem()) {
            Logger.v(TAG, "showResolver: not p2p item");
            return;
        }

        MediaFilesResponse.MediaFile mf = mw.getMediaFile();
        if (mf == null) {
            try {
                mDescriptor = mService.getCurrentMediaWrapper().getDescriptor();
            } catch (TransportFileParsingException e) {
                Logger.v(TAG, "showResolver: failed to get descriptor: " + e.getMessage());
                return;
            }

            if (mAceStreamManager == null) {
                Logger.v(TAG, "showResolver: missing current media file, no playback manager");
            } else {
                Logger.v(TAG, "showResolver: missing current media file, get from engine");

                MediaItem mediaItem = new MediaItem(
                        VideoPlayerActivity.this,
                        mw.getUri(),
                        mw.getTitle(),
                        mw.getId(),
                        mDescriptor,
                        mw.getMediaFile(),
                        new MediaItem.UpdateListener() {
                            @Override
                            public void onTitleChange(MediaItem item, String title) {
                            }

                            @Override
                            public void onP2PInfoChanged(MediaItem item, String infohash, int fileIndex) {
                                mw.setP2PInfo(infohash, fileIndex);
                            }

                            @Override
                            public void onLiveChanged(MediaItem item, int live) {
                                mw.setP2PLive(live);
                            }
                        });

                mAceStreamManager.getMediaFileAsync(mDescriptor, mediaItem, new org.acestream.engine.controller.Callback<Pair<String, MediaFilesResponse.MediaFile>>() {
                    @Override
                    public void onSuccess(Pair<String, MediaFilesResponse.MediaFile> result) {
                        mw.setMediaFile(result.second);
                        showResolver(result.second);
                    }

                    @Override
                    public void onError(String err) {
                        Logger.v(TAG, "showResolver: missing current media file, failed to get from engine: " + err);
                    }
                });
            }
            return;
        }

        showResolver(mf);
    }

    private void showResolver(MediaFilesResponse.MediaFile mf) {
        Intent intent = new AceStream.Resolver.IntentBuilder(
                this,
                mf.infohash,
                mf.type,
                mf.mime)
                .showAceStreamPlayer(!mDisplayManager.isPrimary())
                .allowRememberPlayer(false)
                .build();
        startActivityForResult(intent, REQUEST_CODE_SELECT_PLAYER);
    }
    ///ace

    public void toggleTimeDisplay() {
        sDisplayRemainingTime = !sDisplayRemainingTime;
        showOverlay();
        mSettings.edit().putBoolean(KEY_REMAINING_TIME_DISPLAY, sDisplayRemainingTime).apply();
    }

    public void toggleLock() {
        if (mIsLocked)
            unlockScreen();
        else
            lockScreen();
    }

    public boolean toggleLoop(View v) {
        if (mService == null) return false;
        if (mService.getRepeatType() == Constants.REPEAT_ONE) {
            showInfo(getString(R.string.repeat), 1000);
            mService.setRepeatType(Constants.REPEAT_NONE);
        } else {
            mService.setRepeatType(Constants.REPEAT_ONE);
            showInfo(getString(R.string.repeat_single), 1000);
        }
        return true;
    }

    @Override
    public boolean onScale(ScaleGestureDetector detector) {
        float diff = DEFAULT_FOV * (1 - detector.getScaleFactor());
        if (mService.updateViewpoint(0, 0, 0, diff, false)) {
            mFov = Math.min(Math.max(MIN_FOV, mFov + diff), MAX_FOV);
            return true;
        }
        return false;
    }

    @Override
    public boolean onScaleBegin(ScaleGestureDetector detector) {
        return mSurfaceXDisplayRange!= 0 && mFov != 0f;
    }

    @Override
    public void onScaleEnd(ScaleGestureDetector detector) {}

    @Override
    public void onStorageAccessGranted() {
        mHandler.sendEmptyMessage(START_PLAYBACK);
    }

    @Override
    public void onRenderersChanged(boolean empty) {
        UiTools.setViewVisibility(mRendererBtn, empty ? View.GONE : View.VISIBLE);
    }

    @Override
    public void onRendererChanged(boolean fromUser, @Nullable RendererItemWrapper renderer) {
        if (mRendererBtn != null) mRendererBtn.setImageResource(renderer == null ? R.drawable.ic_renderer_circle : R.drawable.ic_renderer_on_circle);
    }

    private interface TrackSelectedListener {
        void onTrackSelected(int trackID);
    }

    private void selectDeinterlaceMode() {
        if (!isFinishing()) {
            final String[] deinterlaceNames = {"Disable", "Discard", "Blend", "Mean", "Bob", "Linear", "X", "Yadif", "Yadif2x", "Phosphor", "Ivtc"};
            final String[] deinterlaceIds = {"_disable_", "discard", "blend", "mean", "bob", "linear", "x", "yadif", "yadif2x", "phosphor", "ivtc"};

            int listPosition = 0;

            mAlertDialog = new AlertDialog.Builder(VideoPlayerActivity.this)
                    .setTitle("Deinterlace mode")
                    .setSingleChoiceItems(deinterlaceNames, listPosition, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int listPosition) {
                            String id = deinterlaceIds[listPosition];
                            VLCInstance.setDeinterlace(id, true);
                            dialog.dismiss();
                        }
                    })
                    .create();
            mAlertDialog.setCanceledOnTouchOutside(true);
            mAlertDialog.setOwnerActivity(VideoPlayerActivity.this);
            mAlertDialog.show();
        }
    }

    private void setAudioOutput(final String id, boolean pause) {
        if(mService == null) {
            return;
        }

        if(pause) {
            Logger.v(TAG, "setAudioOutput: set pause");
            mService.pause();
            VLCApplication.postOnMainThread(new Runnable() {
                @Override
                public void run() {
                    setAudioOutput(id, false);
                }
            }, 500);
            return;
        }

        boolean success;
        String device = null;
        String aout = VLCOptions.getAout(id);
        mSettings.edit().putString("aout", id).apply();
        if(aout == null) {
            aout = "android_audiotrack";
        }

        if(TextUtils.equals(aout, "android_audiotrack")) {
            device = "encoded";
        }

        Logger.v(TAG, "selectAudioOutput: aout=" + aout + " device=" + device);

        success = mService.setAudioOutput(aout, device);
        Logger.v(TAG, "selectAudioOutput: done: success=" + success);

        int otherTrack = -1;
        int audioTrack = mService.getAudioTrack();
        int audioTrackCount = mService.getAudioTracksCount();

        if(audioTrackCount > 1) {
            otherTrack = (audioTrack + 1) % audioTrackCount;
        }

        if(otherTrack != -1 && !mService.isRemoteDeviceSelected()) {
            Logger.v(TAG, "selectAudioOutput: set new track: track=" + otherTrack);
            mService.setAudioTrack(otherTrack);
            Logger.v(TAG, "selectAudioOutput: set current track: track=" + audioTrack);
            mService.setAudioTrack(audioTrack);
            Logger.v(TAG, "selectAudioOutput: set track done");
        }

        mService.play();

        if(DEBUG_AUDIO_OUTPUT_SWITCHER) {
            VLCApplication.postOnMainThread(new Runnable() {
                @Override
                public void run() {
                    if (TextUtils.equals(id, "1")) {
                        setAudioOutput("0", true);
                    } else {
                        setAudioOutput("1", true);
                    }
                }
            }, 5000);
        }
    }

    public void selectAudioOutput() {
        if (!isFinishing()) {
            final String[] aoutIds = getResources().getStringArray(R.array.aouts_values);
            final SharedPreferences prefs = VLCApplication.getSettings();
            String currentAout = prefs.getString("aout", null);

            if(currentAout == null) {
                // Default is 'android_audiotrack'
                currentAout = "0";
            }

            int listPosition = -1;
            for(int i = 0; i < aoutIds.length; i++) {
                if(TextUtils.equals(aoutIds[i], currentAout)) {
                    listPosition = i;
                    break;
                }
            }

            if(DEBUG_AUDIO_OUTPUT_SWITCHER) {
                VLCApplication.postOnMainThread(new Runnable() {
                    @Override
                    public void run() {
                        setAudioOutput("1", true);
                    }
                }, 10);
                return;
            }

            mAlertDialog = new AlertDialog.Builder(VideoPlayerActivity.this)
                    .setTitle(R.string.aout)
                    .setSingleChoiceItems(R.array.aouts, listPosition, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int listPosition) {
                            //mCurrentAudioOutput = listPosition;
                            final String id = aoutIds[listPosition];
                            setAudioOutput(id, true);
                            dialog.dismiss();
                        }
                    })
                    .create();
            mAlertDialog.setCanceledOnTouchOutside(true);
            mAlertDialog.setOwnerActivity(VideoPlayerActivity.this);
            mAlertDialog.show();
        }
    }

    private void selectTrack(final TrackDescription[] tracks, int currentTrack, int titleId,
                             final TrackSelectedListener listener) {
        if (listener == null)
            throw new IllegalArgumentException("listener must not be null");
        if (tracks == null)
            return;
        final String[] nameList = new String[tracks.length];
        final int[] idList = new int[tracks.length];
        int i = 0;
        int listPosition = 0;
        for (TrackDescription track : tracks) {
            idList[i] = track.id;
            nameList[i] = track.name;
            // map the track position to the list position
            if (track.id == currentTrack)
                listPosition = i;
            i++;
        }

        if (!isFinishing()) {
            mAlertDialog = new AlertDialog.Builder(VideoPlayerActivity.this)
                    .setTitle(titleId)
                    .setSingleChoiceItems(nameList, listPosition, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int listPosition) {
                            int trackID = -1;
                            // Reverse map search...
                            for (TrackDescription track : tracks) {
                                if (idList[listPosition] == track.id) {
                                    trackID = track.id;
                                    break;
                                }
                            }
                            listener.onTrackSelected(trackID);
                            dialog.dismiss();
                        }
                    })
                    .create();
            mAlertDialog.setCanceledOnTouchOutside(true);
            mAlertDialog.setOwnerActivity(VideoPlayerActivity.this);
            mAlertDialog.show();
        }
    }

    private void selectVideoTrack() {
        setESTrackLists();
        selectTrack(mVideoTracksList, mService.getVideoTrack(), R.string.track_video,
                new TrackSelectedListener() {
                    @Override
                    public void onTrackSelected(int trackID) {
                        if (trackID < -1 || mService == null) return;
                        mService.setVideoTrack(trackID);
                        seek(mService.getTime());
                    }
                });
    }

    public void selectAudioTrack() {
        setESTrackLists();
        selectTrack(mAudioTracksList, mService.getAudioTrack(), R.string.track_audio,
                new TrackSelectedListener() {
                    @Override
                    public void onTrackSelected(int trackID) {
                        if (trackID < -1 || mService == null) return;
                        mService.setAudioTrack(trackID);
                        MediaWrapper mw = mMedialibrary.findMedia(mService.getCurrentMediaWrapper());
                        if (mw != null && mw.getId() != 0L)
                            mw.setLongMeta(MediaWrapper.META_AUDIOTRACK, trackID);
                    }
                });
    }

    public void selectSubtitles() {
        setESTrackLists();
        selectTrack(mSubtitleTracksList, mService.getSpuTrack(), R.string.track_text,
                new TrackSelectedListener() {
                    @Override
                    public void onTrackSelected(int trackID) {
                        if (trackID < -1 || mService == null)
                            return;
                        mService.setSpuTrack(trackID);
                        final MediaWrapper mw = mMedialibrary.findMedia(mService.getCurrentMediaWrapper());
                        if (mw != null && mw.getId() != 0L)
                            mw.setLongMeta(MediaWrapper.META_SUBTITLE_TRACK, trackID);
                    }
                });
    }

    private void showNavMenu() {
        if (mMenuIdx >= 0)
            mService.setTitleIdx(mMenuIdx);
    }

    private void updateSeekable(boolean seekable) {
        Logger.vv(TAG, "updateSeekable: seekable=" + seekable);
        if (mHudBinding == null) return;
        mHudBinding.playerOverlayRewind.setEnabled(seekable);
        mHudBinding.playerOverlayRewind.setImageResource(seekable
                ? R.drawable.ic_rewind_circle
                : R.drawable.ic_rewind_circle_disable_o);
        mHudBinding.playerOverlayForward.setEnabled(seekable);
        mHudBinding.playerOverlayForward.setImageResource(seekable
                ? R.drawable.ic_forward_circle
                : R.drawable.ic_forward_circle_disable_o);
        if (!mIsLocked)
            mHudBinding.playerOverlaySeekbar.setEnabled(seekable);
    }

    private void updatePausable(boolean pausable) {
        Logger.vv(TAG, "updatePausable: pausable=" + pausable);
        if (mHudBinding == null) return;
        mHudBinding.playerOverlayPlay.setEnabled(pausable);
        //ace
        // button drawable selector contains "disabled" state
//        if (!pausable)
//            mHudBinding.playerOverlayPlay.setImageResource(R.drawable.ic_play_circle_disable_o);
        //:ace
    }

    public void doPlayPause() {
        if (!mService.isPausable()) return;
        if (mService.isPlaying()) {
            showOverlayTimeout(OVERLAY_INFINITE);
            pause();
        } else {
            hideOverlay(true);
            play();
        }
    }

    private long getTime() {
        long time = mService.getTime();
        if (mForcedTime != -1 && mLastTime != -1) {
            /* XXX: After a seek, mService.getTime can return the position before or after
             * the seek position. Therefore we return mForcedTime in order to avoid the seekBar
             * to move between seek position and the actual position.
             * We have to wait for a valid position (that is after the seek position).
             * to re-init mLastTime and mForcedTime to -1 and return the actual position.
             */
            if (mLastTime > mForcedTime) {
                if (time <= mLastTime && time > mForcedTime || time > mLastTime)
                    mLastTime = mForcedTime = -1;
            } else {
                if (time > mForcedTime)
                    mLastTime = mForcedTime = -1;
            }
        } else if (time == 0) {
            final MediaWrapper mw = mService.getCurrentMediaWrapper();
            if (mw != null)
                time = (int) mw.getTime();
        }
        return mForcedTime == -1 ? time : mForcedTime;
    }

    protected void seek(long position) {
        seek(position, mService.getLength());
    }

    private void seek(long position, long length) {
        mForcedTime = position;
        mLastTime = mService.getTime();
        mService.seek(position, length);
        mProgress.set((int) position);
        mCurrentTime.set((int) position);
    }

    private void seekDelta(int delta) {
        // unseekable stream
        if (mService.getLength() <= 0 || !mService.isSeekable()) return;

        long position = getTime() + delta;
        if (position < 0) position = 0;
        seek(position);
        StringBuilder sb = new StringBuilder();
        if (delta > 0f)
            sb.append('+');
        sb.append((int)(delta/1000f))
                .append("s (")
                .append(Tools.millisToString(mService.getTime()))
                .append(')');
        showInfo(sb.toString(), 1000);
    }

    private void initSeekButton() {
        mHudBinding.playerOverlayRewind.setOnClickListener(this);
        mHudBinding.playerOverlayForward.setOnClickListener(this);
        mHudBinding.playerOverlayRewind.setOnTouchListener(new OnRepeatListener(this));
        mHudBinding.playerOverlayForward.setOnTouchListener(new OnRepeatListener(this));
    }

    public void resizeVideo() {
        int newSize;
        if (mCurrentSize < SURFACE_ORIGINAL) {
            newSize = mCurrentSize+1;
        } else {
            newSize = 0;
        }
        setCurrentSize(newSize);
    }

    protected void setCurrentSize(int size) {
        RemoteDevice remoteDevice = null;
        if(mService != null) {
            remoteDevice = mService.getCurrentRemoteDevice();
        }

        mCurrentSize = size;
        if(remoteDevice != null) {
            remoteDevice.setVideoSize(getVideoSizeName(size));
        }
        else {
            changeSurfaceLayout();
        }
        switch (mCurrentSize) {
            case SURFACE_BEST_FIT:
                showInfo(R.string.surface_best_fit, 1000);
                break;
            case SURFACE_FIT_SCREEN:
                showInfo(R.string.surface_fit_screen, 1000);
                break;
            case SURFACE_FILL:
                showInfo(R.string.surface_fill, 1000);
                break;
            case SURFACE_16_9:
                showInfo("16:9", 1000);
                break;
            case SURFACE_4_3:
                showInfo("4:3", 1000);
                break;
            case SURFACE_ORIGINAL:
                showInfo(R.string.surface_original, 1000);
                break;
        }
        final SharedPreferences.Editor editor = mSettings.edit();
        editor.putInt(PreferencesActivity.VIDEO_RATIO, mCurrentSize);
        editor.apply();
        showOverlay();
    }

    /**
     * show overlay
     * @param forceCheck: adjust the timeout in function of playing state
     */
    private void showOverlay(boolean forceCheck) {
        if (forceCheck)
            mOverlayTimeout = 0;
        showOverlayTimeout(0);
    }

    /**
     * show overlay with the previous timeout value
     */
    private void showOverlay() {
        showOverlay(false);
    }

    /**
     * show overlay
     */
    protected void showOverlayTimeout(int timeout) {
        if(UiTools.isViewVisible(mOverlayTips))
            return;
        if (mService == null)
            return;
        if(isInPictureInPictureMode())
            return;
        initOverlay();
        if (timeout != 0)
            mOverlayTimeout = timeout;
        else
            mOverlayTimeout = mService.isPlaying() ? OVERLAY_TIMEOUT : OVERLAY_INFINITE;
        if (mIsNavMenu){
            mShowing = true;
            return;
        }
        if (mSysTime != null) mHandler.sendEmptyMessage(SHOW_PROGRESS);
        if (!mShowing) {
            mShowing = true;
            if (!mIsLocked) {
                showControls(true);
            }
            else {
                // show lock button
                mHudBinding.lockOverlayButton.setVisibility(View.VISIBLE);
            }
            dimStatusBar(false);
            mHudBinding.progressOverlay.setVisibility(View.VISIBLE);
            if (!mDisplayManager.isPrimary())
                mOverlayBackground.setVisibility(View.VISIBLE);
            updateOverlayPausePlay();
        }
        mHandler.removeMessages(FADE_OUT);
        if (mOverlayTimeout != OVERLAY_INFINITE)
            mHandler.sendMessageDelayed(mHandler.obtainMessage(FADE_OUT), mOverlayTimeout);
    }

    private void showControls(boolean show) {
        if (mHudBinding != null) {
            mHudBinding.playerOverlayPlay.setVisibility(show ? View.VISIBLE : View.INVISIBLE);
            if (mSeekButtons) {
                mHudBinding.playerOverlayRewind.setVisibility(show ? View.VISIBLE : View.INVISIBLE);
                mHudBinding.playerOverlayForward.setVisibility(show ? View.VISIBLE : View.INVISIBLE);
            }
            if(mShowLockButton) {
                mHudBinding.lockOverlayButton.setVisibility(show ? View.VISIBLE : View.INVISIBLE);
            }
            if(show) {
                updateVideoSizeButton();
            }
            else {
                mHudBinding.playerOverlaySize.setVisibility(View.INVISIBLE);
            }
            //mHudBinding.playerOverlayTracks.setVisibility(show ? View.VISIBLE : View.INVISIBLE);
            //mHudBinding.playerOverlayAdvFunction.setVisibility(show ? View.VISIBLE : View.INVISIBLE);
            if (mHasPlaylist) {
                mHudBinding.playlistPrevious.setVisibility(show ? View.VISIBLE : View.INVISIBLE);
                mHudBinding.playlistNext.setVisibility(show ? View.VISIBLE : View.INVISIBLE);
            }
        }
    }

    //r
    //private PlayerHudBinding mHudBinding;
    //private ObservableInt mProgress = new ObservableInt(0);
    //private ObservableLong mMediaLength = new ObservableLong(0L);
    //--
    protected PlayerHudBinding mHudBinding;
    protected ObservableInt mProgress = new ObservableInt(0);
    protected ObservableInt mCurrentTime = new ObservableInt(0);
    protected ObservableLong mMediaLength = new ObservableLong(0L);
    protected ObservableField<String> mTitle = new ObservableField<>();
    //<<
    private boolean mSeekButtons, mHasPlaylist;
    private boolean mShowLockButton = false;
    @SuppressLint("RestrictedApi")
    @TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR1)
    private void initOverlay() {
        final ViewStubCompat vsc = (ViewStubCompat) findViewById(R.id.player_hud_stub);
        if (vsc != null) {
            mSeekButtons = mSettings.getBoolean("enable_seek_buttons", false);
            mShowLockButton = mSettings.getBoolean("show_lock_button", false);
            vsc.inflate();
            mHudBinding = DataBindingUtil.bind(findViewById(R.id.progress_overlay));
            mHudBinding.setPlayer(this);
            updateTimeValues();
            mHudBinding.setProgress(mProgress);
            //:ace
            mHudBinding.setCurrentTime(mCurrentTime);
            mHudBinding.setTitle(mTitle);
            ///ace
            mHudBinding.setLength(mMediaLength);
            final RelativeLayout.LayoutParams layoutParams =
                    (RelativeLayout.LayoutParams)mHudBinding.progressOverlay.getLayoutParams();
            if (AndroidDevices.isPhone || !AndroidDevices.hasNavBar)
                layoutParams.width = LayoutParams.MATCH_PARENT;
            else
                layoutParams.addRule(RelativeLayout.CENTER_HORIZONTAL, RelativeLayout.TRUE);
            mHudBinding.progressOverlay.setLayoutParams(layoutParams);
            mOverlayBackground = findViewById(R.id.player_overlay_background);
            mNavMenu = (ImageView) findViewById(R.id.player_overlay_navmenu);
            if (!AndroidDevices.isChromeBook) {
                //r
                //mRendererBtn = (ImageView) findViewById(R.id.video_renderer);
                //onRemoteDevicesChanged(RendererDelegate.INSTANCE.getRenderers().isEmpty());
                //onRendererChanged(RendererDelegate.INSTANCE.getSelectedRenderer());
                //--
                //<<
            }
            if (mSeekButtons) initSeekButton();
            resetHudLayout();
            updateOverlayPausePlay();
            updateSeekable(isSeekable());
            updatePausable(mService.isPausable() && (mUri != null || mService.isRemoteDeviceConnected()));
            updateNavStatus();
            setListeners(true);
            initPlaylistUi();
            updateTracksSelectors();
            //:ace
            updateStopButton();
            updateVideoSizeButton();
            ///ace
        }
    }


    /**
     * hider overlay
     */
    protected void hideOverlay(boolean fromUser) {
        if(mDisplayManager.isOnRenderer()) {
            return;
        }

        if (mShowing) {
            mHandler.removeMessages(FADE_OUT);
            mHandler.removeMessages(SHOW_PROGRESS);
            Log.i(TAG, "remove View!");
            UiTools.setViewVisibility(mOverlayTips, View.INVISIBLE);
            if (!mDisplayManager.isPrimary()) {
                mOverlayBackground.startAnimation(AnimationUtils.loadAnimation(this, android.R.anim.fade_out));
                mOverlayBackground.setVisibility(View.INVISIBLE);
            }
            mHudBinding.progressOverlay.setVisibility(View.INVISIBLE);
            showControls(false);
            mShowing = false;
            dimStatusBar(true);
        } else if (!fromUser) {
            /*
             * Try to hide the Nav Bar again.
             * It seems that you can't hide the Nav Bar if you previously
             * showed it in the last 1-2 seconds.
             */
            dimStatusBar(true);
        }
    }

    /**
     * Dim the status bar and/or navigation icons when needed on Android 3.x.
     * Hide it on Android 4.0 and later
     */
    @TargetApi(Build.VERSION_CODES.KITKAT)
    private void dimStatusBar(boolean dim) {
        if (dim || mIsLocked)
            hideActionBar();
        else
            showActionBar();
        if (mIsNavMenu)
            return;
        int visibility = 0;
        int navbar = 0;

        visibility = View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN;
        navbar = View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION;
        if (dim || mIsLocked) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
            navbar |= View.SYSTEM_UI_FLAG_LOW_PROFILE;
            if (!AndroidDevices.hasCombBar) {
                navbar |= View.SYSTEM_UI_FLAG_HIDE_NAVIGATION;
                if (AndroidUtil.isKitKatOrLater)
                    visibility |= View.SYSTEM_UI_FLAG_IMMERSIVE;
                visibility |= View.SYSTEM_UI_FLAG_FULLSCREEN;
            }
        } else {
            showActionBar();
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
            visibility |= View.SYSTEM_UI_FLAG_VISIBLE;
        }

        if (AndroidDevices.hasNavBar)
            visibility |= navbar;
        getWindow().getDecorView().setSystemUiVisibility(visibility);
    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
    private void showTitle() {
        if (mIsNavMenu)
            return;
        int visibility = 0;
        int navbar = 0;
        showActionBar();

        visibility = View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN;
        navbar = View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION;
        navbar |= View.SYSTEM_UI_FLAG_HIDE_NAVIGATION;

        if (AndroidDevices.hasNavBar)
            visibility |= navbar;
        getWindow().getDecorView().setSystemUiVisibility(visibility);

    }

    private void showActionBar() {
        mActionBar.show();
    }

    private void hideActionBar() {
        mActionBar.hide();
    }

    private void updateOverlayPausePlay() {
        if (mService == null || mHudBinding == null)
            return;
        if (mService.isPausable())
            mHudBinding.playerOverlayPlay.setImageResource(mService.isPlaying() ? R.drawable.rci_pause_selector
                    : R.drawable.rci_play_selector);
        mHudBinding.playerOverlayPlay.requestFocus();
    }

    private void invalidateESTracks(int type) {
        switch (type) {
            case Media.Track.Type.Audio:
                mAudioTracksList = null;
                break;
            case Media.Track.Type.Text:
                mSubtitleTracksList = null;
                break;
        }

        updateTracksSelectors();
    }

    private void updateTracksSelectors() {
        if(mHudBinding != null) {
            mHudBinding.selectAudioTrack.setVisibility(mIsLocked ? View.GONE : View.VISIBLE);
            mHudBinding.selectAudioTrack.setEnabled(mService.getAudioTracksCount() > 0);
            mHudBinding.selectSubtitles.setVisibility(mService.getSpuTracksCount() > 0 ? View.VISIBLE : View.GONE);
        }
    }

    private void setESTracks() {
        if (mLastAudioTrack >= -1) {
            mService.setAudioTrack(mLastAudioTrack);
            mLastAudioTrack = -2;
        }
        if (mLastSpuTrack >= -1) {
            mService.setSpuTrack(mLastSpuTrack);
            mLastSpuTrack = -2;
        }
    }

    private void setESTrackLists() {
        if (mAudioTracksList == null && mService.getAudioTracksCount() > 0)
            mAudioTracksList = mService.getAudioTracks();
        if (mSubtitleTracksList == null && mService.getSpuTracksCount() > 0)
            mSubtitleTracksList = mService.getSpuTracks();
        if (mVideoTracksList == null && mService.getVideoTracksCount() > 0)
            mVideoTracksList = mService.getVideoTracks();
    }


    /**
     *
     */
    protected void play() {
        if(mService != null)
            mService.play();
        if (mRootView != null)
            mRootView.setKeepScreenOn(true);
    }

    /**
     *
     */
    protected void pause() {
        if(mService != null)
            mService.pause();
        if (mRootView != null)
            mRootView.setKeepScreenOn(false);
    }

    public void next() {
        if (mService != null) mService.next();
    }

    public void previous() {
        if (mService != null) mService.previous(false);
    }

    /*
     * Additionnal method to prevent alert dialog to pop up
     */
    @SuppressWarnings({ "unchecked" })
    private void loadMedia(boolean fromStart) {
        mAskResume = false;
        getIntent().putExtra(Constants.PLAY_EXTRA_FROM_START, fromStart);
        loadMedia();
    }

    /**
     * External extras:
     * - position (long) - position of the video to start with (in ms)
     * - subtitles_location (String) - location of a subtitles file to load
     * - from_start (boolean) - Whether playback should start from start or from resume point
     * - title (String) - video title, will be guessed from file if not set.
     */
    @SuppressLint("SdCardPath")
    @TargetApi(12)
    @SuppressWarnings({ "unchecked" })
    protected void loadMedia() {
        if (mService == null) return;
        mUri = null;
        setPlaying(false);
        String title = null;
        boolean fromStart = false;
        String itemTitle = null;
        int positionInPlaylist = -1;
        final Intent intent = getIntent();
        final Bundle extras = intent.getExtras();
        long savedTime = 0L;
        //:ace
        boolean resetMediaTime = false;
        ///ace
        final boolean hasMedia = mService.hasMedia();
        final boolean isPlaying = mService.isPlaying();
        /*
         * If the activity has been paused by pressing the power button, then
         * pressing it again will show the lock screen.
         * But onResume will also be called, even if vlc-android is still in
         * the background.
         * To workaround this, pause playback if the lockscreen is displayed.
         */
        final KeyguardManager km = (KeyguardManager) getApplicationContext().getSystemService(KEYGUARD_SERVICE);
        if (km != null && km.inKeyguardRestrictedInputMode())
            mWasPaused = true;
        if (mWasPaused)
            Logger.v(TAG, "Video was previously paused, resuming in paused mode");

        if (intent.getData() != null) {
            mUri = intent.getData();
            Logger.v(TAG, "loadMedia: got uri from intent: uri=" + mUri);
        }
        if (extras != null) {
            //:ace
            // disable deinterlace for VOD
            if(!mIsLive) {
                VLCInstance.setDeinterlace(DEINTERLACE_MODE_DISABLED, true);
            }
            ///ace
            if (intent.hasExtra(Constants.PLAY_EXTRA_ITEM_LOCATION)) {
                mUri = extras.getParcelable(Constants.PLAY_EXTRA_ITEM_LOCATION);
                Logger.v(TAG, "loadMedia: got uri from extras: uri=" + mUri);
            }
            fromStart = extras.getBoolean(Constants.PLAY_EXTRA_FROM_START, false);
            // Consume fromStart option after first use to prevent
            // restarting again when playback is paused.
            intent.putExtra(Constants.PLAY_EXTRA_FROM_START, false);
            mAskResume &= !fromStart;
            savedTime = fromStart ? 0L : extras.getLong(Constants.PLAY_EXTRA_START_TIME); // position passed in by intent (ms)
            if (!fromStart && savedTime == 0L)
                savedTime = extras.getInt(Constants.PLAY_EXTRA_START_TIME);
            positionInPlaylist = extras.getInt(Constants.PLAY_EXTRA_OPENED_POSITION, -1);

            //:ace
            // consume this once
            if(mSeekOnStart != -1) {
                if(mSeekOnStart == 0) {
                    fromStart = true;
                    savedTime = 0;
                }
                else {
                    fromStart = false;
                    savedTime = mSeekOnStart;
                }
                mAskResume = false;
                resetMediaTime = true;
                mSeekOnStart = -1;
            }
            ///ace

            Logger.v(TAG, "loadMedia: positionInPlaylist=" + positionInPlaylist);

            if (intent.hasExtra(Constants.PLAY_EXTRA_SUBTITLES_LOCATION))
                synchronized (mSubtitleSelectedFiles) {
                    mSubtitleSelectedFiles.add(extras.getString(Constants.PLAY_EXTRA_SUBTITLES_LOCATION));
                }
            if (intent.hasExtra(Constants.PLAY_EXTRA_ITEM_TITLE))
                itemTitle = extras.getString(Constants.PLAY_EXTRA_ITEM_TITLE);
        }
        final boolean restorePlayback = hasMedia && mService.getCurrentMediaWrapper().getPlaybackUri() != null && mService.getCurrentMediaWrapper().getPlaybackUri().equals(mUri);

        MediaWrapper openedMedia = null;
        final boolean resumePlaylist = mService.isValidIndex(positionInPlaylist);
        final boolean continueplayback = isPlaying && (restorePlayback || positionInPlaylist == mService.getCurrentMediaPosition());

        Logger.v(TAG, "loadMedia: resume=" + resumePlaylist
                + " continue=" + continueplayback
                + " savedTime=" + savedTime
                + " fromStart=" + fromStart
                + " mAskResume=" + mAskResume
                + " resetMediaTime=" + resetMediaTime
                + " uri=" + mUri
                + " hasMedia=" + hasMedia
                + " currentMedia=" + mService.getCurrentMediaWrapper()
                + " hasRenderer=" + mService.hasRenderer()
        );

        if (resumePlaylist) {
            // Provided externally from AudioService
            Logger.v(TAG, "loadMedia: continuing playback from PlaybackService at index " + positionInPlaylist);

            openedMedia = mService.getMedias().get(positionInPlaylist);
            if (openedMedia == null) {
                encounteredError();
                return;
            }
            openedMedia.updateFromMediaLibrary();
            Logger.v(TAG, "loadMedia: got media from playlist: p2p=" + openedMedia.isP2PItem() + " uri=" + openedMedia.getUri() + " playback_uri=" + openedMedia.getPlaybackUri());
            itemTitle = openedMedia.getTitle();
            updateSeekable(isSeekable());
            updatePausable(mService.isPausable());
        }
        mService.addCallback(this);
        if (mUri != null) {
            MediaWrapper media = null;
            if (!continueplayback) {
                if (!resumePlaylist) {
                    // restore last position
                    media = mMedialibrary.getMedia(mUri);
                    if (media == null && TextUtils.equals(mUri.getScheme(), "file") &&
                            mUri.getPath() != null && mUri.getPath().startsWith("/sdcard")) {
                        mUri = FileUtils.convertLocalUri(mUri);
                        media = mMedialibrary.getMedia(mUri);
                    }
                    if (media != null && media.getId() != 0L && media.getTime() == 0L) {
                        media.setTime(media.getMetaLong(MediaWrapper.META_PROGRESS));
                    }
                } else
                    media = openedMedia;

                Runnable resumeYes = new Runnable() {
                    @Override
                    public void run() {
                        loadMedia(false);
                    }
                };
                Runnable resumeNo = new Runnable() {
                    @Override
                    public void run() {
                        loadMedia(true);
                    }
                };

                if (media != null) {
                    // in media library
                    //if (mAskResume && !fromStart && positionInPlaylist == -1 && media.getTime() > 0) {
                    if (mAskResume && !fromStart && media.getTime() > 0) {
                        showConfirmResumeDialog(resumeYes, resumeNo);
                        return;
                    }

                    mLastAudioTrack = media.getAudioTrack();
                    mLastSpuTrack = media.getSpuTrack();
                } else if (!fromStart) {
                    // not in media library
                    if (mAskResume && savedTime > 0L) {
                        showConfirmResumeDialog(resumeYes, resumeNo);
                        return;
                    } else {
                        long rTime = mSettings.getLong(PreferencesActivity.VIDEO_RESUME_TIME, -1);
                        if (rTime > 0) {
                            if (mAskResume) {
                                showConfirmResumeDialog(resumeYes, resumeNo);
                                return;
                            } else {
                                mSettings.edit()
                                    .putLong(PreferencesActivity.VIDEO_RESUME_TIME, -1)
                                    .apply();
                                savedTime = rTime;
                            }
                        }
                    }
                }
            }

            // Start playback & seek
            /* prepare playback */
            final boolean medialoaded = media != null;
            if (!medialoaded) {
                //r
//                if (hasMedia) {
//                    if(BuildConfig.DEBUG) {
//                        Log.v(TAG, "loadMedia: load current media");
//                    }
//                    media = mService.getCurrentMediaWrapper();
//                }
//                else {
//                    if(BuildConfig.DEBUG) {
//                        Log.v(TAG, "loadMedia: create media from uri");
//                    }
//                    media = new MediaWrapper(mUri);
//                }
                //--
                // What is the reason to start current playlist item instead of uri from intent?
                // Original code seems strange.
                Logger.v(TAG, "loadMedia: create media from uri");
                media = new MediaWrapper(mUri);
                //<<
            }

            if (mWasPaused) {
                media.addFlags(MediaWrapper.MEDIA_PAUSED);
            }

            if (intent.hasExtra(Constants.PLAY_DISABLE_HARDWARE))
                media.addFlags(MediaWrapper.MEDIA_NO_HWACCEL);
            media.removeFlags(MediaWrapper.MEDIA_FORCE_AUDIO);
            media.addFlags(MediaWrapper.MEDIA_VIDEO);

            // Set resume point
            if (!continueplayback) {
                //:ace
                if(resetMediaTime)
                    media.setTime(savedTime);
                ///ace
                if (!fromStart && savedTime <= 0L && media.getTime() > 0L)
                    savedTime = media.getTime();
                if (savedTime > 0L)
                    mService.saveTimeToSeek(savedTime);
            }

            Logger.v(TAG, "loadMedia: resume=" + resumePlaylist + " continue=" + continueplayback + " uri=" + media.getPlaybackUri());

            // Handle playback
            Bundle playbackExtras = new Bundle(1);
            playbackExtras.putBoolean("playFromStart", fromStart);
            if (resumePlaylist) {
                if (continueplayback) {
                    if (mDisplayManager.isPrimary()) mService.flush();
                    onPlaying();
                } else
                    mService.playIndex(positionInPlaylist, playbackExtras);
            } else {
                mService.load(media, playbackExtras);
            }

            // Get possible subtitles
            getSubtitles();

            // Get the title
            if (itemTitle == null && !TextUtils.equals(mUri.getScheme(), "content"))
                title = mUri.getLastPathSegment();
        } else if (mService.hasMedia() && mService.hasRenderer()){
            Log.v(TAG, "loadMedia: has media and renderer, start playing");
            runWhenPlaybackManagerReady(new Runnable() {
                @Override
                public void run() {
                    mAceStreamManager.registerCastResultListener(mCastResultListener);
                }
            });
            onPlaying();
        } else {
            Log.v(TAG, "loadMedia: load last playlist");
            mService.loadLastPlaylist(Constants.PLAYLIST_TYPE_VIDEO);
        }
        if (itemTitle != null)
            title = itemTitle;

        if(TextUtils.isEmpty(mTitle.get())) {
            mTitle.set(title);
        }

        if(mService.getCurrentMediaWrapper() != null) {
            mIsLive = mService.getCurrentMediaWrapper().isLive();
            updateSwitchPlayerButton();
        }

        if (mWasPaused) {
            Log.v(TAG, "loadMedia: was paused: savedTime=" + savedTime);
            // XXX: Workaround to update the seekbar position
            mForcedTime = savedTime;
            mForcedTime = -1;
            showOverlay(true);
        }
        enableSubs();
    }

    private boolean enableSubs = true;
    private void enableSubs() {
        if (mUri != null) {
            final String lastPath = mUri.getLastPathSegment();
            enableSubs = !TextUtils.isEmpty(lastPath) && !lastPath.endsWith(".ts") && !lastPath.endsWith(".m2ts")
                    && !lastPath.endsWith(".TS") && !lastPath.endsWith(".M2TS");
        }
    }

    private SubtitlesGetTask mSubtitlesGetTask = null;
    private class SubtitlesGetTask extends AsyncTask<String, Void, List<String>> {
        @Override
        protected List<String> doInBackground(String... strings) {
            final String subtitleList_serialized = strings[0];
            List<String> prefsList = new ArrayList<>();

            if (subtitleList_serialized != null) {
                final ByteArrayInputStream bis = new ByteArrayInputStream(subtitleList_serialized.getBytes());
                ObjectInputStream ois = null;
                try {
                    ois = new ObjectInputStream(bis);
                    prefsList = (List<String>) ois.readObject();
                } catch (InterruptedIOException ignored) {
                    return prefsList; /* Task is cancelled */
                } catch (ClassNotFoundException | IOException ignored) {
                } finally {
                    Util.close(ois);
                }
            }

            if (mUri != null && !TextUtils.equals(mUri.getScheme(), "content"))
                prefsList.addAll(MediaDatabase.getInstance().getSubtitles(mUri.getLastPathSegment()));

            return prefsList;
        }

        @Override
        protected void onPostExecute(List<String> prefsList) {
            // Add any selected subtitle file from the file picker
            if (prefsList.size() > 0) {
                for (String file : prefsList) {
                    synchronized (mSubtitleSelectedFiles) {
                        if (!mSubtitleSelectedFiles.contains(file))
                            mSubtitleSelectedFiles.add(file);
                    }
                    Log.i(TAG, "Adding user-selected subtitle " + file);
                    mService.addSubtitleTrack(file, true);
                }
            }
            mSubtitlesGetTask = null;
        }

        @Override
        protected void onCancelled() {
            mSubtitlesGetTask = null;
        }
    }

    public void getSubtitles() {
        //r
//        if (mSubtitlesGetTask != null || mService == null)
//            return;
//        final String subtitleList_serialized = mSettings.getString(PreferencesActivity.VIDEO_SUBTITLE_FILES, null);
//
//        mSubtitlesGetTask = new SubtitlesGetTask();
//        mSubtitlesGetTask.execute(subtitleList_serialized);
        //--
        // disable subtitles for now
        //<<
    }

    @SuppressWarnings("deprecation")
    private int getScreenRotation(){
        final WindowManager wm = (WindowManager) getApplicationContext().getSystemService(Context.WINDOW_SERVICE);
        if (wm == null) return Surface.ROTATION_0;
        final Display display = wm.getDefaultDisplay();
        try {
            final Method m = display.getClass().getDeclaredMethod("getRotation");
            return (Integer) m.invoke(display);
        } catch (Exception e) {
            return Surface.ROTATION_0;
        }
    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
    private int getScreenOrientation(int mode){
        switch(mode) {
            case 99: //screen orientation user
                return AndroidUtil.isJellyBeanMR2OrLater ?
                        ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR :
                        ActivityInfo.SCREEN_ORIENTATION_SENSOR;
            case 101: //screen orientation landscape
                return ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE;
            case 102: //screen orientation portrait
                return ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT;
        }
        /*
         mScreenOrientation = 100, we lock screen at its current orientation
         */
        final WindowManager wm = (WindowManager) getApplicationContext().getSystemService(Context.WINDOW_SERVICE);
        if (wm == null) return 0;
        final Display display = wm.getDefaultDisplay();
        int rot = getScreenRotation();
        /*
         * Since getRotation() returns the screen's "natural" orientation,
         * which is not guaranteed to be SCREEN_ORIENTATION_PORTRAIT,
         * we have to invert the SCREEN_ORIENTATION value if it is "naturally"
         * landscape.
         */
        @SuppressWarnings("deprecation")
        boolean defaultWide = display.getWidth() > display.getHeight();
        if(rot == Surface.ROTATION_90 || rot == Surface.ROTATION_270)
            defaultWide = !defaultWide;
        if(defaultWide) {
            switch (rot) {
                case Surface.ROTATION_0:
                    return ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE;
                case Surface.ROTATION_90:
                    return ActivityInfo.SCREEN_ORIENTATION_PORTRAIT;
                case Surface.ROTATION_180:
                    // SCREEN_ORIENTATION_REVERSE_PORTRAIT only available since API
                    // Level 9+
                    return ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE;
                case Surface.ROTATION_270:
                    // SCREEN_ORIENTATION_REVERSE_LANDSCAPE only available since API
                    // Level 9+
                    return ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT;
                default:
                    return 0;
            }
        } else {
            switch (rot) {
                case Surface.ROTATION_0:
                    return ActivityInfo.SCREEN_ORIENTATION_PORTRAIT;
                case Surface.ROTATION_90:
                    return ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE;
                case Surface.ROTATION_180:
                    // SCREEN_ORIENTATION_REVERSE_PORTRAIT only available since API
                    // Level 9+
                    return ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT;
                case Surface.ROTATION_270:
                    // SCREEN_ORIENTATION_REVERSE_LANDSCAPE only available since API
                    // Level 9+
                    return ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE;
                default:
                    return 0;
            }
        }
    }

    public void showConfirmResumeDialog(final Runnable runnableYes, final Runnable runnableNo) {
        if (isFinishing())
            return;
        if(mService != null && !mService.hasRenderer())
            mService.pause();
        mAlertDialog = new AlertDialog.Builder(VideoPlayerActivity.this)
                .setMessage(R.string.confirm_resume)
                .setPositiveButton(R.string.resume_from_position, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        runnableYes.run();
                    }
                })
                .setNegativeButton(R.string.play_from_start, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        runnableNo.run();
                    }
                })
                .create();
        mAlertDialog.setCancelable(false);
        mAlertDialog.show();
    }

    public void showAdvancedOptions() {
        final FragmentManager fm = getSupportFragmentManager();
        final AdvOptionsDialog advOptionsDialog = new AdvOptionsDialog();
        final Bundle args = new Bundle(1);
        args.putBoolean(AdvOptionsDialog.PRIMARY_DISPLAY, mDisplayManager.isPrimary());
        advOptionsDialog.setArguments(args);
        advOptionsDialog.setOnDismissListener(new DialogInterface.OnDismissListener() {
            @Override
            public void onDismiss(DialogInterface dialog) {
                dimStatusBar(true);
            }
        });
        advOptionsDialog.show(fm, "fragment_adv_options");
        hideOverlay(false);
    }

    private void togglePlaylist() {
        if (mPlaylist.getVisibility() == View.VISIBLE) {
            mPlaylist.setVisibility(View.GONE);
            mPlaylist.setOnClickListener(null);
            return;
        }
        hideOverlay(true);
        mPlaylist.setVisibility(View.VISIBLE);
        mPlaylist.setAdapter(mPlaylistAdapter);
        updateList();
    }

    private BroadcastReceiver mBtReceiver = new BroadcastReceiver() {
        @TargetApi(Build.VERSION_CODES.HONEYCOMB)
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent == null || intent.getAction() == null) return;
            switch (intent.getAction()) {
                case BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED:
                case BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED:
                    long savedDelay = mSettings.getLong(KEY_BLUETOOTH_DELAY, 0L);
                    long currentDelay = mService.getAudioDelay();
                    if (savedDelay != 0L) {
                        boolean connected = intent.getIntExtra(BluetoothA2dp.EXTRA_STATE, -1) == BluetoothA2dp.STATE_CONNECTED;
                        if (connected && currentDelay == 0L)
                            toggleBtDelay(true);
                        else if (!connected && savedDelay == currentDelay)
                            toggleBtDelay(false);
                    }
            }
        }
    };

    private void toggleBtDelay(boolean connected) {
        mService.setAudioDelay(connected ? mSettings.getLong(KEY_BLUETOOTH_DELAY, 0) : 0L);
    }

    private OnClickListener mBtSaveListener = new OnClickListener() {
        @Override
        public void onClick(View view) {
            mSettings.edit().putLong(KEY_BLUETOOTH_DELAY, mService.getAudioDelay()).apply();
        }
    };

    /**
     * Start the video loading animation.
     */
    private void startLoading() {
        if (mIsLoading)
            return;
        mIsLoading = true;
        //final AnimationSet anim = new AnimationSet(true);
        //final RotateAnimation rotate = new RotateAnimation(0f, 360f, Animation.RELATIVE_TO_SELF, 0.5f, Animation.RELATIVE_TO_SELF, 0.5f);
        //rotate.setDuration(800);
        //rotate.setInterpolator(new DecelerateInterpolator());
        //rotate.setRepeatCount(RotateAnimation.INFINITE);
        //anim.addAnimation(rotate);
        mLoading.setVisibility(View.VISIBLE);
        //mLoading.startAnimation(anim);
    }

    /**
     * Stop the video loading animation.
     */
    private void stopLoading() {
        //mHandler.removeMessages(LOADING_ANIMATION);
        if (!mIsLoading) return;
        mIsLoading = false;
        mLoading.setVisibility(View.INVISIBLE);
        //mLoading.clearAnimation();
    }

    public void onClickOverlayTips(View v) {
        UiTools.setViewVisibility(mOverlayTips, View.GONE);
    }

    public void onClickDismissTips(View v) {
        UiTools.setViewVisibility(mOverlayTips, View.GONE);
        mSettings.edit().putBoolean(PREF_TIPS_SHOWN, true).apply();
    }

    private void updateNavStatus() {
        mIsNavMenu = false;
        mMenuIdx = -1;

        final MediaPlayer.Title[] titles = mService.getTitles();
        if (titles != null) {
            final int currentIdx = mService.getTitleIdx();
            for (int i = 0; i < titles.length; ++i) {
                final MediaPlayer.Title title = titles[i];
                if (title.isMenu()) {
                    mMenuIdx = i;
                    break;
                }
            }
            mIsNavMenu = mMenuIdx == currentIdx;
        }

        if (mIsNavMenu) {
            /*
             * Keep the overlay hidden in order to have touch events directly
             * transmitted to navigation handling.
             */
            hideOverlay(false);
        }
        else if (mMenuIdx != -1)
            setESTracks();

        UiTools.setViewVisibility(mNavMenu, mMenuIdx >= 0 && mNavMenu != null ? View.VISIBLE : View.GONE);
        supportInvalidateOptionsMenu();
    }

    private GestureDetector.SimpleOnGestureListener mGestureListener = new GestureDetector.SimpleOnGestureListener() {

        @Override
        public boolean onSingleTapUp(MotionEvent e) {
            mHandler.sendEmptyMessageDelayed(mShowing ? HIDE_INFO : SHOW_INFO, 200);
            return true;
        }

        @Override
        public boolean onDoubleTap(MotionEvent e) {
            mHandler.removeMessages(HIDE_INFO);
            mHandler.removeMessages(SHOW_INFO);
            float range = mCurrentScreenOrientation == Configuration.ORIENTATION_LANDSCAPE ? mSurfaceXDisplayRange : mSurfaceYDisplayRange;
            if (mService == null)
                return false;
            if (!mIsLocked) {
                if ((mTouchControls & TOUCH_FLAG_SEEK) == 0) {
                    doPlayPause();
                    return true;
                }
                float x = e.getX();
                if (x < range/4f)
                    seekDelta(-10000);
                else if (x > range*0.75)
                    seekDelta(10000);
                else
                    doPlayPause();
                return true;
            }
            return false;
        }
    };

    public PlaybackServiceActivity.Helper getHelper() {
        return mHelper;
    }

    @Override
    public void onNewVideoLayout(IVLCVout vlcVout, int width, int height, int visibleWidth, int visibleHeight, int sarNum, int sarDen) {
        // store video size
        mVideoWidth = width;
        mVideoHeight = height;
        mVideoVisibleWidth  = visibleWidth;
        mVideoVisibleHeight = visibleHeight;
        mSarNum = sarNum;
        mSarDen = sarDen;
        changeSurfaceLayout();
    }

    @Override
    public void onSurfacesCreated(IVLCVout vlcVout) {
    }

    @Override
    public void onSurfacesDestroyed(IVLCVout vlcVout) {
    }

    private BroadcastReceiver mServiceReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (TextUtils.equals(intent.getAction(), Constants.PLAY_FROM_SERVICE)) {
                // We receive this intent when regular item started.
                Log.v(TAG, "receiver: play from service");
                if(mAceStreamManager != null) {
                    mAceStreamManager.stopEngineSession(true);
                }
                mIsLive = false;
                showLiveContainer(false);
                showStreamSelectorContainer(false);
                updatePlaybackStatus();
                onNewIntent(intent);
            }
            else if (TextUtils.equals(intent.getAction(), Constants.ACTION_P2P_SESSION_STARTED)) {
                unfreezeEngineStatus();
            }
            else if (TextUtils.equals(intent.getAction(), Constants.ACTION_P2P_STARTED)) {
                Log.v(TAG, "receiver: p2p playback started");
                onNewIntent(intent);
            }
            else if (TextUtils.equals(intent.getAction(), Constants.EXIT_PLAYER)) {
                mStoppingOnDeviceDisconnect = intent.getBooleanExtra(Constants.PLAY_EXTRA_STOP_AFTER_DEVICE_DISCONNECT, false);
                Logger.v(TAG, "receiver: exit player: stoppingOnDeviceDisconnect=" + mStoppingOnDeviceDisconnect);
                exitOK();
            }
            else if (TextUtils.equals(intent.getAction(), Constants.ACTION_P2P_STARTING)) {
                Log.v(TAG, "receiver: p2p starting");
                mUri = null;
                updatePausable(false);
                mProgress.set(0);
                mCurrentTime.set(0);
                mMediaLength.set(0);
                mIsLive = false;

                showLiveContainer(false);
                showStreamSelectorContainer(false);
                freezeEngineStatus(5000);
                newItemSelected();
                setEngineStatus(EngineStatus.fromString("starting"));
                mExitOnStop = false;
                mService.stopPlayer();
            }
            else if (TextUtils.equals(intent.getAction(), Constants.ACTION_P2P_FAILED)) {
                unfreezeEngineStatus();
                String errorMessage = intent.getStringExtra(EXTRA_ERROR_MESSAGE);
                Logger.v(TAG, "receiver: p2p failed: " + errorMessage);
                setEngineStatus(EngineStatus.error(errorMessage));
            }
        }
    };

    public boolean getIsLive() {
        return mIsLive;
    }

    @BindingAdapter({"player", "length", "time"})
    public static void setPlaybackTime(TextView view, VideoPlayerActivity player, long length, int time) {
        //r
        //view.setText(sDisplayRemainingTime && length > 0
        //        ? "-" + '\u00A0' + Tools.millisToString(length - time)
        //        : Tools.millisToString(length));
        //--
        String text;
        if(player.getIsLive()) {
            text = Tools.millisToString(0);
        }
        else {
            text = sDisplayRemainingTime && length > 0
                    ? "-" + '\u00A0' + Tools.millisToString(length - time)
                    : Tools.millisToString(length);
        }
        view.setText(text);
        //<<
    }

    @BindingAdapter({"mediamax"})
    public static void setProgressMax(SeekBar view, long length) {
        view.setMax((int) length);
    }

    //:ace
    protected boolean isSeekable() {
        if(mIsLive) {
            return true;
        }
        else if(mService != null) {
            return mService.isSeekable();
        }
        else {
            return false;
        }
    }

    private void showLiveContainer(boolean visible) {
        if(mHudBinding != null) {
            int marginRight;
            int nextFocusUpId;

            if(visible) {
                mHudBinding.liveContainer.setVisibility(View.VISIBLE);
                marginRight = getResources().getDimensionPixelSize(R.dimen.time_margin_with_live_button);
                nextFocusUpId = R.id.go_live_button;
            }
            else {
                mHudBinding.liveContainer.setVisibility(View.GONE);
                marginRight = getResources().getDimensionPixelSize(R.dimen.time_margin_sides);
                nextFocusUpId = R.id.player_overlay_seekbar;
            }

            mHudBinding.lockOverlayButton.setNextFocusUpId(nextFocusUpId);
            mHudBinding.playerOverlayTracks.setNextFocusUpId(nextFocusUpId);
            mHudBinding.selectSubtitles.setNextFocusUpId(nextFocusUpId);
            mHudBinding.selectAudioTrack.setNextFocusUpId(nextFocusUpId);
            mHudBinding.playlistPrevious.setNextFocusUpId(nextFocusUpId);
            mHudBinding.playerOverlayRewind.setNextFocusUpId(nextFocusUpId);
            mHudBinding.playerOverlayPlay.setNextFocusUpId(nextFocusUpId);
            mHudBinding.playerOverlayForward.setNextFocusUpId(nextFocusUpId);
            mHudBinding.playlistNext.setNextFocusUpId(nextFocusUpId);
            mHudBinding.playerOverlaySize.setNextFocusUpId(nextFocusUpId);

            final RelativeLayout.LayoutParams lp = (RelativeLayout.LayoutParams) mHudBinding.playerOverlayLength.getLayoutParams();
            lp.setMargins(0, 0, marginRight, 0);
            mHudBinding.playerOverlayLength.setLayoutParams(lp);
        }
    }

    private void showStreamSelectorContainer(boolean visible) {
        if(mHudBinding != null) {
            int marginLeft;
            mHudBinding.streamSelectorContainer.setVisibility(visible ? View.VISIBLE : View.GONE);

            if(visible) {
                mHudBinding.streamSelectorContainer.setVisibility(View.VISIBLE);
                marginLeft = getResources().getDimensionPixelSize(R.dimen.time_margin_with_stream_selector);
            }
            else {
                mHudBinding.streamSelectorContainer.setVisibility(View.GONE);
                marginLeft = getResources().getDimensionPixelSize(R.dimen.time_margin_sides);
            }

            final RelativeLayout.LayoutParams lp = (RelativeLayout.LayoutParams) mHudBinding.playerOverlayTime.getLayoutParams();
            lp.setMargins(marginLeft, 0, 0, 0);
            mHudBinding.playerOverlayTime.setLayoutParams(lp);
        }
    }

    private void processEngineStatus(final EngineStatus status) {
        setEngineStatus(status);

        // livepos
        mLastLivePos = status.livePos;
        if (status.livePos == null) {
            showLiveContainer(false);
        } else if(!TextUtils.equals(status.outputFormat, "http")) {
            // We can seek only for http output format
            showLiveContainer(false);
        } else {
            showLiveContainer(true);

            if (status.livePos.first == -1) {
                //pass
            } else if (status.livePos.last == -1) {
                //pass
            } else if (status.livePos.pos == -1) {
                //pass
            } else if (status.livePos.lastTimestamp == -1) {
                //pass
            } else if (status.livePos.firstTimestamp == -1) {
                //pass
            } else {
                int duration = status.livePos.lastTimestamp - status.livePos.firstTimestamp;
                int pieces = status.livePos.last - status.livePos.first;
                int offset = status.livePos.pos - status.livePos.first;

                long posAge = new Date().getTime() - freezeLivePosAt;
                boolean isUserSeeking = mDragging || (mHudBinding != null && mHudBinding.playerOverlaySeekbar.hasFocus());
                if (!isUserSeeking && (posAge > FREEZE_LIVE_POS_FOR)) {
                    mMediaLength.set(pieces);
                    mProgress.set(offset);

                    if(mHudBinding != null) {
                        // Fix strange binding behavior: sometimes after media length and progress
                        // change seek bar uses old progress value.
                        // To fix this need to emit additional onPropertyChanged event.
                        if(mProgress.get() != mHudBinding.playerOverlaySeekbar.getProgress()) {
                            mProgress.set(offset-1);
                            mProgress.set(offset);
                        }
                    }

                    mCurrentTime.set(-duration * 1000);
                }

                if(mHudBinding != null) {
                    long statusAge = new Date().getTime() - freezeLiveStatusAt;
                    if (statusAge > FREEZE_LIVE_STATUS_FOR) {
                        if(status.livePos.isLive) {
                            UiTools.setBackgroundWithPadding(mHudBinding.goLiveButton, R.drawable.button_live_blue);
                            mHudBinding.goLiveButton.setTextColor(getResources().getColor(R.color.live_status_yes));
                        }
                        else {
                            UiTools.setBackgroundWithPadding(mHudBinding.goLiveButton, R.drawable.button_live_yellow);
                            mHudBinding.goLiveButton.setTextColor(getResources().getColor(R.color.live_status_no));
                        }
                    }
                }
            }
        }

        // Stream selector
        if(mHudBinding != null) {
            // list of streams
            // show only for remote device, our player and http output
            if (status.streams.size() > 0 && TextUtils.equals(status.outputFormat, "http")) {
                if (status.currentStreamIndex < 0 || status.currentStreamIndex >= status.streams.size()) {
                    Log.w(TAG, "processEngineStatus: bad remote stream index: index=" + status.currentStreamIndex + " streams=" + status.streams.size());
                    showStreamSelectorContainer(false);
                } else {
                    showStreamSelectorContainer(true);

                    String streamName;
                    streamName = status.streams.get(status.currentStreamIndex).getName();

                    mHudBinding.selectStreamButton.setText(streamName);

                    if (streamName.length() > 6) {
                        mHudBinding.selectStreamButton.setTextSize(8);
                    } else {
                        mHudBinding.selectStreamButton.setTextSize(12);
                    }
                }
            } else {
                showStreamSelectorContainer(false);
            }
        }

        // Debug info
        if(showDebugInfo()) {
            StringBuilder sb = new StringBuilder(100);
            sb.append("status: ").append(status.status);
            sb.append("\npeers: ").append(status.peers);
            sb.append("\ndl: ").append(status.speedDown);
            sb.append("\nul: ").append(status.speedUp);
            sb.append("\nlive: ").append(status.isLive);
            sb.append("\nof: ").append(status.outputFormat);

            SystemUsageInfo si = status.systemInfo;
            if(si == null) {
                si = MiscUtils.getSystemUsage(this);
            }

            if(si != null) {
                long p = -1;
                if(si.memoryTotal != 0)
                    p = Math.round(si.memoryAvailable / si.memoryTotal * 100);
                sb.append("\nram: ").append(p).append("%");

                sb.append("\ncpu: ").append(Math.round(si.cpuUsage * 100)).append("%");
            }

            mDebugInfo.setText(sb.toString());
        }
    }

    public void goLive() {
        Log.d(TAG, "goLive");

        boolean isLive = true;
        if(mLastLivePos != null) {
            isLive = mLastLivePos.isLive;
        }

        if(!isLive) {
            if(mAceStreamManager != null) {
                mAceStreamManager.liveSeek(-1);
            }
            if(mHudBinding != null) {
                UiTools.setBackgroundWithPadding(mHudBinding.goLiveButton, R.drawable.button_live_blue);
                mHudBinding.goLiveButton.setTextColor(getResources().getColor(R.color.live_status_yes));
            }

            mProgress.set((int)mMediaLength.get());

            // to freeze status and pos for some time
            freezeLiveStatusAt = new Date().getTime();
            freezeLivePosAt = new Date().getTime();
        }
    }

    public void selectStream() {
        Log.e(TAG, "selectStream: not implemented");
//        Log.d(TAG, "selectStream");
//        Playlist playlist = null;
//
//        if(mAceStreamManager != null) {
//            playlist = mAceStreamManager.getCurrentPlaylist();
//        }
//
//        if(playlist == null) {
//            Log.d(TAG, "click:select_stream: no playlist");
//            return;
//        }
//
//        final List<ContentStream> originalStreams = playlist.getStreams();
//
//        // filter audio streams because of bug in engine (cannot select audio stream)
//        final List<ContentStream> streams = new ArrayList<>();
//        for(ContentStream stream: originalStreams) {
//            if(!stream.getName().startsWith("Audio")) {
//                streams.add(stream);
//            }
//        }
//
//        if(streams.size() == 0) {
//            Log.d(TAG, "click:select_stream: no streams");
//            return;
//        }
//
//        String[] entries = new String[streams.size()];
//        for(int i = 0; i < streams.size(); i++) {
//            entries[i] = streams.get(i).getName();
//        }
//        int selectedId = playlist.getCurrentStreamIndex();
//
//        AlertDialog.Builder builder = new AlertDialog.Builder(this);
//        //TODO: translate
//        builder.setTitle("Select stream");
//        builder.setSingleChoiceItems(entries, selectedId, new DialogInterface.OnClickListener() {
//            @Override
//            public void onClick(DialogInterface dialogInterface, int i) {
//                switchStream(i, streams.get(i).streamType);
//                dialogInterface.dismiss();
//            }
//        });
//
//        Dialog dialog = builder.create();
//        dialog.show();
    }

    private void switchStream(int streamIndex, int streamType) {
        Log.e(TAG, "switchStream: not implemented");
//        Log.d(TAG, "switchStream: type=" + streamType + " index=" + streamIndex);
//
//        if(mAceStreamManager == null) {
//            Log.e(TAG, "switchStream: missing playback manager");
//            return;
//        }
//
//        if(streamType == ContentStream.StreamType.HLS) {
//            mAceStreamManager.setHlsStream(streamIndex);
//        }
//        else if(streamType == ContentStream.StreamType.DIRECT) {
//            Playlist playlist = mAceStreamManager.getCurrentPlaylist();
//            if (playlist == null) {
//                Log.d(TAG, "switchStream: missing current playlist");
//                return;
//            }
//
//            Log.d(TAG, "switchStream: current=" + playlist.getCurrentStreamIndex() + " new=" + streamIndex);
//            playlist.setCurrentStreamIndex(streamIndex);
//
//            mService.switchStream(streamIndex);
//        }
//        else {
//            Log.e(TAG, "switchStream: unknown stream type: index=" + streamIndex + " type=" + streamType);
//        }
    }

    @Override
    public void onRestartPlayer() {
        Log.d(TAG, "onRestartPlayer");
        if(mService != null) {
            mRestartingPlayer = true;
            mService.stopPlayer();

            mHandler.postDelayed(mEnsurePlayerIsPlayingTask, 5000);
        }
    }

    @Override
    public void onConnected(PlaybackService service) {
        Log.v(TAG, "connected playback service");
        mService = service;

        if(mDisplayManager.isPrimary()) {
            MediaWrapper mw = mService.getCurrentMediaWrapper();
            if(mw != null && AceStreamUtils.shouldStartAceStreamPlayer(mw)) {
                mService.playIndex(mService.getCurrentMediaPosition());
                finish();
                return;
            }
        }

        if(mLastRemoteDeviceId != null
                && !mService.isRemoteDeviceConnected()
                && !intentHasTransportDescriptor()) {
            Log.d(TAG, "ps-connected: exit, missing remote device on start: lastRemoteDeviceId=" + mLastRemoteDeviceId);
            finish();
            return;
        }

        if(!intentHasTransportDescriptor()) {
            //We may not have the permission to access files
            if (Permissions.checkReadStoragePermission(VideoPlayerActivity.this, true) && !mSwitchingView) {
                mHandler.sendEmptyMessage(START_PLAYBACK);
            }
        }
        mSwitchingView = false;
        mSettings.edit().putBoolean(PreferencesActivity.VIDEO_RESTORE, false).apply();
        if (mService.getVolume() > 100 && !audioBoostEnabled)
            mService.setVolume(100);

        //:ace
        mActivityHelper.onStart();

        if(mService.isRemoteDeviceConnected()) {
            showOverlayTimeout(OVERLAY_INFINITE);
            if(mCastingInfo != null) {
                if(mService.isRemoteDeviceSelected()) {
                    mCastingInfo.setText("Casting to " + mService.getCurrentRemoteDevice().getName());
                }
            }
        }
        ///ace
    }

    @Override
    public void onDisconnected() {
        Log.v(TAG, "disconnected playback service");
        mService = null;
        mHandler.sendEmptyMessage(AUDIO_SERVICE_CONNECTION_FAILED);

        //:ace
        mActivityHelper.onStop();
        ///ace
    }

    protected void setEngineStatus(EngineStatus status) {
        mLastEngineStatus = status;
        updatePlaybackStatus();
    }

    protected void setPlaying(boolean playing) {
        mIsPlaying = playing;
        updatePlaybackStatus();
    }

    protected void setBuffering(boolean buffering) {
        mIsBuffering = buffering;
        updatePlaybackStatus();
    }

    protected void updatePlaybackStatus() {
        boolean p2p = mService != null
                && mService.getCurrentMediaWrapper() != null
                && mService.getCurrentMediaWrapper().isP2PItem();

        boolean showOverlay;
        boolean showProgress;
        String message = null;

        if(p2p && mLastEngineStatus != null) {
            switch(mLastEngineStatus.status) {
                case "engine_unpacking":
                    message = getResources().getString(R.string.dialog_unpack);
                    break;
                case "engine_starting":
                    message = getResources().getString(R.string.dialog_start);
                    break;
                case "engine_failed":
                    message = getResources().getString(R.string.start_fail);
                    break;
                case "loading":
                    message = getResources().getString(R.string.loading);
                    break;
                case "starting":
                    message = getResources().getString(R.string.starting);
                    break;
                case "checking":
                    message = getResources().getString(R.string.status_checking_short, mLastEngineStatus.progress);
                    break;
                case "prebuf":
                    message = getResources().getString(R.string.status_prebuffering, mLastEngineStatus.progress, mLastEngineStatus.peers, mLastEngineStatus.speedDown);
                    break;
                case "error":
                    message = mLastEngineStatus.errorMessage;
                    break;
                case "dl":
                    break;
            }
        }

        if(!p2p) {
            showOverlay = false;
            showProgress = false;
        }
        else if(TextUtils.isEmpty(message)) {
            showOverlay = !mIsPlaying;
            showProgress = !mIsPlaying || mIsBuffering;
        }
        else {
            showOverlay = true;
            showProgress = false;
        }

        if(DEBUG_LOG_ENGINE_STATUS) {
            Log.v(TAG, "show_status: engine=" + (mLastEngineStatus == null ? null : mLastEngineStatus.status) + " playing=" + mIsPlaying + " buffering=" + mIsBuffering + " overlay=" + showOverlay + " progress=" + showProgress + " msg=" + message);
        }

        showStatusOverlay(showOverlay, message);

        if(showProgress)
            startLoading();
        else
            stopLoading();
    }

    protected void showStatusOverlay(final boolean visible, final String message) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mEngineStatus.setVisibility(visible ? View.VISIBLE : View.GONE);
                if(message == null) {
                    mEngineStatus.setText("");
                }
                else {
                    mEngineStatus.setText(message);
                }
            }
        });
    }

    //:ace
    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    private boolean intentHasTransportDescriptor() {
        return getIntent().hasExtra(EXTRA_TRANSPORT_DESCRIPTOR);
    }

    private void handleIntent(Intent intent) {
        String data = intent.getStringExtra(EXTRA_TRANSPORT_DESCRIPTOR);

        Logger.v(TAG, "handleIntent: intent=" + intent + " data=" + data);

        if(mWasStopped && !intentHasTransportDescriptor() && mService != null && !mService.hasMedia() && mService.hasLastPlaylist()) {
            Log.v(TAG, "handleIntent: was stopped, start last playlist");
            mWasStopped = false;
            mService.loadLastPlaylist(org.videolan.vlc.util.Constants.PLAYLIST_TYPE_VIDEO);
            return;
        }

        if(data == null) {
            Logger.v(TAG, "handleIntent: no descriptor");
            return;
        }

        TransportFileDescriptor descriptor = TransportFileDescriptor.fromJson(data);
        descriptor.setTransportFileData(AceStream.getTransportFileFromCache(descriptor.getDescriptorString()));

        resumePlayback(descriptor);
    }

    private void resumePlayback(@NonNull TransportFileDescriptor descriptor) {
        boolean descriptorChanged = false;
        boolean fileIndexChanged = false;

        if(mDescriptor == null) {
            descriptorChanged = true;
        }
        else if(!mDescriptor.equals(descriptor)) {
            descriptorChanged = true;
        }

        if(descriptorChanged) {
            Log.v(TAG, "resumePlayback: descriptor changed");
            mDescriptor = descriptor;
        }
        else if(mService != null) {
            // check file index
            int currentFileIndex = -1;
            int selectedFileIndex = getIntent().getIntExtra(EXTRA_FILE_INDEX, 0);
            MediaWrapper mw = mService.getCurrentMediaWrapper();
            Logger.v(TAG, "resumePlayback: current=" + mw);
            if(mw != null) {
                MediaFilesResponse.MediaFile mf = mw.getMediaFile();
                if(mf != null) {
                    currentFileIndex = mf.index;
                }
            }

            if(selectedFileIndex != currentFileIndex) {
                fileIndexChanged = true;
                int pos = mService.findPositionByFileIndex(selectedFileIndex);
                Log.v(TAG, "resumePlayback: file index changed: index=" + currentFileIndex + "->" + selectedFileIndex + " pos=" + pos);
                if(pos != -1) {
                    mService.playIndex(pos);
                    return;
                }
            }
        }

        //TODO: make this code more elegant
        // When switching to another playlist item from AceCast remote control
        // this code detect file index change on the server side.
        // All we need is to switch to another item without reloading playlist.
        // But currently remote control always sends "stop" command before
        // switching to another item, so playlist is always empty here.
        if(descriptorChanged || fileIndexChanged) {
            loadMediaFiles();
        }
    }

    private void loadMediaFiles() {
        ensureEngineService();
        // Load transport file
        setEngineStatus(EngineStatus.fromString("loading"));
        mEngineService.getMediaFiles(mDescriptor, new Callback<MediaFilesResponse>() {
            @Override
            public void onSuccess(MediaFilesResponse result) {
                gotMediaFiles(result);
            }

            @Override
            public void onError(String err) {
                setEngineStatus(EngineStatus.error(err));
            }
        });
    }

    private void gotMediaFiles(MediaFilesResponse response) {
        // We can receive results after PS/PM disconnect, so need to check
        if(mService == null) {
            Logger.v(TAG, "gotMediaFiles: missing playback service");
            return;
        }

        if(mAceStreamManager == null) {
            Logger.v(TAG, "gotMediaFiles: missing playback manager");
            return;
        }

        mDescriptor.setTransportFileData(response.transport_file_data);
        mDescriptor.setCacheKey(response.transport_file_cache_key);
        mDescriptor.setInfohash(response.infohash);

        mMediaFiles = response;

        int selectedFileIndex = getIntent().getIntExtra(EXTRA_FILE_INDEX, -1);
        List<MediaWrapper> mwList = new ArrayList<>();

        if(mService == null) {
            Logger.v(TAG, "gotMediaFiles: no service");
            return;
        }

        final File transportFile = MiscUtils.getFile(mDescriptor.getLocalPath());
        for(MediaFilesResponse.MediaFile mf: response.files) {
            boolean isMulti = response.files.length > 1;
            MediaWrapper mw = new MediaWrapper(mDescriptor, mf);
            mwList.add(mw);

            // Add to ML
            MediaWrapper mlItem = mMedialibrary.findMedia(mw);
            if(mlItem == null || mlItem.getId() == 0) {
                mw = mMedialibrary.addMedia(mw);
                if(mw != null) {
                    if (isMulti && !TextUtils.isEmpty(response.name)) {
                        // Set group name for multifile torrent
                        mw.setStringMeta(MediaWrapper.META_GROUP_NAME, response.name);
                    }
                    if (!mf.isLive()) {
                        mw.setLongMeta(MediaWrapper.META_FILE_SIZE,
                                mf.size);
                    }
                    if (transportFile != null && transportFile.exists()) {
                        mw.setStringMeta(MediaWrapper.META_TRANSPORT_FILE_PATH,
                                transportFile.getAbsolutePath());
                        mw.setLongMeta(MediaWrapper.META_LAST_MODIFIED,
                                transportFile.lastModified());
                    }
                }
            }
        }

        Collections.sort(mwList, new Comparator<MediaWrapper>() {
            @Override
            public int compare(MediaWrapper a, MediaWrapper b) {
                return a.getTitle().compareToIgnoreCase(b.getTitle());
            }
        });

        // Detect position after sorting
        int startPosition = 0;
        for(int i = 0; i < mwList.size(); i++) {
            MediaFilesResponse.MediaFile mf = mwList.get(i).getMediaFile();
            if(mf.index == selectedFileIndex) {
                startPosition = i;
            }
        }

        mSeekOnStart = getIntent().getLongExtra(EXTRA_SEEK_ON_START, -1);

        Logger.v(TAG, "gotMediaFiles: count=" + mwList.size() + " index=" + selectedFileIndex + " pos=" + startPosition + " seek=" + mSeekOnStart);

        mService.load(mwList, startPosition);
    }

    private void ensureEngineService() {
        if(mEngineService == null) {
            throw new IllegalStateException("missing engine service");
        }
    }
    ///from_engine_video_player_activity

    private void freezeEngineStatus(long delay) {
        freezeEngineStatusAt = System.currentTimeMillis();
        freezeEngineStatusFor = delay;
    }

    private void unfreezeEngineStatus() {
        freezeEngineStatusAt = 0;
        freezeEngineStatusFor = 0;
    }

    private boolean areViewsAttached() {
        return mService != null && mService.getVLCVout().areViewsAttached();
    }

    private void runWhenPlaybackManagerReady(Runnable runnable) {
        runWhenPlaybackManagerReady(runnable, false);
    }

    private void runWhenPlaybackManagerReady(Runnable runnable, boolean alwaysSchedule) {
        if(mAceStreamManager != null) {
            if(alwaysSchedule) {
                mHandler.post(runnable);
            }
            else {
                runnable.run();
            }
        }
        else {
            mPlaybackManagerOnReadyQueue.add(runnable);
        }
    }

    private void remotePlayerClosed() {
        exitOK();
    }

    private void updateVideoSizeButton() {
        if (mHudBinding != null && mService != null) {
            boolean visible = mDisplayManager.isPrimary() || mService.isAceCastConnected();
            mHudBinding.playerOverlaySize.setVisibility(visible ? View.VISIBLE : View.GONE);
        }
    }

    private void updateStopButton() {
        if (mHudBinding != null && mService != null) {
            mHudBinding.playerOverlayStop.setVisibility(
                    mService.isRemoteDeviceConnected()
                            ? View.VISIBLE
                            : View.GONE);
        }
    }

    /**
     * Triggered when "stop" button is clicked.
     * Used for remote control.
     */
    public void stop() {
        stopPlayback(true, true);
        exitOK();
    }

    public static String getVideoSizeName(int id) {
        String name = null;
        switch(id) {
            case SURFACE_BEST_FIT:
                name = "best_fit";
                break;
            case SURFACE_FIT_SCREEN:
                name = "fit_screen";
                break;
            case SURFACE_FILL:
                name = "fill";
                break;
            case SURFACE_16_9:
                name = "16:9";
                break;
            case SURFACE_4_3:
                name = "4:3";
                break;
            case SURFACE_ORIGINAL:
                name = "original";
                break;
        }

        return name;
    }

    public static String getVideoSizeTitle(int id) {
        String title = null;
        switch(id) {
            case SURFACE_BEST_FIT:
                title = "Best fit";
                break;
            case SURFACE_FIT_SCREEN:
                title = "Fit screen";
                break;
            case SURFACE_FILL:
                title = "Fill";
                break;
            case SURFACE_16_9:
                title = "16:9";
                break;
            case SURFACE_4_3:
                title = "4:3";
                break;
            case SURFACE_ORIGINAL:
                title = "Original";
                break;
        }

        return title;
    }

    private void updateTitle() {
        final MediaWrapper mw = mService.getCurrentMediaWrapper();
        if(mw != null && !TextUtils.equals(mTitle.get(), mw.getTitle())) {
            mTitle.set(mw.getTitle());
        }
    }

    private boolean isCurrentMediaP2P() {
        return mService != null
                && mService.getCurrentMediaWrapper() != null
                && mService.getCurrentMediaWrapper().isP2PItem();
    }

    private void updateSwitchPlayerButton() {
        boolean visible = isCurrentMediaP2P();
        mSwitchPlayer.setVisibility(visible ? View.VISIBLE : View.GONE);
    }

    private boolean showDebugInfo() {
        return mAceStreamManager != null && mAceStreamManager.showDebugInfo();
    }
    ///ace
}
