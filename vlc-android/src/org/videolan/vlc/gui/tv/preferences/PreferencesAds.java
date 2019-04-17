/*
 * *************************************************************************
 *  PreferencesUi.java
 * **************************************************************************
 *  Copyright © 2015 VLC authors and VideoLAN
 *  Author: Geoffrey Métais
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

package org.videolan.vlc.gui.tv.preferences;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;

import org.acestream.sdk.utils.Logger;
import org.videolan.vlc.R;
import org.acestream.sdk.AceStreamManager;
import org.acestream.sdk.AceStreamManagerActivityHelper;
import org.acestream.sdk.controller.api.response.AuthData;
import org.acestream.sdk.utils.AuthUtils;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import androidx.preference.Preference;

public class PreferencesAds extends BasePreferenceFragment implements SharedPreferences.OnSharedPreferenceChangeListener {

    private final static String TAG = "AS/PrefsAds";

    private final static Set<String> NEED_AUTH_PREFS = new HashSet<>(Arrays.asList(
            "show_rewarded_ads"
    ));

    private final static Set<String> NEED_NOADS_PREFS = new HashSet<>(Arrays.asList(
            "show_ads_on_main_screen",
            "show_ads_on_preroll",
            "show_ads_on_pause",
            "show_ads_on_close"
            ));

    private AceStreamManager mAceStreamManager;

    private AceStreamManager.AuthCallback mAuthCallback = new AceStreamManager.AuthCallback() {
        @Override
        public void onAuthUpdated(AuthData authData) {
            updatePrefs();
        }
    };

    private AceStreamManagerActivityHelper.ActivityCallback mActivityCallback = new AceStreamManagerActivityHelper.ActivityCallback() {
        @Override
        public void onResumeConnected() {
            mAceStreamManager.addAuthCallback(mAuthCallback);
        }

        @Override
        public void onConnected(AceStreamManager pm) {
            Log.v(TAG, "connected playback manager");
            mAceStreamManager = pm;
            updatePrefs();
        }

        @Override
        public void onDisconnected() {
            Log.v(TAG, "disconnected playback manager");
            mAceStreamManager = null;
        }
    };

    private AceStreamManagerActivityHelper mActivityHelper;

    @Override
    protected int getXml() {
        return R.xml.preferences_ads;
    }

    @Override
    protected int getTitleId() {
        return R.string.ads_preferences_short;
    }

    @Override
    public void onStart() {
        super.onStart();
        getPreferenceScreen().getSharedPreferences().registerOnSharedPreferenceChangeListener(this);
        mActivityHelper.onStart();
    }

    @Override
    public void onStop() {
        super.onStop();
        getPreferenceScreen().getSharedPreferences()
                .unregisterOnSharedPreferenceChangeListener(this);
        mActivityHelper.onStop();
    }

    @Override
    public void onResume() {
        super.onResume();
        mActivityHelper.onResume();
    }

    @Override
    public void onPause() {
        super.onPause();
        mActivityHelper.onPause();

        if(mAceStreamManager != null) {
            mAceStreamManager.removeAuthCallback(mAuthCallback);
        }
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mActivityHelper = new AceStreamManagerActivityHelper(getActivity(), mActivityCallback);
        updatePrefs();
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        Object value = sharedPreferences.getAll().get(key);
        Preference preference = findPreference(key);

        Logger.vv(TAG, "onSharedPreferenceChanged: key=" + key + " value=" + value);

        if(preference == null) {
            // This happens when some preference which doesn't belong to engine is updated while
            // this activity is active.
            Logger.v(TAG, "onSharedPreferenceChanged: not found: key=" + key);
            return;
        }

        if(mAceStreamManager != null) {
            mAceStreamManager.setPreference(key, value);
        }
        else {
            Log.e(TAG, "onSharedPreferenceChanged: missing acestream manager");
        }
    }

    private void updatePrefs() {
        for(String key: NEED_AUTH_PREFS) {
            findPreference(key).setVisible(getAuthLevel() > 0);
        }

        for(String key: NEED_NOADS_PREFS) {
            findPreference(key).setVisible(AuthUtils.hasNoAds(getAuthLevel()));
        }
    }

    private int getAuthLevel() {
        if(mAceStreamManager != null) {
            return mAceStreamManager.getAuthLevel();
        }
        return 0;
    }
}
