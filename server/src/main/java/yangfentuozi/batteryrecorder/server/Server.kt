package yangfentuozi.batteryrecorder.server

import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.os.RemoteException
import android.system.ErrnoException
import android.system.Os
import android.system.OsConstants
import android.util.Log
import yangfentuozi.batteryrecorder.server.recorder.IRecordListener
import yangfentuozi.batteryrecorder.server.recorder.Monitor
import yangfentuozi.batteryrecorder.server.recorder.sampler.DumpsysSampler
import yangfentuozi.batteryrecorder.server.recorder.sampler.SysfsSampler
import yangfentuozi.batteryrecorder.server.writer.PowerRecordWriter
import yangfentuozi.batteryrecorder.shared.Constants
import yangfentuozi.batteryrecorder.shared.config.Config
import yangfentuozi.batteryrecorder.shared.config.ConfigConstants
import yangfentuozi.batteryrecorder.shared.config.ConfigUtil
import yangfentuozi.batteryrecorder.shared.data.BatteryStatus.Charging
import yangfentuozi.batteryrecorder.shared.data.BatteryStatus.Discharging
import yangfentuozi.batteryrecorder.shared.data.RecordsFile
import yangfentuozi.batteryrecorder.shared.sync.PfdFileSender
import yangfentuozi.hiddenapi.compat.ActivityManagerCompat
import yangfentuozi.hiddenapi.compat.PackageManagerCompat
import yangfentuozi.hiddenapi.compat.ServiceManagerCompat
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.nio.channels.FileLock
import java.nio.file.Files
import java.util.Scanner
import kotlin.system.exitProcess

class Server internal constructor() : IService.Stub() {
    private val mMainHandler: Handler
    private lateinit var monitor: Monitor
    private lateinit var writer: PowerRecordWriter
    private val writerThread = HandlerThread("WriterThread")

    private lateinit var appDataDir: File
    private lateinit var appConfigFile: File
    private lateinit var appPowerDataDir: File
    private lateinit var shellDataDir: File
    private lateinit var shellPowerDataDir: File
    private var instanceLockFile: RandomAccessFile? = null
    private var instanceLock: FileLock? = null

    private fun startService() {
        fun getAppInfo(packageName: String): ApplicationInfo {
            try {
                return PackageManagerCompat.getApplicationInfo(packageName, 0L, 0)
            } catch (e: RemoteException) {
                throw RuntimeException(
                    "Failed to get application info for package: $packageName",
                    e
                )
            } catch (e: PackageManager.NameNotFoundException) {
                throw RuntimeException("$packageName is not installed", e)
            }
        }

        var appInfo = getAppInfo(Constants.APP_PACKAGE_NAME)
        appUid = appInfo.uid
        appDataDir = File(appInfo.dataDir)
        appConfigFile = File("${appInfo.dataDir}/shared_prefs/${ConfigConstants.PREFS_NAME}.xml")
        appPowerDataDir = File("${appInfo.dataDir}/${Constants.APP_POWER_DATA_PATH}")

        val sampler = if (SysfsSampler.init(appInfo)) SysfsSampler else DumpsysSampler()

        appInfo = getAppInfo(Constants.SHELL_PACKAGE_NAME)
        shellDataDir = File(appInfo.dataDir)
        shellPowerDataDir = File("${appInfo.dataDir}/${Constants.SHELL_POWER_DATA_PATH}")

        if (Os.getuid() == 0) {
            shellPowerDataDir.let { shellPowerDataDir ->
                appPowerDataDir.let { appPowerDataDir ->
                    if (shellPowerDataDir.exists() && shellPowerDataDir.isDirectory) {
                        shellPowerDataDir.copyRecursively(
                            target = appPowerDataDir,
                            overwrite = true
                        )
                        shellPowerDataDir.deleteRecursively()
                        changeOwnerRecursively(appPowerDataDir)
                    }
                }
            }
        }

        try {
            writerThread.start()
            writer = if (Os.getuid() == 0)
                PowerRecordWriter(
                    writerThread.looper,
                    appPowerDataDir
                ) { changeOwnerRecursively(it) }
            else
                PowerRecordWriter(
                    writerThread.looper,
                    shellPowerDataDir
                ) {}
        } catch (e: IOException) {
            throw RuntimeException(e)
        }

        monitor = Monitor(
            writer = writer,
            sendBinder = this::sendBinder,
            sampler
        )

        if (Os.getuid() == 0) {
            ConfigUtil.getConfigByReading(appConfigFile)
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

    override fun getVersion(): Int {
        return BuildConfig.VERSION
    }

    override fun getCurrRecordsFile(): RecordsFile? {
        return RecordsFile.fromFile(when (writer.lastStatus) {
            Charging -> writer.chargeDataWriter.getCurrFile(writer.lastStatus != Charging)
            Discharging -> writer.dischargeDataWriter.getCurrFile(writer.lastStatus != Discharging)
            else -> null
        } ?: return null)
    }

    override fun registerRecordListener(listener: IRecordListener) {
        monitor.registerRecordListener(listener)
    }

    override fun unregisterRecordListener(listener: IRecordListener) {
        monitor.unregisterRecordListener(listener)
    }

    override fun updateConfig(config: Config) {
        monitor.recordIntervalMs = config.recordIntervalMs
        unlockOPlusSampleTimeLimit(config.recordIntervalMs.coerceAtLeast(200))
        monitor.screenOffRecord = config.screenOffRecordEnabled
        writer.flushIntervalMs = config.writeLatencyMs
        writer.batchSize = config.batchSize
        writer.maxSegmentDurationMs = config.segmentDurationMin * 60 * 1000L
        monitor.notifyLock()
    }

    private fun unlockOPlusSampleTimeLimit(intervalMs: Long) {
        runCatching {
            val forceActive = "/proc/oplus-votable/GAUGE_UPDATE/force_active"
            val forceVal = "/proc/oplus-votable/GAUGE_UPDATE/force_val"
            if (Os.access(forceActive, OsConstants.R_OK)) {
                val forceActiveFile = File(forceActive)
                val forceValFile = File(forceVal)
                if (forceValFile.readText().trim().toLong() > intervalMs) {
                    Os.chmod(forceActive, "666".toInt(8))
                    Os.chmod(forceVal, "666".toInt(8))
                    forceActiveFile.writeText("1\n")
                    forceValFile.writeText("$intervalMs\n")
                }
            }
        }
    }

    override fun sync(): ParcelFileDescriptor? {
        writer.flushBuffer()
        if (Os.getuid() == 0)
            return null

        val pipe = ParcelFileDescriptor.createPipe()
        val readEnd = pipe[0]
        val writeEnd = pipe[1]

        // 服务端在后台线程写入（发送）
        Thread {
            try {
                val currChargeDataPath =
                    if (writer.chargeDataWriter.needStartNewSegment(writer.lastStatus != Charging)) null
                    else writer.chargeDataWriter.segmentFile?.toPath()

                val currDischargeDataPath =
                    if (writer.dischargeDataWriter.needStartNewSegment(writer.lastStatus != Discharging)) null
                    else writer.dischargeDataWriter.segmentFile?.toPath()

                PfdFileSender.sendFile(
                    writeEnd,
                    shellPowerDataDir
                ) { file ->
                    if ((currChargeDataPath == null || !Files.isSameFile(
                            file.toPath(),
                            currChargeDataPath
                        )) &&
                        (currDischargeDataPath == null || !Files.isSameFile(
                            file.toPath(),
                            currDischargeDataPath
                        ))
                    ) file.delete()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Sync error", e)
                try {
                    writeEnd.close()
                } catch (_: Exception) {
                }
            }
        }.start()

        // 返回给客户端用于读取
        return readEnd
    }

    private fun stopServiceImmediately() {
        if (::monitor.isInitialized) {
            monitor.stop()
        }

        if (::writer.isInitialized) {
            try {
                writer.flushBuffer()
            } catch (e: IOException) {
                Log.e(TAG, Log.getStackTraceString(e))
            }
            writer.close()
        }

        releaseInstanceLock()
    }

    private fun sendBinder(attempt: Int): Boolean {
        try {
            val reply = ActivityManagerCompat.contentProviderCall(
                "yangfentuozi.batteryrecorder.binderProvider",
                "setBinder",
                null,
                Bundle().apply {
                    putBinder("binder", this@Server)
                }
            )
            val accepted = reply?.getBoolean("accepted", false) == true
            if (accepted) {
                Log.i(TAG, "[BINDER] 推送成功 attempt=$attempt")
            } else {
                Log.w(TAG, "[BINDER] 推送被拒绝 attempt=$attempt")
            }
            return accepted
        } catch (e: RemoteException) {
            Log.e(TAG, "[BINDER] 推送失败 attempt=$attempt", e)
        }
        return false
    }

    private fun scheduleSendBinderRetry() {
        mMainHandler.postDelayed(
            {
                sendBinder(2)
            },
            BINDER_RETRY_DELAY_MS
        )
    }

    private fun sendBinder() {
        if (!sendBinder(1)) {
            scheduleSendBinderRetry()
        }
    }

    private fun acquireInstanceLockOrExit() {
        try {
            val lockFile = File(SINGLETON_LOCK_FILE_PATH)
            lockFile.parentFile?.mkdirs()
            if (!lockFile.exists()) {
                lockFile.createNewFile()
            }
            ensureLockFileWritable(lockFile)
            val randomAccessFile = RandomAccessFile(lockFile, "rw")
            ensureLockFileWritable(lockFile)
            val lock = randomAccessFile.channel.tryLock()
            if (lock == null) {
                randomAccessFile.close()
                Log.e(TAG, "[SINGLETON] 已有服务实例运行，当前进程退出")
                exitProcess(0)
            }

            randomAccessFile.setLength(0)
            randomAccessFile.writeBytes("${Os.getpid()}\n")
            instanceLockFile = randomAccessFile
            instanceLock = lock
            Log.i(TAG, "[SINGLETON] 获取实例锁成功 pid=${Os.getpid()}")
        } catch (e: Throwable) {
            Log.e(TAG, "[SINGLETON] 获取实例锁失败，当前进程退出", e)
            exitProcess(1)
        }
    }

    private fun ensureLockFileWritable(lockFile: File) {
        runCatching {
            Os.chmod(lockFile.absolutePath, SINGLETON_LOCK_FILE_MODE)
        }.onFailure {
            Log.w(TAG, "[SINGLETON] 设置锁文件权限失败 path=${lockFile.absolutePath}", it)
        }
    }

    private fun releaseInstanceLock() {
        runCatching { instanceLock?.release() }
            .onFailure { Log.w(TAG, "[SINGLETON] 释放实例锁失败", it) }
        runCatching { instanceLockFile?.close() }
            .onFailure { Log.w(TAG, "[SINGLETON] 关闭锁文件失败", it) }
        instanceLock = null
        instanceLockFile = null
    }

    init {
        if (Looper.getMainLooper() == null) {
            Looper.prepareMainLooper()
        }

        mMainHandler = Handler(Looper.getMainLooper())
        acquireInstanceLockOrExit()
        Runtime.getRuntime().addShutdownHook(Thread { this.stopServiceImmediately() })
        ServiceManagerCompat.waitService("activity_task")
        ServiceManagerCompat.waitService("display")
        ServiceManagerCompat.waitService("power")

        startService()
        Looper.loop()
    }

    companion object {
        const val TAG: String = "Server"
        private const val BINDER_RETRY_DELAY_MS = 300L
        private const val SINGLETON_LOCK_FILE_PATH = "/data/local/tmp/batteryrecorder_server.lock"
        private val SINGLETON_LOCK_FILE_MODE = "666".toInt(8)

        var appUid: Int = 0

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
