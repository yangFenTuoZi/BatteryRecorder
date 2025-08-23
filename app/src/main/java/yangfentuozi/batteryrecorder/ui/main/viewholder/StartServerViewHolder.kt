package yangfentuozi.batteryrecorder.ui.main.viewholder

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import rikka.recyclerview.BaseViewHolder
import yangfentuozi.batteryrecorder.R
import yangfentuozi.batteryrecorder.Service
import yangfentuozi.batteryrecorder.databinding.HomeItemContainerBinding
import yangfentuozi.batteryrecorder.databinding.HomeStartServerBinding
import android.text.method.LinkMovementMethod
import androidx.core.text.HtmlCompat
import androidx.recyclerview.widget.StaggeredGridLayoutManager

class StartServerViewHolder(binding: HomeStartServerBinding, root: View) :
    BaseViewHolder<Any?>(root) {

    private val vb: HomeStartServerBinding = binding

    private val listener = object : Service.ServiceConnection {
        override fun onServiceConnected() {
            // Ensure UI updates run on main thread
            itemView.post { updateUi(true) }
        }

        override fun onServiceDisconnected() {
            // Ensure UI updates run on main thread
            itemView.post { updateUi(false) }
        }
    }

    private fun updateUi(connected: Boolean) {
        val textRes = if (connected) R.string.stop_server else R.string.start_server
        vb.button.text = vb.root.context.getString(textRes)
        vb.button.isEnabled = true
    }

    override fun onBind() {
        super.onBind()
        val ctx = vb.root.context
        // Bind title and description (B variants)
        vb.title.setText(R.string.start_card_title_b)
        vb.desc.text = HtmlCompat.fromHtml(ctx.getString(R.string.start_card_desc_b), HtmlCompat.FROM_HTML_MODE_LEGACY)
        vb.desc.movementMethod = LinkMovementMethod.getInstance()
        vb.desc.linksClickable = true
        // Initial state
        val connected = Service.binder?.pingBinder() ?: false
        updateUi(connected)
        // Click handler
        vb.button.setOnClickListener {
            val isConnected = Service.binder?.pingBinder() ?: false
            if (isConnected) {
                // Stop service directly (no confirm, as per option A)
                vb.button.isEnabled = false
                Thread {
                    try {
                        Service.service?.stopService()
                    } catch (_: Throwable) {
                    } finally {
                        itemView.post {
                            vb.button.isEnabled = true
                            Toast.makeText(vb.root.context, "已请求停止服务", Toast.LENGTH_SHORT).show()
                        }
                    }
                }.start()
            } else {
                // Start via root using provided command as-is
                startWithRoot()
            }
        }
    }

    private fun startWithRoot() {
        val ctx = vb.root.context
        val cmd = ctx.getString(R.string.root_start_cmd)
        if (cmd.isBlank()) {
            Toast.makeText(ctx, "未配置 root 启动命令", Toast.LENGTH_SHORT).show()
            return
        }
        vb.button.isEnabled = false
        Thread {
            try {
                // 非阻塞启动：不等待服务进程退出
                Runtime.getRuntime().exec(arrayOf("su", "-c", cmd))
                itemView.post {
                    vb.button.isEnabled = true
                    Toast.makeText(ctx, "已执行启动命令", Toast.LENGTH_SHORT).show()
                }
            } catch (_: Throwable) {
                itemView.post {
                    vb.button.isEnabled = true
                    Toast.makeText(ctx, "无法获取 root 或执行失败", Toast.LENGTH_SHORT).show()
                }
            }
        }.start()
    }

    override fun onViewAttachedToWindow() {
        super.onViewAttachedToWindow()
        (itemView.layoutParams as? StaggeredGridLayoutManager.LayoutParams)?.isFullSpan = true
        Service.addListener(listener)
    }

    override fun onViewDetachedFromWindow() {
        super.onViewDetachedFromWindow()
        Service.removeListener(listener)
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