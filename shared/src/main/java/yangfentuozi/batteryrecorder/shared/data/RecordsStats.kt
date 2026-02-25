package yangfentuozi.batteryrecorder.shared.data

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import java.io.File
import kotlin.sequences.forEach

@Parcelize
data class RecordsStats(
    val startTime: Long,
    val endTime: Long,
    val startCapacity: Int,
    val endCapacity: Int,
    val screenOnTimeMs: Long,
    val screenOffTimeMs: Long,
    val averagePower: Double
) : Parcelable {
    override fun toString(): String {
        return "$startTime,$endTime,$startCapacity,$endCapacity,$screenOnTimeMs,$screenOffTimeMs,$averagePower"
    }

    companion object {
        fun fromString(stats: String) : RecordsStats? {
            val parts = stats.split(",")
            if (parts.size < 7) return null

            // v1
            val startTime = parts[0].toLongOrNull() ?: return null
            val endTime = parts[1].toLongOrNull() ?: return null
            val startCapacity = parts[2].toIntOrNull() ?: return null
            val endCapacity = parts[3].toIntOrNull() ?: return null
            val screenOnTimeMs = parts[4].toLongOrNull() ?: return null
            val screenOffTimeMs = parts[5].toLongOrNull() ?: return null
            val averagePower = parts[6].toDoubleOrNull() ?: return null
            return RecordsStats(
                startTime, endTime, startCapacity, endCapacity, screenOnTimeMs, screenOffTimeMs, averagePower
            )
        }

        fun parseAndCompute(filePath: File): RecordsStats {
            if (!filePath.exists()) {
                throw IllegalArgumentException("File not found: ${filePath.absolutePath}")
            }

            var startTime = Long.MAX_VALUE
            var endTime = Long.MIN_VALUE
            var startCapacity = -1
            var endCapacity = -1

            var screenOnTime = 0L
            var screenOffTime = 0L

            // 能量积分（μW * ms）
            var totalEnergy = 0.0
            var totalDuration = 0L

            var lastTimestamp: Long? = null
            var lastPower: Long? = null
            var lastDisplayOn: Int? = null

            filePath.bufferedReader().useLines { lines ->
                lines.forEach { raw ->
                    val line = raw.trim()
                    if (line.isEmpty()) return@forEach

                    val record = LineRecord.fromString(line) ?: return@forEach

                    // 起止时间与电量
                    if (record.timestamp < startTime) {
                        startTime = record.timestamp
                        startCapacity = record.capacity
                    }
                    if (record.timestamp > endTime) {
                        endTime = record.timestamp
                        endCapacity = record.capacity
                    }

                    val prevTs = lastTimestamp
                    val prevPower = lastPower
                    val prevDisplay = lastDisplayOn

                    if (prevTs != null && prevPower != null && prevDisplay != null) {
                        val dt = record.timestamp - prevTs
                        if (dt > 0) {
                            // 时间统计
                            if (prevDisplay == 1) {
                                screenOnTime += dt
                            } else {
                                screenOffTime += dt
                            }

                            // 能量积分（sample-and-hold）
                            totalEnergy += prevPower * dt
                            totalDuration += dt
                        }
                    }

                    lastTimestamp = record.timestamp
                    lastPower = record.power
                    lastDisplayOn = record.isDisplayOn
                }
            }

            if (totalDuration <= 0) {
                throw IllegalStateException("Not enough valid samples")
            }

            // 时间加权平均功耗（μW）
            val averagePower = totalEnergy / totalDuration

            return RecordsStats(
                startTime = startTime,
                endTime = endTime,
                startCapacity = startCapacity,
                endCapacity = endCapacity,
                screenOnTimeMs = screenOnTime,
                screenOffTimeMs = screenOffTime,
                averagePower = averagePower
            )
        }

        fun getCachedStats(
            cacheDir: File,
            file: File,
            needCaching: Boolean = true
        ): RecordsStats {
            if (!cacheDir.exists()) cacheDir.mkdirs()

            val cacheFile = File(cacheDir, file.name)
            if (cacheFile.exists()) {
                fromString(cacheFile.readText().trim())?.let { return it }
                cacheFile.delete()
            }
            val recordsStats = parseAndCompute(file)
            if (needCaching) {
                cacheFile.createNewFile()
                cacheFile.writeText(recordsStats.toString())
            }
            return recordsStats
        }
    }
}
