package yangfentuozi.batteryrecorder.config

object Constants {
    const val PREFS_NAME = "app_settings"
    // server
    /** 记录间隔（毫秒） */
    const val KEY_RECORD_INTERVAL_MS = "record_interval_ms"
    const val MIN_RECORD_INTERVAL_MS = 100L
    const val MAX_RECORD_INTERVAL_MS = 60_000L
    const val DEF_RECORD_INTERVAL_MS = 1000L

    /** 单批次写入数量 */
    const val KEY_BATCH_SIZE = "batch_size"
    const val MIN_BATCH_SIZE = 0
    const val MAX_BATCH_SIZE = 1000
    const val DEF_BATCH_SIZE = 200

    /** 写入延迟（毫秒） */
    const val KEY_WRITE_LATENCY_MS = "write_latency_ms"
    const val MIN_WRITE_LATENCY_MS = 100L
    const val MAX_WRITE_LATENCY_MS = 60_000L
    const val DEF_WRITE_LATENCY_MS = 30_000L

    /** 息屏时是否继续记录 */
    const val KEY_SCREEN_OFF_RECORD_ENABLED = "screen_off_record_enabled"
    const val DEF_SCREEN_OFF_RECORD_ENABLED = true

    /** 数据分段时长（分钟） */
    const val KEY_SEGMENT_DURATION_MIN = "segment_duration_min"
    const val MIN_SEGMENT_DURATION_MIN = 0L
    const val MAX_SEGMENT_DURATION_MIN = 1440L
    const val DEF_SEGMENT_DURATION_MIN = 1440L


    // app
    /** 是否启用双电芯模式 */
    const val KEY_DUAL_CELL_ENABLED = "dual_cell_enabled"
    const val DEF_DUAL_CELL_ENABLED = false

    /** 放电电流显示为正值 */
    const val KEY_DISCHARGE_DISPLAY_POSITIVE = "discharge_display_positive"
    const val DEF_DISCHARGE_DISPLAY_POSITIVE = true

    /** 校准值 */
    const val KEY_CALIBRATION_VALUE = "calibration_value"
    const val MIN_CALIBRATION_VALUE = -100_000_000
    const val MAX_CALIBRATION_VALUE = 100_000_000
    const val DEF_CALIBRATION_VALUE = -1

}