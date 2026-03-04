package yangfentuozi.batteryrecorder.startup

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Settings
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

                    val currentBootCount = runCatching {
                        Settings.Global.getInt(
                            appContext.contentResolver,
                            Settings.Global.BOOT_COUNT
                        )
                    }.getOrElse {
                        Log.w(TAG, "[BOOT] 读取 boot_count 失败，跳过本次去重", it)
                        Int.MIN_VALUE
                    }
                    if (currentBootCount != Int.MIN_VALUE) {
                        val lastBootCount = prefs.getInt(
                            ConfigConstants.KEY_ROOT_BOOT_AUTO_START_LAST_BOOT_COUNT,
                            ConfigConstants.DEF_ROOT_BOOT_AUTO_START_LAST_BOOT_COUNT
                        )
                        if (lastBootCount == currentBootCount) {
                            Log.i(TAG, "[BOOT] 命中 boot_count 去重，跳过自启动，boot_count=$currentBootCount")
                            return@Thread
                        }
                        prefs.edit()
                            .putInt(
                                ConfigConstants.KEY_ROOT_BOOT_AUTO_START_LAST_BOOT_COUNT,
                                currentBootCount
                            )
                            .apply()
                        Log.i(TAG, "[BOOT] 已记录 boot_count 去重标记，boot_count=$currentBootCount")
                    }

                    Log.i(TAG, "[BOOT] 满足自启动条件，准备拉起服务")
                    RootServerStarter.start(
                        context = appContext,
                        source = RootServerStarter.Source.BOOT
                    )
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
