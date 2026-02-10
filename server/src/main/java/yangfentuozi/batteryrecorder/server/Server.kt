package yangfentuozi.batteryrecorder.server

import android.annotation.SuppressLint
import android.app.ActivityManager.RunningTaskInfo
import android.app.IActivityManager
import android.app.IActivityTaskManager
import android.app.ITaskStackListener
import android.app.TaskInfo
import android.app.TaskStackListener
import android.hardware.display.IDisplayManager
import android.hardware.display.IDisplayManagerCallback
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.os.IPowerManager
import android.os.Looper
import android.os.RemoteCallbackList
import android.os.RemoteException
import android.os.ServiceManager
import android.system.ErrnoException
import android.system.Os
import android.util.Log
import yangfentuozi.batteryrecorder.config.Config
import yangfentuozi.batteryrecorder.config.ConfigUtil
import yangfentuozi.batteryrecorder.server.Native.nativeInit
import yangfentuozi.hiddenapi.compat.ActivityManagerCompat
import yangfentuozi.hiddenapi.compat.PackageManagerCompat
import yangfentuozi.hiddenapi.compat.ServiceManagerCompat
import java.io.File
import java.io.IOException
import java.util.Scanner
import kotlin.system.exitProcess

class Server internal constructor() : IService.Stub() {
    private val iActivityTaskManager: IActivityTaskManager
    private val iActivityManager: IActivityManager
    private val iDisplayManager: IDisplayManager
    private val iPowerManager: IPowerManager
    private val mMainHandler: Handler

    private val taskStackListener: ITaskStackListener = object : TaskStackListener() {
        override fun onTaskMovedToFront(taskInfo: RunningTaskInfo) {
            onFocusedAppChanged(taskInfo)
        }
    }

    private val displayCallback: IDisplayManagerCallback = object : IDisplayManagerCallback.Stub() {
        override fun onDisplayEvent(displayId: Int, event: Int) {
            isInteractive = iPowerManager.isInteractive
            if (isInteractive && monitor.paused) {
                monitor.notifz()
            }
        }
    }

    private val callbacks: RemoteCallbackList<IRecordListener> = RemoteCallbackList()

    private val monitor = Monitor()

    private inner class Monitor {
        var paused = false
            private set
        var stopped = false
            private set
        private val lock = Object()
        private var thread = Thread({
            synchronized(lock) {
                while (!stopped) {
                    try {
                        val timestamp = System.currentTimeMillis()
                        val power = Native.power
                        val status = Native.status
                        writer.write(
                            DataWriter.PowerRecord(
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
                        lock.wait(mIntervalMillis)
                    } else {
                        paused = true
                        lock.wait()
                    }
                }
            }
        }, "MonitorThread")

        fun start() {
            thread.start()
        }

        fun stop() {
            stopped = true
            thread.interrupt()
        }

        fun notifz() {
            lock.notifyAll()
        }
    }

    private var mIntervalMillis: Long = 900

    private lateinit var writer: DataWriter
    private val writerThread = HandlerThread("WriterThread")
    private var currForegroundApp: String? = null
    private var isInteractive: Boolean
    private var screenOffRecord: Boolean = false

    private fun startService() {
        try {
            val appInfo = PackageManagerCompat.getApplicationInfo(APP_PACKAGE, 0L, Os.getuid())
            appUid = appInfo.uid
            System.load("${appInfo.nativeLibraryDir}/libbatteryrecorder.so")
            nativeInit()
        } catch (e: RemoteException) {
            throw RuntimeException("Failed to get application info for package: $APP_PACKAGE", e)
        }

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

        try {
            writerThread.start()
            writer = DataWriter(writerThread.looper)
        } catch (e: IOException) {
            throw RuntimeException(e)
        }

        if (Os.getuid() == 0) {
            ConfigUtil.getConfigByReading(File(CONFIG))
        } else {
            ConfigUtil.getConfigByContentProvider()
        }?.let {
            updateConfig(it)
        }

        monitor.start()

        Thread({
            try {
                val scanner = Scanner(System.`in`)
                var line: String
                while ((scanner.nextLine().also { line = it }) != null) {
                    if (line.trim { it <= ' ' } == "exit") {
                        stopService()
                    }
                }
                scanner.close()
            } catch (_: Throwable) {
            }
        }, "InputHandler").start()
    }

    fun onFocusedAppChanged(taskInfo: TaskInfo) {
        val componentName = taskInfo.topActivity ?: return
        val packageName = componentName.packageName
        if (packageName == APP_PACKAGE) {
            sendBinder()
        }
        currForegroundApp = packageName
    }

    override fun stopService() {
        mMainHandler.postDelayed({ exitProcess(0) }, 100)
    }

    @Throws(RemoteException::class)
    override fun writeToDatabaseImmediately() {
        try {
            writer.flushBuffer()
        } catch (e: IOException) {
            throw RemoteException(Log.getStackTraceString(e))
        }
    }

    override fun registerRecordListener(listener: IRecordListener) {
        callbacks.register(listener)
    }

    override fun unregisterRecordListener(listener: IRecordListener) {
        callbacks.unregister(listener)
    }

    override fun updateConfig(config: Config) {
        mIntervalMillis = config.recordIntervalMs
        writer.flushIntervalMs = config.writeLatencyMs
        writer.batchSize = config.batchSize
        screenOffRecord = config.screenOffRecordEnabled
        writer.maxSegmentDurationMs = config.segmentDurationMin
    }

    override fun sync() {
        TODO("Not yet implemented")
    }

    private fun stopServiceImmediately() {
        monitor.stop()

        try {
            writer.flushBuffer()
        } catch (e: IOException) {
            Log.e(TAG, Log.getStackTraceString(e))
        }

        writer.close()

        try {
            iActivityTaskManager.unregisterTaskStackListener(taskStackListener)
        } catch (e: RemoteException) {
            Log.e(TAG, "Failed to unregister task stack listener", e)
        }
    }

    private fun sendBinder() {
        try {

            ActivityManagerCompat.contentProviderCall(
                "yangfentuozi.batteryrecorder.binderProvider",
                "setBinder",
                null,
                Bundle().apply {
                    putBinder("binder", this@Server)
                }
            )
            Log.i(TAG, "Send binder")
        } catch (e: RemoteException) {
            Log.e(TAG, "Failed send binder", e)
        }
    }

    init {
        if (Looper.getMainLooper() == null) {
            Looper.prepareMainLooper()
        }

        Runtime.getRuntime().addShutdownHook(Thread { this.stopServiceImmediately() })
        mMainHandler = Handler(Looper.getMainLooper())
        ServiceManagerCompat.waitService("activity")
        ServiceManagerCompat.waitService("activity_task")
        ServiceManagerCompat.waitService("display")
        ServiceManagerCompat.waitService("power")
        iActivityTaskManager =
            IActivityTaskManager.Stub.asInterface(ServiceManager.getService("activity_task"))
        iActivityManager = IActivityManager.Stub.asInterface(ServiceManager.getService("activity"))
        iDisplayManager = IDisplayManager.Stub.asInterface(ServiceManager.getService("display"))
        iPowerManager = IPowerManager.Stub.asInterface(ServiceManager.getService("power"))

        isInteractive = iPowerManager.isInteractive

        startService()
        Looper.loop()
    }

    companion object {
        const val TAG: String = "Server"
        const val APP_PACKAGE: String = "yangfentuozi.batteryrecorder"

        @SuppressLint("SdCardPath")
        const val APP_DATA: String = "/data/user/0/$APP_PACKAGE"
        const val CONFIG: String = APP_DATA + "/shared_prefs/" + APP_PACKAGE + "_preferences.xml"

        var appUid: Int = 0

        @JvmStatic
        fun changeOwner(file: File) {
            try {
                Os.chown(file.absolutePath, appUid, appUid)
            } catch (e: ErrnoException) {
                throw RuntimeException("Failed to set file owner or group", e)
            }
        }

        fun changeOwnerRecursively(file: File) {
            changeOwner(file)
            if (file.isDirectory()) {
                val files = file.listFiles()
                if (files != null) {
                    for (child in files) {
                        changeOwnerRecursively(child)
                    }
                }
            }
        }
    }
}
