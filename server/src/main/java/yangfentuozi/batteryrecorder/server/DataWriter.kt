package yangfentuozi.batteryrecorder.server

import android.os.Handler
import android.os.Looper
import android.util.Log
import yangfentuozi.batteryrecorder.server.BatteryStatus.Charging
import yangfentuozi.batteryrecorder.server.BatteryStatus.Discharging
import yangfentuozi.batteryrecorder.server.BatteryStatus.Full
import yangfentuozi.batteryrecorder.server.Server.Companion.changeOwner
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStream

class DataWriter(
    private val looper: Looper
) {

    private var lastStatus: BatteryStatus? = null

    private val chargeDataWriter = ChargeDataWriter(chargeDir)
    private val dischargeDataWriter = DischargeDataWriter(dischargeDir)

    var batchSize = 200
    var flushIntervalMs = 30 * 1000L
    var maxSegmentDurationMs = 24 * 60 * 60 * 1000L

    init {
        fun makeSureExists(file: File) {
            if (!file.exists()) {
                if (!file.mkdirs()) {
                    throw IOException("Failed to create power data directory: " + file.absolutePath)
                }
                changeOwner(file)
            } else if (!file.isDirectory()) {
                throw IOException("Power data path is not a directory: " + file.absolutePath)
            }
        }
        makeSureExists(powerDir)
        makeSureExists(chargeDir)
        makeSureExists(dischargeDir)
    }

    fun write(record: PowerRecord) {
        when (record.status) {
            Charging -> {
                chargeDataWriter.write(record, lastStatus != Charging)
            }

            Discharging -> {
                dischargeDataWriter.write(record, lastStatus != Discharging)
            }

            Full -> {}
        }
        lastStatus = record.status
    }

    fun close() {
        chargeDataWriter.closeCurrentSegment()
        dischargeDataWriter.closeCurrentSegment()
    }

    fun flushBuffer() {
        chargeDataWriter.flushBuffer()
        dischargeDataWriter.flushBuffer()
    }

    inner class ChargeDataWriter(dir: File) : BaseWriter(dir) {
        override fun needStartNewSegment(justChangedStatus: Boolean, nowTime: Long): Boolean {
            // case1 记录超过最大分段时间（0 表示不按时间分段）
            return (maxSegmentDurationMs > 0 && nowTime - startTime > maxSegmentDurationMs) ||
                    // case2 允许短时间内续接之前记录
                    (justChangedStatus && nowTime - lastTime > 30 * 1000)
        }

        override fun needDeleteSegment(nowTime: Long): Boolean {
            return nowTime - startTime < 1 * 60 * 1000 // 1min
        }
    }

    inner class DischargeDataWriter(dir: File) : BaseWriter(dir) {
        override fun needStartNewSegment(justChangedStatus: Boolean, nowTime: Long): Boolean {
            // case1 记录超过最大分段时间（0 表示不按时间分段）
            return (maxSegmentDurationMs > 0 && nowTime - startTime > maxSegmentDurationMs) ||
                    // case2 允许短时间内续接之前记录
                    (justChangedStatus && nowTime - lastTime > 10 * 60 * 1000)
        }

        override fun needDeleteSegment(nowTime: Long): Boolean {
            return nowTime - startTime < 15 * 60 * 1000 // 15min
        }
    }

    abstract inner class BaseWriter(val dir: File) {
        protected var segmentFile: File? = null
        protected var outputStream: OutputStream? = null
        protected var autoRetryWriter: AutoRetryStringWriter? = null
        protected var oldAutoRetryWriters: Set<AutoRetryStringWriter> = HashSet()

        protected var startTime: Long = 0L
        protected var lastTime: Long = 0L
        protected var lastChangedStatusTime = 0L

        protected val buffer = StringBuilder(4096)
        protected var batchCount = 0

        protected val handler: Handler = Handler(looper)
        protected val writingRunnable = Runnable {
            flushBuffer()
            // 防止异步写完被忽略掉
            if (batchCount > 0) {
                postDelayedWriting()
            }
        }

        fun postDelayedWriting() {
            handler.postDelayed(writingRunnable, flushIntervalMs)
        }

        fun write(
            record: PowerRecord,
            justChangedStatus: Boolean
        ) {

            // 选择性丢弃一些干扰数据
            if (justChangedStatus) lastChangedStatusTime = record.timestamp
            if (record.timestamp - lastChangedStatusTime < 2 * 1000L) {
                if ((record.power > 0) != (record.status == Discharging)) {
                    if (justChangedStatus) {
                        closeCurrentSegment()
                    }
                    return
                }
            }

            val startedNewSegment = startNewSegmentIfNeed(justChangedStatus)
            lastTime = record.timestamp

            buffer.append(record).append("\n")
            batchCount++

            if (startedNewSegment) {
                flushBuffer()
                if (handler.hasCallbacks(writingRunnable)) {
                    handler.removeCallbacks(writingRunnable)
                }
                return
            }

            if (batchCount >= batchSize) {
                flushBuffer()
                if (handler.hasCallbacks(writingRunnable)) {
                    handler.removeCallbacks(writingRunnable)
                }
            } else {
                if (!handler.hasCallbacks(writingRunnable)) {
                    postDelayedWriting()
                }
            }
        }

        private fun startNewSegmentIfNeed(
            justChangedStatus: Boolean
        ): Boolean {
            val nowTime = System.currentTimeMillis()
            if (needStartNewSegment(justChangedStatus, nowTime) ||
                // case 还没记录过
                autoRetryWriter == null
            ) {
                // 关闭之前的记录，打开新的
                closeCurrentSegment()
                startTime = nowTime
                val fileName = "$nowTime.txt"
                val file = File(dir, fileName)
                segmentFile = file

                val openOutputStream: (() -> OutputStream) = {
                    if (!file.exists() && !file.createNewFile()) {
                        throw IOException("Failed to create segment file: " + file.absolutePath)
                    }
                    changeOwner(file)
                    FileOutputStream(file, true)
                }
                autoRetryWriter = AutoRetryStringWriter(
                    openOutputStream(),
                    3,
                    1000,
                    openOutputStream
                )
                return true
            }
            return false
        }

        abstract fun needStartNewSegment(
            justChangedStatus: Boolean,
            nowTime: Long = System.currentTimeMillis()
        ): Boolean

        abstract fun needDeleteSegment(
            nowTime: Long = System.currentTimeMillis()
        ): Boolean

        fun flushBuffer() {
            if (batchCount == 0 || autoRetryWriter == null) return
            autoRetryWriter!!.write(buffer)
            buffer.setLength(0) // 清空 StringBuilder
            batchCount = 0
        }

        fun closeCurrentSegment() {
            flushBuffer()
            if (autoRetryWriter != null) {
                try {
                    autoRetryWriter!!.close()
                } catch (e: IOException) {
                    Log.e(TAG, "Failed to close segment file", e)
                }
                autoRetryWriter = null
                if (needDeleteSegment(System.currentTimeMillis())) {
                    segmentFile!!.delete()
                }
                segmentFile = null
            }
        }
    }

    data class PowerRecord(
        val timestamp: Long, val power: Long, val packageName: String?,
        val capacity: Int, val isDisplayOn: Int, val status: BatteryStatus
    ) {
        override fun toString(): String {
            return "$timestamp,$power,$packageName,$capacity,$isDisplayOn"
        }
    }

    companion object {
        const val TAG = "DataWriter"
        val powerDir = File(Server.APP_DATA + "/power_data")
        val chargeDir = File(powerDir, "charge")
        val dischargeDir = File(powerDir, "discharge")
    }
}
