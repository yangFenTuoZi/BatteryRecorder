package yangfentuozi.batteryrecorder.data.history

import java.io.File

object StatisticsUtil {
    private fun parseAndComputePowerStats(filePath: File): PowerStats {
        val lines = filePath.readLines()
            .filter { it.isNotBlank() }

        if (lines.isEmpty()) {
            throw IllegalArgumentException("File is empty")
        }

        var startTime = Long.MAX_VALUE
        var endTime = Long.MIN_VALUE
        var startCapacity = -1
        var endCapacity = -1

        var screenOnTime = 0L
        var screenOffTime = 0L

        var totalPower = 0.0
        var totalIntervals = 0

        var lastTimestamp: Long? = null
        var lastDisplayOn: Int? = null

        for (line in lines) {
            val parts = line.split(",")
            if (parts.size < 5) continue

            val timestamp = parts[0].toLongOrNull() ?: continue
            val power = parts[1].toDoubleOrNull() ?: continue
            val capacity = parts[3].toIntOrNull() ?: continue
            val isDisplayOn = parts[4].toIntOrNull() ?: continue

            // 记录开始和结束
            if (timestamp < startTime) {
                startTime = timestamp
                startCapacity = capacity
            }
            if (timestamp > endTime) {
                endTime = timestamp
                endCapacity = capacity
            }

            // 统计亮屏/息屏时间
            if (lastTimestamp != null && lastDisplayOn != null) {
                val interval = timestamp - lastTimestamp
                if (lastDisplayOn == 1) {
                    screenOnTime += interval
                } else {
                    screenOffTime += interval
                }
            }

            // 累加功耗（简单平均）
            totalPower += power
            totalIntervals++

            lastTimestamp = timestamp
            lastDisplayOn = isDisplayOn
        }

        val averagePower = totalPower / totalIntervals

        return PowerStats(
            startTime = startTime,
            endTime = endTime,
            startCapacity = startCapacity,
            endCapacity = endCapacity,
            screenOnTimeMs = screenOnTime,
            screenOffTimeMs = screenOffTime,
            averagePower = averagePower
        )
    }

    fun getCachedPowerStats(
        cacheDir: File,
        dataDir: File,
        name: String,
        lastestFileName: String? = null
    ): PowerStats {
        if (!cacheDir.exists()) cacheDir.mkdirs()

        val cacheFile = File(cacheDir, name)
        if (cacheFile.exists()) {
            val parts = cacheFile.readText().trim().split(",")
            val startTime = parts[0].toLongOrNull()
            val endTime = parts[1].toLongOrNull()
            val startCapacity = parts[2].toIntOrNull()
            val endCapacity = parts[3].toIntOrNull()
            val screenOnTimeMs = parts[4].toLongOrNull()
            val screenOffTimeMs = parts[5].toLongOrNull()
            val averagePower = parts[6].toDoubleOrNull()
            if (startTime != null &&
                endTime != null &&
                startCapacity != null &&
                endCapacity != null &&
                screenOnTimeMs != null &&
                screenOffTimeMs != null &&
                averagePower != null
            ) {
                return PowerStats(
                    startTime = startTime,
                    endTime = endTime,
                    startCapacity = startCapacity,
                    endCapacity = endCapacity,
                    screenOnTimeMs = screenOnTimeMs,
                    screenOffTimeMs = screenOffTimeMs,
                    averagePower = averagePower
                )
            }
            cacheFile.delete()
        }
        val powerStats = parseAndComputePowerStats(File(dataDir, name))
        if (name !=
            (lastestFileName ?: (dataDir.listFiles()?.filter { it.isFile }
                ?.maxByOrNull { it.lastModified() }?.name))
        ) {
            cacheFile.createNewFile()
            cacheFile.writeText(powerStats.toString())
        }
        return powerStats
    }
}

data class PowerStats(
    val startTime: Long,
    val endTime: Long,
    val startCapacity: Int,
    val endCapacity: Int,
    val screenOnTimeMs: Long,
    val screenOffTimeMs: Long,
    val averagePower: Double
) {
    override fun toString(): String {
        return "$startTime,$endTime,$startCapacity,$endCapacity,$screenOnTimeMs,$screenOffTimeMs,$averagePower"
    }
}
