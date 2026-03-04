package yangfentuozi.batteryrecorder.startup

import android.content.Context
import android.util.Log

object RootServerStarter {
    private const val TAG = "BootAutoStart"

    object Source {
        const val BOOT = "开机广播"
        const val HOME_BUTTON = "首页按钮"
    }

    fun start(
        context: Context,
        source: String
    ): Boolean {
        val appContext = context.applicationContext
        val command =
            "app_process \"-Djava.class.path=${appContext.applicationInfo.sourceDir}\" / yangfentuozi.batteryrecorder.server.Main"
        Log.i(TAG, "[启动请求] 来源=$source，准备执行 ROOT 启动命令")
        return try {
            Runtime.getRuntime().exec(
                arrayOf(
                    "su",
                    "-c",
                    command
                )
            )
            Log.i(TAG, "[启动请求] 来源=$source，已发起启动命令")
            true
        } catch (e: Throwable) {
            Log.e(TAG, "[启动请求] 来源=$source，发起启动命令失败", e)
            false
        }
    }
}
