package yangfentuozi.batteryrecorder.ui.viewmodel

import android.content.Context
import yangfentuozi.batteryrecorder.data.history.HistoryRecord
import yangfentuozi.batteryrecorder.data.history.HistorySummary
import yangfentuozi.batteryrecorder.data.history.RecordType
import yangfentuozi.batteryrecorder.data.model.ChartPoint
import yangfentuozi.batteryrecorder.shared.config.ConfigConstants

internal fun getDischargeDisplayPositive(context: Context): Boolean {
    return context.getSharedPreferences(ConfigConstants.PREFS_NAME, Context.MODE_PRIVATE)
        .getBoolean(
            ConfigConstants.KEY_DISCHARGE_DISPLAY_POSITIVE,
            ConfigConstants.DEF_DISCHARGE_DISPLAY_POSITIVE
        )
}

private fun getDischargeDisplayMultiplier(dischargeDisplayPositive: Boolean): Double {
    return if (dischargeDisplayPositive) -1.0 else 1.0
}

internal fun mapHistoryRecordForDisplay(
    record: HistoryRecord,
    dischargeDisplayPositive: Boolean
): HistoryRecord {
    if (record.type != RecordType.DISCHARGE) return record
    val multiplier = getDischargeDisplayMultiplier(dischargeDisplayPositive)
    return record.copy(
        stats = record.stats.copy(averagePower = record.stats.averagePower * multiplier)
    )
}

internal fun mapHistorySummaryForDisplay(
    summary: HistorySummary,
    dischargeDisplayPositive: Boolean
): HistorySummary {
    if (summary.type != RecordType.DISCHARGE) return summary
    val multiplier = getDischargeDisplayMultiplier(dischargeDisplayPositive)
    return summary.copy(averagePower = summary.averagePower * multiplier)
}

internal fun mapChartPointsForDisplay(
    points: List<ChartPoint>,
    recordType: RecordType,
    dischargeDisplayPositive: Boolean
): List<ChartPoint> {
    if (recordType != RecordType.DISCHARGE) return points
    val multiplier = getDischargeDisplayMultiplier(dischargeDisplayPositive)
    return points.map { point -> point.copy(power = point.power * multiplier) }
}
