package yangfentuozi.batteryrecorder.ui.main.adapter

import rikka.recyclerview.IdBasedRecyclerViewAdapter
import yangfentuozi.batteryrecorder.ui.main.viewholder.StartServerViewHolder
import yangfentuozi.batteryrecorder.Service
import android.os.Handler
import android.os.Looper

class HomeAdapter(): IdBasedRecyclerViewAdapter() {
    companion object {
        const val ID_START_SERVER = 0x0000000000000001L
    }

    // 在主线程执行适配器的变更（需先于 serviceListener 声明）
    private val mainHandler = Handler(Looper.getMainLooper())

    // 提前声明并初始化 serviceListener，避免在 init 中引用未初始化变量
    private val serviceListener = object : Service.ServiceConnection {
        override fun onServiceConnected() {
            // 服务连接成功：重新构建列表（不包含启动卡片）
            mainHandler.post {
                updateDataForServiceConnected()
            }
        }

        override fun onServiceDisconnected() {
            // 服务断连：重新构建列表（包含启动卡片）
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

    fun updateData() {
        clear()
        addItem(StartServerViewHolder.CREATOR, null, ID_START_SERVER)
        notifyDataSetChanged()
    }

    private fun updateDataForServiceConnected() {
        // 服务已连接，不显示启动卡片
        clear()
        // 这里可以添加其他卡片，目前为空列表
        notifyDataSetChanged()
    }

    private fun updateDataForServiceDisconnected() {
        // 服务已断开，显示启动卡片
        clear()
        addItem(StartServerViewHolder.CREATOR, null, ID_START_SERVER)
        notifyDataSetChanged()
    }
}