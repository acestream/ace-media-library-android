package org.videolan.vlc.gui.preferences.hack;

import android.content.Context;
import androidx.preference.DialogPreference;
import androidx.preference.EditTextPreference;
import androidx.preference.ListPreference;
import androidx.preference.ListPreferenceDialogFragmentCompat;
import android.util.AttributeSet;

public class FolderPreference extends ListPreference {
    public FolderPreference(Context context) {
        super(context);
        init();
        ListPreferenceDialogFragmentCompat p;
    }

    public FolderPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public FolderPreference(Context context, AttributeSet attrs,
                            int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    public FolderPreference(Context context, AttributeSet attrs,
                            int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        init();
    }

    private void init() {
    }
}