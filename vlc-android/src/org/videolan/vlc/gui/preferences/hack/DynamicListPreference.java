package org.videolan.vlc.gui.preferences.hack;

//import android.preference.ListPreference;
import android.content.Context;
import androidx.preference.ListPreference;
import android.util.AttributeSet;
import android.widget.ArrayAdapter;
import android.widget.ListAdapter;

public class DynamicListPreference extends ListPreference {


    public DynamicListPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public DynamicListPreference(Context context) {
        super(context);
    }

//    @Override
//    protected View onCreateDialogView() {
//        ListView view = new ListView(getContext());
//        view.setAdapter(adapter());
//        setEntries(entries());
//        setEntryValues(entryValues());
//        setValueIndex(initializeIndex());
//        return view;
//    }

    private ListAdapter adapter() {
        return new ArrayAdapter(getContext(), android.R.layout.select_dialog_singlechoice);
    }

    private CharSequence[] entries() {
        //action to provide entry data in char sequence array for list
        return null;
    }

    private CharSequence[] entryValues() {
        //action to provide value data for list
        return null;
    }
}