package org.videolan.vlc.gui.preferences.hack;

import android.content.DialogInterface;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.preference.ListPreference;
import androidx.preference.PreferenceDialogFragmentCompat;
import android.util.Log;

import org.acestream.sdk.helpers.SettingDialogFragmentCompat;
import org.videolan.vlc.R;

import java.util.ArrayList;

public class FolderPreferenceDialogFragmentCompat
        extends PreferenceDialogFragmentCompat
        implements SettingDialogFragmentCompat.SettingDialogListener
{

    private static final String SAVE_STATE_INDEX = "ListPreferenceDialogFragment.index";
    private static final String SAVE_STATE_ENTRIES = "ListPreferenceDialogFragment.entries";
    private static final String SAVE_STATE_ENTRY_VALUES =
            "ListPreferenceDialogFragment.entryValues";

    private int mClickedDialogEntryIndex;
    private CharSequence[] mEntries;
    private CharSequence[] mEntryValues;

    public static FolderPreferenceDialogFragmentCompat newInstance(String key) {
        final FolderPreferenceDialogFragmentCompat fragment =
                new FolderPreferenceDialogFragmentCompat();
        final Bundle b = new Bundle(1);
        b.putString(ARG_KEY, key);
        fragment.setArguments(b);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (savedInstanceState == null) {
            final ListPreference preference = getListPreference();

            if (preference.getEntries() == null || preference.getEntryValues() == null) {
                throw new IllegalStateException(
                        "ListPreference requires an entries array and an entryValues array.");
            }

            mClickedDialogEntryIndex = preference.findIndexOfValue(preference.getValue());
            mEntries = preference.getEntries();
            mEntryValues = preference.getEntryValues();
        } else {
            mClickedDialogEntryIndex = savedInstanceState.getInt(SAVE_STATE_INDEX, 0);
            mEntries = getCharSequenceArray(savedInstanceState, SAVE_STATE_ENTRIES);
            mEntryValues = getCharSequenceArray(savedInstanceState, SAVE_STATE_ENTRY_VALUES);
        }
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(SAVE_STATE_INDEX, mClickedDialogEntryIndex);
        putCharSequenceArray(outState, SAVE_STATE_ENTRIES, mEntries);
        putCharSequenceArray(outState, SAVE_STATE_ENTRY_VALUES, mEntryValues);
    }

    private static void putCharSequenceArray(Bundle out, String key, CharSequence[] entries) {
        final ArrayList<String> stored = new ArrayList<>(entries.length);

        for (final CharSequence cs : entries) {
            stored.add(cs.toString());
        }

        out.putStringArrayList(key, stored);
    }

    private static CharSequence[] getCharSequenceArray(Bundle in, String key) {
        final ArrayList<String> stored = in.getStringArrayList(key);

        return stored == null ? null : stored.toArray(new CharSequence[stored.size()]);
    }

    private ListPreference getListPreference() {
        return (ListPreference) getPreference();
    }

    @Override
    protected void onPrepareDialogBuilder(AlertDialog.Builder builder) {
        super.onPrepareDialogBuilder(builder);

        builder.setSingleChoiceItems(mEntries, mClickedDialogEntryIndex,
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        if(which == mEntries.length - 1) {
                            // Show custom "select folder" dialog
                            SettingDialogFragmentCompat dialogFragment = new SettingDialogFragmentCompat();
                            dialogFragment.setListener(FolderPreferenceDialogFragmentCompat.this);
                            Bundle bundle = new Bundle();
                            bundle.putString("name", "cache_dir");
                            bundle.putString("type", "folder");
                            bundle.putString("title", getResources().getString(R.string.prefs_item_cache_dir));
                            bundle.putBoolean("sendToEngine", false);

                            dialogFragment.setArguments(bundle);
                            dialogFragment.show(getActivity().getSupportFragmentManager(), "setting_dialog");
                            return;
                        }

                        mClickedDialogEntryIndex = which;

                        /*
                         * Clicking on an item simulates the positive button
                         * click, and dismisses the dialog.
                         */
                        FolderPreferenceDialogFragmentCompat.this.onClick(dialog,
                                DialogInterface.BUTTON_POSITIVE);
                        dialog.dismiss();
                    }
                });

        /*
         * The typical interaction for list-based dialogs is to have
         * click-on-an-item dismiss the dialog instead of the user having to
         * press 'Ok'.
         */
        builder.setPositiveButton(null, null);
    }

    @Override
    public void onDialogClosed(boolean positiveResult) {
        final ListPreference preference = getListPreference();
        if (positiveResult && mClickedDialogEntryIndex >= 0) {
            String value = mEntryValues[mClickedDialogEntryIndex].toString();
            if (preference.callChangeListener(value)) {
                preference.setValue(value);
            }
        }
    }

    @Override
    public void onSaveSetting(String type, String name, Object value, boolean sendToEngine) {
        final ListPreference preference = getListPreference();
        if (preference.callChangeListener(value)) {
            preference.setValue((String)value);
        }
        dismiss();
    }
}