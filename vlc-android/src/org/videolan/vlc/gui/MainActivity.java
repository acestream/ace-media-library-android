/*****************************************************************************
 * MainActivity.java
 *****************************************************************************
 * Copyright © 2011-2014 VLC authors and VideoLAN
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

package org.videolan.vlc.gui;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.internal.NavigationMenuView;
import com.google.android.material.navigation.NavigationView;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;
import androidx.collection.SimpleArrayMap;
import androidx.core.view.GravityCompat;
import androidx.core.view.MenuItemCompat;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.appcompat.view.ActionMode;
import android.text.TextUtils;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FilterQueryProvider;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.acestream.sdk.AceStream;
import org.acestream.sdk.AceStreamManager;
import org.acestream.sdk.controller.api.response.AuthData;
import org.acestream.sdk.utils.AuthUtils;
import org.acestream.sdk.utils.Logger;
import org.acestream.sdk.utils.PermissionUtils;
import org.acestream.sdk.utils.VlcBridge;
import org.videolan.libvlc.util.AndroidUtil;
import org.videolan.medialibrary.Medialibrary;
import org.videolan.vlc.BuildConfig;
import org.videolan.medialibrary.media.MediaWrapper;
import org.videolan.vlc.MediaParsingService;
import org.videolan.vlc.R;
import org.videolan.vlc.PlaybackService;
import org.videolan.vlc.StartActivity;
import org.videolan.vlc.VLCApplication;
import org.videolan.vlc.extensions.ExtensionListing;
import org.videolan.vlc.extensions.ExtensionManagerService;
import org.videolan.vlc.extensions.ExtensionsManager;
import org.videolan.vlc.extensions.api.VLCExtensionItem;
import org.videolan.vlc.gui.audio.AudioBrowserFragment;
import org.videolan.vlc.gui.browser.BaseBrowserFragment;
import org.videolan.vlc.gui.browser.ExtensionBrowser;
import org.videolan.vlc.gui.browser.FileBrowserFragment;
import org.videolan.vlc.gui.browser.MediaBrowserFragment;
import org.videolan.vlc.gui.browser.NetworkBrowserFragment;
import org.videolan.vlc.gui.helpers.MainActivityHelper;
import org.videolan.vlc.gui.helpers.UiTools;
import org.videolan.vlc.gui.network.MRLPanelFragment;
import org.videolan.vlc.gui.preferences.PreferencesActivity;
import org.videolan.vlc.gui.preferences.PreferencesFragment;
import org.videolan.vlc.gui.video.VideoGridFragment;
import org.videolan.vlc.gui.view.HackyDrawerLayout;
import org.videolan.vlc.interfaces.IRefreshable;
import org.videolan.vlc.media.MediaUtils;
import org.videolan.vlc.util.Constants;
import org.videolan.vlc.util.VLCInstance;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class MainActivity
        extends ContentActivity
        implements
            FilterQueryProvider,
            NavigationView.OnNavigationItemSelectedListener,
            ExtensionManagerService.ExtensionManagerActivity
{
    public final static String TAG = "AS/VLC/Main";

    private static final int ACTIVITY_RESULT_PREFERENCES = 1;
    private static final int ACTIVITY_RESULT_OPEN = 2;
    private static final int ACTIVITY_RESULT_SECONDARY = 3;

    public static final int REQUEST_CODE_REQUEST_PERMISSIONS = 1;

    //:ace
    private static final int UPGRADE_BUTTON_ROTATE_INTERVAL = 10000;
    ///ace

    private Medialibrary mMediaLibrary;
    private ExtensionsManager mExtensionsManager;
    private HackyDrawerLayout mDrawerLayout;
    private NavigationView mNavigationView;
    private ActionBarDrawerToggle mDrawerToggle;
    private Drawable mDrawerDrawable;

    private int mCurrentFragmentId;
    private Fragment mCurrentFragment = null;
    private final SimpleArrayMap<String, WeakReference<Fragment>> mFragmentsStack = new SimpleArrayMap<>();

    private boolean mScanNeeded = false;

    // Extensions management
    private ServiceConnection mExtensionServiceConnection;
    private ExtensionManagerService mExtensionManagerService;

    //:ace
    private ImageView mAccountBalanceButton = null;
    private TextView mAccountBalanceText = null;
    private ImageView mAccountProfileButton = null;
    private TextView mAccountProfileText = null;
    private ImageView mAccountUpgradeButton = null;
    private TextView mAccountUpgradeText = null;
    private Handler mHandler = new Handler();
    private MainActivityHelper mMainActivityHelper;
    private boolean mGotStorageAccess = false;

    private MainActivityHelper.Callback mMainActivityHelperCallback = new MainActivityHelper.Callback() {
        @Override
        public void showProgress(int category, String message) {
            AceStream.toast(message);
        }

        @Override
        public void hideProgress(int category) {
        }

        @Override
        public void onAuthUpdated(AuthData authData, String login) {
            updateNavigationHeader(authData, login);
        }

        @Override
        public void onBonusAdsAvailable(boolean available) {
            showBonusAdsButton(available);
        }
    };

    private Runnable mRotateUpgradeButton = new Runnable() {
        @Override
        public void run() {
            // Rotate: upgrade/disable_ads
            if(mAccountUpgradeText != null) {
                String name = (String) mAccountUpgradeText.getTag(R.id.tag_name);
                if (TextUtils.equals(name, "upgrade")) {
                    mAccountUpgradeText.setText(R.string.disable_ads);
                    mAccountUpgradeText.setTag(R.id.tag_name, "disable_ads");
                    mAccountUpgradeButton.setImageDrawable(
                            getResources().getDrawable(R.drawable.ic_no_ads));
                } else {
                    mAccountUpgradeText.setText(R.string.upgrade);
                    mAccountUpgradeText.setTag(R.id.tag_name, "upgrade");
                    mAccountUpgradeButton.setImageDrawable(
                            getResources().getDrawable(R.drawable.ic_upgrade));
                }
            }
            mHandler.removeCallbacks(mRotateUpgradeButton);
            mHandler.postDelayed(mRotateUpgradeButton, UPGRADE_BUTTON_ROTATE_INTERVAL);
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

        /*** Start initializing the UI ***/

        setContentView(R.layout.main);

        mDrawerLayout = (HackyDrawerLayout) findViewById(R.id.root_container);
        setupNavigationView();

        initAudioPlayerContainerActivity();

        if (savedInstanceState != null) {
            final FragmentManager fm = getSupportFragmentManager();
            mCurrentFragment = fm.getFragment(savedInstanceState, "current_fragment");
            //Restore fragments stack
            restoreFragmentsStack(savedInstanceState, fm);
            mCurrentFragmentId = savedInstanceState.getInt("current", mSettings.getInt("fragment_id", R.id.nav_video_local));
        } else {
            if (getIntent().getBooleanExtra(Constants.EXTRA_UPGRADE, false)) {
            /*
             * The sliding menu is automatically opened when the user closes
             * the info dialog. If (for any reason) the dialog is not shown,
             * open the menu after a short delay.
             */
            mActivityHandler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        mDrawerLayout.openDrawer(mNavigationView);
                    }
                }, 500);
            }
            reloadPreferences();
        }

        /* Set up the action bar */
        prepareActionBar();

        /* Set up the sidebar click listener
         * no need to invalidate menu for now */
        mDrawerToggle = new ActionBarDrawerToggle(this, mDrawerLayout, R.string.drawer_open, R.string.drawer_close) {
            @Override
            public void onDrawerClosed(View drawerView) {
                super.onDrawerClosed(drawerView);

                // If account submenu is visible when drawer is closed - collapse it.
                if(isAccountDropDownVisible()) {
                    switchAccountDropDown();
                }

                final Fragment current = getCurrentFragment();
                if (current instanceof MediaBrowserFragment)
                    ((MediaBrowserFragment) current).setReadyToDisplay(true);
            }

            // Hack to make navigation drawer browsable with DPAD.
            // see https://code.google.com/p/android/issues/detail?id=190975
            // and http://stackoverflow.com/a/34658002/3485324
            @Override
            public void onDrawerOpened(View drawerView) {
                super.onDrawerOpened(drawerView);
                if (mNavigationView.requestFocus())
                    ((NavigationMenuView) mNavigationView.getFocusedChild()).setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);
            }
        };

        // Set the drawer toggle as the DrawerListener
        mDrawerLayout.setDrawerListener(mDrawerToggle);
        // set a custom shadow that overlays the main content when the drawer opens
        mDrawerLayout.setDrawerShadow(R.drawable.drawer_shadow, GravityCompat.START);

        /* Reload the latest preferences */
        mScanNeeded = savedInstanceState == null && mSettings.getBoolean("auto_rescan", true);
        mExtensionsManager = ExtensionsManager.getInstance();
        mMediaLibrary = VLCApplication.getMLInstance();

        //:ace
        mMainActivityHelper = new MainActivityHelper(this, mMainActivityHelperCallback);
        ///ace
    }

    private void restoreFragmentsStack(Bundle savedInstanceState, FragmentManager fm) {
        final List<Fragment> fragments = fm.getFragments();
        if (fragments != null) {
            final FragmentTransaction ft =  fm.beginTransaction();
            final Fragment displayed = fm.getFragment(savedInstanceState, "current_fragment_visible");
            for (Fragment fragment : fragments)
                if (fragment != null) {
                    if (fragment instanceof ExtensionBrowser) {
                        ft.remove(fragment);
                    } else if ((fragment instanceof MediaBrowserFragment)) {
                        mFragmentsStack.put(fragment.getTag(), new WeakReference<>(fragment));
                        if (!TextUtils.equals(fragment.getTag(), displayed.getTag())) ft.hide(fragment);
                    }
                }
            ft.commit();
        }
    }

    private void setupNavigationView() {
        mNavigationView = (NavigationView) findViewById(R.id.navigation);
        mNavigationView.getMenu().findItem(R.id.nav_history).setVisible(mSettings.getBoolean(PreferencesFragment.PLAYBACK_HISTORY, true));

        //:ace
        final View headerLayout = mNavigationView.getHeaderView(0); // 0-index header

        final LinearLayout dropDownSwitch = headerLayout.findViewById(R.id.account_dropdown_switch);
        if(dropDownSwitch != null) {
            dropDownSwitch.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    switchAccountDropDown();
                }
            });
        }

        final LinearLayout signInButton = headerLayout.findViewById(R.id.account_sign_in);
        if(signInButton != null) {
            signInButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    if(checkAceStream()) {
                        AceStream.openProfileActivity(MainActivity.this);
                    }
                    closeDrawer();
                }
            });
        }

        mAccountBalanceButton = headerLayout.findViewById(R.id.nav_header_balance_button);
        mAccountBalanceText = headerLayout.findViewById(R.id.nav_header_balance_text);
        if(mAccountBalanceButton != null) {
            mAccountBalanceButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    if(checkAceStream()) {
                        if (mMainActivityHelper.isUserLoggedIn()) {
                            AceStream.openTopupActivity(MainActivity.this);
                        } else {
                            AceStream.openProfileActivity(MainActivity.this);
                        }
                    }
                    closeDrawer();
                }
            });
        }

        mAccountProfileButton = headerLayout.findViewById(R.id.nav_header_account_button);
        mAccountProfileText = headerLayout.findViewById(R.id.nav_header_account_text);
        if(mAccountProfileButton != null) {
            mAccountProfileButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    if(checkAceStream()) {
                        AceStream.openProfileActivity(MainActivity.this);
                    }
                    closeDrawer();
                }
            });
        }

        mAccountUpgradeButton = headerLayout.findViewById(R.id.nav_header_upgrade_button);
        mAccountUpgradeText = headerLayout.findViewById(R.id.nav_header_upgrade_text);
        if(mAccountUpgradeButton != null) {
            mAccountUpgradeButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    if(checkAceStream()) {
                        if (mMainActivityHelper.isUserLoggedIn()) {
                            AceStream.openUpgradeActivity(MainActivity.this);
                        } else {
                            AceStream.openProfileActivity(MainActivity.this);
                        }
                    }
                    closeDrawer();
                }
            });
        }

        View getBonusAdsButton = headerLayout.findViewById(R.id.nav_header_bonus_ads_button);
        if(getBonusAdsButton != null) {
            getBonusAdsButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if(checkAceStream()) {
                        mMainActivityHelper.showBonusAds();
                    }
                    closeDrawer();
                }
            });
        }
        ///ace
    }

    private boolean isAccountDropDownVisible() {
        final View headerLayout = mNavigationView.getHeaderView(0); // 0-index header
        final LinearLayout dropDownSwitch = headerLayout.findViewById(R.id.account_dropdown_switch);
        return dropDownSwitch.getTag() != null;
    }

    private void switchAccountDropDown() {
        final View headerLayout = mNavigationView.getHeaderView(0); // 0-index header
        final LinearLayout dropDownSwitch = headerLayout.findViewById(R.id.account_dropdown_switch);

        ImageView dropDownSwitchImage =
                headerLayout.findViewById(R.id.account_dropdown_image);

        int drawableId;
        if(dropDownSwitch.getTag() == null) {
            drawableId = R.drawable.ic_arrow_drop_up_black_24dp;
            dropDownSwitch.setTag(true);
            showAccountDrawerMenu();
        }
        else {
            drawableId = R.drawable.ic_arrow_drop_down_black_24dp;
            dropDownSwitch.setTag(null);
            showTopLevelDrawerMenu();
        }

        if(dropDownSwitchImage != null) {
            dropDownSwitchImage.setImageDrawable(getResources().getDrawable(drawableId));
        }
    }

    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);
        // Sync the toggle state after onRestoreInstanceState has occurred.
        mDrawerToggle.syncState();
    }

    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    private void prepareActionBar() {
        ActionBar actionBar = getSupportActionBar();
        actionBar.setDisplayHomeAsUpEnabled(true);
        actionBar.setHomeButtonEnabled(true);
    }

    @Override
    protected void onStart() {
        super.onStart();

        if(!PermissionUtils.hasStorageAccess()) {
            Log.v(TAG, "onStart: request storage access");
            PermissionUtils.requestStoragePermissions(this, REQUEST_CODE_REQUEST_PERMISSIONS);
            mCurrentFragmentId = R.id.nav_request_permissions;
        }
        else {
            mGotStorageAccess = true;
        }

        if (mCurrentFragment == null && !currentIdIsExtension())
            showFragment(mCurrentFragmentId);
        if (mMediaLibrary.isInitiated()) {
            /* Load media items from database and storage */
            if (mScanNeeded && PermissionUtils.hasStorageAccess())
                startService(new Intent(Constants.ACTION_RELOAD, null,this, MediaParsingService.class));
            else if (!currentIdIsExtension())
                restoreCurrentList();
        }
        mNavigationView.setNavigationItemSelectedListener(this);
        if (BuildConfig.DEBUG)
            createExtensionServiceConnection();
//        mActivityHandler.post(new Runnable() {
//            @Override
//            public void run() {
//                new RenderersDialog().show(getSupportFragmentManager(), "renderers");
//            }
//        });
    }

    @Override
    protected void onStop() {
        super.onStop();
        mNavigationView.setNavigationItemSelectedListener(null);
        if (getChangingConfigurations() == 0) {
            /* Check for an ongoing scan that needs to be resumed during onResume */
            mScanNeeded = mMediaLibrary.isWorking();
        }
        if (mExtensionServiceConnection != null) {
            unbindService(mExtensionServiceConnection);
            mExtensionServiceConnection = null;
        }
        if (currentIdIsExtension())
            mSettings.edit()
                    .putString("current_extension_name", mExtensionsManager.getExtensions(getApplication(), false).get(mCurrentFragmentId).componentName().getPackageName())
                    .apply();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        setIntent(intent);
    }

    @Override
    protected void onResume() {
        super.onResume();
        mMainActivityHelper.onResume();

        // Additional check on resume: access can be granted from system settings
        if(!mGotStorageAccess && PermissionUtils.hasStorageAccess()) {
            onStorageAccessGranted();
        }
        else if(mGotStorageAccess && !PermissionUtils.hasStorageAccess()) {
            onStorageAccessDenied();
        }

        Intent intent = getIntent();

        String fragmentId = (intent == null) ? null : intent.getStringExtra(VlcBridge.EXTRA_FRAGMENT_ID);
        if(fragmentId != null) {
            // consume once
            getIntent().removeExtra(VlcBridge.EXTRA_FRAGMENT_ID);
            switch(fragmentId) {
                case VlcBridge.FRAGMENT_VIDEO_LOCAL:
                    showFragment(R.id.nav_video_local);
                    break;
                case VlcBridge.FRAGMENT_VIDEO_TORRENTS:
                    showFragment(R.id.nav_video_torrents);
                    break;
                case VlcBridge.FRAGMENT_VIDEO_LIVE_STREAMS:
                    showFragment(R.id.nav_video_live_streams);
                    break;
                case VlcBridge.FRAGMENT_AUDIO_LOCAL:
                    showFragment(R.id.nav_audio_local);
                    break;
                case VlcBridge.FRAGMENT_AUDIO_TORRENTS:
                    showFragment(R.id.nav_audio_torrents);
                    break;
                case VlcBridge.FRAGMENT_BROWSING_DIRECTORIES:
                    showFragment(R.id.nav_directories);
                    break;
                case VlcBridge.FRAGMENT_BROWSING_LOCAL_NETWORKS:
                    showFragment(R.id.nav_network);
                    break;
                case VlcBridge.FRAGMENT_HISTORY:
                    showFragment(R.id.nav_history);
                    break;
                case VlcBridge.FRAGMENT_ABOUT:
                    showSecondaryFragment(SecondaryActivity.ABOUT);
                    break;
                case VlcBridge.FRAGMENT_BROWSING_STREAM:
                    new MRLPanelFragment().show(getSupportFragmentManager(), "fragment_mrl");
                    break;
                case VlcBridge.FRAGMENT_SETTINGS_ADS:
                    openSettings("ads");
                    break;
                case VlcBridge.FRAGMENT_SETTINGS_ENGINE:
                    openSettings("engine");
                    break;
                case VlcBridge.FRAGMENT_SETTINGS_PLAYER:
                    openSettings("player");
                    break;
            }
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        mMainActivityHelper.onPause();
        mHandler.removeCallbacks(mRotateUpgradeButton);
    }

    @Override
    public void onConnected(PlaybackService service) {
        Log.v(TAG, "connected playback service");
        super.onConnected(service);
        mMainActivityHelper.onStart();
    }

    @Override
    public void onDisconnected() {
        Log.v(TAG, "disconnected playback service");
        super.onDisconnected();
        mMainActivityHelper.onStop();
    }

    private void loadPlugins() {
        List<ExtensionListing> plugins = mExtensionsManager.getExtensions(this, true);
        if (plugins.isEmpty()) {
            unbindService(mExtensionServiceConnection);
            mExtensionServiceConnection = null;
            mExtensionManagerService.stopSelf();
            return;
        }
        MenuItem extensionGroup = mNavigationView.getMenu().findItem(R.id.extensions_group);
        extensionGroup.getSubMenu().clear();
        for (int id = 0; id < plugins.size(); ++id) {
            final ExtensionListing extension = plugins.get(id);
            String key = "extension_" + extension.componentName().getPackageName();
            if (mSettings.contains(key)) {
                mExtensionsManager.displayPlugin(this, id, extension, mSettings.getBoolean(key, false));
            } else {
                mExtensionsManager.showExtensionPermissionDialog(this, id, extension, key);
            }
        }
        if (extensionGroup.getSubMenu().size() == 0)
            extensionGroup.setVisible(false);
        onPluginsLoaded();
        mNavigationView.invalidate();
    }

    private void onPluginsLoaded() {
        if (mCurrentFragment == null && currentIdIsExtension())
            if (mExtensionsManager.previousExtensionIsEnabled(getApplication()))
                mExtensionManagerService.openExtension(mCurrentFragmentId);
            else
                showFragment(R.id.nav_video_local);
    }

    private void createExtensionServiceConnection() {
        mExtensionServiceConnection = new ServiceConnection() {
            @Override
            public void onServiceConnected(ComponentName name, IBinder service) {
                mExtensionManagerService = ((ExtensionManagerService.LocalBinder)service).getService();
                mExtensionManagerService.setExtensionManagerActivity(MainActivity.this);
                loadPlugins();
            }
            @Override
            public void onServiceDisconnected(ComponentName name) {}
        };
        // Bind service which discoverves au connects toplugins
        if (!bindService(new Intent(MainActivity.this,
                ExtensionManagerService.class), mExtensionServiceConnection, Context.BIND_AUTO_CREATE))
            mExtensionServiceConnection = null;
    }

    protected void onSaveInstanceState(Bundle outState) {
        if (mCurrentFragment instanceof ExtensionBrowser)
            mCurrentFragment = null;
        else {
            getSupportFragmentManager().putFragment(outState, "current_fragment", mCurrentFragment);
            getSupportFragmentManager().putFragment(outState, "current_fragment_visible", getCurrentFragment());
        }
        super.onSaveInstanceState(outState);
        outState.putInt("current", mCurrentFragmentId);
    }

    @Override
    protected void onRestart() {
        super.onRestart();
        /* Reload the latest preferences */
        reloadPreferences();
    }

    @SuppressLint("NewApi")
    @Override
    public void onBackPressed() {
        /* Close the menu first */
        if (mDrawerLayout.isDrawerOpen(mNavigationView)) {
            closeDrawer();
            return;
        }

        /* Close playlist search if open or Slide down the audio player if it is shown entirely. */
        if (isAudioPlayerReady() && (mAudioPlayer.clearSearch() || slideDownAudioPlayer()))
            return;

        // If it's the directory view, a "backpressed" action shows a parent.
        final Fragment fragment = getCurrentFragment();
        if (fragment instanceof BaseBrowserFragment && ((BaseBrowserFragment)fragment).goBack()){
            return;
        } else if (fragment instanceof ExtensionBrowser) {
            ((ExtensionBrowser) fragment).goBack();
            return;
        }
        if (AndroidUtil.isNougatOrLater && isInMultiWindowMode()) {
            UiTools.confirmExit(this);
            return;
        }
        finish();
    }

    @NonNull
    private Fragment getNewFragment(int id) {
        switch (id) {
            case R.id.nav_audio_local:
            case R.id.nav_audio_torrents:
                return new AudioBrowserFragment();
            case R.id.nav_directories:
                return new FileBrowserFragment();
            case R.id.nav_history:
                return new HistoryFragment();
            case R.id.nav_network:
                return new NetworkBrowserFragment();
            case R.id.nav_request_permissions:
                return new RequestPermissionsFragment();
            default:
                return new VideoGridFragment();
        }
    }

    @Override
    public void displayExtensionItems(int extensionId, String title, List<VLCExtensionItem> items, boolean showParams, boolean refresh) {
        if (refresh && getCurrentFragment() instanceof ExtensionBrowser) {
            ExtensionBrowser browser = (ExtensionBrowser) getCurrentFragment();
            browser.doRefresh(title, items);
        } else {
            ExtensionBrowser fragment = new ExtensionBrowser();
            ArrayList<VLCExtensionItem> list = new ArrayList<>(items);
            Bundle args = new Bundle();
            args.putParcelableArrayList(ExtensionBrowser.KEY_ITEMS_LIST, list);
            args.putBoolean(ExtensionBrowser.KEY_SHOW_FAB, showParams);
            args.putString(ExtensionBrowser.KEY_TITLE, title);
            fragment.setArguments(args);
            fragment.setExtensionService(mExtensionManagerService);

            FragmentTransaction ft = getSupportFragmentManager().beginTransaction();
            if (!(mCurrentFragment instanceof ExtensionBrowser)) {
                //case: non-extension to extension root
                if (mCurrentFragment != null)
                    ft.hide(mCurrentFragment);
                ft.add(R.id.fragment_placeholder, fragment, title);
                mCurrentFragment = fragment;
            } else if (mCurrentFragmentId == extensionId) {
                //case: extension root to extension sub dir
                ft.hide(mCurrentFragment);
                ft.add(R.id.fragment_placeholder, fragment, title);
                ft.addToBackStack(getTag(mCurrentFragmentId));
            } else {
                //case: extension to other extension root
                clearBackstackFromClass(ExtensionBrowser.class);
                while (getSupportFragmentManager().popBackStackImmediate());
                ft.remove(mCurrentFragment);
                ft.add(R.id.fragment_placeholder, fragment, title);
                mCurrentFragment = fragment;
            }
            ft.commit();
            mNavigationView.getMenu().findItem(extensionId).setCheckable(true);
            updateCheckedItem(extensionId);
            mCurrentFragmentId = extensionId;
        }
    }

    /**
     * Show a secondary fragment.
     */
    public void showSecondaryFragment(String fragmentTag) {
        showSecondaryFragment(fragmentTag, null, -1, 0);
    }

    public void showSecondaryFragment(String fragmentTag, String param1, int param2, long param3) {
        Intent i = new Intent(this, SecondaryActivity.class);
        i.putExtra("fragment", fragmentTag);
        if (param1 != null)
            i.putExtra("param1", param1);
        if (param2 != -1)
            i.putExtra("param2", param2);
        if (param3 != -1)
            i.putExtra("param3", param3);
        startActivityForResult(i, ACTIVITY_RESULT_SECONDARY);
        // Slide down the audio player if needed.
        slideDownAudioPlayer();
    }

    @Nullable
    @Override
    public ActionMode startSupportActionMode(@NonNull ActionMode.Callback callback) {
        mAppBarLayout.setExpanded(true);
        return super.startSupportActionMode(callback);
    }

    /**
     * Handle onClick form menu buttons
     */
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        closeDrawer();
        UiTools.setKeyboardVisibility(mDrawerLayout, false);

        // Handle item selection
        switch (item.getItemId()) {
            // Refresh
            case R.id.ml_menu_refresh:
                forceRefresh();
                return true;
            case android.R.id.home:
                // Slide down the audio player.
                if (slideDownAudioPlayer())
                    return true;
                /* Toggle the sidebar */
                return mDrawerToggle.onOptionsItemSelected(item);
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    public void forceRefresh() {
        forceRefresh(getCurrentFragment());
    }

    private void forceRefresh(Fragment current) {
        if (!mMediaLibrary.isWorking()) {
            if(current != null && current instanceof IRefreshable)
                ((IRefreshable) current).refresh();
            else
                startService(new Intent(Constants.ACTION_RELOAD, null,this, MediaParsingService.class));
        }
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
                    Intent intent = new Intent(MainActivity.this, resultCode == PreferencesActivity.RESULT_RESTART_APP ? StartActivity.class : MainActivity.class);
                    finish();
                    startActivity(intent);
                    break;
                case PreferencesActivity.RESULT_UPDATE_SEEN_MEDIA:
                    for (Fragment fragment : getSupportFragmentManager().getFragments())
                        if (fragment instanceof VideoGridFragment)
                            ((VideoGridFragment) fragment).updateSeenMediaMarker();
                    break;
                case PreferencesActivity.RESULT_UPDATE_ARTISTS:
                    final Fragment fragment = getCurrentFragment();
                    if (fragment instanceof AudioBrowserFragment) ((AudioBrowserFragment) fragment).updateArtists();
                    break;
                case PreferencesActivity.RESULT_CLEAR_CACHE:
                    mMainActivityHelper.clearEngineCache();
                    break;
                case PreferencesActivity.RESULT_SHUTDOWN_ENGINE:
                    mMainActivityHelper.shutdown();
                    break;
            }
        } else if (requestCode == ACTIVITY_RESULT_OPEN && resultCode == RESULT_OK){
            MediaUtils.openUri(this, data.getData());
        } else if (requestCode == ACTIVITY_RESULT_SECONDARY) {
            if (resultCode == PreferencesActivity.RESULT_RESCAN) {
                forceRefresh(getCurrentFragment());
            }
        }
    }

    // Note. onKeyDown will not occur while moving within a list
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        //Filter for LG devices, see https://code.google.com/p/android/issues/detail?id=78154
        if ((keyCode == KeyEvent.KEYCODE_MENU) &&
                (Build.VERSION.SDK_INT <= 16) &&
                (Build.MANUFACTURER.compareTo("LGE") == 0)) {
            return true;
        } else if (keyCode == KeyEvent.KEYCODE_SEARCH) {
            MenuItemCompat.expandActionView(mMenu.findItem(R.id.ml_menu_filter));
        }
        return super.onKeyDown(keyCode, event);
    }

    // Note. onKeyDown will not occur while moving within a list
    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        //Filter for LG devices, see https://code.google.com/p/android/issues/detail?id=78154
        if ((keyCode == KeyEvent.KEYCODE_MENU) &&
                (Build.VERSION.SDK_INT <= 16) &&
                (Build.MANUFACTURER.compareTo("LGE") == 0)) {
            openOptionsMenu();
            return true;
        }
        return super.onKeyUp(keyCode, event);
    }

    private void reloadPreferences() {
        mCurrentFragmentId = mSettings.getInt("fragment_id", R.id.nav_video_local);
    }

    @Override
    public Cursor runQuery(final CharSequence constraint) {
        return null;
    }

    //Filtering
    @Override
    public boolean onQueryTextSubmit(String query) {
        return false;
    }

    private void showTopLevelDrawerMenu() {
        for (int i = 0; i < mNavigationView.getMenu().size(); i++) {
            MenuItem mi = mNavigationView.getMenu().getItem(i);
            mi.setVisible(mi.getGroupId() == R.id.scrollable_group
                    || mi.getGroupId() == R.id.fixed_group
                    || mi.getGroupId() == R.id.remote_control_group
                    || mi.getItemId() == R.id.extensions_group);
        }
    }

    private void showAccountDrawerMenu() {
        int groupId = R.id.submenu_account_signed_in;
        for (int i = 0; i < mNavigationView.getMenu().size(); i++) {
            MenuItem mi = mNavigationView.getMenu().getItem(i);
            mi.setVisible(mi.getGroupId() == groupId);
        }
    }

    private void openSettings(String category) {
        Intent intent = new Intent(this, PreferencesActivity.class);
        intent.putExtra("category", category);
        startActivityForResult(intent, ACTIVITY_RESULT_PREFERENCES);
    }

    @Override
    public boolean onNavigationItemSelected(MenuItem item) {
        //NOTE: return false for items that should not remain selected after click
        boolean makeItemChecked = false;
        // This should not happen
        if(item == null)
            return false;

        int id = item.getItemId();

        // For certain menu items check that AceStream is installed
        switch(id) {
            case R.id.nav_audio_torrents:
            case R.id.nav_video_torrents:
            case R.id.nav_video_live_streams:
            case R.id.nav_remote_control:
            case R.id.nav_report_problem:
                if(!checkAceStream()) {
                    return false;
                }
                break;
        }

        // Submenu triggers
        switch(id) {
            case R.id.nav_submenu_video:
                for (int i = 0; i < mNavigationView.getMenu().size(); i++) {
                    MenuItem mi = mNavigationView.getMenu().getItem(i);
                    mi.setVisible(mi.getGroupId() == R.id.submenu_video || mi.getItemId() == R.id.nav_return);

                    if (mi.getItemId() == R.id.nav_return) {
                        mi.setTitle(getString(R.string.video));
                    }
                }
                return false;
            case R.id.nav_submenu_audio:
                for (int i = 0; i < mNavigationView.getMenu().size(); i++) {
                    MenuItem mi = mNavigationView.getMenu().getItem(i);
                    mi.setVisible(mi.getGroupId() == R.id.submenu_audio || mi.getItemId() == R.id.nav_return);

                    if (mi.getItemId() == R.id.nav_return) {
                        mi.setTitle(getString(R.string.audio));
                    }
                }
                return false;
            case R.id.nav_submenu_browsing:
                for (int i = 0; i < mNavigationView.getMenu().size(); i++) {
                    MenuItem mi = mNavigationView.getMenu().getItem(i);
                    mi.setVisible(mi.getGroupId() == R.id.submenu_browsing || mi.getItemId() == R.id.nav_return);

                    if (mi.getItemId() == R.id.nav_return) {
                        mi.setTitle(getString(R.string.browsing));
                    }
                }
                return false;
            case R.id.nav_submenu_settings:
                for (int i = 0; i < mNavigationView.getMenu().size(); i++) {
                    MenuItem mi = mNavigationView.getMenu().getItem(i);
                    mi.setVisible(mi.getGroupId() == R.id.submenu_settings || mi.getItemId() == R.id.nav_return);

                    if (mi.getItemId() == R.id.nav_return) {
                        mi.setTitle(getString(R.string.preferences));
                    }
                }
                return false;
            case R.id.nav_return:
                showTopLevelDrawerMenu();
                return false;
            case R.id.nav_sign_out:
                signOut();
                switchAccountDropDown();
                closeDrawer();
                return false;
        }

        final Fragment current = getCurrentFragment();
        if (item.getGroupId() == R.id.extensions_group)  {
            if(mCurrentFragmentId == id) {
                clearBackstackFromClass(ExtensionBrowser.class);
                closeDrawer();
                return false;
            }
            else
                mExtensionManagerService.openExtension(id);
        } else {
            if (mExtensionServiceConnection != null)
                mExtensionManagerService.disconnect();

            if (current == null) {
                closeDrawer();
                return false;
            }

            if (mCurrentFragmentId == id) { /* Already selected */
                // Go back at root level of current browser
                if (current instanceof BaseBrowserFragment && !((BaseBrowserFragment) current).isRootDirectory()) {
                    getSupportFragmentManager().popBackStackImmediate(getTag(id), FragmentManager.POP_BACK_STACK_INCLUSIVE);
                } else {
                    closeDrawer();
                    return false;
                }
            } else switch (id) {
                case R.id.nav_about:
                    showSecondaryFragment(SecondaryActivity.ABOUT);
                    break;
                case R.id.nav_settings_ads:
                    openSettings("ads");
                    showTopLevelDrawerMenu();
                    break;
                case R.id.nav_settings_player:
                    openSettings("player");
                    showTopLevelDrawerMenu();
                    break;
                case R.id.nav_settings_engine:
                    openSettings("engine");
                    showTopLevelDrawerMenu();
                    break;
                case R.id.nav_mrl:
                    new MRLPanelFragment().show(getSupportFragmentManager(), "fragment_mrl");
                    break;
                case R.id.nav_shutdown_engine:
                    mMainActivityHelper.shutdown();
                    break;
                case R.id.nav_clear_cache:
                    mMainActivityHelper.clearEngineCache();
                    showTopLevelDrawerMenu();
                    break;
                case R.id.nav_restart:
                    mMainActivityHelper.restartApp();
                    showTopLevelDrawerMenu();
                    break;
                case R.id.nav_report_problem:
                    AceStream.openReportProblemActivity(this);
                    showTopLevelDrawerMenu();
                    break;
                case R.id.nav_remote_control:
                    MediaWrapper mw = getLastP2PMedia();
                    Uri uri = mw != null ? mw.getUri() : null;
                    AceStream.openRemoteControlActivity(this, uri);
                    break;
                case R.id.nav_directories:
                // WARN: all new items must be added above nav_directories because it doesn't break
                default:
                    makeItemChecked = true;
                /* Slide down the audio player */
                    slideDownAudioPlayer();
                /* Switch the fragment */
                    showFragment(id);
            }
        }
        closeDrawer();
        return makeItemChecked;
    }

    private MediaWrapper getLastP2PMedia() {
        if(mService == null) return null;

        MediaWrapper media = mService.getCurrentMediaWrapper();
        if(media != null) {
            return media.isP2PItem() ? media : null;
        }

        // Get first p2p item from last playlist
        List<MediaWrapper> lastPlaylist = mService.getLastPlaylist(Constants.PLAYLIST_TYPE_VIDEO);
        if (lastPlaylist == null) {
            Log.v(TAG, "getLastP2PMedia: no last playlist");
            return null;
        }
        if (lastPlaylist.size() == 0) {
            Log.v(TAG, "getLastP2PMedia: empty last playlist");
            return null;
        }

        // Get first p2p item
        for (MediaWrapper mw : lastPlaylist) {
            if (mw.isP2PItem()) {
                media = mw;
                break;
            }
        }
        if (media == null) {
            Log.v(TAG, "getLastP2PMedia: no p2p items in last playlist");
        }

        return media;
    }

    private void closeDrawer() {
        if(mDrawerLayout != null && mDrawerLayout.isDrawerOpen(mNavigationView)) {
            mDrawerLayout.closeDrawer(mNavigationView);
        }
    }

    public void updateCheckedItem(int id) {
        switch (id) {
            case R.id.nav_mrl:
            case R.id.nav_settings_ads:
            case R.id.nav_settings_player:
            case R.id.nav_settings_engine:
            case R.id.nav_about:
            case R.id.nav_sign_out:
                return;
            default:
                if (mNavigationView.getMenu().findItem(id) != null) {
                    mNavigationView.getMenu().findItem(id).setChecked(true);

                    if (id != mCurrentFragmentId) {
                        if (mNavigationView.getMenu().findItem(mCurrentFragmentId) != null)
                            mNavigationView.getMenu().findItem(mCurrentFragmentId).setChecked(false);

                        /* Save the tab status in pref */
                        mSettings.edit().putInt("fragment_id", id).apply();
                    }
                }
        }
    }

    public void showFragment(int id) {
        final FragmentManager fm = getSupportFragmentManager();
        final String tag = getTag(id);
        //Get new fragment
        Fragment fragment = null;
        final WeakReference<Fragment> wr = mFragmentsStack.get(tag);
        final boolean add = wr == null || (fragment = wr.get()) == null;
        if (add) {
            fragment = getNewFragment(id);
            mFragmentsStack.put(tag, new WeakReference<>(fragment));
        }

        if(fragment instanceof VideoGridFragment) {
            Bundle bundle = new Bundle(1);
            int category;
            if(id == R.id.nav_video_torrents) {
                category = MediaWrapper.CATEGORY_P2P_VIDEO;
            }
            else if(id == R.id.nav_video_live_streams) {
                category = MediaWrapper.CATEGORY_P2P_STREAM;
            }
            else {
                category = MediaWrapper.CATEGORY_REGULAR_VIDEO;
            }
            bundle.putInt("category", category);
            fragment.setArguments(bundle);
        }
        else if(fragment instanceof AudioBrowserFragment) {
            Bundle bundle = new Bundle(1);
            int category;
            if(id == R.id.nav_audio_torrents) {
                category = MediaWrapper.CATEGORY_P2P_AUDIO;
            }
            else {
                category = MediaWrapper.CATEGORY_REGULAR_AUDIO;
            }
            bundle.putInt("category", category);
            fragment.setArguments(bundle);
        }

        if (mCurrentFragment != null)
            if (mCurrentFragment instanceof ExtensionBrowser)
                fm.beginTransaction().remove(mCurrentFragment).commit();
            else {
                if (mCurrentFragment instanceof BaseBrowserFragment
                        && !((BaseBrowserFragment) getCurrentFragment()).isRootDirectory())
                    fm.popBackStackImmediate("root", FragmentManager.POP_BACK_STACK_INCLUSIVE);
                fm.beginTransaction().hide(mCurrentFragment).commit();
            }
        final FragmentTransaction ft = fm.beginTransaction();
        if (add)
            ft.add(R.id.fragment_placeholder, fragment, tag);
        else
            ft.show(fragment);
        ft.commit();
        updateCheckedItem(id);
        mCurrentFragment = fragment;
        mCurrentFragmentId = id;
    }

    private void clearBackstackFromClass(Class clazz) {
        final FragmentManager fm = getSupportFragmentManager();
        while (clazz.isInstance(getCurrentFragment())) {
            if (!fm.popBackStackImmediate())
                break;
        }
    }

    private String getTag(int id){
        switch (id){
            case R.id.nav_about:
                return ID_ABOUT;
            case R.id.nav_settings_ads:
            case R.id.nav_settings_player:
            case R.id.nav_settings_engine:
                return ID_PREFERENCES;
            case R.id.nav_audio_local:
                return ID_AUDIO;
            case R.id.nav_directories:
                return ID_DIRECTORIES;
            case R.id.nav_history:
                return ID_HISTORY;
            case R.id.nav_mrl:
                return ID_MRL;
            case R.id.nav_network:
                return ID_NETWORK;
            case R.id.nav_request_permissions:
                return ID_REQUEST_PERMISSIONS;
            case R.id.nav_video_torrents:
                return ID_P2P_VIDEO;
            case R.id.nav_audio_torrents:
                return ID_P2P_AUDIO;
            case R.id.nav_video_live_streams:
                return ID_P2P_STREAMS;
            default:
                return ID_VIDEO;
        }
    }

    protected Fragment getCurrentFragment() {
        return mCurrentFragment instanceof BaseBrowserFragment || currentIdIsExtension()
                ? getFirstVisibleFragment() : mCurrentFragment;
    }

    private Fragment getFirstVisibleFragment() {
        final Fragment frag = getSupportFragmentManager().findFragmentById(R.id.fragment_placeholder);
        if (frag != null && !frag.isHidden())
            return frag;
        final List<Fragment> fragments = getSupportFragmentManager().getFragments();
        if (fragments != null)
            for (Fragment fragment : fragments)
                if (fragment != null && !fragment.isHidden() && fragment.getClass().isInstance(mCurrentFragment))
                    return fragment;
        return mCurrentFragment;
    }

    public boolean currentIdIsExtension() {
        return idIsExtension(mCurrentFragmentId);
    }

    public boolean idIsExtension(int id) {
        return id <= 100;
    }

    public int getCurrentFragmentId() {
        return mCurrentFragmentId;
    }

    public void setCurrentFragmentId(int id) {
        mCurrentFragmentId = id;
    }

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

        AceStream.onStorageAccessGranted();
        mGotStorageAccess = true;
        mHelper.onStart();

        showUi();
        showFragment(R.id.nav_video_local);

        // Show drawer
        mDrawerLayout.openDrawer(mNavigationView);
    }

    private void onStorageAccessDenied() {
        Log.v(TAG, "onStorageAccessDenied");
        mGotStorageAccess = false;
        hideUi();
        showFragment(R.id.nav_request_permissions);
    }

    private void showUi() {
        // Restore drawer icon
        if(mDrawerDrawable != null) {
            mToolbar.setNavigationIcon(mDrawerDrawable);
        }
    }

    private void hideUi() {
        // Hide drawer icon. Save it to restore later.
        if(mToolbar.getNavigationIcon() != null) {
            mDrawerDrawable = mToolbar.getNavigationIcon();
        }
        mToolbar.setNavigationIcon(null);

        MenuItem mi;
        Menu menu = mToolbar.getMenu();

        mi = menu.findItem(R.id.ml_menu_renderers);
        if(mi != null) mi.setVisible(false);

        closeDrawer();
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

            } else {
                Log.d(TAG, "user denied permission");
            }
        }
    }

    //:ace
    @Override
    protected void onParsingServiceFinished() {
        super.onParsingServiceFinished();
    }

    private void updateNavigationHeader(final AuthData authData, String text) {
        final boolean userSignedIn;

        if(TextUtils.isEmpty(text)) {
            text = "";
            userSignedIn = false;
        }
        else {
            userSignedIn = true;
        }

        final String fText = text;
        VLCApplication.runOnMainThread(new Runnable() {
            @Override
            public void run() {
                int balance = -1;
                int color = R.color.orange100;
                int icon = R.drawable.ic_account_circle_24dp_bluegrey100;
                String accountText = getResources().getString(R.string.user_profile);
                if(authData != null && authData.auth_level > 0 && authData.package_color != null) {
                    accountText = authData.package_name;
                    balance = authData.purse_amount;
                    switch (authData.package_color) {
                        case "red":
                            icon = R.drawable.ic_account_circle_24dp_red;
                            color = R.color.ace_red;
                            break;
                        case "yellow":
                            icon = R.drawable.ic_account_circle_24dp_yellow;
                            color = R.color.ace_yellow;
                            break;
                        case "green":
                            icon = R.drawable.ic_account_circle_24dp_green;
                            color = R.color.ace_green;
                            break;
                        case "blue":
                            icon = R.drawable.ic_account_circle_24dp_blue;
                            color = R.color.ace_blue;
                            break;
                    }
                }

                if(mAccountProfileButton != null) {
                    mAccountProfileButton.setImageDrawable(getResources().getDrawable(icon));
                }

                if(mAccountProfileText != null) {
                    mAccountProfileText.setText(accountText);
                    mAccountProfileText.setTextColor(getResources().getColor(color));
                }

                if(mAccountBalanceText != null) {
                    String balanceText;
                    if(balance == -1) {
                        balanceText = getResources().getString(R.string.account_balance);
                    }
                    else {
                        balanceText = getResources().getString(
                                R.string.topup_button_title_short, balance / 100.0);
                    }
                    mAccountBalanceText.setText(balanceText);
                }

                if(mAccountUpgradeButton != null && mAccountUpgradeText != null) {
                    if(authData != null && AuthUtils.hasNoAds(authData.auth_level)) {
                        mAccountUpgradeText.setText(R.string.upgrade);
                        mAccountUpgradeText.setTag(R.id.tag_name, "upgrade");
                        mAccountUpgradeButton.setImageDrawable(
                                getResources().getDrawable(R.drawable.ic_upgrade));
                        mHandler.removeCallbacks(mRotateUpgradeButton);
                    }
                    else {
                        // Initial value 50/50
                        if (new Random().nextInt(100) > 50) {
                            mAccountUpgradeText.setText(R.string.disable_ads);
                            mAccountUpgradeText.setTag(R.id.tag_name, "disable_ads");
                            mAccountUpgradeButton.setImageDrawable(
                                    getResources().getDrawable(R.drawable.ic_no_ads));
                        } else {
                            mAccountUpgradeText.setText(R.string.upgrade);
                            mAccountUpgradeText.setTag(R.id.tag_name, "upgrade");
                            mAccountUpgradeButton.setImageDrawable(
                                    getResources().getDrawable(R.drawable.ic_upgrade));
                        }
                        mHandler.removeCallbacks(mRotateUpgradeButton);
                        mHandler.postDelayed(mRotateUpgradeButton, UPGRADE_BUTTON_ROTATE_INTERVAL);
                    }
                }

                final View headerLayout = mNavigationView.getHeaderView(0); // 0-index header
                if(headerLayout != null) {
                    if (userSignedIn) {
                        headerLayout.findViewById(R.id.account_dropdown_switch).setVisibility(View.VISIBLE);
                        headerLayout.findViewById(R.id.account_sign_in).setVisibility(View.GONE);
                    } else {
                        headerLayout.findViewById(R.id.account_dropdown_switch).setVisibility(View.GONE);
                        headerLayout.findViewById(R.id.account_sign_in).setVisibility(View.VISIBLE);
                    }

                    TextView v = headerLayout.findViewById(R.id.account_dropdown_text);
                    if (v != null) {
                        v.setText(fText);
                    }
                }
            }
        });
    }

    private AceStreamManager getPlaybackManager() {
        if(mService != null) {
            return mService.getAceStreamManager();
        }
        else {
            return null;
        }
    }

    public void signOut() {
        AceStreamManager pm = getPlaybackManager();
        if(pm == null) {
            Log.w(TAG, "signOut: missing pm");
            return;
        }
        pm.signOut();
    }

    private boolean checkAceStream() {
        if(!AceStream.isInstalled()) {
            //TODO: show rich alert dialog with ability to install AceStream
            AceStream.toast("Ace Stream Engine is not installed");
            return false;
        }

        return true;
    }

    public boolean areBonusAdsAvailable() {
        return mMainActivityHelper.areBonusAdsAvailable();
    }

    private void showBonusAdsButton(boolean visible) {
        for (Fragment fragment : getSupportFragmentManager().getFragments()) {
            if (fragment instanceof MediaBrowserFragment) {
                ((MediaBrowserFragment) fragment).showBonusAdsButton(visible);
            }
        }
    }
    ///ace
}
