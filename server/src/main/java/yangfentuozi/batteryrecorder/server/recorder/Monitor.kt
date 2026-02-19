package yangfentuozi.batteryrecorder.server.recorder

import android.app.ActivityManager.RunningTaskInfo
import android.app.IActivityTaskManager
import android.app.ITaskStackListener
import android.app.TaskInfo
import android.app.TaskStackListener
import android.hardware.display.IDisplayManager
import android.hardware.display.IDisplayManagerCallback
import android.os.Handler
import android.os.HandlerThread
import android.os.IPowerManager
import android.os.RemoteCallbackList
import android.os.RemoteException
import android.os.ServiceManager
import android.util.Log
import androidx.annotation.Keep
import yangfentuozi.batteryrecorder.server.Server.Companion.TAG
import yangfentuozi.batteryrecorder.server.writer.PowerRecordWriter
import yangfentuozi.batteryrecorder.shared.Constants
import yangfentuozi.batteryrecorder.shared.config.ConfigConstants
import yangfentuozi.batteryrecorder.shared.data.PowerRecord
import java.io.IOException
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock

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

    @Volatile
    private var currForegroundApp: String? = null
    @Volatile
    private var isInteractive = true

    private val callbacks: RemoteCallbackList<IRecordListener> = RemoteCallbackList()

    @Volatile
    var recordIntervalMs: Long = ConfigConstants.DEF_RECORD_INTERVAL_MS
    @Volatile
    var screenOffRecord: Boolean = ConfigConstants.DEF_SCREEN_OFF_RECORD_ENABLED
    @Volatile
    private var paused = false
    @Volatile
    private var stopped = false

    private val lock = ReentrantLock()
    private val canRun = lock.newCondition()

    private fun shouldPause(): Boolean {
        return !isInteractive && !screenOffRecord
    }

    private fun awaitNext(intervalMs: Long) {
        lock.lock()
        try {
            while (!stopped && shouldPause()) {
                paused = true
                canRun.await()
            }
            paused = false
            if (stopped) return

            var nanos = TimeUnit.MILLISECONDS.toNanos(intervalMs)
            while (!stopped && !shouldPause() && nanos > 0L) {
                nanos = canRun.awaitNanos(nanos)
            }
        } finally {
            lock.unlock()
        }
    }

    private val callbackThread = HandlerThread("CallbackThread")
    private lateinit var callbackHandler: Handler
    private var thread = Thread({
        while (!stopped) {
            try {
                val timestamp = System.currentTimeMillis()
                val power = Native.power
                val status = Native.status
                val temp = Native.temp
                writer.write(
                    PowerRecord(
                        timestamp,
                        power,
                        currForegroundApp,
                        Native.capacity,
                        if (isInteractive) 1 else 0,
                        status,
                        temp
                    )
                )

                callbackHandler.post {
                    // 回调 app
                    val n: Int = callbacks.beginBroadcast()
                    for (i in 0..<n) {
                        try {
                            callbacks.getBroadcastItem(i)
                                .onRecord(timestamp, power, status.value, temp)
                        } catch (e: RemoteException) {
                            Log.e(TAG, "Failed to call back", e)
                        }
                    }
                    callbacks.finishBroadcast()
                }
            } catch (e: IOException) {
                Log.e(TAG, "Error reading power data", e)
            }

            awaitNext(recordIntervalMs)
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

        callbackThread.start()
        callbackHandler = Handler(callbackThread.looper)
        thread.start()
    }

    fun stop() {
        stopped = true
        notifyLock()
        thread.interrupt()
        callbackThread.interrupt()
        try {
            iActivityTaskManager.unregisterTaskStackListener(taskStackListener)
        } catch (e: RemoteException) {
            Log.e(TAG, "Failed to unregister task stack listener", e)
        }
    }

    fun notifyLock() {
        lock.lock()
        try {
            canRun.signalAll()
        } finally {
            lock.unlock()
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
