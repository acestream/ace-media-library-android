package org.videolan.medialibrary.media;

import org.videolan.libvlc.MediaPlayer;

import androidx.annotation.Nullable;

/**
 * Copy of Media.TrackDescription.
 * Need this class to allow creation of tracks in java code,
 * because Media.TrackDescription has private constructor.
 */
public class TrackDescription {
    public final int id;
    public final String name;

    public static TrackDescription[] fromNative(@Nullable MediaPlayer.TrackDescription[] tracks) {
        if(tracks == null) {
            return null;
        }

        TrackDescription[] list = new TrackDescription[tracks.length];
        for(int i = 0; i < tracks.length; i++) {
            list[i] = new TrackDescription(tracks[i].id, tracks[i].name);
        }
        return list;
    }

    public TrackDescription(int id, String name) {
        this.id = id;
        this.name = name;
    }
}