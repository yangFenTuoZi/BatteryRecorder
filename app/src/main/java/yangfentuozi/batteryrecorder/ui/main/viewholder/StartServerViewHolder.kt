package yangfentuozi.batteryrecorder.ui.main.viewholder

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import rikka.recyclerview.BaseViewHolder
import yangfentuozi.batteryrecorder.R
import yangfentuozi.batteryrecorder.Service
import yangfentuozi.batteryrecorder.databinding.HomeItemContainerBinding
import yangfentuozi.batteryrecorder.databinding.HomeStartServerBinding

class StartServerViewHolder(private val binding: HomeStartServerBinding, root: View) :
    BaseViewHolder<Any?>(root), Service.ServiceConnection {

    override fun onBind() {
        super.onBind()
        binding.title.setText(R.string.start_card_title_b)
        binding.desc.text = context.getString(R.string.start_card_desc_b)

        // 注册服务连接监听器
        Service.addListener(this)

        // 更新按钮状态
        updateButtonState()

        binding.button.setOnClickListener {
            if (Service.service == null) {
                startWithRoot()
            } else {
                stopServer()
            }
        }
    }

    private fun startWithRoot() {
        Thread {
            try {
                Runtime.getRuntime().exec(
                    arrayOf(
                        "su", "-c",
                        "app_process \"-Djava.class.path=$(pm path yangfentuozi.batteryrecorder | sed 's/^package://')\" / yangfentuozi.batteryrecorder.server.ServerMain"
                    )
                )
                itemView.post {
                    Toast.makeText(context, "已执行启动命令", Toast.LENGTH_SHORT).show()
                }
            } catch (_: Throwable) {
                itemView.post {
                    Toast.makeText(context, "无法获取 root 或执行失败", Toast.LENGTH_SHORT).show()
                }
            }
        }.start()
    }

    private fun stopServer() {
        Thread {
            try {
                Service.service?.stopService()
                itemView.post {
                    Toast.makeText(context, "已停止服务", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                itemView.post {
                    Toast.makeText(context, "停止服务失败: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }.start()
    }

    private fun updateButtonState() {
        if (Service.service != null) {
            binding.button.text = context.getString(R.string.stop_server)
            binding.button.isEnabled = true
        } else {
            binding.button.text = context.getString(R.string.start_server)
            binding.button.isEnabled = true
        }
    }

    override fun onServiceConnected() {
        itemView.post {
            updateButtonState()
        }
    }

    override fun onServiceDisconnected() {
        itemView.post {
            updateButtonState()
        }
    }

    override fun onViewAttachedToWindow() {
        super.onViewAttachedToWindow()
        val layoutParams = itemView.layoutParams
        if (layoutParams is StaggeredGridLayoutManager.LayoutParams) {
            layoutParams.isFullSpan = true
        }
    }

    override fun onRecycle() {
        super.onRecycle()
        Service.removeListener(this)
    }

    companion object {
        val CREATOR: Creator<Any?> =
            Creator<Any?> { inflater: LayoutInflater?, parent: ViewGroup? ->
                val outer = HomeItemContainerBinding.inflate(
                    inflater!!, parent, false
                )
                val inner = HomeStartServerBinding.inflate(inflater, outer.root, true)
                StartServerViewHolder(inner, outer.root)
            }
    }
}