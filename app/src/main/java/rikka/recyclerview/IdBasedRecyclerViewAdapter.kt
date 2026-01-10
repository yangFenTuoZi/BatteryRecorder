package rikka.recyclerview

import androidx.recyclerview.widget.RecyclerView

open class IdBasedRecyclerViewAdapter : BaseRecyclerViewAdapter<IndexCreatorPool> {
    val ids = ArrayList<Long>()

    constructor() : super()

    constructor(data: MutableList<Any?>) : super(data)

    override fun getItemId(position: Int): Long {
        return if (position >= ids.size || position < 0) RecyclerView.NO_ID
        else ids[position]
    }

    fun clear() {
        creatorPool.clear()
        items.clear()
        this.ids.clear()
    }

    fun <T> addItemAt(index: Int, creator: BaseViewHolder.Creator<T?>, `object`: T?, id: Long) {
        creatorPool.add(index, creator)
        items.add(index, `object`)
        this.ids.add(index, id)
    }

    fun <T> addItem(creator: BaseViewHolder.Creator<T?>, `object`: T?, id: Long) {
        creatorPool.add(creator)
        items.add(`object`)
        this.ids.add(id)
    }

    fun <T> addItemsAt(
        index: Int,
        creator: BaseViewHolder.Creator<T?>,
        list: MutableList<T?>,
        ids: MutableList<Long>
    ) {
        for (i in list.indices) {
            creatorPool.add(index, creator)
        }

        items.addAll(index, list)
        this.ids.addAll(index, ids)
    }

    fun <T> addItems(
        creator: BaseViewHolder.Creator<T?>,
        list: MutableList<T?>,
        ids: MutableList<Long?>
    ) {
        for (index in list.indices) {
            addItem<T?>(creator, list[index], ids[index]!!)
        }
    }

    fun removeItemAt(index: Int) {
        creatorPool.remove(index)
        items.removeAt(index)
        this.ids.removeAt(index)
    }

    fun notifyItemChangeById(targetId: Long) {
        for (index in 0..<itemCount) {
            if (this.ids[index] == targetId) {
                notifyItemChanged(index)
            }
        }
    }

    fun notifyItemChangeById(targetId: Long, payload: Any?) {
        for (index in 0..<itemCount) {
            if (this.ids[index] == targetId) {
                notifyItemChanged(index, payload)
            }
        }
    }

    fun setFirstItemById(targetId: Long, `object`: Any?) {
        for (index in 0..<itemCount) {
            if (this.ids[index] == targetId) {
                items[index] = `object`
                return
            }
        }
        throw NoSuchElementException("Cannot found any items belongs to id=$targetId")
    }

    override fun onCreateCreatorPool(): IndexCreatorPool {
        return IndexCreatorPool()
    }
}

