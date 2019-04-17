/*
 * *************************************************************************
 *  MediaBrowserFragment.java
 * **************************************************************************
 *  Copyright © 2015-2016 VLC authors and VideoLAN
 *
 *  This program is free software; you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation; either version 2 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program; if not, write to the Free Software
 *  Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston MA 02110-1301, USA.
 *  ***************************************************************************
 */

package org.videolan.vlc.gui.browser;

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import androidx.annotation.Nullable;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.view.ActionMode;

import android.text.TextUtils;
import android.util.Log;
import android.view.ContextMenu;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;

import org.acestream.sdk.AceStream;
import org.acestream.sdk.AceStreamManager;
import org.videolan.libvlc.util.AndroidUtil;
import org.videolan.medialibrary.Medialibrary;
import org.videolan.medialibrary.media.MediaLibraryItem;
import org.videolan.medialibrary.media.MediaWrapper;
import org.videolan.vlc.R;
import org.videolan.vlc.VLCApplication;
import org.videolan.vlc.gui.InfoActivity;
import org.videolan.vlc.gui.MainActivity;
import org.videolan.vlc.gui.PlaybackServiceFragment;
import org.videolan.vlc.gui.helpers.UiTools;
import org.videolan.vlc.gui.helpers.hf.WriteExternalDelegate;
import org.videolan.vlc.gui.view.ContextMenuRecyclerView;
import org.videolan.vlc.gui.view.SwipeRefreshLayout;
import org.videolan.vlc.util.AndroidDevices;
import org.videolan.vlc.util.Constants;
import org.videolan.vlc.util.FileUtils;
import org.videolan.vlc.util.Permissions;

import java.util.LinkedList;

public abstract class MediaBrowserFragment extends PlaybackServiceFragment implements androidx.appcompat.view.ActionMode.Callback {

    public final static String TAG = "VLC/MediaBrowserFragment";

    protected SwipeRefreshLayout mSwipeRefreshLayout;
    protected volatile boolean mReadyToDisplay = true;
    protected Medialibrary mMediaLibrary;
    protected ActionMode mActionMode;
    public FloatingActionButton mFabPlay;
    protected int mFabPlayImageResourceId = R.drawable.ic_fab_play;
    protected Menu mMenu;
    protected boolean mCanShowBonusAds = false;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mMediaLibrary = VLCApplication.getMLInstance();
        setHasOptionsMenu(true);
    }

    @Override
    public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        if (mSwipeRefreshLayout != null)
            mSwipeRefreshLayout.setColorSchemeResources(R.color.orange700);
            mFabPlay = getActivity().findViewById(R.id.fab);
    }


    public void onStart() {
        super.onStart();
        if (!isHidden())
            onHiddenChanged(false);
    }

    @Override
    public void onHiddenChanged(boolean hidden) {
        if (!hidden) {
            updateTitle();
            if (mFabPlay != null) {
                setFabPlayVisibility(false);
                mFabPlay.setImageResource(mFabPlayImageResourceId);
                setFabPlayVisibility(true);
                mFabPlay.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        onFabPlayClick(v);
                    }
                });

                if(mCanShowBonusAds) {
                    final Activity activity = getActivity();
                    if (activity instanceof MainActivity) {
                        if (((MainActivity) activity).areBonusAdsAvailable()) {
                            showBonusAdsButton(true);
                        }
                    }
                }
            }
        }
        else {
            LocalBroadcastManager.getInstance(getActivity()).unregisterReceiver(mParsingServiceReceiver);
        }
        setUserVisibleHint(!hidden);
    }

    @Override
    public void onStop() {
        super.onStop();
        if (!isHidden())
            onHiddenChanged(true);
    }

    public void updateTitle() {
        final AppCompatActivity activity = (AppCompatActivity)getActivity();
        if (activity != null && activity.getSupportActionBar() != null) {
            activity.getSupportActionBar().setTitle(getTitle());
            activity.getSupportActionBar().setSubtitle(getSubTitle());
            activity.supportInvalidateOptionsMenu();
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        stopActionMode();
    }

    public void setFabPlayVisibility(boolean enable) {
        if (mFabPlay != null)
            if(enable)
                mFabPlay.show();
            else
                mFabPlay.hide();
    }

    /**
     * @param view
     * @return true when click was handled
     */
    public boolean onFabPlayClick(View view) {
        Object tag = mFabPlay.getTag();
        if(mCanShowBonusAds && tag instanceof String && TextUtils.equals((String)tag, "bonus_ads")) {
            final AceStreamManager am = mService.getAceStreamManager();
            if(am != null) {
                am.showBonusAds(getActivity());
            }
            return true;
        }
        return false;
    }

    public void setReadyToDisplay(boolean ready) {
        if (ready && !mReadyToDisplay)
            display();
        else
            mReadyToDisplay = ready;
    }


    public abstract String getTitle();
    public abstract void onRefresh();

    protected String getSubTitle() { return null; }
    public void clear() {}
    protected void display() {}

    protected void inflate(Menu menu, int position) {}
    protected void setContextMenuItems(Menu menu, int position) {}
    protected boolean handleContextItemSelected(MenuItem menu, int position) { return false;}

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenu.ContextMenuInfo menuInfo) {
        if (menuInfo == null)
            return;
        ContextMenuRecyclerView.RecyclerContextMenuInfo info = (ContextMenuRecyclerView.RecyclerContextMenuInfo)menuInfo;
        inflate(menu, info.position);

        setContextMenuItems(menu, info.position);
    }

    @Override
    public boolean onContextItemSelected(MenuItem menu) {
        if (!getUserVisibleHint()) return false;
        final ContextMenuRecyclerView.RecyclerContextMenuInfo info = (ContextMenuRecyclerView.RecyclerContextMenuInfo) menu.getMenuInfo();
        return info != null && handleContextItemSelected(menu, info.position);
    }

    protected void deleteMedia(final MediaLibraryItem mw, final boolean refresh, final Runnable failCB) {
        VLCApplication.runBackground(new Runnable() {
            @Override
            public void run() {
                final LinkedList<String> foldersToReload = new LinkedList<>();
                final LinkedList<String> mediaPaths = new LinkedList<>();

                if(mw.isP2PItem()) {
                    if(mMediaLibrary.deleteMedia(mw.getId())) {
                        if (mw instanceof MediaWrapper) {
                            mediaPaths.add(((MediaWrapper) mw).getLocation());
                        }
                    }
                }
                else {
                    for (MediaWrapper media : mw.getTracks()) {
                        final String path = media.getUri().getPath();
                        final String parentPath = FileUtils.getParent(path);
                        if (FileUtils.deleteFile(media.getUri())) {
                            if (media.getId() > 0L && !foldersToReload.contains(parentPath)) {
                                foldersToReload.add(parentPath);
                            }
                            mediaPaths.add(media.getLocation());
                        } else onDeleteFailed(media);
                    }
                }
                for (String folder : foldersToReload) mMediaLibrary.reload(folder);
                if (mService != null && getActivity() != null) {
                    VLCApplication.runOnMainThread(new Runnable() {
                        @Override
                        public void run() {
                            if (mediaPaths.isEmpty()) {
                                if (failCB != null) failCB.run();
                                return;
                            }
                            if (mService != null)
                                for (String path : mediaPaths) mService.removeLocation(path);
                            if (refresh) onRefresh();
                        }
                    });
                }
            }
        });
    }

    protected boolean checkWritePermission(MediaWrapper media, Runnable callback) {
        final Uri uri = media.getUri();
        if (!"file".equals(uri.getScheme())) return false;
        if (uri.getPath().startsWith(AndroidDevices.EXTERNAL_PUBLIC_DIRECTORY)) {
            //Check write permission starting Oreo
            if (AndroidUtil.isOOrLater && !Permissions.canWriteStorage()) {
                Permissions.askWriteStoragePermission(getActivity(), false, callback);
                return false;
            }
        } else if (AndroidUtil.isLolliPopOrLater && WriteExternalDelegate.Companion.needsWritePermission(uri)) {
            WriteExternalDelegate.Companion.askForExtWrite(getActivity(), uri, callback);
            return false;
        }
        return true;
    }

    private void onDeleteFailed(MediaWrapper media) {
        final View v = getView();
        if (v != null && isAdded()) UiTools.snacker(v, getString(R.string.msg_delete_failed, media.getTitle()));
    }

    protected void showInfoDialog(MediaLibraryItem item) {
        final Intent i = new Intent(getActivity(), InfoActivity.class);
        i.putExtra(InfoActivity.TAG_ITEM, item);
        startActivity(i);
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);
        mMenu = menu;
    }

    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    public void startActionMode() {
        mActionMode = ((AppCompatActivity)getActivity()).startSupportActionMode(this);
        setFabPlayVisibility(false);
    }

    protected void stopActionMode() {
        if (mActionMode != null) {
            mActionMode.finish();
            onDestroyActionMode(mActionMode);
            setFabPlayVisibility(true);
        }
    }

    public void invalidateActionMode() {
        if (mActionMode != null)
            mActionMode.invalidate();
    }

    @Override
    public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
        return false;
    }

    protected void onMedialibraryReady() {
        IntentFilter parsingServiceFilter = new IntentFilter(Constants.ACTION_SERVICE_ENDED);
        parsingServiceFilter.addAction(Constants.ACTION_SERVICE_STARTED);
        parsingServiceFilter.addAction(Constants.ACTION_MEDIALIBRARY_UPDATED);
        LocalBroadcastManager.getInstance(getActivity()).registerReceiver(mParsingServiceReceiver, parsingServiceFilter);
    }

    protected void setupMediaLibraryReceiver() {
        final BroadcastReceiver libraryReadyReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                LocalBroadcastManager.getInstance(getActivity()).unregisterReceiver(this);
                onMedialibraryReady();
            }
        };
        LocalBroadcastManager.getInstance(getActivity()).registerReceiver(libraryReadyReceiver, new IntentFilter(VLCApplication.ACTION_MEDIALIBRARY_READY));
    }

    protected final BroadcastReceiver mParsingServiceReceiver = new BroadcastReceiver() {

        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            switch (action) {
                case Constants.ACTION_SERVICE_ENDED:
                    onParsingServiceFinished();
                    break;
                case Constants.ACTION_SERVICE_STARTED:
                    onParsingServiceStarted();
                    break;
                //:ace
                case Constants.ACTION_MEDIALIBRARY_UPDATED:
                    onMedialibraryUpdated();
                    break;
                ///ace
            }
        }
    };

    protected void onParsingServiceStarted() {}

    protected void onParsingServiceFinished() {}

    //:ace
    protected void onMedialibraryUpdated() {}

    public void showBonusAdsButton(boolean visible) {
        if(!isHidden() && mFabPlay != null && mCanShowBonusAds) {
            // If FAB button was visible previously (e.g. activity was stopped but not finished)
            // then icon disappears.
            // Seems to be a bug: https://issuetracker.google.com/issues/111316656
            // Workaround is to set icon when button is hidden.
            setFabPlayVisibility(false);
            if(visible) {
                mFabPlay.setImageResource(R.drawable.ic_bonus);
                mFabPlay.setTag("bonus_ads");
            }
            else {
                mFabPlay.setImageResource(mFabPlayImageResourceId);
                mFabPlay.setTag(null);
            }
            setFabPlayVisibility(true);
        }
    }
    ///ace
}
