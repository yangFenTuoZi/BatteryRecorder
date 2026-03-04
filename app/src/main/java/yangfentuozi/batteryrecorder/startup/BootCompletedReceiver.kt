package yangfentuozi.batteryrecorder.startup

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import yangfentuozi.batteryrecorder.shared.config.ConfigConstants

class BootCompletedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        val pendingResult = goAsync()
        val appContext = context.applicationContext
        Thread(
            {
                try {
                    Log.i(TAG, "[BOOT] 收到开机广播")
                    val prefs = appContext.getSharedPreferences(
                        ConfigConstants.PREFS_NAME,
                        Context.MODE_PRIVATE
                    )
                    val autoStartEnabled = prefs.getBoolean(
                        ConfigConstants.KEY_ROOT_BOOT_AUTO_START_ENABLED,
                        ConfigConstants.DEF_ROOT_BOOT_AUTO_START_ENABLED
                    )
                    if (!autoStartEnabled) {
                        Log.i(TAG, "[BOOT] 开机 ROOT 自启动未开启")
                        return@Thread
                    }
                    RootServerStarter.start(appContext)
                } catch (e: Throwable) {
                    Log.e(TAG, "[BOOT] 开机自启动执行失败", e)
                } finally {
                    pendingResult.finish()
                }
            },
            "BootAutoStart"
        ).start()
    }

    companion object {
        private const val TAG = "BootAutoStart"
    }
}
