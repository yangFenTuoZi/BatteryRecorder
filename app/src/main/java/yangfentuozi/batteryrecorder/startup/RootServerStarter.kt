package yangfentuozi.batteryrecorder.startup

import android.content.Context
import android.util.Log

object RootServerStarter {
    private const val TAG = "BootAutoStart"

    fun start(context: Context): Boolean {
        val appContext = context.applicationContext
        val command =
            "app_process \"-Djava.class.path=${appContext.applicationInfo.sourceDir}\" / yangfentuozi.batteryrecorder.server.Main"
        return try {
            Runtime.getRuntime().exec(
                arrayOf(
                    "su",
                    "-c",
                    command
                )
            )
            Log.i(TAG, "[ROOT] 已发起启动命令")
            true
        } catch (e: Throwable) {
            Log.e(TAG, "[ROOT] 发起启动命令失败", e)
            false
        }
    }
}
