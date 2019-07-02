package org.videolan.vlc.util;

import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;

import org.acestream.sdk.AceStream;
import org.acestream.sdk.player.api.AceStreamPlayer;
import org.videolan.medialibrary.media.MediaWrapper;
import org.videolan.medialibrary.media.TrackDescription;
import org.videolan.vlc.R;
import org.videolan.vlc.RendererDelegate;
import org.videolan.vlc.VLCApplication;

public class AceStreamUtils {

    private final static boolean ACE_STREAM_PLAYER_ENABLED = true;

    public static boolean shouldStartAceStreamPlayer(Uri uri) {
        return ACE_STREAM_PLAYER_ENABLED
                && uri != null
                && TextUtils.equals(uri.getScheme(), "acestream")
                && !RendererDelegate.INSTANCE.hasRenderer();
    }

    public static boolean shouldStartAceStreamPlayer(MediaWrapper mw) {
        if(!ACE_STREAM_PLAYER_ENABLED) {
            return false;
        }
        if(mw == null) {
            return false;
        }
        if(AceStream.isAceStreamUrl(mw.getUri())) {
            // This happens when some external app started engine session and passed
            // playback url to our app.
            return true;
        }
        return mw.isP2PItem()
                && !RendererDelegate.INSTANCE.hasRenderer()
                && mw.getType() != MediaWrapper.TYPE_AUDIO
                && !mw.hasFlag(MediaWrapper.MEDIA_FORCE_AUDIO);
    }

    /**
     * Create intent to start AceStream player and fill it with common params.
     */
    public static Intent getPlayerIntent() {
        SharedPreferences prefs = VLCApplication.getSettings();
        Intent intent = AceStreamPlayer.getPlayerIntent();
        intent.putExtra(AceStreamPlayer.EXTRA_SHOW_TV_UI,
                VLCApplication.showTvUi());
        intent.putExtra(AceStreamPlayer.EXTRA_AUDIO_OUTPUT,
                VLCOptions.getAout(prefs));
        intent.putExtra(AceStreamPlayer.EXTRA_AUDIO_DIGITAL_OUTPUT_ENABLED,
                VLCOptions.isAudioDigitalOutputEnabled(prefs));
        intent.putExtra(AceStreamPlayer.EXTRA_ASK_RESUME,
                prefs.getBoolean("dialog_confirm_resume", false));
        intent.putExtra(AceStreamPlayer.EXTRA_BROADCAST_ACTION,
                Constants.ACTION_ACE_STREAM_PLAYER_EVENT);
        intent.putExtra(AceStreamPlayer.EXTRA_SCREEN_ORIENTATION,
                prefs.getString("screen_orientation", "99" /*SCREEN ORIENTATION SENSOR*/));

        // LibVLC options
        Bundle libvlcOptions = new Bundle();

        int hardwareAcceleration = VLCOptions.HW_ACCELERATION_DISABLED;
        try {
            hardwareAcceleration = Integer.parseInt(prefs.getString("hardware_acceleration", "-1"));
        } catch (NumberFormatException ignored) {}

        libvlcOptions.putInt("hardware_acceleration", hardwareAcceleration);

        libvlcOptions.putBoolean(
                "enable_time_stretching_audio",
                prefs.getBoolean("enable_time_stretching_audio", VLCApplication.getAppResources().getBoolean(R.bool.time_stretching_default)));

        libvlcOptions.putString(
                "subtitle_text_encoding",
                prefs.getString("subtitle_text_encoding", ""));

        libvlcOptions.putBoolean(
                "enable_frame_skip",
                prefs.getBoolean("enable_frame_skip", false));

        libvlcOptions.putString(
                "chroma_format",
                prefs.getString("chroma_format", VLCApplication.getAppResources().getString(R.string.chroma_format_default)));

        int deblocking = -1;
        try {
            deblocking = VLCOptions.getDeblocking(Integer.parseInt(prefs.getString("deblocking", "-1")));
        } catch (NumberFormatException ignored) {}
        libvlcOptions.putInt("deblocking", deblocking);

        int networkCaching = 0;
        try {
            networkCaching = prefs.getInt("network_caching_value", 0);
        }
        catch(ClassCastException ignored) {}

        if (networkCaching > 60000)
            networkCaching = 60000;
        else if (networkCaching < 0)
            networkCaching = 0;

        libvlcOptions.putInt("network_caching_value", networkCaching);

        libvlcOptions.putString("subtitles_size", prefs.getString("subtitles_size", "16"));
        libvlcOptions.putBoolean("subtitles_bold", prefs.getBoolean("subtitles_bold", false));
        libvlcOptions.putString("subtitles_color", prefs.getString("subtitles_color", "16777215"));
        libvlcOptions.putBoolean("subtitles_background", prefs.getBoolean("subtitles_background", false));
        libvlcOptions.putString("opengl", prefs.getString("opengl", "-1"));
        libvlcOptions.putString("resampler", VLCOptions.getResampler());
        libvlcOptions.putBoolean("fix_audio_volume", prefs.getBoolean("fix_audio_volume", true));

        intent.putExtra(AceStreamPlayer.EXTRA_LIBVLC_OPTIONS, libvlcOptions);

        return intent;
    }

    public static TrackDescription[] convertTrackDescriptionArray(org.acestream.sdk.TrackDescription[] tracks) {
        if(tracks == null) return null;
        TrackDescription[] libvlcTracks = new TrackDescription[tracks.length];
        for(int i = 0; i < tracks.length; i++) {
            libvlcTracks[i] = new TrackDescription(tracks[i].id, tracks[i].name);
        }
        return libvlcTracks;
    }
}
