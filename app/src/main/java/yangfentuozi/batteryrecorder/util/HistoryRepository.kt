package yangfentuozi.batteryrecorder.util

import android.content.Context
import yangfentuozi.batteryrecorder.ui.components.charts.ChartPoint
import java.io.File

enum class RecordType(val dirName: String) {
    CHARGE("charge"),
    DISCHARGE("discharge")
}

data class HistoryRecord(
    val name: String,
    val type: RecordType,
    val stats: PowerStats,
    val lastModified: Long
)

data class HistorySummary(
    val type: RecordType,
    val recordCount: Int,
    val averagePower: Double,
    val totalDurationMs: Long,
    val totalScreenOnMs: Long,
    val totalScreenOffMs: Long
)

object HistoryRepository {
    fun loadRecords(context: Context, type: RecordType): List<HistoryRecord> {
        val dataDir = getDataDir(context, type)
        if (!dataDir.exists() || !dataDir.isDirectory) return emptyList()

        val files = dataDir.listFiles()?.filter { it.isFile }?.sortedByDescending { it.lastModified() }
            ?: return emptyList()
        val latestFileName = files.firstOrNull()?.name
        val cacheDir = File(context.cacheDir, "power_stats")

        return files.mapNotNull { file ->
            val stats = runCatching {
                StatisticsUtil.getCachedPowerStats(
                    cacheDir = cacheDir,
                    dataDir = dataDir,
                    name = file.name,
                    lastestFileName = latestFileName
                )
            }.getOrNull() ?: return@mapNotNull null

            HistoryRecord(
                name = file.name,
                type = type,
                stats = stats,
                lastModified = file.lastModified()
            )
        }
    }

    fun loadRecord(context: Context, type: RecordType, name: String): HistoryRecord? {
        val dataDir = getDataDir(context, type)
        if (!dataDir.exists() || !dataDir.isDirectory) return null

        val file = File(dataDir, name)
        if (!file.exists() || !file.isFile) return null

        val latestFileName = dataDir.listFiles()?.filter { it.isFile }
            ?.maxByOrNull { it.lastModified() }?.name
        val cacheDir = File(context.cacheDir, "power_stats")
        val stats = runCatching {
            StatisticsUtil.getCachedPowerStats(
                cacheDir = cacheDir,
                dataDir = dataDir,
                name = file.name,
                lastestFileName = latestFileName
            )
        }.getOrNull() ?: return null

        return HistoryRecord(
            name = file.name,
            type = type,
            stats = stats,
            lastModified = file.lastModified()
        )
    }

    fun loadRecordPoints(
        context: Context,
        type: RecordType,
        name: String
    ): List<ChartPoint> {
        val dataDir = getDataDir(context, type)
        if (!dataDir.exists() || !dataDir.isDirectory) return emptyList()

        val file = File(dataDir, name)
        if (!file.exists() || !file.isFile) return emptyList()

        return file.readLines()
            .asSequence()
            .filter { it.isNotBlank() }
            .mapNotNull { line ->
                val parts = line.split(",")
                if (parts.size < 5) return@mapNotNull null
                val timestamp = parts[0].toLongOrNull() ?: return@mapNotNull null
                val power = parts[1].toDoubleOrNull() ?: return@mapNotNull null
                val capacity = parts[3].toIntOrNull() ?: return@mapNotNull null
                val isDisplayOn = parts[4].toIntOrNull() ?: return@mapNotNull null
                ChartPoint(
                    timestamp = timestamp,
                    power = power,
                    capacity = capacity,
                    isDisplayOn = isDisplayOn == 1
                )
            }
            .sortedBy { it.timestamp }
            .toList()
    }

    fun loadLatestRecord(context: Context): HistoryRecord? {
        val chargeLatest = loadLatestRecordForType(context, RecordType.CHARGE)
        val dischargeLatest = loadLatestRecordForType(context, RecordType.DISCHARGE)

        return when {
            chargeLatest == null -> dischargeLatest
            dischargeLatest == null -> chargeLatest
            chargeLatest.lastModified >= dischargeLatest.lastModified -> chargeLatest
            else -> dischargeLatest
        }
    }

    fun loadSummary(
        context: Context,
        type: RecordType,
        limit: Int = 10
    ): HistorySummary? {
        val records = loadRecords(context, type).take(limit)
        if (records.isEmpty()) return null

        var totalDurationMs = 0L
        var weightedPowerSum = 0.0
        var totalScreenOnMs = 0L
        var totalScreenOffMs = 0L

        records.forEach { record ->
            val stats = record.stats
            val durationMs = (stats.endTime - stats.startTime).coerceAtLeast(0)
            totalDurationMs += durationMs
            weightedPowerSum += stats.averagePower * durationMs
            totalScreenOnMs += stats.screenOnTimeMs
            totalScreenOffMs += stats.screenOffTimeMs
        }

        val averagePower = if (totalDurationMs > 0) {
            weightedPowerSum / totalDurationMs
        } else {
            records.map { it.stats.averagePower }.average()
        }

        return HistorySummary(
            type = type,
            recordCount = records.size,
            averagePower = averagePower,
            totalDurationMs = totalDurationMs,
            totalScreenOnMs = totalScreenOnMs,
            totalScreenOffMs = totalScreenOffMs
        )
    }

    private fun getDataDir(context: Context, type: RecordType): File {
        return File(File(context.dataDir, "power_data"), type.dirName)
    }

    fun deleteRecord(context: Context, type: RecordType, name: String): Boolean {
        val dataDir = getDataDir(context, type)
        if (!dataDir.exists() || !dataDir.isDirectory) return false

        val recordFile = File(dataDir, name)
        if (!recordFile.exists() || !recordFile.isFile) return false

        val deleted = runCatching { recordFile.delete() }.getOrDefault(false)
        if (!deleted) return false

        val cacheFile = File(File(context.cacheDir, "power_stats"), name)
        runCatching { if (cacheFile.exists()) cacheFile.delete() }

        return true
    }

    private fun loadLatestRecordForType(context: Context, type: RecordType): HistoryRecord? {
        val dataDir = getDataDir(context, type)
        if (!dataDir.exists() || !dataDir.isDirectory) return null
        val latestFile = dataDir.listFiles()?.filter { it.isFile }
            ?.maxByOrNull { it.lastModified() } ?: return null
        return loadRecord(context, type, latestFile.name)
    }
}
