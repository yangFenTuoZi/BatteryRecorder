package yangfentuozi.batteryrecorder.data.history

import android.content.Context
import java.io.File
import kotlin.math.sqrt

/**
 * 场景统计结果。
 *
 * 同时保留原始与 effective 时长/掉电口径，分别供展示与预测使用。
 */
data class SceneStats(
    val screenOffAvgPowerRaw: Double,
    val screenOffTotalMs: Long,
    val screenOffEffectiveTotalMs: Double,
    val screenOnDailyAvgPowerRaw: Double,
    val screenOnDailyTotalMs: Long,
    val screenOnDailyEffectiveTotalMs: Double,
    val totalEnergyRawMs: Double,
    val totalSocDrop: Double,
    val totalDurationMs: Long,
    val fileCount: Int,
    val rawTotalSocDrop: Double
) {
    override fun toString(): String =
        "$screenOffAvgPowerRaw,$screenOffTotalMs,$screenOffEffectiveTotalMs," +
                "$screenOnDailyAvgPowerRaw,$screenOnDailyTotalMs,$screenOnDailyEffectiveTotalMs," +
                "$totalEnergyRawMs,$totalSocDrop,$totalDurationMs,$fileCount,$rawTotalSocDrop"

    companion object {
        /**
         * 兼容历史缓存格式。
         *
         * 旧版本缺少 effective 时长或 rawTotalSocDrop 时，用原始口径回填。
         */
        fun fromString(s: String): SceneStats? {
            val p = s.split(",")
            if (p.size < 8) return null
            if (p.size == 8) {
                val totalSocDrop = p[5].toDoubleOrNull() ?: return null
                return SceneStats(
                    screenOffAvgPowerRaw = p[0].toDoubleOrNull() ?: return null,
                    screenOffTotalMs = p[1].toLongOrNull() ?: return null,
                    screenOffEffectiveTotalMs = (p[1].toLongOrNull() ?: return null).toDouble(),
                    screenOnDailyAvgPowerRaw = p[2].toDoubleOrNull() ?: return null,
                    screenOnDailyTotalMs = p[3].toLongOrNull() ?: return null,
                    screenOnDailyEffectiveTotalMs = (p[3].toLongOrNull() ?: return null).toDouble(),
                    totalEnergyRawMs = p[4].toDoubleOrNull() ?: return null,
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
                    screenOffAvgPowerRaw = p[0].toDoubleOrNull() ?: return null,
                    screenOffTotalMs = offTotalMs,
                    screenOffEffectiveTotalMs = offTotalMs.toDouble(),
                    screenOnDailyAvgPowerRaw = p[2].toDoubleOrNull() ?: return null,
                    screenOnDailyTotalMs = dailyTotalMs,
                    screenOnDailyEffectiveTotalMs = dailyTotalMs.toDouble(),
                    totalEnergyRawMs = p[4].toDoubleOrNull() ?: return null,
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
                screenOffAvgPowerRaw = p[0].toDoubleOrNull() ?: return null,
                screenOffTotalMs = offTotalMs,
                screenOffEffectiveTotalMs = p[2].toDoubleOrNull() ?: return null,
                screenOnDailyAvgPowerRaw = p[3].toDoubleOrNull() ?: return null,
                screenOnDailyTotalMs = dailyTotalMs,
                screenOnDailyEffectiveTotalMs = p[5].toDoubleOrNull() ?: return null,
                totalEnergyRawMs = p[6].toDoubleOrNull() ?: return null,
                totalSocDrop = p[7].toDoubleOrNull() ?: return null,
                totalDurationMs = p[8].toLongOrNull() ?: return null,
                fileCount = p[9].toIntOrNull() ?: return null,
                rawTotalSocDrop = p[10].toDoubleOrNull() ?: return null
            )
        }
    }
}

/**
 * displayStats 面向 UI 展示，predictionStats 面向预测算法。
 */
data class SceneComputeResult(
    val displayStats: SceneStats,
    val predictionStats: SceneStats,
    val medianK: Double?,
    val kCV: Double?,
    val kEffectiveN: Double
)

object SceneStatsComputer {

    /**
     * 聚合最近放电文件的场景统计，并生成展示/预测双口径结果。
     */
    fun compute(
        context: Context,
        request: StatisticsRequest,
        currentDischargeFileName: String? = null,
    ): SceneComputeResult? {
        val files = DischargeRecordScanner.listRecentDischargeFiles(
            context = context,
            recentFileCount = request.sceneStatsRecentFileCount
        )
        if (files.isEmpty()) return null

        val cacheDir = File(context.cacheDir, "scene_stats")
        val cacheKey = buildCacheKey(
            files = files,
            request = request,
            currentDischargeFileName = currentDischargeFileName
        )
        val cacheFile = File(cacheDir, "$cacheKey.csv")
        if (cacheFile.exists()) {
            val cacheLines = cacheFile.readText().trim().lines()
            val displayStats = cacheLines.getOrNull(0)?.let { SceneStats.fromString(it) }
            val predictionStats = cacheLines.getOrNull(1)?.let { SceneStats.fromString(it) }
            if (displayStats != null && predictionStats != null) {
                val cachedMedianK = cacheLines.getOrNull(2)?.toDoubleOrNull()
                val cachedKCV = cacheLines.getOrNull(3)?.toDoubleOrNull()
                val cachedKEffN = cacheLines.getOrNull(4)?.toDoubleOrNull() ?: 0.0
                return SceneComputeResult(displayStats, predictionStats, cachedMedianK, cachedKCV, cachedKEffN)
            }
            cacheFile.delete()
        }

        var rawOffEnergy = 0.0
        var offTime = 0L
        var rawDailyEnergy = 0.0
        var dailyTime = 0L
        var rawGameEnergy = 0.0
        var gameTime = 0L

        var rawTotalCapDrop = 0.0
        var effectiveTotalCapDrop = 0.0
        var usedFileCount = 0

        var effectiveOffEnergy = 0.0
        var effectiveOffTimeWeighted = 0.0
        var effectiveDailyEnergy = 0.0
        var effectiveDailyTimeWeighted = 0.0
        var effectiveGameEnergy = 0.0
        var effectiveGameTimeWeighted = 0.0

        data class FileKInput(val capDrop: Double, val energy: Double)
        val fileKInputs = mutableListOf<FileKInput>()
        val gamePackages = request.gamePackages

        DischargeRecordScanner.scan(
            context = context,
            request = request,
            currentDischargeFileName = currentDischargeFileName
        ) { acceptedFile ->
            var fileRawOffEnergy = 0.0
            var fileOffTime = 0L
            var fileRawDailyEnergy = 0.0
            var fileDailyTime = 0L
            var fileRawGameEnergy = 0.0
            var fileGameTime = 0L

            var fileEffectiveOffEnergy = 0.0
            var fileEffectiveOffTime = 0.0
            var fileEffectiveDailyEnergy = 0.0
            var fileEffectiveDailyTime = 0.0
            var fileEffectiveGameEnergy = 0.0
            var fileEffectiveGameTime = 0.0

            acceptedFile.intervals.forEach { interval ->
                when {
                    !interval.isDisplayOn -> {
                        fileRawOffEnergy += interval.energyRawMs
                        fileOffTime += interval.durationMs
                        fileEffectiveOffEnergy += interval.effectiveEnergyRawMs
                        fileEffectiveOffTime += interval.effectiveDurationMs
                    }
                    interval.packageName == null || interval.packageName !in gamePackages -> {
                        fileRawDailyEnergy += interval.energyRawMs
                        fileDailyTime += interval.durationMs
                        fileEffectiveDailyEnergy += interval.effectiveEnergyRawMs
                        fileEffectiveDailyTime += interval.effectiveDurationMs
                    }
                    else -> {
                        fileRawGameEnergy += interval.energyRawMs
                        fileGameTime += interval.durationMs
                        fileEffectiveGameEnergy += interval.effectiveEnergyRawMs
                        fileEffectiveGameTime += interval.effectiveDurationMs
                    }
                }
            }

            usedFileCount += 1
            fileKInputs += FileKInput(
                capDrop = acceptedFile.rawTotalCapDrop,
                energy = acceptedFile.rawTotalEnergyRawMs
            )
            rawOffEnergy += fileRawOffEnergy
            offTime += fileOffTime
            rawDailyEnergy += fileRawDailyEnergy
            dailyTime += fileDailyTime
            rawGameEnergy += fileRawGameEnergy
            gameTime += fileGameTime
            rawTotalCapDrop += acceptedFile.rawTotalCapDrop
            effectiveOffEnergy += fileEffectiveOffEnergy
            effectiveOffTimeWeighted += fileEffectiveOffTime
            effectiveDailyEnergy += fileEffectiveDailyEnergy
            effectiveDailyTimeWeighted += fileEffectiveDailyTime
            effectiveGameEnergy += fileEffectiveGameEnergy
            effectiveGameTimeWeighted += fileEffectiveGameTime
            effectiveTotalCapDrop += acceptedFile.effectiveTotalCapDrop
        }

        // 中位数 k 只使用掉电量足够的文件，降低短样本噪声。
        val minCapDropForMedian = 3.0
        val kEntries = fileKInputs.mapNotNull { input ->
            if (input.energy > 0 && input.capDrop >= minCapDropForMedian) {
                input.capDrop / input.energy to input.capDrop
            } else {
                null
            }
        }
        val medianK = weightedMedian(kEntries)
        val kCV = weightedCV(kEntries)
        val kEffectiveN = effectiveSampleCount(kEntries)

        val totalMs = offTime + dailyTime + gameTime
        if (totalMs <= 0L) return null

        val rawTotalEnergy = rawOffEnergy + rawDailyEnergy + rawGameEnergy
        val effectiveTotalEnergy = effectiveOffEnergy + effectiveDailyEnergy + effectiveGameEnergy

        // 展示口径保持原始观测值，不被当次加权策略影响。
        val displayStats = SceneStats(
            screenOffAvgPowerRaw = if (offTime > 0) rawOffEnergy / offTime.toDouble() else 0.0,
            screenOffTotalMs = offTime,
            screenOffEffectiveTotalMs = offTime.toDouble(),
            screenOnDailyAvgPowerRaw = if (dailyTime > 0) rawDailyEnergy / dailyTime.toDouble() else 0.0,
            screenOnDailyTotalMs = dailyTime,
            screenOnDailyEffectiveTotalMs = dailyTime.toDouble(),
            totalEnergyRawMs = rawTotalEnergy,
            totalSocDrop = rawTotalCapDrop,
            totalDurationMs = totalMs,
            fileCount = usedFileCount,
            rawTotalSocDrop = rawTotalCapDrop
        )

        // 预测口径使用 effective 能量/时长/掉电，反映当次记录加权后的趋势。
        val predictionStats = SceneStats(
            screenOffAvgPowerRaw = if (effectiveOffTimeWeighted > 0) effectiveOffEnergy / effectiveOffTimeWeighted else 0.0,
            screenOffTotalMs = offTime,
            screenOffEffectiveTotalMs = effectiveOffTimeWeighted,
            screenOnDailyAvgPowerRaw = if (effectiveDailyTimeWeighted > 0) effectiveDailyEnergy / effectiveDailyTimeWeighted else 0.0,
            screenOnDailyTotalMs = dailyTime,
            screenOnDailyEffectiveTotalMs = effectiveDailyTimeWeighted,
            totalEnergyRawMs = effectiveTotalEnergy,
            totalSocDrop = effectiveTotalCapDrop,
            totalDurationMs = totalMs,
            fileCount = usedFileCount,
            rawTotalSocDrop = rawTotalCapDrop
        )

        if (!cacheDir.exists()) cacheDir.mkdirs()
        cacheFile.writeText(
            displayStats.toString() + "\n" + predictionStats.toString() + "\n" +
                    (medianK ?: "") + "\n" + (kCV ?: "") + "\n" + kEffectiveN
        )

        return SceneComputeResult(displayStats, predictionStats, medianK, kCV, kEffectiveN)
    }

    private fun buildCacheKey(
        files: List<File>,
        request: StatisticsRequest,
        currentDischargeFileName: String?,
    ): String {
        val gamePackages = request.gamePackages
        val recentFileCount = request.sceneStatsRecentFileCount
        val maxGapMs = DischargeRecordScanner.computeMaxGapMs(request.recordIntervalMs)
        val predCurrentSessionWeightEnabled = request.predCurrentSessionWeightEnabled
        val predCurrentSessionWeightMaxX100 = request.predCurrentSessionWeightMaxX100
        val predCurrentSessionWeightHalfLifeMin = request.predCurrentSessionWeightHalfLifeMin
        // 带上文件长度，避免仅依赖 lastModified 命中陈旧缓存。
        val filesHash = files.joinToString(",") { "${it.name}:${it.lastModified()}:${it.length()}" }.hashCode()
        val gamesHash = gamePackages.sorted().joinToString(",").hashCode()
        val currentNameHash = (currentDischargeFileName ?: "").hashCode()
        return "${HISTORY_STATS_CACHE_VERSION}_${filesHash}_${gamesHash}_${recentFileCount}_${maxGapMs}_" +
                "${predCurrentSessionWeightEnabled}_${predCurrentSessionWeightMaxX100}_${predCurrentSessionWeightHalfLifeMin}_${currentNameHash}"
    }

    /** 加权变异系数 CV = σ_weighted / μ_weighted。 */
    private fun weightedCV(entries: List<Pair<Double, Double>>): Double? {
        if (entries.size < 2) return null
        val sumW = entries.sumOf { it.second }
        if (sumW <= 0) return null
        val kMean = entries.sumOf { it.first * it.second } / sumW
        if (kMean <= 0 || !kMean.isFinite()) return null
        val variance = entries.sumOf { it.second * (it.first - kMean) * (it.first - kMean) } / sumW
        if (!variance.isFinite()) return null
        return sqrt(variance) / kMean
    }

    /** 加权有效样本量 n_eff = (Σw)^2 / Σ(w^2)。 */
    private fun effectiveSampleCount(entries: List<Pair<Double, Double>>): Double {
        if (entries.isEmpty()) return 0.0
        val sumW = entries.sumOf { it.second }
        val sumW2 = entries.sumOf { it.second * it.second }
        if (sumW2 <= 0) return 0.0
        return sumW * sumW / sumW2
    }

    /** 加权中位数：按 k 升序累积权重到 50% 时线性插值。 */
    private fun weightedMedian(entries: List<Pair<Double, Double>>): Double? {
        if (entries.size < 2) return null
        val sorted = entries.sortedBy { it.first }
        val totalWeight = sorted.sumOf { it.second }
        if (totalWeight <= 0) return null
        val halfWeight = totalWeight * 0.5
        var cumulative = 0.0
        for (i in sorted.indices) {
            val prev = cumulative
            cumulative += sorted[i].second
            if (cumulative >= halfWeight) {
                if (i == 0 || prev >= halfWeight) return sorted[i].first
                val fraction = (halfWeight - prev) / sorted[i].second
                return sorted[i - 1].first + (sorted[i].first - sorted[i - 1].first) * fraction
            }
        }
        return sorted.last().first
    }
}
