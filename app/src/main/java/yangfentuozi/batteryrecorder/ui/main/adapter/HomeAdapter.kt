package yangfentuozi.batteryrecorder.ui.main.adapter

import android.annotation.SuppressLint
import android.os.Handler
import android.os.Looper
import rikka.recyclerview.IdBasedRecyclerViewAdapter
import yangfentuozi.batteryrecorder.Service
import yangfentuozi.batteryrecorder.ui.main.viewholder.StartServerViewHolder

class HomeAdapter() : IdBasedRecyclerViewAdapter() {
    companion object {
        const val ID_START_SERVER = 0x0000000000000001L
    }

    // 在主线程执行适配器的变更（需先于 serviceListener 声明）
    private val mainHandler = Handler(Looper.getMainLooper())

    // 提前声明并初始化 serviceListener，避免在 init 中引用未初始化变量
    private val serviceListener = object : Service.ServiceConnection {
        override fun onServiceConnected() {
            // 服务连接成功
            mainHandler.post {
                updateDataForServiceConnected()
            }
        }

        override fun onServiceDisconnected() {
            // 服务断连
            mainHandler.post {
                updateDataForServiceDisconnected()
            }
        }
    }

    init {
        updateData()
        setHasStableIds(true)
        // 监听服务连接状态以动态增删“启动服务”卡片
        Service.addListener(serviceListener)
    }

    @SuppressLint("NotifyDataSetChanged")
    fun updateData() {
        clear()
        if (Service.service == null)
            addItem(StartServerViewHolder.CREATOR, null, ID_START_SERVER)
        notifyDataSetChanged()
    }

    private fun updateDataForServiceConnected() {
        if (getItemId(0) == ID_START_SERVER) {
            removeItemAt(0)
            notifyItemRemoved(0)
        }
    }

    private fun updateDataForServiceDisconnected() {
        if (getItemId(0) != ID_START_SERVER) {
            addItemAt(0, StartServerViewHolder.CREATOR, null, ID_START_SERVER)
            notifyItemInserted(0)
        }
    }
}