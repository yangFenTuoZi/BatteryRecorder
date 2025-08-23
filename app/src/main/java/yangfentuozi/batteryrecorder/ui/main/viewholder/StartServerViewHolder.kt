package yangfentuozi.batteryrecorder.ui.main.viewholder

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import android.util.Log
import rikka.recyclerview.BaseViewHolder
import yangfentuozi.batteryrecorder.R
import yangfentuozi.batteryrecorder.Service
import yangfentuozi.batteryrecorder.databinding.HomeItemContainerBinding
import yangfentuozi.batteryrecorder.databinding.HomeStartServerBinding
import java.io.BufferedReader
import java.io.InputStreamReader

class StartServerViewHolder(private val binding: HomeStartServerBinding, root: View) :
    BaseViewHolder<Any?>(root), Service.ServiceConnection {

    private val TAG = "BatteryRecorderApp"

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
                Runtime.getRuntime().exec(arrayOf("su", "-c",
                    "app_process \"-Djava.class.path=$(pm path yangfentuozi.batteryrecorder | sed 's/^package://')\" / yangfentuozi.batteryrecorder.server.ServerMain"
                ))
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
                // 等待一小段时间让服务退出
                Thread.sleep(300)
                // 重试检查几次，最多 2 秒
                var attempts = 0
                var binderAlive: Boolean
                do {
                    binderAlive = Service.binder?.pingBinder() ?: false
                    Log.i(TAG, "Stop check: attempt=${attempts + 1}, binderAlive=$binderAlive, serviceNull=${Service.service == null}")
                    if (!binderAlive && Service.service == null) break
                    Thread.sleep(300)
                    attempts++
                } while (attempts < 6)

                val serverRunning = isServerProcessRunning()
                Log.i(TAG, "Stop check result: binderAlive=$binderAlive, serviceNull=${Service.service == null}, serverRunning=$serverRunning")

                itemView.post {
                    Toast.makeText(context, "已停止服务", Toast.LENGTH_SHORT).show()
                    updateButtonState()
                }
            } catch (e: Exception) {
                itemView.post {
                    Toast.makeText(context, "停止服务失败: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }.start()
    }

    private fun isServerProcessRunning(): Boolean {
        // 通过 root 检查 server 进程是否仍在运行
        // 优先使用 pgrep，其次回退到 ps | grep
        val cmds = arrayOf(
            arrayOf("su", "-c", "pgrep -f yangfentuozi.batteryrecorder.server.ServerMain || true"),
            arrayOf("su", "-c", "ps -A | grep -F yangfentuozi.batteryrecorder.server.ServerMain || true")
        )
        for (cmd in cmds) {
            try {
                val p = Runtime.getRuntime().exec(cmd)
                val output = BufferedReader(InputStreamReader(p.inputStream)).use { it.readText() }
                val err = BufferedReader(InputStreamReader(p.errorStream)).use { it.readText() }
                val code = p.waitFor()
                Log.d(TAG, "Proc check cmd='${cmd.joinToString(" ")}', exit=$code, out='${output.trim()}', err='${err.trim()}'")
                if (output.isNotBlank()) {
                    // 如果输出包含 grep 自身，需要过滤
                    val lines = output.lines().filter { it.isNotBlank() && !it.contains("grep -F") }
                    if (lines.any()) return true
                }
            } catch (t: Throwable) {
                Log.w(TAG, "Proc check failed for cmd=${cmd.joinToString(" ")}: ${t.message}")
            }
        }
        return false
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