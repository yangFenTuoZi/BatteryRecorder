package yangfentuozi.batteryrecorder.ui.main.viewholder

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import rikka.recyclerview.BaseViewHolder
import yangfentuozi.batteryrecorder.R
import yangfentuozi.batteryrecorder.databinding.HomeItemContainerBinding
import yangfentuozi.batteryrecorder.databinding.HomeStartServerBinding

class StartServerViewHolder(private val binding: HomeStartServerBinding, root: View) :
    BaseViewHolder<Any?>(root) {

    override fun onBind() {
        super.onBind()
        binding.title.setText(R.string.start_card_title_b)
        binding.desc.text = context.getString(R.string.start_card_desc_b)
        binding.button.text = context.getString(R.string.start_server)
        binding.button.setOnClickListener {
            startWithRoot()
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