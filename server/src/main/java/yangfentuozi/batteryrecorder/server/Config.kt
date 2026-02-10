package yangfentuozi.batteryrecorder.server

import android.os.Parcelable

import kotlinx.parcelize.Parcelize

@Parcelize
data class Config(
    val recordInterval: Long = 1000L,
    val batchSize: Int = 200,
    val flushInterval: Long = 30_1000L,
    val screenOffRecord: Boolean = true,
    val segmentDuration: Long = 1440L
) : Parcelable