package yangfentuozi.batteryrecorder.startup

import android.content.Context
import yangfentuozi.batteryrecorder.shared.util.LoggerX

object RootServerStarter {
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
            "nohup app_process \"-Djava.class.path=${appContext.applicationInfo.sourceDir}\" / " +
                    "yangfentuozi.batteryrecorder.server.Main >/dev/null 2>&1 &"
        LoggerX.i<RootServerStarter>("[启动请求] 来源=$source，准备执行 ROOT 启动命令")
        return try {
            Runtime.getRuntime().exec(
                arrayOf(
                    "su",
                    "-c",
                    command
                )
            )
            LoggerX.i<RootServerStarter>("[启动请求] 来源=$source，已发起 ROOT 后台启动命令")
            true
        } catch (e: Throwable) {
            LoggerX.e<RootServerStarter>("[启动请求] 来源=$source，发起启动命令失败", tr = e)
            false
        }
    }
}
