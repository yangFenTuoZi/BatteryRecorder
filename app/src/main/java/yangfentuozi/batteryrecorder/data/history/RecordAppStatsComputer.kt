package yangfentuozi.batteryrecorder.data.history

import yangfentuozi.batteryrecorder.shared.data.LineRecord
import yangfentuozi.batteryrecorder.shared.data.RecordFileParser
import java.io.File

data class RecordAppStatsEntry(
    val packageName: String?,
    val totalDurationMs: Long,
    val averagePowerRaw: Double,
    val averageTempCelsius: Double?,
    val maxTempCelsius: Double?,
    val isScreenOff: Boolean
)

object RecordAppStatsComputer {

    private sealed interface GroupKey {
        data object ScreenOff : GroupKey
        data class Package(val packageName: String) : GroupKey
    }

    private data class MutableRecordAppStats(
        var totalDurationMs: Long = 0L,
        var signedEnergyRawMs: Double = 0.0,
        var tempDurationMs: Long = 0L,
        var weightedTempSum: Double = 0.0,
        var maxTempCelsius: Double? = null
    )

    fun compute(
        file: File,
        recordIntervalMs: Long
    ): List<RecordAppStatsEntry> =
        compute(RecordFileParser.parseToList(file), recordIntervalMs)

    fun compute(
        records: List<LineRecord>,
        recordIntervalMs: Long
    ): List<RecordAppStatsEntry> {
        if (records.size < 2) return emptyList()

        val maxGapMs = DischargeRecordScanner.computeMaxGapMs(recordIntervalMs)
        val statsMap = LinkedHashMap<GroupKey, MutableRecordAppStats>()
        var previous: LineRecord? = null

        records.forEach { current ->
            val previousRecord = previous
            previous = current
            if (previousRecord == null) return@forEach

            val durationMs = current.timestamp - previousRecord.timestamp
            if (durationMs !in 1..maxGapMs) return@forEach

            val groupKey = resolveGroupKey(previousRecord) ?: return@forEach
            val stats = statsMap.getOrPut(groupKey) { MutableRecordAppStats() }
            val signedEnergyRawMs =
                (previousRecord.power.toDouble() + current.power.toDouble()) * 0.5 * durationMs
            stats.totalDurationMs += durationMs
            stats.signedEnergyRawMs += signedEnergyRawMs
            appendTemperatureStats(stats, previousRecord.temp, durationMs)
        }

        return statsMap.mapNotNull { (groupKey, stats) ->
            if (stats.totalDurationMs <= 0L) return@mapNotNull null

            RecordAppStatsEntry(
                packageName = (groupKey as? GroupKey.Package)?.packageName,
                totalDurationMs = stats.totalDurationMs,
                averagePowerRaw = stats.signedEnergyRawMs / stats.totalDurationMs.toDouble(),
                averageTempCelsius = stats.averageTempCelsius(),
                maxTempCelsius = stats.maxTempCelsius,
                isScreenOff = groupKey == GroupKey.ScreenOff
            )
        }.sortedWith(
            compareByDescending<RecordAppStatsEntry> { it.isScreenOff }
                .thenByDescending { it.totalDurationMs }
                .thenByDescending { it.averagePowerRaw }
                .thenBy { it.packageName.orEmpty() }
        )
    }

    private fun resolveGroupKey(record: LineRecord): GroupKey? {
        if (record.isDisplayOn == 0) return GroupKey.ScreenOff
        val packageName = record.packageName?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        return GroupKey.Package(packageName)
    }

    private fun appendTemperatureStats(
        stats: MutableRecordAppStats,
        tempTenths: Int,
        durationMs: Long
    ) {
        if (tempTenths <= 0) return

        val tempCelsius = tempTenths / 10.0
        stats.tempDurationMs += durationMs
        stats.weightedTempSum += tempCelsius * durationMs
        val currentMax = stats.maxTempCelsius
        stats.maxTempCelsius = if (currentMax == null) {
            tempCelsius
        } else {
            maxOf(currentMax, tempCelsius)
        }
    }

    private fun MutableRecordAppStats.averageTempCelsius(): Double? {
        if (tempDurationMs <= 0L) return null
        return weightedTempSum / tempDurationMs.toDouble()
    }
}
