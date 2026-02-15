package yangfentuozi.batteryrecorder.server.recorder

import android.app.ActivityManager.RunningTaskInfo
import android.app.IActivityTaskManager
import android.app.ITaskStackListener
import android.app.TaskInfo
import android.app.TaskStackListener
import android.hardware.display.IDisplayManager
import android.hardware.display.IDisplayManagerCallback
import android.os.IPowerManager
import android.os.RemoteCallbackList
import android.os.RemoteException
import android.os.ServiceManager
import android.util.Log
import androidx.annotation.Keep
import yangfentuozi.batteryrecorder.server.Server.Companion.TAG
import yangfentuozi.batteryrecorder.server.data.PowerRecord
import yangfentuozi.batteryrecorder.server.writer.PowerRecordWriter
import yangfentuozi.batteryrecorder.shared.Constants
import yangfentuozi.batteryrecorder.shared.config.ConfigConstants
import java.io.IOException

class Monitor(
    private val writer: PowerRecordWriter,
    private val sendBinder: (() -> Unit)
) {

    private val iActivityTaskManager =
        IActivityTaskManager.Stub.asInterface(ServiceManager.getService("activity_task"))
    private val iDisplayManager =
        IDisplayManager.Stub.asInterface(ServiceManager.getService("display"))
    private val iPowerManager = IPowerManager.Stub.asInterface(ServiceManager.getService("power"))
    private val taskStackListener: ITaskStackListener = object : TaskStackListener() {
        @Keep
        override fun onTaskMovedToFront(taskInfo: RunningTaskInfo) {
            onFocusedAppChanged(taskInfo)
        }
    }

    private val displayCallback: IDisplayManagerCallback = object : IDisplayManagerCallback.Stub() {
        @Keep
        override fun onDisplayEvent(displayId: Int, event: Int) {
            isInteractive = iPowerManager.isInteractive
            if (isInteractive && paused) {
                notifyLock()
            }
        }
    }

    private var currForegroundApp: String? = null
    private var isInteractive = true

    private val callbacks: RemoteCallbackList<IRecordListener> = RemoteCallbackList()

    var recordIntervalMs: Long = ConfigConstants.DEF_RECORD_INTERVAL_MS
    var screenOffRecord: Boolean = ConfigConstants.DEF_SCREEN_OFF_RECORD_ENABLED
    private var paused = false
    private var stopped = false
    private val lock = Object()
    private var thread = Thread({
        synchronized(lock) {
            while (!stopped) {
                try {
                    val timestamp = System.currentTimeMillis()
                    val power = Native.power
                    val status = Native.status
                    writer.write(
                        PowerRecord(
                            timestamp,
                            power,
                            currForegroundApp,
                            Native.capacity,
                            if (isInteractive) 1 else 0,
                            status
                        )
                    )

                    // 回调 app
                    val n: Int = callbacks.beginBroadcast()
                    for (i in 0..<n) {
                        try {
                            callbacks.getBroadcastItem(i)
                                .onRecord(timestamp, power, status.value)
                        } catch (e: RemoteException) {
                            Log.e(TAG, "Failed to call back", e)
                        }
                    }
                    callbacks.finishBroadcast()
                } catch (e: IOException) {
                    Log.e(TAG, "Error reading power data", e)
                }

                if (isInteractive || screenOffRecord) {
                    paused = false
                    lock.wait(recordIntervalMs)
                } else {
                    paused = true
                    lock.wait()
                }
            }
        }
    }, "MonitorThread")

    fun start() {
        try {
            iActivityTaskManager.registerTaskStackListener(taskStackListener)
            iDisplayManager.registerCallback(displayCallback)
        } catch (e: RemoteException) {
            throw RuntimeException("Failed to register task stack listener", e)
        }

        try {
            onFocusedAppChanged(iActivityTaskManager.getFocusedRootTaskInfo())
        } catch (e: RemoteException) {
            Log.e(TAG, "Failed to get focused root task info", e)
            throw RuntimeException(e)
        }
        isInteractive = iPowerManager.isInteractive

        thread.start()
    }

    fun stop() {
        stopped = true
        thread.interrupt()
        try {
            iActivityTaskManager.unregisterTaskStackListener(taskStackListener)
        } catch (e: RemoteException) {
            Log.e(TAG, "Failed to unregister task stack listener", e)
        }
    }

    fun notifyLock() {
        synchronized(lock) {
            lock.notifyAll()
        }
    }

    fun registerRecordListener(callback: IRecordListener) {
        callbacks.register(callback)
    }

    fun unregisterRecordListener(callback: IRecordListener) {
        callbacks.unregister(callback)
    }

    private fun onFocusedAppChanged(taskInfo: TaskInfo) {
        val componentName = taskInfo.topActivity ?: return
        val packageName = componentName.packageName
        if (packageName == Constants.APP_PACKAGE_NAME) {
            sendBinder()
        }
        currForegroundApp = packageName
    }
}