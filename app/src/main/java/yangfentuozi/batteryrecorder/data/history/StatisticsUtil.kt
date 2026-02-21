package yangfentuozi.batteryrecorder.data.history

import java.io.File

object StatisticsUtil {
    private fun parseAndComputePowerStats(filePath: File): PowerStats {
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
        var lastPower: Double? = null
        var lastDisplayOn: Int? = null

        filePath.bufferedReader().useLines { lines ->
            lines.forEach { raw ->
                val line = raw.trim()
                if (line.isEmpty()) return@forEach

                val parts = line.split(",")
                if (parts.size < 5) return@forEach

                val timestamp = parts[0].toLongOrNull() ?: return@forEach
                val power = parts[1].toDoubleOrNull() ?: return@forEach
                val capacity = parts[3].toIntOrNull() ?: return@forEach
                val isDisplayOn = parts[4].toIntOrNull() ?: return@forEach

                // 起止时间与电量
                if (timestamp < startTime) {
                    startTime = timestamp
                    startCapacity = capacity
                }
                if (timestamp > endTime) {
                    endTime = timestamp
                    endCapacity = capacity
                }

                val prevTs = lastTimestamp
                val prevPower = lastPower
                val prevDisplay = lastDisplayOn

                if (prevTs != null && prevPower != null && prevDisplay != null) {
                    val dt = timestamp - prevTs
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

                lastTimestamp = timestamp
                lastPower = power
                lastDisplayOn = isDisplayOn
            }
        }

        if (totalDuration <= 0) {
            throw IllegalStateException("Not enough valid samples")
        }

        // 时间加权平均功耗（μW）
        val averagePower = totalEnergy / totalDuration

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
            if (parts.size >= 7) {
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
