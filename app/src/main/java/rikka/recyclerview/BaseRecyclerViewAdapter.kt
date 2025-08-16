package rikka.recyclerview

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView

abstract class BaseRecyclerViewAdapter<CP : CreatorPool?> : RecyclerView.Adapter<BaseViewHolder<*>?> {
    private var mItems: MutableList<*>
    val creatorPool: CP?
    var listener: Any? = null

    constructor(creatorPool: CP?) : this(java.util.ArrayList<Any?>(), creatorPool)

    @JvmOverloads
    constructor(items: MutableList<*> = java.util.ArrayList<Any?>()) {
        mItems = items
        this.creatorPool = onCreateCreatorPool()
    }

    constructor(items: MutableList<*>, creatorPool: CP?) {
        mItems = items
        this.creatorPool = creatorPool
    }

    abstract fun onCreateCreatorPool(): CP?

    fun <T> getItems(): MutableList<T?>? {
        return mItems.cast()
    }

    fun setItems(items: MutableList<*>) {
        mItems = items
    }

    fun <T> getItemAt(position: Int): T? {
        return mItems[position].cast()
    }

    override fun getItemCount(): Int {
        return mItems.size
    }

    override fun getItemViewType(position: Int): Int {
        val data = getItemAt<Any?>(position)
        val index = creatorPool!!.getCreatorIndex(this, position)
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
        val creator: BaseViewHolder.Creator<*> = creatorPool!!.getCreator(creatorIndex)!!
        return creator.createViewHolder(inflater, parent)!!
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