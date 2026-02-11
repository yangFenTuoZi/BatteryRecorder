package yangfentuozi.batteryrecorder.shared.config

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class Config(
    val recordIntervalMs: Long = ConfigConstants.DEF_RECORD_INTERVAL_MS,
    val batchSize: Int = ConfigConstants.DEF_BATCH_SIZE,
    val writeLatencyMs: Long = ConfigConstants.DEF_WRITE_LATENCY_MS,
    val screenOffRecordEnabled: Boolean = ConfigConstants.DEF_SCREEN_OFF_RECORD_ENABLED,
    val segmentDurationMin: Long = ConfigConstants.DEF_SEGMENT_DURATION_MIN
) : Parcelable
