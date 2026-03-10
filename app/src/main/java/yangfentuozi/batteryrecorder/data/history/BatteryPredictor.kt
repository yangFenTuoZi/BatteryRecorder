package yangfentuozi.batteryrecorder.data.history

import kotlin.math.roundToInt

// 预测终点统一按 1% 计算，分别给出当前电量与满电的可用时长
private const val SOC_CUTOFF = 1.0
private const val MIN_SCENE_MS = 30 * 60 * 1000L  // 30 分钟
private const val MIN_APP_SCENE_MS = 10 * 60 * 1000L  // 10 分钟
private const val MIN_FILE_COUNT = 3
private const val MAX_DRAIN_RATE_PER_HOUR = 50.0   // %/h，超过视为数据异常

/**
 * 首页场景预测结果。
 */
data class PredictionResult(
    val screenOffCurrentHours: Double?,
    val screenOffFullHours: Double?,
    val screenOnDailyCurrentHours: Double?,
    val screenOnDailyFullHours: Double?,
    val insufficientData: Boolean,
    val confidenceScore: Int
)

object BatteryPredictor {

    /**
     * 根据场景统计计算首页“息屏/亮屏日常”预测。
     *
     * 优先使用分文件中位数 k，避免单个异常文件把整体预测拉偏。
     */
    fun predict(
        sceneStats: SceneStats?,
        currentSoc: Int,
        medianK: Double? = null,
        kCV: Double? = null,
        kEffectiveN: Double = 0.0
    ): PredictionResult {
        if (sceneStats == null || sceneStats.fileCount < MIN_FILE_COUNT
            || sceneStats.totalSocDrop <= 0 || sceneStats.totalEnergyRawMs <= 0
            || sceneStats.totalDurationMs <= 0
        ) {
            return PredictionResult(
                screenOffCurrentHours = null,
                screenOffFullHours = null,
                screenOnDailyCurrentHours = null,
                screenOnDailyFullHours = null,
                insufficientData = true,
                confidenceScore = 0
            )
        }

        // 剩余可用电量（到 SOC_CUTOFF 为止，而非 0%）
        val currentRemaining = (currentSoc - SOC_CUTOFF).coerceAtLeast(0.0)
        val fullRemaining = 100.0 - SOC_CUTOFF

        // k = ΔSOC_total / E_total，单位：% / (raw·ms)
        // 优先使用分文件加权中位数 k，对异常文件更鲁棒
        val k = medianK ?: (sceneStats.totalSocDrop / sceneStats.totalEnergyRawMs)

        // k 合理性校验：反推整体掉电速率，超过阈值视为 SOC 跳变等异常
        val overallDrainPerHour = sceneStats.rawTotalSocDrop / sceneStats.totalDurationMs * 3_600_000.0
        if (overallDrainPerHour > MAX_DRAIN_RATE_PER_HOUR) {
            return PredictionResult(
                screenOffCurrentHours = null,
                screenOffFullHours = null,
                screenOnDailyCurrentHours = null,
                screenOnDailyFullHours = null,
                insufficientData = true,
                confidenceScore = 0
            )
        }

        // 置信度合成
        val cvScore = if (kCV != null) ((0.30 - kCV) / 0.30).coerceIn(0.0, 1.0) else 0.0
        val nScore = ((kEffectiveN - 3.0) / 7.0).coerceIn(0.0, 1.0)
        val confidenceScore = (100 * (0.7 * cvScore + 0.3 * nScore)).roundToInt()

        // 息屏预测：drain_rate = k × P_off (%/ms) → 转 %/h 后算剩余小时
        val screenOffCurrentHours = if (sceneStats.screenOffTotalMs >= MIN_SCENE_MS && sceneStats.screenOffAvgPowerRaw > 0) {
            val drainPerMs = k * sceneStats.screenOffAvgPowerRaw
            currentRemaining / (drainPerMs * 3_600_000.0)
        } else null
        val screenOffFullHours = if (sceneStats.screenOffTotalMs >= MIN_SCENE_MS && sceneStats.screenOffAvgPowerRaw > 0) {
            val drainPerMs = k * sceneStats.screenOffAvgPowerRaw
            fullRemaining / (drainPerMs * 3_600_000.0)
        } else null

        // 亮屏日常预测
        val screenOnCurrentHours = if (sceneStats.screenOnDailyTotalMs >= MIN_SCENE_MS && sceneStats.screenOnDailyAvgPowerRaw > 0) {
            val drainPerMs = k * sceneStats.screenOnDailyAvgPowerRaw
            currentRemaining / (drainPerMs * 3_600_000.0)
        } else null
        val screenOnFullHours = if (sceneStats.screenOnDailyTotalMs >= MIN_SCENE_MS && sceneStats.screenOnDailyAvgPowerRaw > 0) {
            val drainPerMs = k * sceneStats.screenOnDailyAvgPowerRaw
            fullRemaining / (drainPerMs * 3_600_000.0)
        } else null

        return PredictionResult(
            screenOffCurrentHours = screenOffCurrentHours,
            screenOffFullHours = screenOffFullHours,
            screenOnDailyCurrentHours = screenOnCurrentHours,
            screenOnDailyFullHours = screenOnFullHours,
            insufficientData = false,
            confidenceScore = confidenceScore
        )
    }

    /**
     * 计算应用维度的当前剩余时长。
     *
     * 这里仍保留原始时长掉电速率校验，用于过滤 SOC 跳变；
     * 实际剩余时长使用 effective 口径，才能反映“当次记录加权”配置。
     */
    fun predictAppCurrentHours(
        entry: AppStatsEntry,
        currentSoc: Int
    ): Double? {
        if (entry.totalForegroundMs < MIN_APP_SCENE_MS) return null
        if (entry.rawAvgPowerRaw <= 0) return null
        if (entry.rawSocDrop <= 0 || entry.effectiveSocDrop <= 0) return null
        if (entry.effectiveForegroundMs <= 0) return null

        val drainPerHour = entry.rawSocDrop / entry.totalForegroundMs * 3_600_000.0
        if (drainPerHour > MAX_DRAIN_RATE_PER_HOUR) return null

        val currentRemaining = (currentSoc - SOC_CUTOFF).coerceAtLeast(0.0)
        val drainPerMs = entry.effectiveSocDrop / entry.effectiveForegroundMs
        return currentRemaining / (drainPerMs * 3_600_000.0)
    }
}
