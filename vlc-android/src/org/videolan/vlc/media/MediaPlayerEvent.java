package org.videolan.vlc.media;

import org.videolan.libvlc.MediaPlayer;

/**
 * Wrapper class to allow using protected constructor.
 */
public class MediaPlayerEvent extends MediaPlayer.Event {
    private String args1;

    // AceStream player events
    public static final int PlayerClosed = 5001; // remote player closed
    public static final int VolumeChanged = 5002; // player volume changed
    public static final int VideoSizeChanged = 5003; // player volume changed
    public static final int DeinterlaceModeChanged = 5004; // player volume changed

    public MediaPlayerEvent(int type) {
        super(type);
    }

    public MediaPlayerEvent(int type, long arg1) {
        super(type, arg1);
    }

    public MediaPlayerEvent(int type, String arg1) {
        super(type);
        this.args1 = arg1;
    }

    public MediaPlayerEvent(int type, long arg1, long arg2) {
        super(type, arg1, arg2);
    }

    public MediaPlayerEvent(int type, float argf) {
        super(type, argf);
    }

    public String getDeinterlaceMode() {
        return this.args1;
    }

    public int getVolume() {
        return (int)this.arg1;
    }

    public int getVideoSize() {
        return (int)this.arg1;
    }
}
