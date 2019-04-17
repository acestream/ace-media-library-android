package org.videolan.vlc.gui.preferences.hack;

import android.content.Context;
import androidx.preference.EditTextPreference;
import android.util.AttributeSet;

import org.videolan.vlc.R;

public class NumberPreference extends EditTextPreference {
    public NumberPreference(Context context) {
        super(context);
        init();
    }

    public NumberPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public NumberPreference(Context context, AttributeSet attrs,
                          int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    public NumberPreference(Context context, AttributeSet attrs,
                          int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        init();
    }

    private void init() {
        setDialogLayoutResource(R.layout.custom_pref_dialog_number);
    }
}