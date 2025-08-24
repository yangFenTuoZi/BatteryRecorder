package rikka.recyclerview

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView

abstract class BaseRecyclerViewAdapter<CP : CreatorPool> : RecyclerView.Adapter<BaseViewHolder<*>?> {
    var items: MutableList<Any?>
    val creatorPool: CP
    var listener: Any? = null

    constructor(creatorPool: CP) : this(ArrayList<Any?>(), creatorPool)

    @JvmOverloads
    constructor(items: MutableList<Any?> = ArrayList<Any?>()) {
        this@BaseRecyclerViewAdapter.items = items
        this.creatorPool = onCreateCreatorPool()
    }

    constructor(items: MutableList<Any?>, creatorPool: CP) {
        this@BaseRecyclerViewAdapter.items = items
        this.creatorPool = creatorPool
    }

    abstract fun onCreateCreatorPool(): CP

    fun <T> getItemAt(position: Int): T? {
        return items[position].cast()
    }

    override fun getItemCount(): Int {
        return items.size
    }

    override fun getItemViewType(position: Int): Int {
        val data = getItemAt<Any>(position)
        val index = creatorPool.getCreatorIndex(this, position)
        if (index >= 0) {
            return index
        }
        throw IllegalStateException("Can't find Creator for ${data?.javaClass ?: "null"}, position: $position")
    }

    fun onGetLayoutInflater(parent: View): LayoutInflater? {
        return LayoutInflater.from(parent.context)
    }

    override fun onCreateViewHolder(parent: ViewGroup, creatorIndex: Int): BaseViewHolder<*> {
        val inflater = onGetLayoutInflater(parent)
        val creator: BaseViewHolder.Creator<*> = creatorPool.getCreator(creatorIndex)
        return creator.createViewHolder(inflater, parent)
    }

    override fun onBindViewHolder(
        holder: BaseViewHolder<*>,
        position: Int,
        payloads: MutableList<Any?>
    ) {
        if (!payloads.isEmpty()) {
            holder.bind(payloads, getItemAt(position), this)
        } else {
            onBindViewHolder(holder, position)
        }
    }

    override fun onBindViewHolder(holder: BaseViewHolder<*>, position: Int) {
        holder.bind(getItemAt(position), this)
    }

    override fun onViewRecycled(holder: BaseViewHolder<*>) {
        super.onViewRecycled(holder)

        holder.recycle()
    }

    override fun onViewAttachedToWindow(holder: BaseViewHolder<*>) {
        super.onViewAttachedToWindow(holder)

        holder.onViewAttachedToWindow()
    }

    override fun onViewDetachedFromWindow(holder: BaseViewHolder<*>) {
        super.onViewDetachedFromWindow(holder)

        holder.onViewDetachedFromWindow()
    }
}

@Suppress("unchecked_cast")
private fun <T> Any?.cast(): T? {
    return this as? T
}