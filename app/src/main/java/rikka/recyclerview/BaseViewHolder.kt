package rikka.recyclerview

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView


open class BaseViewHolder<T>(itemView: View) : RecyclerView.ViewHolder(itemView) {
    fun interface Creator<T> {
        fun createViewHolder(inflater: LayoutInflater?, parent: ViewGroup?): BaseViewHolder<T>?
    }

    private var mData: T? = null
    private var mAdapter: BaseRecyclerViewAdapter<*>? = null

    val context: Context
        get() = itemView.context

    var data: T?
        get() = mData
        set(data) {
            setData(data, null)
        }

    fun setData(data: T?, payload: Any?) {
        mData = data

        val position = getAdapterPosition()
        this.adapter?.getItems<Any?>()?.set(position, data)
        this.adapter?.notifyItemChanged(position, payload)
    }

    val adapter: BaseRecyclerViewAdapter<*>?
        get() = mAdapter

    fun bind(payloads: MutableList<Any?>, data: T?, adapter: BaseRecyclerViewAdapter<*>?) {
        mAdapter = adapter
        mData = data

        onBind(payloads)
    }

    fun bind(data: T?, adapter: BaseRecyclerViewAdapter<*>?) {
        mAdapter = adapter
        mData = data

        onBind()
    }

    /**
     * Called when bind.
     *
     */
    open fun onBind() {
    }

    /**
     * Called when partial bind.
     *
     * @param payloads A non-null list of merged payloads
     */
    fun onBind(payloads: MutableList<Any?>) {
    }

    fun recycle() {
        onRecycle()

        mData = null
        mAdapter = null
    }

    fun onRecycle() {
    }

    open fun onViewAttachedToWindow() {
    }

    open fun onViewDetachedFromWindow() {
    }
}