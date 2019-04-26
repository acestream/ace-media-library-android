package org.videolan.vlc.gui.helpers;

import android.app.Activity;
import android.app.AlarmManager;
import android.app.AlertDialog;
import android.app.PendingIntent;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;

import org.acestream.sdk.AceStream;
import org.acestream.sdk.AceStreamManager;
import org.acestream.sdk.AceStreamManagerActivityHelper;
import org.acestream.sdk.EngineStatus;
import org.acestream.sdk.controller.EngineApi;
import org.acestream.sdk.controller.api.AceStreamPreferences;
import org.acestream.sdk.controller.api.response.AuthData;
import org.acestream.sdk.interfaces.EngineStatusListener;
import org.acestream.sdk.interfaces.IRemoteDevice;
import org.acestream.sdk.utils.Logger;
import org.acestream.sdk.utils.PermissionUtils;
import org.videolan.vlc.PlaybackService;
import org.videolan.vlc.R;
import org.videolan.vlc.StartActivity;
import org.videolan.vlc.VLCApplication;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;

import androidx.annotation.Nullable;

public class MainActivityHelper {
    public final static String TAG = "AS/VLC/MainHelper";

    public final static int PROGRESS_CATEGORY_ENGINE_STARTUP = 0;

    private Activity mActivity;
    private Callback mCallback;
    private Handler mHandler;
    private AceStreamManager mAceStreamManager = null;
    private EngineApi mEngineService = null;
    private AceStreamManagerActivityHelper mActivityHelper;

    private final List<Runnable> mEngineOnReadyQueue = new ArrayList<>();

    public interface Callback {
        void showProgress(int category, String message);
        void hideProgress(int category);
        void onAuthUpdated(AuthData authData, String login);
        void onBonusAdsAvailable(boolean available);
    }

    private AceStreamManagerActivityHelper.ActivityCallback mActivityCallback = new AceStreamManagerActivityHelper.ActivityCallback() {
        @Override
        public void onResumeConnected() {
            mAceStreamManager.addEngineSettingsCallback(mEngineSettingsCallback);
            mAceStreamManager.addAuthCallback(mAuthCallback);
            mAceStreamManager.addEngineStatusListener(mEngineStatusListener);
            mAceStreamManager.discoverDevices(false);
            updateAuth();
        }

        @Override
        public void onConnected(AceStreamManager manager) {
            Log.v(TAG, "connected acestream manager");
            mAceStreamManager = manager;
            mAceStreamManager.addCallback(mAceStreamManagerCallback);
            mAceStreamManager.startEngine();
            mCallback.onBonusAdsAvailable(manager.areBonusAdsAvailable());
            mAceStreamManager.getPreferences(new org.acestream.engine.controller.Callback<AceStreamPreferences>() {
                @Override
                public void onSuccess(AceStreamPreferences preferences) {
                    onGotEnginePreferences(preferences);
                }

                @Override
                public void onError(String err) {
                }
            });
        }

        @Override
        public void onDisconnected() {
            Log.v(TAG, "disconnected acestream manager");
            if(mAceStreamManager != null) {
                mAceStreamManager.removeCallback(mAceStreamManagerCallback);
            }
            mAceStreamManager = null;
        }
    };

    private AceStreamManager.AuthCallback mAuthCallback = new AceStreamManager.AuthCallback() {
        @Override
        public void onAuthUpdated(AuthData authData) {
            updateAuth();
        }
    };

    private EngineStatusListener mEngineStatusListener = new EngineStatusListener() {
        @Override
        public void onEngineStatus(final EngineStatus status, final IRemoteDevice remoteDevice) {
            VLCApplication.runOnMainThread(new Runnable() {
                @Override
                public void run() {
                    processEngineStatus(status, remoteDevice);
                }
            });
        }

        @Override
        public boolean updatePlayerActivity() {
            return false;
        }
    };

    private AceStreamManager.Callback mAceStreamManagerCallback = new AceStreamManager.Callback() {
        @Override
        public void onEngineConnected(final EngineApi service) {
            Logger.vv(TAG, "onEngineConnected");
            VLCApplication.runOnMainThread(new Runnable() {
                @Override
                public void run() {
                    mCallback.hideProgress(PROGRESS_CATEGORY_ENGINE_STARTUP);
                    if(mEngineService == null) {
                        mEngineService = service;
                        mAceStreamManager.checkPendingNotification();
                    }

                    for(Runnable runnable: mEngineOnReadyQueue) {
                        runnable.run();
                    }
                    mEngineOnReadyQueue.clear();
                }
            });
        }

        @Override
        public void onEngineFailed() {
            Logger.vv(TAG, "onEngineFailed");
            VLCApplication.runOnMainThread(new Runnable() {
                @Override
                public void run() {
                    AceStream.toast("engine failed");
                }
            });
        }

        @Override
        public void onEngineUnpacking() {
            Logger.vv(TAG, "onEngineUnpacking");
            VLCApplication.runOnMainThread(new Runnable() {
                @Override
                public void run() {
                    mCallback.showProgress(PROGRESS_CATEGORY_ENGINE_STARTUP, mActivity.getResources().getString(R.string.dialog_unpack));
                }
            });
        }

        @Override
        public void onEngineStarting() {
            Logger.vv(TAG, "onEngineStarting");
            VLCApplication.runOnMainThread(new Runnable() {
                @Override
                public void run() {
                    mCallback.showProgress(PROGRESS_CATEGORY_ENGINE_STARTUP, mActivity.getResources().getString(R.string.dialog_start));
                }
            });
        }

        @Override
        public void onEngineStopped() {
            Logger.vv(TAG, "onEngineStopped");
            VLCApplication.runOnMainThread(new Runnable() {
                @Override
                public void run() {
                    mActivity.finish();
                }
            });
        }

        @Override
        public void onBonusAdsAvailable(boolean available) {
            Logger.vv(TAG, "onBonusAdsAvailable: available=" + available);
            mCallback.onBonusAdsAvailable(available);
        }
    };

    private AceStreamManager.EngineSettingsCallback mEngineSettingsCallback = new AceStreamManager.EngineSettingsCallback() {
        @Override
        public void onEngineSettingsUpdated(@Nullable AceStreamPreferences preferences) {
            if(mAceStreamManager != null) {
                mAceStreamManager.checkPendingNotification();
            }
            onGotEnginePreferences(preferences);
        }
    };

    public MainActivityHelper(Activity activity, Callback callback) {
        mActivity = activity;
        mCallback = callback;
        mHandler = new Handler();
        mActivityHelper = new AceStreamManagerActivityHelper(mActivity, mActivityCallback);
    }

    public void shutdown() {
        Logger.v(TAG, "stopApp");
        AceStream.stopApp();
    }

    public void clearEngineCache() {
        if(mAceStreamManager != null) {
            mAceStreamManager.clearCache();
        }
        else {
            AceStream.toast(R.string.not_connected_to_engine);
        }
    }

    public void restartApp() {
        new AlertDialog.Builder(mActivity)
                .setMessage(R.string.restart_confirmation)
                .setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {

                    @Override
                    public void onClick(DialogInterface dialog, int whichButton) {
                        Context ctx = VLCApplication.getAppContext();
                        Intent intent = new Intent(ctx, StartActivity.class);
                        int pendingIntentId = new Random().nextInt();
                        PendingIntent pendingIntent = PendingIntent.getActivity(
                                ctx,
                                pendingIntentId,
                                intent,
                                PendingIntent.FLAG_CANCEL_CURRENT);
                        AlarmManager mgr = (AlarmManager)ctx.getSystemService(android.content.Context.ALARM_SERVICE);
                        if(mgr != null) {
                            mgr.set(AlarmManager.RTC,
                                    System.currentTimeMillis() + 100,
                                    pendingIntent);
                        }
                        AceStream.restartApp();
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    public void processEngineStatus(final EngineStatus status, final IRemoteDevice remoteDevice) {
        if(remoteDevice != null)
            return;

        String message = "";

        if(status != null) {
            switch(status.status) {
                case "engine_unpacking":
                    message = mActivity.getResources().getString(R.string.dialog_unpack);
                    break;
                case "engine_starting":
                    message = mActivity.getResources().getString(R.string.dialog_start);
                    break;
                case "engine_failed":
                    message = mActivity.getResources().getString(R.string.start_fail);
                    break;
                case "loading":
                    message = mActivity.getResources().getString(R.string.loading);
                    break;
                case "starting":
                    message = mActivity.getResources().getString(R.string.starting);
                    break;
                case "checking":
                    message = mActivity.getResources().getString(R.string.status_checking_short, status.progress);
                    break;
                case "prebuf":
                    message = mActivity.getResources().getString(R.string.status_prebuffering, status.progress, status.peers, status.speedDown);
                    break;
                case "error":
                    message = status.errorMessage;
                    break;
                case "dl":
                    message = String.format(
                            Locale.getDefault(),
                            "Peers:%d DL:%d UL:%d",
                            status.peers,
                            status.speedDown,
                            status.speedUp
                    );
                    break;
            }
        }
    }

    public void onResume() {
        mActivityHelper.onResume();
    }

    public void onPause() {
        mActivityHelper.onPause();

        if(mAceStreamManager != null) {
            mAceStreamManager.removeEngineSettingsCallback(mEngineSettingsCallback);
            mAceStreamManager.removeAuthCallback(mAuthCallback);
            mAceStreamManager.removeEngineStatusListener(mEngineStatusListener);
        }
    }

    public void onStart() {
        if(PermissionUtils.hasStorageAccess()) {
            mActivityHelper.onStart();
        }
    }

    public void onStop() {
        mActivityHelper.onStop();
    }

    private void updateAuth() {
        AuthData authData = null;
        String login = null;
        if(mAceStreamManager != null) {
            authData = mAceStreamManager.getAuthData();
            login = mAceStreamManager.getAuthLogin();
        }
        mCallback.onAuthUpdated(authData, login);
    }

    private void runWhenEngineReady(Runnable runnable) {
        runWhenEngineReady(runnable, false);
    }

    private void runWhenEngineReady(Runnable runnable, boolean alwaysSchedule) {
        if(mAceStreamManager != null) {
            if(alwaysSchedule) {
                mHandler.post(runnable);
            }
            else {
                runnable.run();
            }
        }
        else {
            mEngineOnReadyQueue.add(runnable);
        }
    }

    public boolean isUserLoggedIn() {
        if(mAceStreamManager == null) {
            return false;
        }

        return mAceStreamManager.getAuthLevel() > 0;
    }

    private void onGotEnginePreferences(@Nullable AceStreamPreferences preferences) {
        if(preferences == null) return;
        SharedPreferences.Editor editor = VLCApplication.getSettings().edit();
        Bundle bundle = preferences.getAll();
        for(String key: bundle.keySet()) {
            Object value = bundle.get(key);
            if(value == null)
                editor.remove(key);
            else if(value instanceof Boolean)
                editor.putBoolean(key, (boolean)value);
            else if(value instanceof String)
                editor.putString(key, (String)value);
            else
                throw new IllegalStateException("String or boolean expected: value=" + value);
        }
        editor.apply();
    }

    public boolean areBonusAdsAvailable() {
        return mAceStreamManager != null && mAceStreamManager.areBonusAdsAvailable();
    }

    public void showBonusAds() {
        if(mAceStreamManager != null) {
            mAceStreamManager.showBonusAds(mActivity);
        }
    }
}
