package org.videolan.vlc.gui;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;

import org.videolan.vlc.R;
import org.videolan.vlc.VLCApplication;


public class BaseActivity extends AppCompatActivity {

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
    ///ace
}
