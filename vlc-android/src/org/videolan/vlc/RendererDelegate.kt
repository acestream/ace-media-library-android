/*****************************************************************************
 * RendererDelegate.java
 *
 * Copyright © 2017 VLC authors and VideoLAN
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
 */
package org.videolan.vlc

import android.text.TextUtils
import kotlinx.coroutines.*
import org.acestream.sdk.RemoteDevice
import org.videolan.libvlc.RendererDiscoverer
import org.videolan.libvlc.RendererItem
import org.acestream.sdk.utils.Logger
import org.acestream.sdk.utils.MiscUtils
import org.videolan.vlc.util.RendererItemWrapper
import org.videolan.vlc.util.VLCInstance
import org.videolan.vlc.util.retry
import java.util.*

@ObsoleteCoroutinesApi
@ExperimentalCoroutinesApi
object RendererDelegate : RendererDiscoverer.EventListener, ExternalMonitor.NetworkObserver, CoroutineScope {

    override val coroutineContext = Dispatchers.Main.immediate

    private val TAG = "VLC/RendererDelegate"
    private val mDiscoverers = ArrayList<RendererDiscoverer>()
    val renderers = ArrayList<RendererItemWrapper>()
    private val mListeners = LinkedList<RendererListener>()
    private val mPlayers = LinkedList<RendererPlayer>()
    private val mInstanceListeners = LinkedList<InstanceListener>()

    @Volatile private var started = false
    var selectedRenderer: RendererItemWrapper? = null
        private set

    private var globalSelectedRenderer: RendererItemWrapper? = null
    var globalRenderer: Boolean = false
        private set

    init {
        ExternalMonitor.subscribeNetworkCb(this)
    }

    interface RendererListener {
        fun onRenderersChanged(empty: Boolean)
    }

    interface RendererPlayer {
        fun onRendererChanged(fromUser: Boolean, renderer: RendererItemWrapper?)
    }

    interface InstanceListener {
        fun onReloaded()
    }

    suspend fun start() {
        if (started) return
        started = true
        val libVlc = async { VLCInstance.get() }.await()
        for (discoverer in RendererDiscoverer.list(libVlc)) {
            val rd = RendererDiscoverer(libVlc, discoverer.name)
            mDiscoverers.add(rd)
            rd.setEventListener(this@RendererDelegate)
            retry(5, 1000L) { if (!rd.isReleased) rd.start() else false }
        }
    }

    suspend fun stop() {
        if (!started) return
        started = false
        for (discoverer in mDiscoverers) discoverer.stop()
        clear()
        onRenderersChanged()
        for (player in mPlayers) player.onRendererChanged(false, null)
    }

    private fun clear() {
        mDiscoverers.clear()
        for (renderer in renderers) {
            renderer.vlcRenderer?.release()
        }
        renderers.clear()
    }

    fun resume() {
        launch { start() }
    }

    fun shutdown() {
        launch { stop() }
    }

    override fun onNetworkConnectionChanged(connected: Boolean) {
        launch {
            if (connected) {
                start()
                // Notify that instance was reloaded.
                // This allows PlaybackService to repopulate instance with acecast devices.
                onReloaded()
            }
            else {
                stop()
            }
        }
    }

    private fun findDevice(device: RemoteDevice): Int {
        renderers.forEachIndexed { index, renderer ->
            if(renderer.aceStreamRenderer == device) {
                return index
            }
        }
        return -1
    }

    fun findVlcRendererByIp(ip: String): RendererItem? {
        renderers.forEach { renderer ->
            if(renderer.vlcRenderer !== null) {
                if (TextUtils.equals(MiscUtils.getRendererIp(renderer.vlcRenderer.sout), ip)) {
                    return renderer.vlcRenderer
                }
            }
        }

        return null
    }

    fun addDevice(device: RemoteDevice) {
        if(findDevice(device) == -1) {
            renderers.add(RendererItemWrapper(device))
            onRenderersChanged()
        }
    }

    fun removeDevice(device: RemoteDevice) {
        val toRemove = findDevice(device)
        if(toRemove != -1) {
            renderers.removeAt(toRemove)
            onRenderersChanged()
        }

        if(device.equals(selectedRenderer?.aceStreamRenderer)) {
            selectRenderer(false, null)
        }
    }

    private fun findRenderer(item: RendererItem): Int {
        renderers.forEachIndexed { index, renderer ->
            if(renderer.vlcRenderer == item) {
                return index
            }
        }
        return -1
    }

    private fun removeRenderer(item: RendererItem) {
        val toRemove = findRenderer(item)
        if(toRemove != -1) {
            renderers.removeAt(toRemove)
        }
    }

    override fun onEvent(event: RendererDiscoverer.Event?) {
        when (event?.type) {
            RendererDiscoverer.Event.ItemAdded -> { renderers.add(RendererItemWrapper(event.item)) }
            RendererDiscoverer.Event.ItemDeleted -> { removeRenderer(event.item); event.item.release() }
            else -> return
        }
        onRenderersChanged()
    }

    fun addListener(listener: RendererListener) = mListeners.add(listener)

    fun removeListener(listener: RendererListener) = mListeners.remove(listener)

    private fun onRenderersChanged() {
        for (listener in mListeners) listener.onRenderersChanged(renderers.isEmpty())
    }

    private fun onReloaded() {
        for (listener in mInstanceListeners) listener.onReloaded()
    }

    fun selectRenderer(fromUser: Boolean, item: RendererItemWrapper?, global: Boolean=true) {
        selectedRenderer = item
        globalRenderer = global
        if (global) {
            globalSelectedRenderer = selectedRenderer
            Logger.v(TAG, "selectRenderer:global: fromUser=${fromUser} curr=${selectedRenderer}")
        }
        else {
            Logger.v(TAG, "selectRenderer:temp: fromUser=${fromUser} curr=${selectedRenderer} global=${globalSelectedRenderer}")
        }
        for (player in mPlayers) {
            player.onRendererChanged(fromUser, item)
        }
    }

    fun restoreRenderer(fromUser: Boolean) {
        Logger.v(TAG, "restoreRenderer: fromUser=${fromUser} curr=${selectedRenderer} new=${globalSelectedRenderer}")
        val changed = (selectedRenderer !== globalSelectedRenderer)
        selectedRenderer = globalSelectedRenderer
        if(changed) {
            for (player in mPlayers) {
                player.onRendererChanged(fromUser, selectedRenderer)
            }
        }
    }

    fun hasRenderer() = (selectedRenderer !== null)

    fun addPlayerListener(listener: RendererPlayer) = mPlayers.add(listener)

    fun removePlayerListener(listener: RendererPlayer) = mPlayers.remove(listener)

    fun addInstanceListener(listener: InstanceListener) = mInstanceListeners.add(listener)

    fun removeInstanceListener(listener: InstanceListener) = mInstanceListeners.remove(listener)
}
