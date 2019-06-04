package org.videolan.vlc.media

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.annotation.MainThread
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import android.text.TextUtils
import android.util.Log
import android.widget.Toast
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.*
import org.acestream.sdk.*
import org.acestream.sdk.controller.EngineApi
import org.acestream.sdk.player.api.AceStreamPlayer
import org.acestream.sdk.utils.Logger
import org.acestream.sdk.utils.MiscUtils
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer
import org.videolan.medialibrary.Medialibrary
import org.videolan.medialibrary.media.MediaWrapper
import org.videolan.vlc.*
import org.videolan.vlc.BuildConfig
import org.videolan.vlc.R
import org.videolan.vlc.gui.dialogs.MobileNetworksDialogActivity
import org.videolan.vlc.gui.preferences.PreferencesActivity
import org.videolan.vlc.gui.preferences.PreferencesFragment
import org.videolan.vlc.gui.video.VideoPlayerActivity
import org.videolan.vlc.util.*
import java.util.*
import org.videolan.vlc.util.Constants

@ObsoleteCoroutinesApi
@ExperimentalCoroutinesApi
class PlaylistManager(val service: PlaybackService) : MediaWrapperList.EventListener, Media.EventListener, CoroutineScope {

    override val coroutineContext = Dispatchers.Main.immediate

    private val TAG = "VLC/PM"
    private val PREVIOUS_LIMIT_DELAY = 5000L
    private val AUDIO_REPEAT_MODE_KEY = "audio_repeat_mode"

    private val medialibrary by lazy(LazyThreadSafetyMode.NONE) { Medialibrary.getInstance() }
    private var playerInitialized = false
    val player by lazy(LazyThreadSafetyMode.NONE) {
        playerInitialized = true
        PlayerController()
    }
    private val settings by lazy(LazyThreadSafetyMode.NONE) { VLCApplication.getSettings() }
    private val ctx by lazy(LazyThreadSafetyMode.NONE) { VLCApplication.getAppContext() }
    private val mediaList = MediaWrapperList()
    var currentIndex = -1
    private var nextIndex = -1
    private var prevIndex = -1
    private var previous = Stack<Int>()
    var repeating = Constants.REPEAT_NONE
    var shuffling = false
    var videoBackground = false
        private set
    var isBenchmark = false
    var isHardware = false
    private var parsed = false
    var savedTime = 0L
    private var random = Random(System.currentTimeMillis())
    private var newMedia = false
    @Volatile
    private var expanding = false

    fun hasMedia() = mediaList.size() != 0
    fun hasCurrentMedia() = isValidPosition(currentIndex)

    fun hasPlaylist() = mediaList.size() > 1

    fun canShuffle() = mediaList.size() > 2

    //:ace
    fun findPositionByFileIndex(fileIndex: Int) = mediaList.findPositionByFileIndex(fileIndex)

    fun updateCurrentIndex(value: Int) {
        currentIndex = value
        launch { determinePrevAndNextIndices() }
    }
    ///ace

    fun isValidPosition(position: Int) = position in 0 until mediaList.size()

    init {
        if (settings.getBoolean("audio_save_repeat", false)) repeating = settings.getInt(AUDIO_REPEAT_MODE_KEY, Constants.REPEAT_NONE)
    }

    /**
     * Loads a selection of files (a non-user-supplied collection of media)
     * into the primary or "currently playing" playlist.
     *
     * @param mediaPathList A list of locations to load
     * @param position The position to start playing at
     */
    @MainThread
    fun loadLocations(mediaPathList: List<String>, position: Int, start: Boolean=true) {
        val mediaList = ArrayList<MediaWrapper>()

        for (location in mediaPathList) {
            var mediaWrapper = medialibrary.getMedia(location)
            if (mediaWrapper === null) {
                if (!location.validateLocation()) {
                    Log.w(TAG, "Invalid location " + location)
                    service.showToast(service.resources.getString(R.string.invalid_location, location), Toast.LENGTH_SHORT)
                    continue
                }
                Log.v(TAG, "Creating on-the-fly Media object for " + location)
                mediaWrapper = MediaWrapper(Uri.parse(location))
            }
            mediaList.add(mediaWrapper)
        }
        load(mediaList, position, start)
    }

    fun load(list: List<MediaWrapper>,
             position: Int,
             start: Boolean=true,
             skipPlayer: Boolean=false,
             extras: Bundle?=null) {
        mediaList.removeEventListener(this)
        mediaList.clear(service.aceStreamManager)
        previous.clear()
        for (media in list) mediaList.add(media)
        if (!hasMedia()) {
            Log.w(TAG, "Warning: empty media list, nothing to play !")
            return
        }
        currentIndex = if (isValidPosition(position)) position else 0

        // Add handler after loading the list
        mediaList.addEventListener(this)
        if(start)
            playIndex(position, 0, skipPlayer, extras)
        onPlaylistLoaded()
    }

    fun getLastPlaylistSync(type: Int): ArrayList<MediaWrapper>? {
        var playlist: ArrayList<MediaWrapper>? = null
        runBlocking {
            playlist = getLastPlaylist(type)
        }
        return playlist
    }

    suspend fun getLastPlaylist(type: Int): ArrayList<MediaWrapper>? {
        val audio = type == Constants.PLAYLIST_TYPE_AUDIO
        val locationsJson = settings.getString(if (audio) "audio_list" else "media_list", null)
        if(locationsJson == null) {
            Logger.v(TAG, "loadLastPlaylist: empty playlist")
            return null
        }
        val locations = Gson().fromJson<List<String>>(locationsJson, object : TypeToken<List<String>>() {}.getType())
        if(locations == null || locations.isEmpty()) {
            Logger.v(TAG, "loadLastPlaylist: empty playlist")
            return null
        }
        return withContext(Dispatchers.Default) {
            try {
                locations.mapTo(ArrayList(locations.size)) { MediaWrapper.fromJson(it) }
            }
            catch(e: Throwable) {
                AceStream.toast("Failed to load last playlist")
                Logger.wtf(TAG, "Failed to load last playlist", e)
                ArrayList<MediaWrapper>()
            }
        }
    }

    @Volatile
    private var loadingLastPlaylist = false
    fun loadLastPlaylist(type: Int) {
        Logger.v(TAG, "loadLastPlaylist: type=${type} loading=${loadingLastPlaylist}")
        if (loadingLastPlaylist) return
        loadingLastPlaylist = true
        launch {
            val audio = type == Constants.PLAYLIST_TYPE_AUDIO
            val playList = getLastPlaylist(type)
            if (playList === null) {
                loadingLastPlaylist = false
                return@launch
            }
            Logger.v(TAG, "loadLastPlaylist: loaded ${playList.size} items")
            // load playlist
            shuffling = settings.getBoolean(if (audio) "audio_shuffling" else "media_shuffling", false)
            repeating = settings.getInt(if (audio) "audio_repeating" else "media_repeating", Constants.REPEAT_NONE)
            val position = settings.getInt(if (audio) "position_in_audio_list" else "position_in_media_list", 0)
            savedTime = settings.getLong(if (audio) "position_in_song" else "position_in_media", -1)
            if (!audio) {
                if (position < playList.size && settings.getBoolean(PreferencesActivity.VIDEO_PAUSED, false)) {
                    playList[position].addFlags(MediaWrapper.MEDIA_PAUSED)
                }
                val rate = settings.getFloat(PreferencesActivity.VIDEO_SPEED, player.getRate())
                if (rate != 1.0f) player.setRate(rate, false)
            }
            load(playList, position)
            loadingLastPlaylist = false
        }
    }

    private fun onPlaylistLoaded() {
        service.onPlaylistLoaded()
        launch {
            determinePrevAndNextIndices()
            launch { mediaList.updateWithMLMeta() }
        }
    }

    fun play() {
        if (hasMedia()) player.play()
    }

    fun pause() {
        if (player.pause()) savePosition()
    }

    @MainThread
    fun next() {
        val size = mediaList.size()
        previous.push(currentIndex)

        // Save current media meta
        if (hasCurrentMedia()) {
            Logger.v(TAG, "next: save current media meta: currentIndex=$currentIndex")
            saveMediaMeta()
        }

        currentIndex = nextIndex
        if (size == 0 || currentIndex < 0 || currentIndex >= size) {
            Log.w(TAG, "Warning: invalid next index, aborted !")
            //Close video player if started
            LocalBroadcastManager.getInstance(ctx).sendBroadcast(Intent(Constants.EXIT_PLAYER))
            stop()
            return
        }
        videoBackground = !player.isVideoPlaying() && player.canSwitchToVideo()
        playIndex(currentIndex)
    }

    fun stop(systemExit: Boolean = false, clearPlaylist: Boolean = true, saveMetadata: Boolean = true, keepRenderer: Boolean = false) {
        if (saveMetadata && hasCurrentMedia()) {
            savePosition()
            saveMediaMeta()
        }
        if(playerInitialized) {
            player.releaseMedia()
        }

        if (!keepRenderer && !RendererDelegate.globalRenderer) {
            RendererDelegate.restoreRenderer(false)
        }

        if(clearPlaylist) {
            mediaList.removeEventListener(this)
            previous.clear()
            currentIndex = -1
            mediaList.clear(service.aceStreamManager)
        }
        if(playerInitialized) {
            if (systemExit) player.release()
            else player.restart()
        }
        service.onPlaybackStopped()
    }

    @MainThread
    fun previous(force : Boolean) {
        Logger.v(TAG, "prev: hasPrevious=${hasPrevious()} currentIndex=$currentIndex force=$force seekable=${player.seekable} delay=${player.getTime() < PREVIOUS_LIMIT_DELAY}")
        if (hasPrevious() && currentIndex > 0 &&
                (force || !player.seekable || player.getTime() < PREVIOUS_LIMIT_DELAY)) {
            val size = mediaList.size()

            // Save current media meta
            if (hasCurrentMedia()) {
                Logger.v(TAG, "previous: save current media meta: currentIndex=$currentIndex")
                saveMediaMeta()
            }

            currentIndex = prevIndex
            if (previous.size > 0) previous.pop()
            if (size == 0 || prevIndex < 0 || currentIndex >= size) {
                Log.w(TAG, "Warning: invalid previous index, aborted !")
                player.stop()
                return
            }
            playIndex(currentIndex)
        } else player.setPosition(0F)
    }

    fun shuffle() {
        setShuffle(!shuffling)
    }

    fun setShuffle(shuffle: Boolean) {
        if(shuffling == shuffle) return
        if (shuffling) previous.clear()
        shuffling = shuffle
        savePosition()
        launch { determinePrevAndNextIndices() }
    }

    fun setRepeatType(repeatType: Int) {
        if (repeating == repeatType) return
        repeating = repeatType
        if (isAudioList() && settings.getBoolean("audio_save_repeat", false))
            settings.edit().putInt(AUDIO_REPEAT_MODE_KEY, repeating).apply()
        savePosition()
        launch { determinePrevAndNextIndices() }
    }

    fun playIndex(index: Int, flags: Int = 0, skipPlayer: Boolean=false, extras: Bundle?=null) {
        if (mediaList.size() == 0) {
            Log.w(TAG, "Warning: empty media list, nothing to play !")
            return
        }

        currentIndex = if (isValidPosition(index)) {
            index
        } else {
            Log.w(TAG, "Warning: index $index out of bounds")
            0
        }

        val mw = mediaList.getMedia(index) ?: return

        //:ace
        if(BuildConfig.DEBUG) {
            Log.v(TAG, "playIndex: index=$index p2p=${mw.isP2PItem} skipPlayer=${skipPlayer} uri=${mw.uri} playbackUri=${mw.playbackUri}")
        }

        // set uri to null for all p2p items except current
        mediaList.resetP2PItems(service.aceStreamManager, index)
        ///ace

        val isVideoPlaying = mw.type == MediaWrapper.TYPE_VIDEO && player.isVideoPlaying()

        if (!videoBackground && isVideoPlaying) mw.addFlags(MediaWrapper.MEDIA_VIDEO)
        if (videoBackground) mw.addFlags(MediaWrapper.MEDIA_FORCE_AUDIO)
        parsed = false
        player.switchToVideo = false
        if (TextUtils.equals(mw.uri.scheme, "content")) MediaUtils.retrieveMediaTitle(mw)

        service.updateRenderer("pm.playIndex")
        Logger.v(TAG, "playIndex: type=${mw.type} isVideoPlaying=$isVideoPlaying player.isVideoPlaying=${player.isVideoPlaying()} hasRenderer=${service.hasRenderer()}")

        launch {
            if (mw.hasFlag(MediaWrapper.MEDIA_FORCE_AUDIO) && player.getAudioTracksCount() == 0) {
                Logger.v(TAG, "playIndex: start 1")
                determinePrevAndNextIndices(true)
                if (currentIndex != nextIndex) next()
                else stop(false)
            } else if (mw.type != MediaWrapper.TYPE_VIDEO || isVideoPlaying || service.hasRenderer()
                    || mw.hasFlag(MediaWrapper.MEDIA_FORCE_AUDIO)) {

                if(AceStreamUtils.shouldStartAceStreamPlayer(mw)) {
                    // This happens when some external app has started engine session and passed
                    // playback url to this app.
                    startCurrentPlaylistInAceStreamPlayer(extras)
                }
                else if(mw.isP2PItem && mw.playbackUri === null) {
                    Logger.v(TAG, "playIndex:internal: start p2p session: remoteDevice=${service.currentRemoteDevice}")

                    service.getEngine(object : PlaybackService.EngineStateCallback {
                        override fun onEngineConnected(manager: AceStreamManager, engineApi: EngineApi) {
                            Logger.vv(TAG, "playIndex: engine connected")

                            if(manager.engineSession != null && service.currentRemoteDevice == null) {
                                if(mw.mediaFile != null && mw.mediaFile.equals(manager.engineSession?.playbackData?.mediaFile)) {
                                    mw.playbackUri = Uri.parse(manager.engineSession?.playbackUrl)
                                    Log.v(TAG, "playIndex: restore uri for active session: uri=${mw.uri}")
                                    playIndex(index, flags)
                                    return
                                }
                            }

                            MainScope().launch {
                                determinePrevAndNextIndices()

                                LocalBroadcastManager.getInstance(service).sendBroadcast(Intent(Constants.ACTION_P2P_STARTING))

                                val nextMedia = getMedia(nextIndex)

                                var nextFileIndex: Int?
                                if(nextMedia !== null && nextMedia.isP2PItem) {
                                    if (nextMedia.mediaFile !== null) {
                                        nextFileIndex = nextMedia.mediaFile.index
                                    }
                                    else {
                                        try {
                                            nextFileIndex = Integer.parseInt(MiscUtils.getQueryParameter(nextMedia.uri, "index"))
                                        } catch (e: NumberFormatException) {
                                            nextFileIndex = null
                                        }
                                    }
                                }
                                else {
                                    nextFileIndex = null
                                }

                                val nextFileIndexes = if (nextFileIndex !== null) intArrayOf(nextFileIndex) else null

                                if(service.currentVlcRenderer != null && mw.isP2PItem) {
                                    // switch to acestream remote device
                                    val device = manager.findRemoteDeviceByIp(
                                            MiscUtils.getRendererIp(service.currentVlcRenderer.sout),
                                            SelectedPlayer.CONNECTABLE_DEVICE)
                                    Logger.v(TAG, "playIndex: switch to CSDK device: current=${service.currentVlcRenderer} new=${device}")
                                    if(device === null) {
                                        service.showToast("Internal error", Toast.LENGTH_SHORT)
                                        return@launch
                                    }
                                    val renderer = RendererItemWrapper(device)
                                    service.setRenderer(renderer, "switch-to-csdk")
                                    RendererDelegate.selectRenderer(false, renderer)
                                }

                                val fromStart = (true == extras?.getBoolean("playFromStart", false))

                                if(service.currentRemoteDevice != null) {
                                    Logger.v(TAG, "playIndex: start remote acestream device: fromStart=$fromStart")
                                    player.stop()
                                    playbackPostInit(mw)
                                    if(!skipPlayer) {
                                        VideoPlayerActivity.startRemoteDevice(ctx, mw,
                                                service.currentRemoteDevice,
                                                fromStart)
                                    }
                                }
                                else {
                                    if(AceStreamUtils.shouldStartAceStreamPlayer(mw)) {
                                        startCurrentPlaylistInAceStreamPlayer(extras)
                                    }
                                    else {
                                        if(!MobileNetworksDialogActivity.checkMobileNetworkConnection(ctx, manager, mw)) {
                                            Log.w(TAG, "ask about mobile networks")
                                            return@launch
                                        }

                                        // Start local engine session
                                        Logger.v(TAG, "playIndex: start local p2p session")
                                        mw.startP2P(
                                                manager,
                                                object : P2PItemStartListener {
                                                    override fun onSessionStarted(session: EngineSession) {
                                                        Log.v(TAG, "playIndex: p2p session started")
                                                        val intent = Intent(Constants.ACTION_P2P_SESSION_STARTED)
                                                        LocalBroadcastManager.getInstance(service).sendBroadcast(intent)
                                                        // Start actual playback when we got playback uri
                                                        playIndex(index, flags)
                                                    }

                                                    override fun onPrebufferingDone() {
                                                        Log.v(TAG, "playIndex: p2p prebuffering done")
                                                        LocalBroadcastManager.getInstance(service).sendBroadcast(
                                                                VideoPlayerActivity.getIntent(Constants.ACTION_P2P_STARTED,
                                                                        mw, false, currentIndex))
                                                    }

                                                    override fun onError(err: String) {
                                                        Log.e(TAG, "Failed to start engine session: $err")
                                                        val intent = Intent(Constants.ACTION_P2P_FAILED);
                                                        intent.putExtra(org.acestream.sdk.Constants.EXTRA_ERROR_MESSAGE, err)
                                                        LocalBroadcastManager.getInstance(service).sendBroadcast(intent)
                                                    }
                                                },
                                                nextFileIndexes)
                                    }
                                }
                            }
                        }

                    })
                    return@launch
                }

                if(service.currentRemoteDevice != null && !service.currentRemoteDevice.isAceCast && !mw.isP2PItem) {
                    // switch to VLC renderer
                    val device = RendererDelegate.findVlcRendererByIp(service.currentRemoteDevice.ipAddress)
                    Logger.v(TAG, "playIndex: switch to VLC renderer: current=${service.currentRemoteDevice} new=${device}")
                    if(device === null) {
                        service.showToast("Internal error", Toast.LENGTH_SHORT)
                        return@launch
                    }
                    val renderer = RendererItemWrapper(device)
                    service.setRenderer(renderer, "switch-to-vlc")
                    RendererDelegate.selectRenderer(false, renderer)
                }

                Logger.v(TAG, "playIndex:internal: start item: playbackUri=${mw.playbackUri}")
                val media = Media(VLCInstance.get(), FileUtils.getUri(mw.playbackUri))
                VLCOptions.setMediaOptions(media, ctx, flags or mw.flags, mw.mime)
                /* keeping only video during benchmark */
                if (isBenchmark) {
                    media.addOption(":no-audio")
                    media.addOption(":no-spu")
                    if (isHardware) {
                        media.addOption(":codec=mediacodec_ndk,mediacodec_jni,none")
                        isHardware = false
                    }
                }
                mw.slaves?.let {
                    for (slave in it) media.addSlave(slave)
                    launch { MediaDatabase.getInstance().saveSlaves(mw) }
                }
                media.setEventListener(this@PlaylistManager)
                player.setSlaves(mw)
                if(mw.userAgent !== null) {
                    VLCInstance.setUserAgent(mw.userAgent)
                }
                player.startPlayback(media, mediaplayerEventListener)
                media.release()

                playbackPostInit(mw)

            } else {
                if(AceStreamUtils.shouldStartAceStreamPlayer(mw)) {
                    startCurrentPlaylistInAceStreamPlayer(extras)
                }
                else {
                    // Start VideoPlayer for first video, it will trigger playIndex when ready.
                    Logger.v(TAG, "playIndex: start video player")
                    player.stop()
                    VideoPlayerActivity.startOpened(ctx, mw.uri, currentIndex)
                }
            }
        }
    }

    private suspend fun playbackPostInit(mw: MediaWrapper) {
        if (savedTime <= 0L && mw.time >= 0L && mw.isPodcast) savedTime = mw.time
        determinePrevAndNextIndices()
        service.onNewPlayback(mw)
        increasePlayCount(mw)
        saveCurrentMedia()
        saveMediaList()
        newMedia = true
    }

    fun increasePlayCount(mw: MediaWrapper, mediaId: Long=0L) {
        if (settings.getBoolean(PreferencesFragment.PLAYBACK_HISTORY, true)) launch {
            var id = if (mediaId == 0L) mw.id else mediaId
            if (id == 0L) {
                var internalMedia = medialibrary.findMedia(mw)
                if (internalMedia != null && internalMedia.id != 0L) {
                    id = internalMedia.id
                }
                else {
                    internalMedia = medialibrary.addMedia(mw)
                    if (internalMedia != null)
                        id = internalMedia.id
                }
            }
            medialibrary.increasePlayCount(id)
        }
    }

    fun switchStream(streamIndex: Int) {
        val mw = getCurrentMedia()
        if(mw === null) return

        val pm = service.aceStreamManager
        if(pm === null) throw IllegalStateException("missing playback manager")

        launch {
            LocalBroadcastManager.getInstance(service).sendBroadcast(Intent(Constants.ACTION_P2P_STARTING))

            // Save time to resume playback
            mw.time = player.getTime()
            savedTime = mw.time

            val nextMedia = getMedia(nextIndex)
            val nextFileIndexes = if(nextMedia === null) null else intArrayOf(nextMedia.mediaFile.index)

            // start new session
            mw.startP2P(
                    pm,
                    object : P2PItemStartListener {
                        override fun onSessionStarted(session: EngineSession) {
                            // Do nothing.
                            // Current we inform player about session start to load ads,
                            // but no need to show ads when switching stream.
                        }

                        override fun onPrebufferingDone() {
                            LocalBroadcastManager.getInstance(service).sendBroadcast(
                                    VideoPlayerActivity.getIntent(Constants.ACTION_P2P_STARTED,
                                            mw, false, currentIndex))
                        }

                        override fun onError(err: String) {
                            Log.e(TAG, "Failed to start engine session: $err")
                            val intent = Intent(Constants.ACTION_P2P_FAILED);
                            intent.putExtra(org.acestream.sdk.Constants.EXTRA_ERROR_MESSAGE, err)
                            LocalBroadcastManager.getInstance(service).sendBroadcast(intent)
                        }
                    },
                    nextFileIndexes,
                    streamIndex)
        }
    }

    fun onServiceDestroyed() {
        mediaList.resetP2PItems(service.aceStreamManager, -1)
        if(playerInitialized) {
            player.release()
        }
    }

    @MainThread
    fun switchToVideo(): Boolean {
        val media = getCurrentMedia()
        if (media === null || media.hasFlag(MediaWrapper.MEDIA_FORCE_AUDIO) || !player.canSwitchToVideo())
            return false
        val hasRenderer = player.hasRenderer
        videoBackground = false
        if (player.isVideoPlaying() && !hasRenderer) {//Player is already running, just send it an intent
            player.setVideoTrackEnabled(true)
            if(!media.isP2PItem) {
                Logger.v(TAG, "switchToVideo: send PLAY_FROM_SERVICE intent")
                LocalBroadcastManager.getInstance(service).sendBroadcast(
                        VideoPlayerActivity.getIntent(Constants.PLAY_FROM_SERVICE,
                                media, false, currentIndex))
            }
        } else if (!player.switchToVideo) { //Start the video player
            VideoPlayerActivity.startOpened(VLCApplication.getAppContext(), media.uri, currentIndex)
            if (!hasRenderer) player.switchToVideo = true
        }
        return true
    }

    fun setVideoTrackEnabled(enabled: Boolean) {
        if (!hasMedia() || !player.isPlaying()) return
        if (enabled) getCurrentMedia()?.addFlags(MediaWrapper.MEDIA_VIDEO)
        else getCurrentMedia()?.removeFlags(MediaWrapper.MEDIA_VIDEO)
        player.setVideoTrackEnabled(enabled)
    }

    fun hasPrevious() = prevIndex != -1

    fun hasNext() = nextIndex != -1

    override fun onItemAdded(index: Int, mrl: String?) {
        if (BuildConfig.DEBUG) Log.i(TAG, "CustomMediaListItemAdded")
        if (currentIndex >= index && !expanding) ++currentIndex
        launch {
            determinePrevAndNextIndices()
            executeUpdate()
            saveMediaList()
        }
    }

    override fun onItemRemoved(index: Int, mrl: String?) {
        if (BuildConfig.DEBUG) Log.i(TAG, "CustomMediaListItemDeleted")
        val currentRemoved = currentIndex == index
        if (currentIndex >= index && !expanding) --currentIndex
        launch {
            determinePrevAndNextIndices()
            if (currentRemoved && !expanding) {
                when {
                    nextIndex != -1 -> next()
                    currentIndex != -1 -> playIndex(currentIndex, 0)
                    else -> stop()
                }
            }
            executeUpdate()
            saveMediaList()
        }
    }

    private fun executeUpdate() {
        service.executeUpdate()
    }

    fun saveMediaMeta() {
        val media = medialibrary.findMedia(getCurrentMedia())
        if (media === null || media.id == 0L) return

        Logger.v(TAG, "saveMediaMeta: uri=${media.metaUri}")

        val canSwitchToVideo = player.canSwitchToVideo()
        if (media.type == MediaWrapper.TYPE_VIDEO || canSwitchToVideo || media.isPodcast) {
            //Save progress
            val time = service.time
            val length = service.length
            var progress = 0.0f
            if(time > 0 && length > 0)
                progress = time / length.toFloat()

            Logger.v(TAG, "saveMediaMeta: p2p=${media.isP2PItem} length=$length time=$time progress=$progress uri=${media.uri}")

            if (progress > 0.95f || length - time < 10000) {
                //increase seen counter if more than 95% of the media have been seen
                //and reset progress to 0
                media.setLongMeta(MediaWrapper.META_SEEN, ++media.seen)
                progress = 0f
            }
            media.time = if (progress == 0f) 0L else time
            media.setLongMeta(MediaWrapper.META_PROGRESS, media.time)
            //:ace
            if(media.isP2PItem) {
                media.setLongMeta(MediaWrapper.META_DURATION, length)
            }
            ///ace
        }
        if (canSwitchToVideo) {
            //Save audio delay
            if (settings.getBoolean("save_individual_audio_delay", false))
                media.setLongMeta(MediaWrapper.META_AUDIODELAY, player.getAudioDelay())
            media.setLongMeta(MediaWrapper.META_SUBTITLE_DELAY, player.getSpuDelay())
            media.setLongMeta(MediaWrapper.META_SUBTITLE_TRACK, player.getSpuTrack().toLong())
        }
    }

    private fun loadMediaMeta(media: MediaWrapper) {
        if (media.id == 0L) return
        if (player.canSwitchToVideo()) {
            if (settings.getBoolean("save_individual_audio_delay", false))
                player.setAudioDelay(media.getMetaLong(MediaWrapper.META_AUDIODELAY))
            player.setSpuTrack(media.getMetaLong(MediaWrapper.META_SUBTITLE_TRACK).toInt())
            player.setSpuDelay(media.getMetaLong(MediaWrapper.META_SUBTITLE_DELAY))
        }
    }

    @Synchronized
    private fun saveCurrentMedia() {
        settings.edit()
                .putString(if (isAudioList()) "current_song" else "current_media", mediaList.getMRL(Math.max(currentIndex, 0)))
                .apply()
    }

    @Synchronized
    fun saveMediaList(list: MediaWrapperList?=null) {
        Log.v(TAG, "saveMediaList")

        val listToSave: MediaWrapperList
        val isAudio: Boolean
        if(list === null) {
            listToSave = mediaList
            if (getCurrentMedia() === null) return
            isAudio = isAudioList()
        }
        else {
            listToSave = list
            isAudio = list.isAudioList
        }

        val locations = ArrayList<String>()
        for (mw in listToSave.all) {
            if(mw.uri !== null && mw.uri.toString().startsWith("acestream:?data=content%3A%2F%2F")) {
                // Skip p2p items with "content://" scheme
                continue
            }
            locations.add(mw.toJson())
        }
        val data = Gson().toJson(locations)
        settings.edit()
                .putString(if (!isAudio) "media_list" else "audio_list", data)
                .apply()
    }

    fun hasLastPlaylist(): Boolean {
        return settings.contains("media_list")
    }

    override fun onItemMoved(indexBefore: Int, indexAfter: Int, mrl: String?) {
        if (BuildConfig.DEBUG) Log.i(TAG, "CustomMediaListItemMoved")
        if (currentIndex == indexBefore) {
            currentIndex = indexAfter
            if (indexAfter > indexBefore)
                --currentIndex
        } else if (currentIndex in indexAfter..(indexBefore - 1))
            ++currentIndex
        else if (currentIndex in (indexBefore + 1)..(indexAfter - 1))
            --currentIndex

        // If we are in random mode, we completely reset the stored previous track
        // as their indices changed.
        previous.clear()
        launch {
            determinePrevAndNextIndices()
            executeUpdate()
            saveMediaList()
        }
    }

    private suspend fun determinePrevAndNextIndices(expand: Boolean = false) {
        val media = getCurrentMedia()
        if (expand && media !== null) {
            expanding = true
            nextIndex = expand(media.type == MediaWrapper.TYPE_STREAM)
            expanding = false
        } else {
            nextIndex = -1
        }
        prevIndex = -1

        if (nextIndex == -1) {
            // No subitems; play the next item.
            val size = mediaList.size()
            shuffling = shuffling and (size > 2)

            // Repeating once doesn't change the index
            if (repeating == Constants.REPEAT_ONE) {
                nextIndex = currentIndex
                prevIndex = nextIndex
            } else {
                if (shuffling) {
                    if (!previous.isEmpty()) {
                        prevIndex = previous.peek()
                        while (!isValidPosition(prevIndex)) {
                            previous.removeAt(previous.size - 1)
                            if (previous.isEmpty()) {
                                prevIndex = -1
                                break
                            }
                            prevIndex = previous.peek()
                        }
                    }
                    // If we've played all songs already in shuffle, then either
                    // reshuffle or stop (depending on RepeatType).
                    if (previous.size + 1 == size) {
                        if (repeating == Constants.REPEAT_NONE) {
                            nextIndex = -1
                            return
                        } else {
                            previous.clear()
                            random = Random(System.currentTimeMillis())
                        }
                    }
                    random = Random(System.currentTimeMillis())
                    // Find a new index not in previous.
                    do {
                        nextIndex = random.nextInt(size)
                    } while (nextIndex == currentIndex || previous.contains(nextIndex))

                } else {
                    // normal playback
                    if (currentIndex > 0)
                        prevIndex = currentIndex - 1
                    nextIndex = when {
                        currentIndex + 1 < size -> currentIndex + 1
                        repeating == Constants.REPEAT_NONE -> -1
                        else -> 0
                    }
                }
            }
        }
    }

    /**
     * Expand the current media.
     * @return the index of the media was expanded, and -1 if no media was expanded
     */
    @MainThread
    private suspend fun expand(updateHistory: Boolean): Int {
        val index = currentIndex
        val ml = player.expand()
        var ret = -1

        if (ml != null && ml.count > 0) {
            val mrl = if (updateHistory) getCurrentMedia()?.location else null
            mediaList.remove(index)
            for (i in ml.count - 1 downTo 0) {
                val child = ml.getMediaAt(i)
                child.parse()
                mediaList.insert(index, MediaWrapper(child))
                child.release()
            }
            if (mrl !== null && ml.count == 1) medialibrary.addToHistory(mrl, getCurrentMedia()!!.title)
            ret = index
        }
        ml?.release()
        return ret
    }

    fun getCurrentMedia() = mediaList.getMedia(currentIndex)

    fun getPrevMedia() = if (isValidPosition(prevIndex)) mediaList.getMedia(prevIndex) else null

    fun getNextMedia() = if (isValidPosition(nextIndex)) mediaList.getMedia(nextIndex) else null

    fun getMedia(position: Int) = mediaList.getMedia(position)

    private fun seekToResume(media: MediaWrapper) {
        var mw = media
        Logger.v(TAG, "seekToResume: savedTime=$savedTime mw_time=${mw.time} mw_length=${mw.length} player_length=${player.length}")
        if (savedTime > 0L) {
            if (savedTime < 0.95 * player.length) {
                Logger.v(TAG, "seekToResume: seek to $savedTime")
                player.seek(savedTime)
            }
            savedTime = 0L
        } else {
            val length = player.length
            if (mw.length <= 0L && length > 0L) {
                val mediaFromML = medialibrary.findMedia(mw)
                if (mediaFromML.id != 0L) {
                    mw.time = mediaFromML.getMetaLong(MediaWrapper.META_PROGRESS)
                    if (mw.time > 0L) {
                        Logger.v(TAG, "seekToResume: seek to ${mw.time}")
                        player.seek(mw.time)
                    }
                }
            }
        }
    }

    @Synchronized
    private fun savePosition(reset: Boolean = false) {
        if (!hasMedia()) return
        val editor = settings.edit()
        val audio = isAudioList()
        editor.putBoolean(if (audio) "audio_shuffling" else "media_shuffling", shuffling)
        editor.putInt(if (audio) "audio_repeating" else "media_repeating", repeating)
        editor.putInt(if (audio) "position_in_audio_list" else "position_in_media_list", if (reset) 0 else currentIndex)
        editor.putLong(if (audio) "position_in_song" else "position_in_media", if (reset) 0L else player.getTime())
        if (!audio) {
            editor.putBoolean(PreferencesActivity.VIDEO_PAUSED, !player.isPlaying())
            editor.putFloat(PreferencesActivity.VIDEO_SPEED, player.getRate())
        }
        editor.apply()
    }

    /**
     * Append to the current existing playlist
     */
    fun append(list: List<MediaWrapper>) {
        if (!hasCurrentMedia()) {
            load(list, 0)
            return
        }
        for (media in list) mediaList.add(media)
    }

    /**
     * Insert into the current existing playlist
     */

    fun insertNext(list: List<MediaWrapper>) {
        if (!hasCurrentMedia()) {
            load(list, 0)
            return
        }

        val startIndex = currentIndex + 1

        for ((index, mw) in list.withIndex()) mediaList.insert(startIndex + index, mw)
    }

    /**
     * Move an item inside the playlist.
     */
    fun moveItem(positionStart: Int, positionEnd: Int) {
        mediaList.move(positionStart, positionEnd)
        launch { determinePrevAndNextIndices() }
    }

    fun insertItem(position: Int, mw: MediaWrapper) {
        mediaList.insert(position, mw)
        launch { determinePrevAndNextIndices() }
    }


    fun remove(position: Int) {
        mediaList.remove(position)
        launch { determinePrevAndNextIndices() }
    }

    fun removeLocation(location: String) {
        mediaList.remove(location)
        launch { determinePrevAndNextIndices() }
    }

    fun getMediaListSize()= mediaList.size()

    fun getMediaList(): MutableList<MediaWrapper> = mediaList.all

    override fun onEvent(event: Media.Event) {
        var update = true
        when (event.type) {
            Media.Event.MetaChanged -> {
                /* Update Meta if file is already parsed */
                if (parsed && player.updateCurrentMeta(event.metaId, getCurrentMedia())) service.executeUpdate()
                if (BuildConfig.DEBUG) Log.i(MediaDatabase.TAG, "Media.Event.MetaChanged: " + event.metaId)
            }
            Media.Event.ParsedChanged -> {
                if (BuildConfig.DEBUG) Log.i(MediaDatabase.TAG, "Media.Event.ParsedChanged")
                player.updateCurrentMeta(-1, getCurrentMedia())
                parsed = true
            }
            else -> update = false
        }
        if (update) {
            service.onMediaEvent(event)
            if (parsed) service.showNotification()
        }
    }

    private val mediaplayerEventListener = MediaPlayer.EventListener { event ->
        when (event.type) {
            MediaPlayer.Event.Playing -> {
                medialibrary.pauseBackgroundOperations()
                videoBackground = false
                val mw = medialibrary.findMedia(getCurrentMedia())
                if (newMedia) {
                    seekToResume(mw)
                    loadMediaMeta(mw)
                    if (mw.type == MediaWrapper.TYPE_STREAM) medialibrary.addToHistory(mw.location, mw.title)
                    saveMediaList()
                    savePosition(true)
                    saveCurrentMedia()
                    newMedia = false
                }
            }
            MediaPlayer.Event.Paused -> medialibrary.resumeBackgroundOperations()
            MediaPlayer.Event.EndReached -> {
                saveMediaMeta()
                if (isBenchmark) player.setPreviousStats()
                launch {
                    determinePrevAndNextIndices(true)
                    if (nextIndex == -1) savePosition(true)
                    next()
                }
            }
            MediaPlayer.Event.EncounteredError -> {
                service.showToast(service.getString(
                            R.string.invalid_location,
                            getCurrentMedia()?.getLocation() ?: ""), Toast.LENGTH_SHORT)
                next()
            }
        }
        service.onMediaPlayerEvent(event)
    }

    fun isAudioList() = !player.canSwitchToVideo() && mediaList.isAudioList

    //:ace
    fun startCurrentPlaylistInAceStreamPlayer(extras: Bundle?=null) {
        // Start playback in AcePlayer (in separate process)
        val intent: Intent
        var selectedPlayer: SelectedPlayer?
        val context = VLCApplication.getAppContext()
        val currentMedia = getCurrentMedia()

        if(true == extras?.containsKey("player")) {
            selectedPlayer = SelectedPlayer.fromJson(extras.getString("player"))
        }
        else {
            selectedPlayer = service.aceStreamManager?.selectedPlayer
        }

        Logger.v(TAG, "start current playlist in acestream player: currentIndex=$currentIndex selectedPlayer=$selectedPlayer")

        if(currentMedia == null && mediaList.size() == 0) {
            Log.e(TAG, "startCurrentPlaylistInAceStreamPlayer: empty playlist")
            return
        }

        // Use AceStream player as default
        if(selectedPlayer == null || selectedPlayer.isOurPlayer) {
            intent = AceStreamUtils.getPlayerIntent()
            intent.putExtra(AceStreamPlayer.EXTRA_PLAYLIST, mediaList.toAceStreamPlaylist())
            intent.putExtra(AceStreamPlayer.EXTRA_PLAYLIST_POSITION, currentIndex)

            if(true == extras?.containsKey("askResume")) {
                intent.putExtra(AceStreamPlayer.EXTRA_ASK_RESUME,
                        extras.getBoolean("askResume"))
            }

            if(currentMedia !== null) {
                currentMedia.updateFromMediaLibrary()

                var time = -1L
                if(extras != null) {
                    time = extras.getLong("seekOnStart", -1)
                }
                if(time == -1L) {
                    time = currentMedia.getMetaLong(MediaWrapper.META_PROGRESS)
                }
                if(time > 0L) {
                    intent.putExtra(AceStreamPlayer.EXTRA_PLAY_FROM_TIME, time)
                }

                val remoteClientId = extras?.getString("remoteClientId")
                if(remoteClientId != null) {
                    intent.putExtra(AceStreamPlayer.EXTRA_REMOTE_CLIENT_ID, remoteClientId)
                }

                if(time == 0L || true == extras?.getBoolean("playFromStart", false)) {
                    intent.putExtra(AceStreamPlayer.EXTRA_PLAY_FROM_START, true)
                }
            }
        }
        else {
            // use current media of first media in playlist
            val media = currentMedia ?: mediaList.getMedia(0) ?: return
            intent = AceStream.makeIntentFromUri(
                    context,
                    media.uri,
                    selectedPlayer,
                    false,
                    false)
        }

        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
        // Save playlist before clearing
        saveMediaList()
        // Clear playlist because it's now handled in AceStream player
        stop(false, true, false)
        // Ensure that VLC video player is closed
        VideoPlayerActivity.closePlayer()
    }

    fun fireMediaPlayerEvent(event: MediaPlayer.Event) {
        if(hasCurrentMedia()) {
            mediaplayerEventListener.onEvent(event)
        }
    }
    ///ace
}