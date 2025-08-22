package yangfentuozi.batteryrecorder.ui.main.adapter

import rikka.recyclerview.IdBasedRecyclerViewAdapter
import yangfentuozi.batteryrecorder.ui.main.viewholder.StartServerViewHolder

class HomeAdapter(): IdBasedRecyclerViewAdapter() {
    companion object {
        const val ID_START_SERVER = 0x0000000000000001L
    }

    init {
        updateData()
        setHasStableIds(true)
    }

    fun updateData() {
        clear()

        addItem(StartServerViewHolder.CREATOR, null, ID_START_SERVER)
    }
}