package org.videolan.vlc.gui.dialogs;

import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.appcompat.app.AlertDialog;
import android.util.Log;

import org.acestream.sdk.AceStream;
import org.acestream.sdk.AceStreamManager;
import org.acestream.sdk.utils.MiscUtils;
import org.videolan.vlc.R;
import org.videolan.medialibrary.media.MediaWrapper;
import org.videolan.vlc.PlaybackService;
import org.videolan.vlc.gui.PlaybackServiceActivity;
import org.videolan.vlc.util.Constants;

public class MobileNetworksDialogActivity extends PlaybackServiceActivity {
    private final static String TAG = "AS/MobileNetworksD";

    private MediaWrapper mMedia;

    public static boolean checkMobileNetworkConnection(Context context, AceStreamManager manager, MediaWrapper media) {
        boolean isConnectedToMobileNetwork = MiscUtils.isConnectedToMobileNetwork(context);
        boolean askedAboutMobileNetworking = manager.isMobileNetworkingEnabled();
        if(isConnectedToMobileNetwork && !askedAboutMobileNetworking) {
            Intent intent = new Intent(context, MobileNetworksDialogActivity.class);
            intent.putExtra("media", media);
            if(!(context instanceof Activity)) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            }
            context.startActivity(intent);
            return false;
        }

        return true;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mMedia = getIntent().getParcelableExtra("media");
        Log.d(TAG, "onCreate: media=" + mMedia);
    }

    @Override
    public void onConnected(PlaybackService service) {
        super.onConnected(service);

        AlertDialog.Builder builder = new AlertDialog.Builder(this, R.style.Theme_AppCompat_Dialog_Alert);
        builder.setMessage(R.string.allow_mobile_networks);
        builder.setPositiveButton(R.string.yes, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                AceStreamManager manager = mService.getAceStreamManager();
                if(manager != null) {
                    manager.setMobileNetworkingEnabled(true);
                }
                mService.load(mMedia);
                exit(true);
            }
        });
        builder.setNegativeButton(R.string.no, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                AceStreamManager manager = mService.getAceStreamManager();
                if(manager != null) {
                    manager.setMobileNetworkingEnabled(false);
                }
                mService.stop(false, true, false);
                exit(false);
            }
        });
        builder.setOnCancelListener(new DialogInterface.OnCancelListener() {
            @Override
            public void onCancel(DialogInterface dialog) {
                AceStreamManager manager = mService.getAceStreamManager();
                if(manager != null) {
                    manager.setMobileNetworkingEnabled(false);
                }
                mService.stop(false, true, false);
                exit(false);
            }
        });
        builder.create().show();
    }

    private void exit(boolean result) {
        if(!result) {
            //Close video player if started
            LocalBroadcastManager.getInstance(this).sendBroadcast(new Intent(Constants.EXIT_PLAYER));
        }
        finish();
    }
}
