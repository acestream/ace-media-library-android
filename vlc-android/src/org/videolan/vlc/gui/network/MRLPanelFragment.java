/*****************************************************************************
 * MRLPanelFragment.java
 *****************************************************************************
 * Copyright © 2014-2015 VLC authors and VideoLAN
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
package org.videolan.vlc.gui.network;

import android.app.Activity;
import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import androidx.annotation.Nullable;
import androidx.annotation.RestrictTo;
import com.google.android.material.textfield.TextInputLayout;
import androidx.fragment.app.DialogFragment;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.text.TextUtils;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.ImageView;
import android.widget.TextView;

import org.acestream.engine.controller.Callback;
import org.acestream.sdk.AceStream;
import org.acestream.sdk.AceStreamManager;
import org.acestream.sdk.controller.EngineApi;
import org.acestream.sdk.controller.api.TransportFileDescriptor;
import org.acestream.sdk.controller.api.response.MediaFilesResponse;
import org.acestream.sdk.errors.TransportFileParsingException;
import org.acestream.sdk.utils.Logger;
import org.videolan.medialibrary.media.MediaWrapper;
import org.videolan.vlc.R;
import org.videolan.vlc.VLCApplication;
import org.videolan.vlc.gui.DialogActivity;
import org.videolan.vlc.gui.helpers.UiTools;
import org.videolan.vlc.media.MediaUtils;

import java.util.ArrayList;
import java.util.List;

public class MRLPanelFragment extends DialogFragment implements View.OnKeyListener, TextView.OnEditorActionListener, View.OnClickListener, MRLAdapter.MediaPlayerController {
    private static final String TAG = "VLC/MrlPanelFragment";
    public static final String KEY_MRL = "mrl";
    private MRLAdapter mAdapter;
    private TextInputLayout mEditText;
    private ImageView mButtonSend;

    //:ace
    private AceStreamManager.Client mAceStreamManagerClient;
    ///ace

    // used in tests
    private TestCallback mTestCallback = null;
    private boolean mTestMode = false;

    public MRLPanelFragment(){}

    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        setStyle(DialogFragment.STYLE_NO_FRAME, 0);
        final View v = inflater.inflate(R.layout.mrl_panel, container, false);
        mEditText = v.findViewById(R.id.mrl_edit);
        mButtonSend = v.findViewById(R.id.send);
        mEditText.getEditText().setOnKeyListener(this);
        mEditText.getEditText().setOnEditorActionListener(this);
        mEditText.setHint(getString(R.string.open_link_dialog_msg));

        RecyclerView recyclerView = (RecyclerView) v.findViewById(R.id.mrl_list);
        recyclerView.addItemDecoration(new DividerItemDecoration(getActivity(), DividerItemDecoration.VERTICAL));
        recyclerView.setLayoutManager(new LinearLayoutManager(getActivity()));
        mAdapter = new MRLAdapter(this);
        recyclerView.setAdapter(mAdapter);
        v.findViewById(R.id.send).setOnClickListener(this);
        return v;
    }

    public void onStart() {
        super.onStart();
        updateHistory();
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        if (mEditText != null && mEditText.getEditText() != null)
            outState.putString(KEY_MRL, mEditText.getEditText().getText().toString());
    }

    @Override
    public void onViewStateRestored(@Nullable Bundle savedInstanceState) {
        super.onViewStateRestored(savedInstanceState);
        if (savedInstanceState == null || mEditText == null) return;
        final String mrl = savedInstanceState.getString(KEY_MRL);
        if (mEditText != null && mEditText.getEditText() != null) mEditText.getEditText().setText(mrl);
    }

    private void updateHistory() {
        mAdapter.setList(VLCApplication.getMLInstance().lastStreamsPlayed());
    }

    @Override
    public boolean onKey(View v, int keyCode, KeyEvent event) {
        return (keyCode == EditorInfo.IME_ACTION_DONE ||
                keyCode == EditorInfo.IME_ACTION_GO ||
                (event.getAction() == KeyEvent.ACTION_DOWN &&
                        event.getKeyCode() == KeyEvent.KEYCODE_ENTER))
                && processUri();
    }

    private boolean processUri() {
        if (mEditText.getEditText() != null && !TextUtils.isEmpty(mEditText.getEditText().getText())) {
            Uri uri = Uri.parse(mEditText.getEditText().getText().toString().trim());
            final MediaWrapper mw = new MediaWrapper(uri);
            if(mTestCallback != null) {
                mTestCallback.onGotMediaWrapper(mw);
            }
            if(mw.isP2PItem()) {
                processP2PMedia(getActivity(), mw);
            }
            else {
                playMedia(mw);
            }
            mEditText.getEditText().setText(getResources().getString(R.string.loading));
            mEditText.getEditText().setEnabled(false);
            mButtonSend.setEnabled(false);
            return true;
        }
        else {
            if(mTestCallback != null) {
                mTestCallback.onEmptyInput();
            }
        }
        return false;
    }

    public void playMedia(MediaWrapper mw) {
        mw.setType(MediaWrapper.TYPE_STREAM);
        MediaUtils.openMedia(getActivity(), mw);
        updateHistory();
        getActivity().supportInvalidateOptionsMenu();
        UiTools.setKeyboardVisibility(mEditText, false);
        closeDialog();
    }

    //:ace
    private void processP2PMedia(@Nullable final Context context, final MediaWrapper mw) {
        if(context == null) {
            Log.e(TAG, "processP2PMedia: missing context");
            closeDialog();
            return;
        }

        try {
            final TransportFileDescriptor descriptor = TransportFileDescriptor.fromMrl(context.getContentResolver(), mw.getUri());

            mAceStreamManagerClient = new AceStreamManager.Client(context, new AceStreamManager.Client.Callback() {
                private AceStreamManager mAceStreamManager = null;
                private EngineApi mEngineApi = null;

                private AceStreamManager.Callback mCallback = new AceStreamManager.Callback() {
                    @Override
                    public void onEngineConnected(EngineApi engineApi) {
                        Logger.v(TAG, "processP2PMedia: engine connected");
                        if(mEngineApi == null) {
                            mEngineApi = engineApi;
                            mEngineApi.getMediaFiles(descriptor, new Callback<MediaFilesResponse>() {
                                @Override
                                public void onSuccess(MediaFilesResponse result) {
                                    Logger.v(TAG, "processP2PMedia: got media files: count=" + result.files.length);
                                    List<MediaWrapper> playlist = new ArrayList<>(result.files.length);
                                    for (MediaFilesResponse.MediaFile mf : result.files) {
                                        MediaWrapper item = new MediaWrapper(descriptor, mf);
                                        playlist.add(item);
                                    }
                                    if(mTestCallback != null) {
                                        mTestCallback.onMediaFilesSuccess(result, playlist);
                                    }
                                    MediaUtils.openList(context, playlist, 0);
                                    mAceStreamManagerClient.disconnect();
                                    closeDialog();
                                }

                                @Override
                                public void onError(String err) {
                                    Logger.v(TAG, "processP2PMedia: failed to get media files: " + err);
                                    AceStream.toast(err);
                                    if(mTestCallback != null) {
                                        mTestCallback.onMediaFilesError(err);
                                    }
                                    mAceStreamManagerClient.disconnect();
                                    closeDialog();
                                }
                            });
                        }
                    }

                    @Override
                    public void onEngineFailed() {
                        mAceStreamManagerClient.disconnect();
                    }

                    @Override
                    public void onEngineUnpacking() {}

                    @Override
                    public void onEngineStarting() {}

                    @Override
                    public void onEngineStopped() {
                        mAceStreamManagerClient.disconnect();
                    }

                    @Override
                    public void onBonusAdsAvailable(boolean available) {
                    }
                };

                @Override
                public void onConnected(AceStreamManager service) {
                    Logger.v(TAG, "processP2PMedia: pm connected");
                    if(mAceStreamManager == null) {
                        mAceStreamManager = service;
                        mAceStreamManager.addCallback(mCallback);
                        mAceStreamManager.startEngine();
                    }
                }

                @Override
                public void onDisconnected() {
                    Logger.v(TAG, "processP2PMedia: pm disconnected");
                    if(mAceStreamManager != null) {
                        mAceStreamManager.removeCallback(mCallback);
                        mAceStreamManager = null;
                    }
                }
            });
            mAceStreamManagerClient.connect();
        }
        catch(TransportFileParsingException e) {
            AceStream.toast(e.getMessage());
            closeDialog();
        }
    }
    ///ace

    @Override
    public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
        return false;
    }

    public boolean isEmpty(){
        return mAdapter.isEmpty();
    }

    @Override
    public void onClick(View v) {
        processUri();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        final Activity activity = getActivity();
        if (activity instanceof DialogActivity) activity.finish();
    }

    private void closeDialog() {
        if(mTestMode) {
            mTestCallback.onDialogClosed();
        }
        else {
            dismissAllowingStateLoss();
        }
    }

    public interface TestCallback {
        void onEmptyInput();
        void onGotMediaWrapper(MediaWrapper mw);
        void onMediaFilesSuccess(MediaFilesResponse response, List<MediaWrapper> playlist);
        void onMediaFilesError(String error);
        void onDialogClosed();
    }

    @RestrictTo(RestrictTo.Scope.TESTS)
    public void setTestCallback(TestCallback callback) {
        mTestCallback = callback;
    }

    @RestrictTo(RestrictTo.Scope.TESTS)
    public void setTestMode(boolean value) {
        mTestMode = value;
    }
}
