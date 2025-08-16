package rikka.recyclerview

interface CreatorPool {
    fun getCreatorIndex(adapter: BaseRecyclerViewAdapter<*>?, position: Int): Int
    fun getCreator(index: Int): BaseViewHolder.Creator<*>?
}