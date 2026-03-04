package yangfentuozi.batteryrecorder.startup

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import yangfentuozi.batteryrecorder.R

object BootAutoStartNotification {
    private const val CHANNEL_ID = "boot_auto_start_reminder"
    private const val CHANNEL_NAME = "开机自启提醒"
    private const val CHANNEL_DESCRIPTION = "用于提示开机自启相关操作"
    private const val NOTIFICATION_ID_PERMISSION_HINT = 10001
    private const val NOTIFICATION_ID_BOOT_RESULT = 10002
    const val CONTENT_TEXT = "请在系统设置中允许 BatteryRecorder 自启动"
    private const val BOOT_SUCCESS_TITLE = "开机自启动已完成"
    private const val BOOT_SUCCESS_TEXT = "已发起服务启动"
    private const val BOOT_FAILED_TITLE = "开机自启动失败"
    private const val BOOT_FAILED_TEXT = "服务启动命令执行失败"

    @Suppress("DEPRECATION")
    fun notifyEnabled(context: Context) {
        val appContext = context.applicationContext
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                appContext,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) return
        }

        val manager = appContext.getSystemService(NotificationManager::class.java) ?: return
        ensureChannel(manager)
        val notification = Notification.Builder(appContext, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("开机自启已开启")
            .setContentText(CONTENT_TEXT)
            .setCategory(Notification.CATEGORY_REMINDER)
            .setPriority(Notification.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()
        runCatching {
            manager.notify(NOTIFICATION_ID_PERMISSION_HINT, notification)
        }
    }

    @Suppress("DEPRECATION")
    fun notifyBootAutoStartResult(context: Context, started: Boolean) {
        val appContext = context.applicationContext
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                appContext,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) return
        }

        val manager = appContext.getSystemService(NotificationManager::class.java) ?: return
        ensureChannel(manager)
        val (title, text) = if (started) {
            BOOT_SUCCESS_TITLE to BOOT_SUCCESS_TEXT
        } else {
            BOOT_FAILED_TITLE to BOOT_FAILED_TEXT
        }
        val notification = Notification.Builder(appContext, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(text)
            .setCategory(Notification.CATEGORY_STATUS)
            .setPriority(Notification.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()
        runCatching {
            manager.notify(NOTIFICATION_ID_BOOT_RESULT, notification)
        }
    }

    private fun ensureChannel(manager: NotificationManager) {
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            CHANNEL_NAME,
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = CHANNEL_DESCRIPTION
            setShowBadge(true)
        }
        manager.createNotificationChannel(channel)
    }
}
