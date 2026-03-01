package yangfentuozi.batteryrecorder.data.history

import android.content.Context
import yangfentuozi.batteryrecorder.shared.Constants
import yangfentuozi.batteryrecorder.shared.data.BatteryStatus
import java.io.File

data class SceneStats(
    val screenOffAvgPowerNw: Double,
    val screenOffTotalMs: Long,
    val screenOnDailyAvgPowerNw: Double,
    val screenOnDailyTotalMs: Long,
    val totalEnergyNwMs: Double,
    val totalSocDrop: Double,
    val totalDurationMs: Long,
    val fileCount: Int
) {
    override fun toString(): String =
        "$screenOffAvgPowerNw,$screenOffTotalMs,$screenOnDailyAvgPowerNw,$screenOnDailyTotalMs," +
                "$totalEnergyNwMs,$totalSocDrop,$totalDurationMs,$fileCount"

    companion object {
        fun fromString(s: String): SceneStats? {
            val p = s.split(",")
            if (p.size < 8) return null
            return SceneStats(
                screenOffAvgPowerNw = p[0].toDoubleOrNull() ?: return null,
                screenOffTotalMs = p[1].toLongOrNull() ?: return null,
                screenOnDailyAvgPowerNw = p[2].toDoubleOrNull() ?: return null,
                screenOnDailyTotalMs = p[3].toLongOrNull() ?: return null,
                totalEnergyNwMs = p[4].toDoubleOrNull() ?: return null,
                totalSocDrop = p[5].toDoubleOrNull() ?: return null,
                totalDurationMs = p[6].toLongOrNull() ?: return null,
                fileCount = p[7].toIntOrNull() ?: return null
            )
        }
    }
}

object SceneStatsComputer {

    private const val MAX_FILES = 20

    fun compute(
        context: Context,
        gamePackages: Set<String>,
        recordIntervalMs: Long
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
        val cacheKey = buildCacheKey(files, gamePackages)
        val cacheFile = File(cacheDir, "$cacheKey.csv")
        if (cacheFile.exists()) {
            SceneStats.fromString(cacheFile.readText().trim())?.let { return it }
            cacheFile.delete()
        }

        val maxGapMs = recordIntervalMs * 5

        var offEnergy = 0.0
        var offTime = 0L
        var dailyEnergy = 0.0
        var dailyTime = 0L
        // game 时段也参与总能量计算，保证 drainRate 和 P_avg 口径一致
        var gameEnergy = 0.0
        var gameTime = 0L

        // 掉电量统计（与能量积分同口径）
        var totalCapDrop = 0.0

        for (file in files) {
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

                    // 能量积分（三场景）
                    when {
                        pDisplay == 0 -> {
                            offEnergy += pPower * dt.toDouble()
                            offTime += dt
                        }
                        pDisplay == 1 && (pPkg == null || pPkg !in gamePackages) -> {
                            dailyEnergy += pPower * dt.toDouble()
                            dailyTime += dt
                        }
                        else -> {
                            gameEnergy += pPower * dt.toDouble()
                            gameTime += dt
                        }
                    }

                    // ΔSOC 与能量积分同口径
                    val capDelta = pCap - capacity
                    if (capDelta > 0) {
                        totalCapDrop += capDelta.toDouble()
                    }
                }
            }
        }

        // totalDurationMs 包含所有场景（含 game），保证与 drainRate 口径一致
        val totalMs = offTime + dailyTime + gameTime
        if (totalMs <= 0) return null

        val totalEnergy = offEnergy + dailyEnergy + gameEnergy

        val result = SceneStats(
            screenOffAvgPowerNw = if (offTime > 0) offEnergy / offTime else 0.0,
            screenOffTotalMs = offTime,
            screenOnDailyAvgPowerNw = if (dailyTime > 0) dailyEnergy / dailyTime else 0.0,
            screenOnDailyTotalMs = dailyTime,
            totalEnergyNwMs = totalEnergy,
            totalSocDrop = totalCapDrop,
            totalDurationMs = totalMs,
            fileCount = files.size
        )

        // 写入缓存
        if (!cacheDir.exists()) cacheDir.mkdirs()
        cacheFile.writeText(result.toString())

        return result
    }

    private fun buildCacheKey(files: List<File>, gamePackages: Set<String>): String {
        val filesHash = files.joinToString(",") { "${it.name}:${it.lastModified()}" }.hashCode()
        val gamesHash = gamePackages.sorted().joinToString(",").hashCode()
        return "${filesHash}_${gamesHash}"
    }
}
