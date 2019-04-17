package org.videolan.vlc;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;

import org.acestream.sdk.utils.VlcBridge;
import org.videolan.libvlc.util.AndroidUtil;
import org.videolan.vlc.gui.MainActivity;
import org.videolan.vlc.gui.tv.MainTvActivity;
import org.videolan.vlc.util.AndroidDevices;

public class BridgeReceiver extends BroadcastReceiver {
    private static final String TAG = "VLC/BridgeReceiver";

    public BridgeReceiver() {
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        final String action = intent.getAction();
        final Bundle extras = intent.getExtras();
        if(action != null) {
            switch(action) {
                case VlcBridge.ACTION_START_PLAYBACK_SERVICE: {
                    Intent targetIntent = new Intent(context, PlaybackService.class);
                    targetIntent.setAction(action);
                    if(extras != null) {
                        targetIntent.putExtras(extras);
                    }
                    try {
                        if (!PlaybackService.isStarted() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            // Need to start foreground service because target app may be in background.
                            String bridgeAction = VlcBridge.getAction(intent);
                            if(TextUtils.equals(bridgeAction, VlcBridge.ACTION_LOAD_P2P_PLAYLIST)) {
                                context.startForegroundService(targetIntent);
                            }
                            else {
                                Log.e(TAG, "Skip bridge action: " + bridgeAction);
                            }
                        } else {
                            context.startService(targetIntent);
                        }
                    }
                    catch(Throwable e) {
                        Log.e(TAG, "Failed to start service", e);
                    }
                    break;
                }
                case VlcBridge.ACTION_START_MAIN_ACTVITY: {
                    Intent targetIntent = new Intent(context, VLCApplication.showTvUi() ? MainTvActivity.class : MainActivity.class);
                    if(extras != null) {
                        targetIntent.putExtras(extras);
                    }
                    targetIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    context.startActivity(targetIntent);
                    break;
                }
            }
        }
    }
}
