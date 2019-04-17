package org.videolan.vlc.util;


import android.text.TextUtils;

import org.acestream.sdk.RemoteDevice;
import org.acestream.sdk.utils.MiscUtils;
import org.videolan.libvlc.RendererItem;

public class RendererItemWrapper {
    public final static int TYPE_VLC = 1;
    public final static int TYPE_ACESTREAM = 2;

    private int mType;
    private RendererItem mVlcRemoteDevice;
    private RemoteDevice mAceStreamRemoteDevice;

    public RendererItemWrapper(RendererItem device) {
        mVlcRemoteDevice = device;
        mType = TYPE_VLC;
    }

    public RendererItemWrapper(RemoteDevice device) {
        mAceStreamRemoteDevice = device;
        mType = TYPE_ACESTREAM;
    }

    public RendererItem getVlcRenderer() {
        return mVlcRemoteDevice;
    }

    public RemoteDevice getAceStreamRenderer() {
        return mAceStreamRemoteDevice;
    }

    public int getType() {
        return mType;
    }

    public String displayName() {
        if(mAceStreamRemoteDevice != null) {
            return mAceStreamRemoteDevice.getName();
        }
        else if(mVlcRemoteDevice != null) {
            return mVlcRemoteDevice.displayName;
        }
        else {
            return null;
        }
    }

    public String getId() {
        if(mAceStreamRemoteDevice != null) {
            return mAceStreamRemoteDevice.getId();
        }
        else if(mVlcRemoteDevice != null) {
            return mVlcRemoteDevice.name;
        }
        else {
            return null;
        }
    }

    @Override
    public String toString() {
        return "<Renderer: type=" + mType + " id=" + getId() + ">";
    }

    public boolean equals(RemoteDevice renderer) {
        return mAceStreamRemoteDevice != null
                && mAceStreamRemoteDevice.equals(renderer);
    }

    public boolean equals(RendererItem renderer) {
        return mVlcRemoteDevice != null
                && mVlcRemoteDevice.equals(renderer);
    }

    public boolean equals(RendererItemWrapper other) {
        return equals(other, true);
    }

    public boolean equals(RendererItemWrapper other, boolean strict) {
        if(other == null) return false;

        if(other.getType() == getType()) {
            switch (getType()) {
                case TYPE_VLC:
                    return other.equals(getVlcRenderer());
                case TYPE_ACESTREAM:
                    return other.equals(getAceStreamRenderer());
                default:
                    return false;
            }
        }

        if(strict) return false;

        // In non-strict mode vlc and csdk renderers are compared by ip
        if(getType() == TYPE_VLC && other.getType() == TYPE_ACESTREAM && !other.getAceStreamRenderer().isAceCast()) {
            return TextUtils.equals(MiscUtils.getRendererIp(getVlcRenderer().sout),
                    other.getAceStreamRenderer().getIpAddress());
        }
        else if(getType() == TYPE_ACESTREAM && !mAceStreamRemoteDevice.isAceCast() && other.getType() == TYPE_VLC) {
            return TextUtils.equals(MiscUtils.getRendererIp(other.getVlcRenderer().sout),
                    getAceStreamRenderer().getIpAddress());
        }

        return false;
    }

    public static boolean equals(RendererItemWrapper a, RendererItemWrapper b) {
        return equals(a, b, true);
    }

    public static boolean equals(RendererItemWrapper a, RendererItemWrapper b, boolean strict) {
        return a != null && a.equals(b, strict);
    }
}
