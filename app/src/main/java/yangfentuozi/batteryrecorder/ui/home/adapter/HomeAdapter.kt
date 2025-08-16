package yangfentuozi.batteryrecorder.ui.home.adapter

import rikka.recyclerview.IdBasedRecyclerViewAdapter
import yangfentuozi.batteryrecorder.Service
import yangfentuozi.batteryrecorder.ui.home.viewholder.SettingsViewHolder
import yangfentuozi.batteryrecorder.ui.home.viewholder.StartServerViewHolder
import yangfentuozi.batteryrecorder.ui.home.viewholder.StopServerViewHolder

class HomeAdapter(): IdBasedRecyclerViewAdapter() {
    companion object {
        const val ID_START_SERVER = 0x1000000000000001L
        const val ID_STOP_SERVER = 0x1100000000000002L
        const val ID_SETTINGS = 0x1200000000000000L
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

        addItem(SettingsViewHolder.CREATOR, null, ID_SETTINGS)
    }
}