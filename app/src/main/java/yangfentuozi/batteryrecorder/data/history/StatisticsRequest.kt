package yangfentuozi.batteryrecorder.data.history

import yangfentuozi.batteryrecorder.shared.config.ConfigConstants

data class StatisticsRequest(
    val gamePackages: Set<String> = emptySet(),
    val sceneStatsRecentFileCount: Int = ConfigConstants.DEF_SCENE_STATS_RECENT_FILE_COUNT,
    val recordIntervalMs: Long = ConfigConstants.DEF_RECORD_INTERVAL_MS,
    val predCurrentSessionWeightEnabled: Boolean = ConfigConstants.DEF_PRED_CURRENT_SESSION_WEIGHT_ENABLED,
    val predCurrentSessionWeightMaxX100: Int = ConfigConstants.DEF_PRED_CURRENT_SESSION_WEIGHT_MAX_X100,
    val predCurrentSessionWeightHalfLifeMin: Long = ConfigConstants.DEF_PRED_CURRENT_SESSION_WEIGHT_HALF_LIFE_MIN
)
