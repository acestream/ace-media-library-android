/*
 * *************************************************************************
 *  BasePreferenceFragment.java
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

import android.annotation.TargetApi;
import android.app.Fragment;
import android.os.Build;
import android.os.Bundle;
import androidx.preference.MultiSelectListPreference;
import androidx.leanback.preference.LeanbackPreferenceFragment;
import androidx.preference.Preference;
import android.app.DialogFragment;

import org.videolan.vlc.R;
import org.videolan.vlc.gui.preferences.hack.FolderPreference;
import org.videolan.vlc.gui.preferences.hack.FolderPreferenceDialogFragment;
import org.videolan.vlc.gui.preferences.hack.MultiSelectListPreferenceDialogFragment;

public abstract class BasePreferenceFragment extends LeanbackPreferenceFragment {

    private static final String DIALOG_FRAGMENT_TAG = "android.support.v7.preference.PreferenceFragment.DIALOG";

    protected abstract int getXml();
    protected abstract int getTitleId();

    @Override
    public void onCreatePreferences(Bundle bundle, String s) {
        addPreferencesFromResource(getXml());
    }

    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    protected void loadFragment(Fragment fragment) {
        getActivity().getFragmentManager().beginTransaction().replace(R.id.fragment_placeholder, fragment)
                .addToBackStack("main")
                .commit();
    }

    @Override
    public void onDisplayPreferenceDialog(Preference preference) {
        DialogFragment dialogFragment = null;
        if (preference instanceof MultiSelectListPreference) {
            dialogFragment = MultiSelectListPreferenceDialogFragment.newInstance(preference.getKey());
        }
        else if(preference instanceof FolderPreference) {
            dialogFragment = FolderPreferenceDialogFragment.newInstance(preference.getKey());
        }

        if(dialogFragment != null) {
            // Show custom dialog
            dialogFragment.setTargetFragment(this, 0);
            dialogFragment.show(getFragmentManager(), DIALOG_FRAGMENT_TAG);
        }
        else {
            super.onDisplayPreferenceDialog(preference);
        }
    }
}
