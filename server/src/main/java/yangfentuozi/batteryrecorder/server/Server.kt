package yangfentuozi.batteryrecorder.server

import android.annotation.SuppressLint
import android.app.ActivityManager.RunningTaskInfo
import android.app.IActivityManager
import android.app.IActivityTaskManager
import android.app.ITaskStackListener
import android.app.TaskInfo
import android.app.TaskStackListener
import android.content.AttributionSource
import android.content.IContentProvider
import android.content.pm.ApplicationInfo
import android.content.pm.IPackageManager
import android.hardware.display.IDisplayManager
import android.hardware.display.IDisplayManagerCallback
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.os.IPowerManager
import android.os.Looper
import android.os.RemoteException
import android.os.ServiceManager
import android.system.ErrnoException
import android.system.Os
import android.util.Log
import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException
import yangfentuozi.hiddenapi.compat.ServiceManagerCompat
import java.io.File
import java.io.FileInputStream
import java.io.FileNotFoundException
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
            val isInteractiveNow = iPowerManager.isInteractive
            val isInteractiveJustNow = isInteractive
            isInteractive = isInteractiveNow
            if (!recordScreenOff) {
                if (isInteractiveJustNow && !isInteractiveNow) {
                    stopMonitoring()
                    mMonitorHandler!!.post(mMonitorRunnableWithoutRecursion)
                } else if (!isInteractiveJustNow && isInteractiveNow) {
                    startMonitoring()
                }
            }
        }
    }

    private var mMonitorThread: HandlerThread? = null
    private var mMonitorHandler: Handler? = null
    private val mMonitorRunnable = object : Runnable {
        override fun run() {
            try {
                val timestamp = System.currentTimeMillis()
                writer!!.write(
                    DataWriter.PowerRecord(
                        timestamp,
                        PowerUtil.power,
                        currForegroundApp,
                        PowerUtil.capacity,
                        if (isInteractive) 1 else 0,
                        PowerUtil.status
                    )
                )
            } catch (e: IOException) {
                Log.e(TAG, "Error reading power data", e)
            } finally {
                mMonitorHandler!!.postDelayed(this, mIntervalMillis)
            }
        }
    }
    private val mMonitorRunnableWithoutRecursion = Runnable {
        try {
            val timestamp = System.currentTimeMillis()
            writer!!.write(
                DataWriter.PowerRecord(
                    timestamp,
                    PowerUtil.power,
                    currForegroundApp,
                    PowerUtil.capacity,
                    if (isInteractive) 1 else 0,
                    PowerUtil.status
                )
            )
        } catch (e: IOException) {
            Log.e(TAG, "Error reading power data", e)
        }
    }
    private var mIntervalMillis: Long = 900

    private var writer: DataWriter? = null
    private var currForegroundApp: String? = null
    private var isInteractive: Boolean
    private var recordScreenOff: Boolean = false

    private fun startService() {
        try {
            val iPackageManager =
                IPackageManager.Stub.asInterface(ServiceManager.getService("package"))
            val appInfo: ApplicationInfo
            if (Build.VERSION.SDK_INT >= 33) {
                appInfo = iPackageManager.getApplicationInfo(APP_PACKAGE, 0L, Os.getuid())
            } else {
                appInfo = iPackageManager.getApplicationInfo(APP_PACKAGE, 0, Os.getuid())
            }
            appUid = appInfo.uid
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

        mMonitorThread = HandlerThread("MonitorThread")
        mMonitorThread!!.start()
        mMonitorHandler = Handler(mMonitorThread!!.getLooper())

        try {
            writer = DataWriter(mMonitorThread!!.getLooper())
        } catch (e: IOException) {
            throw RuntimeException(e)
        }
        refreshConfig()

        startMonitoring()

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

    private fun startMonitoring() {
        mMonitorHandler!!.postDelayed(mMonitorRunnable, mIntervalMillis)
    }

    private fun stopMonitoring() {
        mMonitorHandler!!.removeCallbacks(mMonitorRunnable)
    }

    fun onFocusedAppChanged(taskInfo: TaskInfo) {
        val componentName = taskInfo.topActivity ?: return
        val packageName = componentName.packageName
        if (packageName == APP_PACKAGE) {
            sendBinder()
        }
        currForegroundApp = packageName
    }

    override fun refreshConfig() {
        val config = File(CONFIG)
        if (!config.exists()) {
            Log.e(TAG, "Config file not found")
            return
        }

        try {
            FileInputStream(config).use { fis ->
                val parser = Xml.newPullParser()
                parser.setInput(fis, "UTF-8")

                var eventType = parser.eventType
                mIntervalMillis = 900
                var batchSize = 200  // 保留兼容性
                var flushIntervalMs = 30000L  // 默认5秒
                var recordScreenOff = false

                while (eventType != XmlPullParser.END_DOCUMENT) {
                    if (eventType == XmlPullParser.START_TAG) {
                        val nameAttr = parser.getAttributeValue(null, "name")
                        val valueAttr = parser.getAttributeValue(null, "value")
                        when (nameAttr) {
                            "interval" ->
                                mIntervalMillis = valueAttr.toLongOrNull() ?: 900

                            "batch_size" ->
                                batchSize = valueAttr.toIntOrNull() ?: 200

                            "flush_interval" ->
                                flushIntervalMs = valueAttr.toLongOrNull() ?: 30000L

                            "record_screen_off" -> {
                                recordScreenOff = valueAttr == "true"
                            }
                        }
                    }
                    eventType = parser.next()
                }

                if (mIntervalMillis < 0) mIntervalMillis = 0
                if (batchSize < 0) batchSize = 0
                else if (batchSize > 1000) batchSize = 1000
                if (flushIntervalMs < 100) flushIntervalMs = 100L  // 最小100ms
                else if (flushIntervalMs > 60000) flushIntervalMs = 60000L  // 最大60秒

                writer!!.batchSize = batchSize
                writer!!.flushIntervalMs = flushIntervalMs

                if (!this.recordScreenOff && recordScreenOff && !isInteractive && !mMonitorHandler!!.hasCallbacks(mMonitorRunnable)) {
                    startMonitoring()
                }
                if (this.recordScreenOff && !recordScreenOff && !isInteractive && mMonitorHandler!!.hasCallbacks(mMonitorRunnable)) {
                    stopMonitoring()
                }
            }
        } catch (e: FileNotFoundException) {
            Log.e(TAG, "Config file not found", e)
        } catch (e: IOException) {
            Log.e(TAG, "Error reading config file", e)
        } catch (e: XmlPullParserException) {
            Log.e(TAG, "Error parsing config file", e)
        }
    }

    override fun stopService() {
        mMainHandler.postDelayed({ exitProcess(0) }, 100)
    }

    @Throws(RemoteException::class)
    override fun writeToDatabaseImmediately() {
        try {
            writer!!.flushBuffer()
        } catch (e: IOException) {
            throw RemoteException(Log.getStackTraceString(e))
        }
    }

    private fun stopServiceImmediately() {
        if (mMonitorThread != null) {
            mMonitorThread!!.quitSafely()
            mMonitorThread!!.interrupt()
        }

        try {
            writer!!.flushBuffer()
        } catch (e: IOException) {
            Log.e(TAG, Log.getStackTraceString(e))
        }

        writer!!.close()

        try {
            iActivityTaskManager.unregisterTaskStackListener(taskStackListener)
        } catch (e: RemoteException) {
            Log.e(TAG, "Failed to unregister task stack listener", e)
        }
    }

    private fun sendBinder() {
        var provider: IContentProvider? = null
        val name = "yangfentuozi.batteryrecorder.binderProvider"
        try {
            val contentProviderHolder =
                iActivityManager.getContentProviderExternal(name, 0, null, name)
            provider = contentProviderHolder?.provider

            if (provider == null) {
                Log.e(TAG, "Provider is null")
                return
            }
            if (!provider.asBinder().pingBinder()) {
                Log.e(TAG, "Provider is dead")
            }

            val extras = Bundle()
            extras.putBinder("binder", this)

            provider.call(
                (AttributionSource.Builder(Os.getuid())).setPackageName(null).build(),
                name,
                "setBinder",
                null,
                extras
            )
            Log.i(TAG, "Send binder")
        } catch (e: RemoteException) {
            Log.e(TAG, "Failed send binder", e)
        } finally {
            if (provider != null) {
                try {
                    iActivityManager.removeContentProviderExternal(name, null)
                } catch (tr: Throwable) {
                    Log.w(TAG, "RemoveContentProviderExternal", tr)
                }
            }
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
        const val TAG: String = "BatteryRecorderServer"
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
