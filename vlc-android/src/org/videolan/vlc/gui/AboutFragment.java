/*****************************************************************************
 * AboutActivity.java
 *****************************************************************************
 * Copyright © 2011-2012 VLC authors and VideoLAN
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston MA 02110-1301, USA.
 *****************************************************************************/

package org.videolan.vlc.gui;

import android.os.Bundle;
import androidx.annotation.Nullable;
import com.google.android.material.tabs.TabLayout;
import androidx.fragment.app.Fragment;
import androidx.viewpager.widget.ViewPager;
import androidx.appcompat.app.AppCompatActivity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebView;

import org.videolan.libvlc.util.AndroidUtil;
import org.videolan.vlc.R;
import org.videolan.vlc.VLCApplication;
import org.videolan.vlc.gui.audio.AudioPagerAdapter;
import org.videolan.vlc.gui.helpers.UiTools;
import org.videolan.vlc.util.Util;

public class AboutFragment extends Fragment {
    public final static String TAG = "VLC/AboutActivity";

    public final static int MODE_ABOUT = 0;
    public final static int MODE_VLC_LICENCE = 1;
    public final static int MODE_LICENCE_AGREEMENT = 1;
    public final static int MODE_PRIVACY_POLICY = 1;
    public final static int MODE_TOTAL = 4; // Number of tabs

    private ViewPager mViewPager;
    private TabLayout mTabLayout;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.about, container, false);
    }

    @Override
    public void onViewCreated(final View v, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(v, savedInstanceState);
        if (getActivity() instanceof AppCompatActivity)
            ((AppCompatActivity) getActivity()).getSupportActionBar().setTitle(getString(R.string.app_name));
        //Fix android 7 Locale problem with webView
        //https://stackoverflow.com/questions/40398528/android-webview-locale-changes-abruptly-on-android-n
        if (AndroidUtil.isNougatOrLater)
            VLCApplication.setLocale();

        final View aboutMain = v.findViewById(R.id.about_main);
        final WebView vlcLicenseWebView = v.findViewById(R.id.vlc_license_webview);
        final WebView licenseAgreementWebView = v.findViewById(R.id.license_agreement_webview);
        final WebView privacyPolicyWebView = v.findViewById(R.id.privacy_policy_webview);
        final String revision = getString(R.string.build_revision);

        View[] lists = new View[]{
                aboutMain,
                vlcLicenseWebView,
                licenseAgreementWebView,
                privacyPolicyWebView,
        };
        String[] titles = new String[] {
                getString(R.string.about),
                getString(R.string.licence),
                getString(R.string.user_agreement),
                getString(R.string.privacy_policy),
        };
        mViewPager = v.findViewById(R.id.pager);
        mViewPager.setOffscreenPageLimit(MODE_TOTAL-1);
        mViewPager.setAdapter(new AudioPagerAdapter(lists, titles));

        mTabLayout = v.findViewById(R.id.sliding_tabs);
        mTabLayout.setupWithViewPager(mViewPager);

        licenseAgreementWebView.loadUrl("http://acestream.org/about/license");
        privacyPolicyWebView.loadUrl("http://acestream.org/about/privacy-policy");

        VLCApplication.runBackground(new Runnable() {
            @Override
            public void run() {
                final String asset = Util.readAsset("licence.htm", "").replace("!COMMITID!",revision);
                VLCApplication.runOnMainThread(new Runnable() {
                    @Override
                    public void run() {
                        UiTools.fillAboutView(v);
                        vlcLicenseWebView.loadDataWithBaseURL(
                                null,
                                asset,
                                "text/html",
                                "UTF8",
                                null);
                    }
                });
            }
        });
    }
}
