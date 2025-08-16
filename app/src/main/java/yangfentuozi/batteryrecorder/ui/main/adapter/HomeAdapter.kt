package yangfentuozi.batteryrecorder.ui.main.adapter

import rikka.recyclerview.IdBasedRecyclerViewAdapter
import yangfentuozi.batteryrecorder.Service
import yangfentuozi.batteryrecorder.ui.main.viewholder.StartServerViewHolder
import yangfentuozi.batteryrecorder.ui.main.viewholder.StopServerViewHolder

class HomeAdapter(): IdBasedRecyclerViewAdapter() {
    companion object {
        const val ID_START_SERVER = 0x1000000000000001L
        const val ID_STOP_SERVER = 0x1000000000000002L
    }

    init {
        updateData()
        setHasStableIds(true)
    }

    fun updateData() {
        clear()

        addItem(StartServerViewHolder.CREATOR, null, ID_START_SERVER)
        if (Service.service != null) {
            addItem(StopServerViewHolder.CREATOR, null, ID_STOP_SERVER)
        }
    }
}