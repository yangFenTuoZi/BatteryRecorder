package yangfentuozi.batteryrecorder.server

import android.os.Handler
import android.os.Looper
import android.util.Log
import yangfentuozi.batteryrecorder.server.Server.Companion.changeOwner
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStream

class DataWriter(val looper: Looper) {

    private var isCharging = false

    private val chargeDataWriter = BaseWriter(chargeDir)
    private val dischargeDataWriter = BaseWriter(dischargeDir)

    var batchSize = 200
    var flushIntervalMs = 30 * 1000L

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
        if (record.current >= 0) {
            dischargeDataWriter.write(record, isCharging)
            isCharging = false
        } else {
            chargeDataWriter.write(record, !isCharging)
            isCharging = true
        }
    }

    fun close() {
        chargeDataWriter.closeCurrentSegment()
        dischargeDataWriter.closeCurrentSegment()
    }

    fun flushBuffer() {
        chargeDataWriter.flushBuffer()
        dischargeDataWriter.flushBuffer()
    }

    inner class BaseWriter(val dir: File) {
        private var outputStream: OutputStream? = null
        private var startTime: Long = 0L
        private var lastTime: Long = 0L

        private val buffer = StringBuilder(4096)
        private var batchCount = 0

        private val handler: Handler = Handler(looper)
        private val writingRunnable = Runnable {
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
            justSwitchedFromTheOppositeCurrentRecord: Boolean
        ) {
            startNewSegmentIfNeed(justSwitchedFromTheOppositeCurrentRecord)

            buffer.append(record).append("\n")
            batchCount++

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
            justSwitchedFromTheOppositeCurrentRecord: Boolean
        ) {
            val nowTime = System.currentTimeMillis()
            if (// case1 记录超过 24 小时
                (nowTime - startTime > 24 * 60 * 60 * 1000) ||
                // case2 应对电压正负快速变化，允许短时间内续接之前记录
                (justSwitchedFromTheOppositeCurrentRecord && nowTime - lastTime > 30 * 1000) ||
                // case3 还没记录过
                outputStream == null
            ) {
                // 关闭之前的记录，打开新的
                closeCurrentSegment()
                startTime = nowTime
                val fileName = "$nowTime.txt"
                val segmentFile = File(dir, fileName)
                if (!segmentFile.exists() && !segmentFile.createNewFile()) {
                    throw IOException("Failed to create segment file: " + segmentFile.absolutePath)
                }
                changeOwner(segmentFile)
                outputStream = FileOutputStream(segmentFile, true)
            }
        }

        fun flushBuffer() {
            if (batchCount == 0 || outputStream == null) return
            outputStream!!.write(buffer.toString().toByteArray())
            outputStream!!.flush()
            buffer.setLength(0) // 清空 StringBuilder
            batchCount = 0
        }

        fun closeCurrentSegment() {
            flushBuffer()
            if (outputStream != null) {
                try {
                    outputStream!!.close()
                } catch (e: IOException) {
                    Log.e(Server.TAG, "Failed to close segment file", e)
                }
                outputStream = null
            }
        }
    }

    data class PowerRecord(
        val timestamp: Long, val current: Long, val voltage: Long, val packageName: String?,
        val capacity: Int, val isDisplayOn: Int
    ) {
        override fun toString(): String {
            return "$timestamp,$current,$voltage,$packageName,$capacity,$isDisplayOn"
        }
    }

    companion object {
        val powerDir = File(Server.APP_DATA + "/power_data")
        val chargeDir = File(powerDir, "charge")
        val dischargeDir = File(powerDir, "discharge")
    }
}
