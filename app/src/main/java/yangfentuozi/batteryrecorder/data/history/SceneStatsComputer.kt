package yangfentuozi.batteryrecorder.data.history

import android.content.Context
import yangfentuozi.batteryrecorder.shared.Constants
import yangfentuozi.batteryrecorder.shared.data.BatteryStatus
import java.io.File
import java.io.RandomAccessFile
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.min

data class SceneStats(
    val screenOffAvgPowerNw: Double,
    val screenOffTotalMs: Long,
    val screenOffEffectiveTotalMs: Double,
    val screenOnDailyAvgPowerNw: Double,
    val screenOnDailyTotalMs: Long,
    val screenOnDailyEffectiveTotalMs: Double,
    val totalEnergyNwMs: Double,
    val totalSocDrop: Double,
    val totalDurationMs: Long,
    val fileCount: Int,
    val rawTotalSocDrop: Double
) {
    override fun toString(): String =
        "$screenOffAvgPowerNw,$screenOffTotalMs,$screenOffEffectiveTotalMs," +
                "$screenOnDailyAvgPowerNw,$screenOnDailyTotalMs,$screenOnDailyEffectiveTotalMs," +
                "$totalEnergyNwMs,$totalSocDrop,$totalDurationMs,$fileCount,$rawTotalSocDrop"

    companion object {
        fun fromString(s: String): SceneStats? {
            val p = s.split(",")
            if (p.size < 8) return null
            if (p.size == 8) {
                val totalSocDrop = p[5].toDoubleOrNull() ?: return null
                return SceneStats(
                    screenOffAvgPowerNw = p[0].toDoubleOrNull() ?: return null,
                    screenOffTotalMs = p[1].toLongOrNull() ?: return null,
                    screenOffEffectiveTotalMs = (p[1].toLongOrNull() ?: return null).toDouble(),
                    screenOnDailyAvgPowerNw = p[2].toDoubleOrNull() ?: return null,
                    screenOnDailyTotalMs = p[3].toLongOrNull() ?: return null,
                    screenOnDailyEffectiveTotalMs = (p[3].toLongOrNull() ?: return null).toDouble(),
                    totalEnergyNwMs = p[4].toDoubleOrNull() ?: return null,
                    totalSocDrop = totalSocDrop,
                    totalDurationMs = p[6].toLongOrNull() ?: return null,
                    fileCount = p[7].toIntOrNull() ?: return null,
                    rawTotalSocDrop = totalSocDrop
                )
            }
            if (p.size == 9) {
                val offTotalMs = p[1].toLongOrNull() ?: return null
                val dailyTotalMs = p[3].toLongOrNull() ?: return null
                return SceneStats(
                    screenOffAvgPowerNw = p[0].toDoubleOrNull() ?: return null,
                    screenOffTotalMs = offTotalMs,
                    screenOffEffectiveTotalMs = offTotalMs.toDouble(),
                    screenOnDailyAvgPowerNw = p[2].toDoubleOrNull() ?: return null,
                    screenOnDailyTotalMs = dailyTotalMs,
                    screenOnDailyEffectiveTotalMs = dailyTotalMs.toDouble(),
                    totalEnergyNwMs = p[4].toDoubleOrNull() ?: return null,
                    totalSocDrop = p[5].toDoubleOrNull() ?: return null,
                    totalDurationMs = p[6].toLongOrNull() ?: return null,
                    fileCount = p[7].toIntOrNull() ?: return null,
                    rawTotalSocDrop = p[8].toDoubleOrNull() ?: return null
                )
            }

            if (p.size < 11) return null

            val offTotalMs = p[1].toLongOrNull() ?: return null
            val dailyTotalMs = p[4].toLongOrNull() ?: return null
            return SceneStats(
                screenOffAvgPowerNw = p[0].toDoubleOrNull() ?: return null,
                screenOffTotalMs = offTotalMs,
                screenOffEffectiveTotalMs = p[2].toDoubleOrNull() ?: return null,
                screenOnDailyAvgPowerNw = p[3].toDoubleOrNull() ?: return null,
                screenOnDailyTotalMs = dailyTotalMs,
                screenOnDailyEffectiveTotalMs = p[5].toDoubleOrNull() ?: return null,
                totalEnergyNwMs = p[6].toDoubleOrNull() ?: return null,
                totalSocDrop = p[7].toDoubleOrNull() ?: return null,
                totalDurationMs = p[8].toLongOrNull() ?: return null,
                fileCount = p[9].toIntOrNull() ?: return null,
                rawTotalSocDrop = p[10].toDoubleOrNull() ?: return null
            )
        }
    }
}

object SceneStatsComputer {

    private const val MAX_FILES = 20
    private const val MAX_GAP_FACTOR = 5
    private const val MAX_DRAIN_RATE_PER_HOUR = 50.0   // %/h，超过视为数据异常
    private const val MIN_CURRENT_SESSION_MS = 10 * 60 * 1000L
    private const val MIN_CURRENT_SESSION_SOC_DROP = 1.0
    private const val CACHE_VERSION = 2

    fun compute(
        context: Context,
        gamePackages: Set<String>,
        recordIntervalMs: Long,
        currentDischargeFileName: String? = null,
        predCurrentSessionWeightEnabled: Boolean = true,
        predCurrentSessionWeightMaxX100: Int = 300,
        predCurrentSessionWeightHalfLifeMin: Long = 30L
    ): SceneStats? {
        val dataDir = File(
            File(context.dataDir, Constants.APP_POWER_DATA_PATH),
            BatteryStatus.Discharging.dataDirName
        )
        if (!dataDir.isDirectory) return null

        val files = dataDir.listFiles()?.filter { it.isFile }
            ?.sortedByDescending { it.lastModified() }
            ?.take(MAX_FILES) ?: return null
        if (files.isEmpty()) return null

        // 缓存检查
        val cacheDir = File(context.cacheDir, "scene_stats")
        val cacheKey = buildCacheKey(
            files = files,
            gamePackages = gamePackages,
            recordIntervalMs = recordIntervalMs,
            currentDischargeFileName = currentDischargeFileName,
            predCurrentSessionWeightEnabled = predCurrentSessionWeightEnabled,
            predCurrentSessionWeightMaxX100 = predCurrentSessionWeightMaxX100,
            predCurrentSessionWeightHalfLifeMin = predCurrentSessionWeightHalfLifeMin
        )
        val cacheFile = File(cacheDir, "$cacheKey.csv")
        if (cacheFile.exists()) {
            SceneStats.fromString(cacheFile.readText().trim())?.let { return it }
            cacheFile.delete()
        }

        val maxGapMs = recordIntervalMs * MAX_GAP_FACTOR

        var rawOffEnergy = 0.0
        var offTime = 0L
        var rawDailyEnergy = 0.0
        var dailyTime = 0L
        // game 时段也参与总能量计算，保证 drainRate 和 P_avg 口径一致
        var rawGameEnergy = 0.0
        var gameTime = 0L

        // 掉电量统计（与能量积分同口径）
        var rawTotalCapDrop = 0.0
        var effectiveTotalCapDrop = 0.0
        var usedFileCount = 0

        var effectiveOffEnergy = 0.0
        var effectiveOffTimeWeighted = 0.0
        var effectiveDailyEnergy = 0.0
        var effectiveDailyTimeWeighted = 0.0
        var effectiveGameEnergy = 0.0
        var effectiveGameTimeWeighted = 0.0

        val maxMultiplier = (predCurrentSessionWeightMaxX100 / 100.0).coerceIn(1.0, 5.0)
        val halfLifeMs = predCurrentSessionWeightHalfLifeMin.coerceIn(1L, 24 * 60L) * 60_000.0
        val enableTimeDecayWeight = predCurrentSessionWeightEnabled &&
                currentDischargeFileName != null &&
                maxMultiplier > 1.0 &&
                halfLifeMs > 0.0

        for (file in files) {
            val isCurrentFile = enableTimeDecayWeight && file.name == currentDischargeFileName
            val fileEndTs = if (isCurrentFile) findFileEndTimestamp(file) else null

            var fileRawOffEnergy = 0.0
            var fileOffTime = 0L
            var fileRawDailyEnergy = 0.0
            var fileDailyTime = 0L
            var fileRawGameEnergy = 0.0
            var fileGameTime = 0L
            var fileCapDrop = 0.0

            var fileWeightedOffEnergy = 0.0
            var fileWeightedOffTime = 0.0
            var fileWeightedDailyEnergy = 0.0
            var fileWeightedDailyTime = 0.0
            var fileWeightedGameEnergy = 0.0
            var fileWeightedGameTime = 0.0
            var fileWeightedCapDrop = 0.0

            var prevTs: Long? = null
            var prevPower: Long? = null
            var prevDisplay: Int? = null
            var prevPkg: String? = null
            var prevCap: Int? = null

            file.bufferedReader().useLines { lines ->
                for (raw in lines) {
                    val line = raw.trim()
                    if (line.isEmpty()) continue

                    val parts = line.split(",")
                    if (parts.size < 6) continue

                    val ts = parts[0].toLongOrNull() ?: continue
                    val power = parts[1].toLongOrNull() ?: continue
                    val pkg = parts[2]
                    val capacity = parts[3].toIntOrNull() ?: continue
                    val displayOn = parts[4].toIntOrNull() ?: continue

                    val pTs = prevTs
                    val pPower = prevPower
                    val pDisplay = prevDisplay
                    val pPkg = prevPkg
                    val pCap = prevCap

                    prevTs = ts
                    prevPower = power
                    prevDisplay = displayOn
                    prevPkg = pkg
                    prevCap = capacity

                    if (pTs == null || pPower == null || pDisplay == null || pCap == null) continue

                    val dt = ts - pTs
                    if (dt <= 0 || dt > maxGapMs) continue

                    val energyNwMs = (pPower.toDouble() + power.toDouble()) * 0.5 * dt.toDouble()
                    val weight = if (isCurrentFile && fileEndTs != null) {
                        val midTs = pTs + dt / 2
                        computeTimeDecayWeight(
                            endTs = fileEndTs,
                            midTs = midTs,
                            maxMultiplier = maxMultiplier,
                            halfLifeMs = halfLifeMs
                        )
                    } else 1.0

                    // 能量积分（三场景）
                    when {
                        pDisplay == 0 -> {
                            fileRawOffEnergy += energyNwMs
                            fileOffTime += dt
                            fileWeightedOffEnergy += energyNwMs * weight
                            fileWeightedOffTime += dt.toDouble() * weight
                        }
                        pDisplay == 1 && (pPkg == null || pPkg !in gamePackages) -> {
                            fileRawDailyEnergy += energyNwMs
                            fileDailyTime += dt
                            fileWeightedDailyEnergy += energyNwMs * weight
                            fileWeightedDailyTime += dt.toDouble() * weight
                        }
                        else -> {
                            fileRawGameEnergy += energyNwMs
                            fileGameTime += dt
                            fileWeightedGameEnergy += energyNwMs * weight
                            fileWeightedGameTime += dt.toDouble() * weight
                        }
                    }

                    // ΔSOC 与能量积分同口径
                    val capDelta = pCap - capacity
                    if (capDelta > 0) {
                        fileCapDrop += capDelta.toDouble()
                        fileWeightedCapDrop += capDelta.toDouble() * weight
                    }
                }
            }

            val fileTotalMs = fileOffTime + fileDailyTime + fileGameTime
            val fileTotalEnergy = fileRawOffEnergy + fileRawDailyEnergy + fileRawGameEnergy
            if (fileTotalMs <= 0 || fileTotalEnergy <= 0.0 || fileCapDrop <= 0.0) continue

            // 按文件反推掉电速率，剔除 SOC 跳变等异常文件，避免全量一票否决
            val fileDrainPerHour = fileCapDrop / fileTotalMs.toDouble() * 3_600_000.0
            if (fileDrainPerHour > MAX_DRAIN_RATE_PER_HOUR) continue

            val useWeightedCurrentSession = isCurrentFile &&
                    fileEndTs != null &&
                    fileTotalMs >= MIN_CURRENT_SESSION_MS &&
                    fileCapDrop >= MIN_CURRENT_SESSION_SOC_DROP

            usedFileCount++
            rawOffEnergy += fileRawOffEnergy
            offTime += fileOffTime
            rawDailyEnergy += fileRawDailyEnergy
            dailyTime += fileDailyTime
            rawGameEnergy += fileRawGameEnergy
            gameTime += fileGameTime
            rawTotalCapDrop += fileCapDrop

            if (useWeightedCurrentSession) {
                effectiveOffEnergy += fileWeightedOffEnergy
                effectiveOffTimeWeighted += fileWeightedOffTime
                effectiveDailyEnergy += fileWeightedDailyEnergy
                effectiveDailyTimeWeighted += fileWeightedDailyTime
                effectiveGameEnergy += fileWeightedGameEnergy
                effectiveGameTimeWeighted += fileWeightedGameTime
                effectiveTotalCapDrop += fileWeightedCapDrop
            } else {
                effectiveOffEnergy += fileRawOffEnergy
                effectiveOffTimeWeighted += fileOffTime.toDouble()
                effectiveDailyEnergy += fileRawDailyEnergy
                effectiveDailyTimeWeighted += fileDailyTime.toDouble()
                effectiveGameEnergy += fileRawGameEnergy
                effectiveGameTimeWeighted += fileGameTime.toDouble()
                effectiveTotalCapDrop += fileCapDrop
            }
        }

        // totalDurationMs 包含所有场景（含 game），保证与 drainRate 口径一致
        val totalMs = offTime + dailyTime + gameTime
        if (totalMs <= 0) return null

            val effectiveTotalEnergy = effectiveOffEnergy + effectiveDailyEnergy + effectiveGameEnergy

        val result = SceneStats(
            screenOffAvgPowerNw = if (effectiveOffTimeWeighted > 0) effectiveOffEnergy / effectiveOffTimeWeighted else 0.0,
            screenOffTotalMs = offTime,
            screenOffEffectiveTotalMs = effectiveOffTimeWeighted,
            screenOnDailyAvgPowerNw = if (effectiveDailyTimeWeighted > 0) effectiveDailyEnergy / effectiveDailyTimeWeighted else 0.0,
            screenOnDailyTotalMs = dailyTime,
            screenOnDailyEffectiveTotalMs = effectiveDailyTimeWeighted,
            totalEnergyNwMs = effectiveTotalEnergy,
            totalSocDrop = effectiveTotalCapDrop,
            totalDurationMs = totalMs,
            fileCount = usedFileCount,
            rawTotalSocDrop = rawTotalCapDrop
        )

        // 写入缓存
        if (!cacheDir.exists()) cacheDir.mkdirs()
        cacheFile.writeText(result.toString())

        return result
    }

    private fun buildCacheKey(
        files: List<File>,
        gamePackages: Set<String>,
        recordIntervalMs: Long,
        currentDischargeFileName: String?,
        predCurrentSessionWeightEnabled: Boolean,
        predCurrentSessionWeightMaxX100: Int,
        predCurrentSessionWeightHalfLifeMin: Long
    ): String {
        val filesHash = files.joinToString(",") { "${it.name}:${it.lastModified()}" }.hashCode()
        val gamesHash = gamePackages.sorted().joinToString(",").hashCode()
        val currentNameHash = (currentDischargeFileName ?: "").hashCode()
        return "${CACHE_VERSION}_${filesHash}_${gamesHash}_${recordIntervalMs}_${MAX_GAP_FACTOR}_" +
                "${predCurrentSessionWeightEnabled}_${predCurrentSessionWeightMaxX100}_${predCurrentSessionWeightHalfLifeMin}_${currentNameHash}"
    }

    private fun findFileEndTimestamp(file: File): Long? {
        val length = file.length()
        if (length <= 0L) return null

        val maxRead = 128 * 1024
        var readSize = 4 * 1024
        while (readSize <= maxRead) {
            val bytes = readTailBytes(file, min(readSize.toLong(), length).toInt())
            val ts = parseLastTimestampFromTail(bytes)
            if (ts != null) return ts
            readSize *= 2
        }

        return null
    }

    private fun computeTimeDecayWeight(
        endTs: Long,
        midTs: Long,
        maxMultiplier: Double,
        halfLifeMs: Double
    ): Double {
        if (maxMultiplier <= 1.0 || halfLifeMs <= 0.0) return 1.0
        val ageMs = (endTs - midTs).toDouble().coerceAtLeast(0.0)
        val decay = exp(-ln(2.0) * ageMs / halfLifeMs)
        return 1.0 + (maxMultiplier - 1.0) * decay
    }

    private fun readTailBytes(file: File, size: Int): ByteArray {
        RandomAccessFile(file, "r").use { raf ->
            val length = raf.length()
            val readSize = size.coerceAtLeast(1).toLong()
            val start = (length - readSize).coerceAtLeast(0L)
            raf.seek(start)
            val bytes = ByteArray((length - start).toInt())
            raf.readFully(bytes)
            return bytes
        }
    }

    private fun parseLastTimestampFromTail(bytes: ByteArray): Long? {
        if (bytes.isEmpty()) return null
        val text = String(bytes, Charsets.UTF_8)
        var end = text.length
        while (end > 0) {
            while (end > 0 && (text[end - 1] == '\n' || text[end - 1] == '\r')) end--
            if (end <= 0) return null
            var start = text.lastIndexOf('\n', end - 1)
            if (start < 0) start = 0 else start += 1
            val line = text.substring(start, end).trim()
            val comma = line.indexOf(',')
            if (comma > 0) {
                val ts = line.substring(0, comma).trim().toLongOrNull()
                if (ts != null) return ts
            }
            end = start - 1
        }
        return null
    }
}
