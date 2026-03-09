package yangfentuozi.batteryrecorder.shared.data

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import java.io.File

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

            var screenOnTime = 0L
            var screenOffTime = 0L

            // 能量积分（μW * ms）
            var totalEnergy = 0.0
            var totalDuration = 0L

            var firstRecord: LineRecord? = null
            var previousRecord: LineRecord? = null

            RecordFileParser.forEachValidRecord(filePath) { record ->
                if (firstRecord == null) firstRecord = record

                val prevRecord = previousRecord
                if (prevRecord != null) {
                    val dt = record.timestamp - prevRecord.timestamp
                    if (prevRecord.isDisplayOn == 1) {
                        screenOnTime += dt
                    } else {
                        screenOffTime += dt
                    }

                    totalEnergy += prevRecord.power * dt
                    totalDuration += dt
                }
                previousRecord = record
            }

            val startRecord = firstRecord
            val endRecord = previousRecord
            if (startRecord == null || endRecord == null || totalDuration <= 0) {
                throw IllegalStateException("Not enough valid samples after filtering: ${filePath.absolutePath}")
            }

            // 时间加权平均功耗（μW）
            val averagePower = totalEnergy / totalDuration

            return RecordsStats(
                startTime = startRecord.timestamp,
                endTime = endRecord.timestamp,
                startCapacity = startRecord.capacity,
                endCapacity = endRecord.capacity,
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
