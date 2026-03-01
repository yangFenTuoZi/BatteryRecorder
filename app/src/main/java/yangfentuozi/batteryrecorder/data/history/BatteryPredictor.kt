package yangfentuozi.batteryrecorder.data.history

private const val SOC_CUTOFF = 5.0
private const val MIN_SCENE_MS = 30 * 60 * 1000L  // 30 分钟
private const val MIN_FILE_COUNT = 3
private const val MAX_DRAIN_RATE_PER_HOUR = 50.0   // %/h，超过视为数据异常

data class PredictionResult(
    val screenOffHours: Double?,
    val screenOnDailyHours: Double?,
    val insufficientData: Boolean
)

object BatteryPredictor {

    fun predict(sceneStats: SceneStats?, currentSoc: Int): PredictionResult {
        if (sceneStats == null || sceneStats.fileCount < MIN_FILE_COUNT
            || sceneStats.totalSocDrop <= 0 || sceneStats.totalEnergyNwMs <= 0
            || sceneStats.totalDurationMs <= 0
        ) {
            return PredictionResult(null, null, insufficientData = true)
        }

        val remaining = currentSoc - SOC_CUTOFF
        if (remaining <= 0) {
            return PredictionResult(0.0, 0.0, insufficientData = false)
        }

        // k = ΔSOC_total / E_total，单位：% / (nW·ms)
        val k = sceneStats.totalSocDrop / sceneStats.totalEnergyNwMs

        // k 合理性校验：反推整体掉电速率，超过阈值视为 SOC 跳变等异常
        val overallDrainPerHour = sceneStats.rawTotalSocDrop / sceneStats.totalDurationMs * 3_600_000.0
        if (overallDrainPerHour > MAX_DRAIN_RATE_PER_HOUR) {
            return PredictionResult(null, null, insufficientData = true)
        }

        // 息屏预测：drain_rate = k × P_off (%/ms) → 转 %/h 后算剩余小时
        val screenOffHours = if (sceneStats.screenOffTotalMs >= MIN_SCENE_MS && sceneStats.screenOffAvgPowerNw > 0) {
            val drainPerMs = k * sceneStats.screenOffAvgPowerNw
            remaining / (drainPerMs * 3_600_000.0)
        } else null

        // 亮屏日常预测
        val screenOnHours = if (sceneStats.screenOnDailyTotalMs >= MIN_SCENE_MS && sceneStats.screenOnDailyAvgPowerNw > 0) {
            val drainPerMs = k * sceneStats.screenOnDailyAvgPowerNw
            remaining / (drainPerMs * 3_600_000.0)
        } else null

        return PredictionResult(
            screenOffHours = screenOffHours,
            screenOnDailyHours = screenOnHours,
            insufficientData = false
        )
    }
}
