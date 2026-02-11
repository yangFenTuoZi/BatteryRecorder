package yangfentuozi.batteryrecorder.shared.config

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class Config(
    val recordIntervalMs: Long = Constants.DEF_RECORD_INTERVAL_MS,
    val batchSize: Int = Constants.DEF_BATCH_SIZE,
    val writeLatencyMs: Long = Constants.DEF_WRITE_LATENCY_MS,
    val screenOffRecordEnabled: Boolean = Constants.DEF_SCREEN_OFF_RECORD_ENABLED,
    val segmentDurationMin: Long = Constants.DEF_SEGMENT_DURATION_MIN
) : Parcelable
