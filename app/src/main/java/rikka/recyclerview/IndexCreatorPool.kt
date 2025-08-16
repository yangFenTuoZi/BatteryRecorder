package rikka.recyclerview

open class IndexCreatorPool : CreatorPool {
    private val mCreators: MutableList<BaseViewHolder.Creator<*>?> = java.util.ArrayList<BaseViewHolder.Creator<*>?>()
    private val mPositionToIndex: MutableList<Int?> = java.util.ArrayList<Int?>()

    fun add(creator: BaseViewHolder.Creator<*>?) {
        var indexOfCreator = mCreators.indexOf(creator)
        if (indexOfCreator == -1) {
            mCreators.add(creator)
            indexOfCreator = mCreators.size - 1
        }
        mPositionToIndex.add(indexOfCreator)
    }

    fun add(itemPosition: Int, creator: BaseViewHolder.Creator<*>?) {
        var indexOfCreator = mCreators.indexOf(creator)
        if (indexOfCreator == -1) {
            mCreators.add(creator)
            indexOfCreator = mCreators.size - 1
        }
        mPositionToIndex.add(itemPosition, indexOfCreator)
    }

    fun remove(itemPosition: Int) {
        mPositionToIndex.removeAt(itemPosition)
    }

    fun clear() {
        mPositionToIndex.clear()
    }

    override fun getCreatorIndex(adapter: BaseRecyclerViewAdapter<*>?, position: Int): Int {
        return mPositionToIndex[position]!!
    }

    override fun getCreator(index: Int): BaseViewHolder.Creator<*>? {
        return mCreators[index]
    }
}