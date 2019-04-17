/*****************************************************************************
 * VLCInstance.java
 *****************************************************************************
 * Copyright © 2011-2014 VLC authors and VideoLAN
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

package org.videolan.vlc.util;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.res.AssetManager;
import android.text.TextUtils;
import android.util.Log;

import org.videolan.vlc.BuildConfig;
import org.videolan.libvlc.LibVLC;
import org.videolan.libvlc.util.VLCUtil;
import org.videolan.vlc.VLCApplication;
import org.videolan.vlc.VLCCrashHandler;
import org.videolan.vlc.gui.CompatErrorActivity;

public class VLCInstance {
    public final static String TAG = "AceStream/VLC/I";

    private static LibVLC sLibVLC = null;
    private static String sUserAgent = null;

    private static Runnable sCopyLua = new Runnable() {
        @Override
        public void run() {
            final String destinationFolder = VLCApplication.getAppContext().getDir("vlc",
                    Context.MODE_PRIVATE).getAbsolutePath() + "/.share/lua";
            AssetManager am = VLCApplication.getAppResources().getAssets();
            FileUtils.copyAssetFolder(am, "lua", destinationFolder);
        }
    };

    public synchronized static void setUserAgent(String userAgent) {
        if(BuildConfig.DEBUG) {
            Log.v(TAG, "set user agent: " + userAgent);
        }
        sUserAgent = userAgent;
        if(sLibVLC != null) {
            sLibVLC.setUserAgent(sUserAgent, sUserAgent);
        }
    }

    /** A set of utility functions for the VLC application */
    public synchronized static LibVLC get() {
        return get(true);
    }

    public synchronized static LibVLC get(boolean autoInit) {
        if (sLibVLC == null && autoInit) {
            Thread.setDefaultUncaughtExceptionHandler(new VLCCrashHandler());

            final Context context = VLCApplication.getAppContext();
            if(!VLCUtil.hasCompatibleCPU(context)) {
                Log.e(TAG, VLCUtil.getErrorMsg());
                throw new IllegalStateException("LibVLC initialisation failed: " + VLCUtil.getErrorMsg());
            }

            // TODO change LibVLC signature to accept a List instead of an ArrayList
            sLibVLC = new LibVLC(context, VLCOptions.getLibOptions());

            if(BuildConfig.DEBUG) {
                Log.v(TAG, "init: ua=" + sUserAgent);
            }
            if(sUserAgent != null) {
                sLibVLC.setUserAgent(sUserAgent, sUserAgent);
            }

            VLCApplication.runBackground(sCopyLua);
        }
        return sLibVLC;
    }

    public static synchronized void restart() {
        restart(true);
    }

    public static synchronized void restart(boolean force) {
        Log.v(TAG, "restart");
        if (sLibVLC != null) {
            sLibVLC.release();
            sLibVLC = null;
        }
        if(force) {
            // init new instance
            get();
        }
    }

    public static synchronized void destroy() {
        Log.v(TAG, "destroy");
        if (sLibVLC != null) {
            sLibVLC.release();
            sLibVLC = null;
            sUserAgent = null;
        }
    }

    public static synchronized void setDeinterlace(String mode, boolean forceRestart) {
        String curretMode = VLCOptions.getDeinterlaceMode(VLCApplication.getAppContext());
        if(!TextUtils.equals(mode, curretMode)) {
            Log.d(TAG, "setDeinterlace: mode changed: " + curretMode + "->" + mode);
            VLCOptions.setDeinterlaceMode(VLCApplication.getAppContext(), mode);
            restart(forceRestart);
        }
    }

    public static synchronized boolean testCompatibleCPU(Context context) {
        if (sLibVLC == null && !VLCUtil.hasCompatibleCPU(context)) {
            if (context instanceof Activity) {
                final Intent i = new Intent(context, CompatErrorActivity.class);
                context.startActivity(i);
            }
            return false;
        } else
            return true;
    }
}
