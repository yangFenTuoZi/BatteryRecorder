package yangfentuozi.batteryrecorder.server

import android.annotation.SuppressLint
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.RemoteException
import android.system.ErrnoException
import android.system.Os
import android.util.Log
import yangfentuozi.batteryrecorder.config.Config
import yangfentuozi.batteryrecorder.config.ConfigUtil
import yangfentuozi.batteryrecorder.server.recorder.IRecordListener
import yangfentuozi.batteryrecorder.server.recorder.Monitor
import yangfentuozi.batteryrecorder.server.recorder.Native.nativeInit
import yangfentuozi.batteryrecorder.server.writer.PowerRecordWriter
import yangfentuozi.hiddenapi.compat.ActivityManagerCompat
import yangfentuozi.hiddenapi.compat.PackageManagerCompat
import yangfentuozi.hiddenapi.compat.ServiceManagerCompat
import java.io.File
import java.io.IOException
import java.util.Scanner
import kotlin.system.exitProcess

class Server internal constructor() : IService.Stub() {
    private val mMainHandler: Handler
    private lateinit var monitor: Monitor
    private lateinit var writer: PowerRecordWriter
    private val writerThread = HandlerThread("WriterThread")

    private fun startService() {
        try {
            val appInfo = PackageManagerCompat.getApplicationInfo(APP_PACKAGE, 0L, Os.getuid())
            appUid = appInfo.uid
            @SuppressLint("UnsafeDynamicallyLoadedCode")
            System.load("${appInfo.nativeLibraryDir}/libbatteryrecorder.so")
            nativeInit()
        } catch (e: RemoteException) {
            throw RuntimeException("Failed to get application info for package: $APP_PACKAGE", e)
        }

        try {
            writerThread.start()
            writer = PowerRecordWriter(writerThread.looper)
        } catch (e: IOException) {
            throw RuntimeException(e)
        }

        monitor = Monitor(
            writer = writer,
            sendBinder = this::sendBinder
        )

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
        monitor.registerRecordListener(listener)
    }

    override fun unregisterRecordListener(listener: IRecordListener) {
        monitor.unregisterRecordListener(listener)
    }

    override fun updateConfig(config: Config) {
        monitor.recordIntervalMs = config.recordIntervalMs
        monitor.screenOffRecord = config.screenOffRecordEnabled
        writer.flushIntervalMs = config.writeLatencyMs
        writer.batchSize = config.batchSize
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
        ServiceManagerCompat.waitService("activity_task")
        ServiceManagerCompat.waitService("display")
        ServiceManagerCompat.waitService("power")

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
