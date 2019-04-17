package org.videolan.vlc.gui.tv;

import android.graphics.Color;
import android.os.Bundle;
import androidx.fragment.app.FragmentActivity;
import android.view.View;
import android.webkit.WebView;

public class PrivacyPolicyActivity extends FragmentActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WebView wv = new WebView(this);
        wv.loadUrl("http://acestream.org/about/privacy-policy");
        setContentView(wv);
        ((View)wv.getParent()).setBackgroundColor(Color.LTGRAY);
        TvUtil.applyOverscanMargin(this);
    }
}
