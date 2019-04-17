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

package org.videolan.vlc.gui.preferences;

import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;

import androidx.preference.CheckBoxPreference;
import androidx.preference.EditTextPreference;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceGroup;
import android.text.TextUtils;
import android.util.Log;

import org.acestream.sdk.AceStream;
import org.acestream.sdk.AceStreamManager;
import org.acestream.sdk.AceStreamManagerActivityHelper;
import org.acestream.sdk.CacheDirLocation;
import org.acestream.sdk.Constants;
import org.acestream.sdk.SelectedPlayer;
import org.acestream.sdk.controller.api.AceStreamPreferences;
import org.acestream.sdk.utils.Logger;
import org.videolan.vlc.R;
import org.videolan.vlc.VLCApplication;

import java.util.ArrayList;
import java.util.List;

import static org.acestream.sdk.Constants.PREF_KEY_SELECTED_PLAYER;

public class PreferencesEngine
        extends BasePreferenceFragment
        implements SharedPreferences.OnSharedPreferenceChangeListener,
        Preference.OnPreferenceChangeListener {
    private final static String TAG = "AS/PrefsEngine";

    private AceStreamManager mAceStreamManager;

    private AceStreamManagerActivityHelper.ActivityCallback mActivityCallback = new AceStreamManagerActivityHelper.ActivityCallback() {
        @Override
        public void onResumeConnected() {
        }

        @Override
        public void onConnected(AceStreamManager manager) {
            Log.v(TAG, "connected playback manager");
            mAceStreamManager = manager;
            updateSelectedPlayerPref();
            updatePrefSummary(findPreference(PREF_KEY_SELECTED_PLAYER));
            VLCApplication.runOnMainThread(new Runnable() {
                @Override
                public void run() {
                    for(String key: AceStreamPreferences.ENGINE_PREFS) {
                        findPreference(key).setVisible(true);
                    }
                }
            });
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
        return R.xml.preferences_engine;
    }

    @Override
    protected int getTitleId() {
        return R.string.engine_preferences_short;
    }

    @Override
    public void onStart() {
        super.onStart();
        getPreferenceScreen()
                .getSharedPreferences()
                .registerOnSharedPreferenceChangeListener(this);
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
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Initially hide all engine prefs
        for(String key: AceStreamPreferences.ENGINE_PREFS) {
            findPreference(key).setVisible(false);
        }

        Preference p;

        p = findPreference("start_acecast_server");
        p.setVisible(!AceStream.isAndroidTv());
        setDefaultPreferenceValue((CheckBoxPreference)p, true);

        p = findPreference(Constants.PREF_KEY_DEVICE_NAME);
        p.setOnPreferenceChangeListener(this);
        setDefaultPreferenceValue((EditTextPreference) p, Build.MODEL);

        p = findPreference("enable_debug_logging");
        setDefaultPreferenceValue((CheckBoxPreference)p, false);

        p = findPreference("disk_cache_limit");
        p.setOnPreferenceChangeListener(this);

        p = findPreference("memory_cache_limit");
        p.setOnPreferenceChangeListener(this);

        p = findPreference(PREF_KEY_SELECTED_PLAYER);
        p.setOnPreferenceChangeListener(this);

        updateCacheDirPref();
        initSummary(getPreferenceScreen());

        mActivityHelper = new AceStreamManagerActivityHelper(getActivity(), mActivityCallback);
    }

    @Override
    public boolean onPreferenceTreeClick(Preference preference) {
        if (preference.getKey() == null)
            return false;

        return super.onPreferenceTreeClick(preference);
    }

    private void updateCacheDirPref() {
        ListPreference p = (ListPreference)findPreference("cache_dir");

        List<CacheDirLocation> cacheDirLocations = AceStream.getCacheDirLocations();
        List<String> types = new ArrayList<>(cacheDirLocations.size()+2);
        List<String> paths = new ArrayList<>(cacheDirLocations.size()+2);
        String currentValue = p.getValue();
        boolean gotCurrentValue = false;
        for(CacheDirLocation location: cacheDirLocations) {
            types.add(location.type);
            paths.add(location.path);
            if(TextUtils.equals(location.path, currentValue)) {
                gotCurrentValue = true;
            }
        }
        if(!gotCurrentValue) {
            types.add(currentValue);
            paths.add(currentValue);
        }
        types.add(getResources().getString(R.string.select_directory));
        paths.add("");

        p.setEntries(types.toArray(new String[0]));
        p.setEntryValues(paths.toArray(new String[0]));
    }

    private void updateSelectedPlayerPref() {
        SelectedPlayer currentPlayer = (mAceStreamManager != null)
                ? mAceStreamManager.getSelectedPlayer()
                : null;
        String currentValue = null;
        if(currentPlayer != null) {
            currentValue = currentPlayer.getId();
        }

        ListPreference p = (ListPreference)findPreference(PREF_KEY_SELECTED_PLAYER);

        List<String> availablePlayerNames = new ArrayList<>();
        List<String> availablePlayerIds = new ArrayList<>();

        availablePlayerNames.add(getResources().getString(R.string.not_selected));
        availablePlayerIds.add("");

        boolean gotCurrentValue = false;
        List<SelectedPlayer> availablePlayers = AceStream.getAvailablePlayers();
        for(SelectedPlayer player: availablePlayers) {
            String id = player.getId();
            availablePlayerNames.add(player.getName());
            availablePlayerIds.add(id);

            if(!gotCurrentValue && TextUtils.equals(id, currentValue)) {
                gotCurrentValue = true;
            }
        }

        // Add current player if it's not in the list.
        // Usually current player is some remote device in such case.
        if(!gotCurrentValue && currentPlayer != null) {
            availablePlayerNames.add(currentPlayer.getName());
            availablePlayerIds.add(currentPlayer.getId());
        }

        String[] availablePlayerNamesArray = new String[availablePlayerNames.size()];
        String[] availablePlayerIdsArray = new String[availablePlayerIds.size()];
        availablePlayerNames.toArray(availablePlayerNamesArray);
        availablePlayerIds.toArray(availablePlayerIdsArray);

        p.setEntries(availablePlayerNamesArray);
        p.setEntryValues(availablePlayerIdsArray);

        if(!TextUtils.isEmpty(currentValue)) {
            p.setValue(currentValue);
        }
        else {
            p.setValue("");
        }
    }

    private void initSummary(Preference p) {
        if (p instanceof PreferenceGroup) {
            PreferenceGroup pGrp = (PreferenceGroup) p;
            for (int i = 0; i < pGrp.getPreferenceCount(); i++) {
                initSummary(pGrp.getPreference(i));
            }
        } else {
            updatePrefSummary(p);
        }
    }

    private void updatePrefSummary(Preference p) {
        if (p instanceof ListPreference) {
            ListPreference listPref = (ListPreference) p;
            p.setSummary(listPref.getEntry());
        }
        if (p instanceof EditTextPreference) {
            String value = VLCApplication.getSettings().getString(p.getKey(), "");
            if(!p.isPersistent()) {
                // Non-persistent prefs don't update automatically
                ((EditTextPreference)p).setText(value);
            }
            p.setSummary(value);
        }
    }

    private void setDefaultPreferenceValue(EditTextPreference p, String value) {
        SharedPreferences prefs = VLCApplication.getSettings();
        if(!prefs.contains(p.getKey())) {
            p.setText(value);
        }
    }

    private void setDefaultPreferenceValue(ListPreference p, String value) {
        if(p.getValue() == null) {
            p.setValue(value);
        }
    }

    private void setDefaultPreferenceValue(CheckBoxPreference p, boolean value) {
        SharedPreferences prefs = VLCApplication.getSettings();
        if(!prefs.contains(p.getKey())) {
            p.setChecked(value);
        }
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

        if(TextUtils.equals(key, "cache_dir")) {
            updateCacheDirPref();
        }
        else if(TextUtils.equals(key, PREF_KEY_SELECTED_PLAYER)) {
            updateSelectedPlayerPref();
        }
        updatePrefSummary(preference);

        if(mAceStreamManager != null) {
            mAceStreamManager.setPreference(key, value);
        }
        else {
            Log.e(TAG, "onSharedPreferenceChanged: missing acestream manager");
        }
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object value) {
        SharedPreferences sharedPreferences = VLCApplication.getSettings();
        String key = preference.getKey();

        Logger.vv(TAG, "onPreferenceChange: key=" + key + " value=" + value);

        boolean updatePrefs = true;
        if ("disk_cache_limit".equals(key)) {
            try {
                long valMb = Long.parseLong((String)value);
                if (valMb < 100) {
                    // modify value
                    value = String.valueOf(100);
                }
            }
            catch(NumberFormatException e) {
                Log.e(TAG, "onPreferenceChange: failed to parse disk cache limit", e);
                return false;
            }
        }
        else if ("memory_cache_limit".equals(key)) {
            try {
                long valMb = Long.parseLong((String)value);
                if (valMb < 25) {
                    // modify value
                    value = String.valueOf(25);
                }
            }
            catch(NumberFormatException e) {
                Log.e(TAG, "onPreferenceChange: failed to parse memory cache limit", e);
                return false;
            }
        }
        else if(TextUtils.equals(key, Constants.PREF_KEY_DEVICE_NAME)) {
            if(TextUtils.isEmpty((String)value)) {
                return false;
            }
        }
        else if (TextUtils.equals(key, Constants.PREF_KEY_SELECTED_PLAYER)) {
            String playerId = (String)value;
            if(mAceStreamManager != null) {
                boolean remove = false;
                if (TextUtils.isEmpty(playerId)) {
                    mAceStreamManager.forgetSelectedPlayer();
                    remove = true;
                } else {
                    SelectedPlayer player = SelectedPlayer.fromId(playerId);
                    mAceStreamManager.saveSelectedPlayer(player, true);
                    if(player == null) {
                        remove = true;
                    }
                    else {
                        sharedPreferences
                                .edit()
                                .putString(Constants.PREF_KEY_SELECTED_PLAYER, player.toJson())
                                .apply();
                    }
                }
                if(remove) {
                    sharedPreferences
                            .edit()
                            .remove(Constants.PREF_KEY_SELECTED_PLAYER)
                            .apply();
                }
            }
            updatePrefs = false;
        }

        if(updatePrefs) {
            SharedPreferences.Editor edit = sharedPreferences.edit();
            if (value instanceof Boolean) {
                edit.putBoolean(key, (boolean) value);
            } else {
                edit.putString(key, (String) value);
            }
            edit.apply();
        }

        return true;
    }
}
