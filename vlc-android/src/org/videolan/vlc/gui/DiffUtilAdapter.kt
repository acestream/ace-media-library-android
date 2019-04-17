package org.videolan.vlc.gui

import androidx.annotation.MainThread
import androidx.annotation.WorkerThread
import androidx.recyclerview.widget.DiffUtil
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.actor
import java.util.*

@ObsoleteCoroutinesApi
@ExperimentalCoroutinesApi
abstract class DiffUtilAdapter<D, VH : androidx.recyclerview.widget.RecyclerView.ViewHolder> : androidx.recyclerview.widget.RecyclerView.Adapter<VH>(), CoroutineScope {
    override val coroutineContext = Dispatchers.Main.immediate

    protected var dataset: List<D> = listOf()
    private set
    @Volatile private var last = dataset
    private val diffCallback by lazy(LazyThreadSafetyMode.NONE) { createCB() }
    private val updateActor = actor<List<D>>(newSingleThreadContext("vlc-updater"), capacity = Channel.CONFLATED) {
        for (list in channel) internalUpdate(list)
    }
    protected abstract fun onUpdateFinished()

    @MainThread
    fun update (list: List<D>) {
        last = list
        updateActor.offer(list)
    }

    @WorkerThread
    private suspend fun internalUpdate(list: List<D>) {
        val finalList = prepareList(list)
        val result = DiffUtil.calculateDiff(diffCallback.apply { update(dataset, finalList) }, detectMoves())
        withContext(Dispatchers.Main) {
            dataset = finalList
            result.dispatchUpdatesTo(this@DiffUtilAdapter)
            onUpdateFinished()
        }
    }

    protected open fun prepareList(list: List<D>) : List<D> = ArrayList(list)

    fun peekLast() = last

    fun hasPendingUpdates() = updateActor.isFull

    protected open fun detectMoves() = false

    protected open fun createCB() = DiffCallback<D>()

    open class DiffCallback<D> : DiffUtil.Callback() {
        lateinit var oldList: List<D>
        lateinit var newList: List<D>

        fun update(oldList: List<D>, newList: List<D>) {
            this.oldList = oldList
            this.newList = newList
        }

        override fun getOldListSize() = oldList.size

        override fun getNewListSize() = newList.size

        override fun areContentsTheSame(oldItemPosition : Int, newItemPosition : Int) = true

        override fun areItemsTheSame(oldItemPosition : Int, newItemPosition : Int) = oldList[oldItemPosition] == newList[newItemPosition]
    }
}