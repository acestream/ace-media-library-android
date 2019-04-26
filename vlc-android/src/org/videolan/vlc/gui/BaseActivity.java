package org.videolan.vlc.gui;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;

import org.acestream.sdk.AceStream;
import org.videolan.vlc.R;
import org.videolan.vlc.VLCApplication;


public class BaseActivity extends AppCompatActivity {

    private static final String TAG = "VLC/BaseActivity";

    // broadcast receiver
    private final BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if(intent == null) return;
            if(TextUtils.equals(intent.getAction(), AceStream.ACTION_STOP_APP)) {
                Log.d(TAG, "receiver: stop app: class=" + BaseActivity.this.getClass().getName());
                finish();
            }
        }
    };

    static {
        AppCompatDelegate.setDefaultNightMode(VLCApplication.getAppContext() != null && PreferenceManager.getDefaultSharedPreferences(VLCApplication.getAppContext()).getBoolean("daynight", false) ? AppCompatDelegate.MODE_NIGHT_AUTO : AppCompatDelegate.MODE_NIGHT_NO);
    }

    protected SharedPreferences mSettings;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        /* Get settings */
        mSettings = PreferenceManager.getDefaultSharedPreferences(this);
        /* Theme must be applied before super.onCreate */
        applyTheme();
        super.onCreate(savedInstanceState);

        final IntentFilter filter = new IntentFilter();
        filter.addAction(AceStream.ACTION_STOP_APP);
        registerReceiver(mBroadcastReceiver, filter);
    }

    private void applyTheme() {
        if (VLCApplication.isBlackTheme()) {
            setTheme(R.style.Theme_VLC_Black);
        }
    }

    //:ace
    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(VLCApplication.updateBaseContextLocale(base));
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unregisterReceiver(mBroadcastReceiver);
    }
    ///ace
}
