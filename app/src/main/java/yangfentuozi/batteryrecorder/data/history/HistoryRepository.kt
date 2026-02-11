package yangfentuozi.batteryrecorder.data.history

import android.content.Context
import yangfentuozi.batteryrecorder.data.model.ChartPoint
import yangfentuozi.batteryrecorder.shared.config.Constants
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
    private const val KEY_DISCHARGE_DISPLAY_POSITIVE = "discharge_display_positive"

    // 放电功率默认为负值，用户可选择显示为正值
    private fun getPowerMultiplier(context: Context, type: RecordType): Double {
        if (type != RecordType.DISCHARGE) return 1.0
        val enabled = context.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_DISCHARGE_DISPLAY_POSITIVE, true)
        return if (enabled) -1.0 else 1.0
    }

    private fun getDataDir(context: Context, type: RecordType) =
        File(File(context.dataDir, "power_data"), type.dirName)

    private fun getCacheDir(context: Context) = File(context.cacheDir, "power_stats")

    // 验证目录有效性，返回 null 表示无效
    private fun validDataDir(context: Context, type: RecordType): File? =
        getDataDir(context, type).takeIf { it.isDirectory }

    // 验证文件有效性，返回 null 表示无效
    private fun validFile(dir: File, name: String): File? =
        File(dir, name).takeIf { it.isFile }

    /** 加载统计数据，应用功率乘数后构建 HistoryRecord */
    private fun loadStats(
        context: Context,
        type: RecordType,
        file: File,
        dataDir: File,
        latestFileName: String?
    ): HistoryRecord? {
        val stats = runCatching {
            StatisticsUtil.getCachedPowerStats(
                cacheDir = getCacheDir(context),
                dataDir = dataDir,
                name = file.name,
                lastestFileName = latestFileName
            )
        }.getOrNull() ?: return null

        val multiplier = getPowerMultiplier(context, type)
        return HistoryRecord(
            name = file.name,
            type = type,
            stats = stats.copy(averagePower = stats.averagePower * multiplier),
            lastModified = file.lastModified()
        )
    }

    /** 加载指定类型的所有记录，按修改时间降序 */
    fun loadRecords(context: Context, type: RecordType): List<HistoryRecord> {
        val dataDir = validDataDir(context, type) ?: return emptyList()
        val files = dataDir.listFiles()?.filter { it.isFile }
            ?.sortedByDescending { it.lastModified() } ?: return emptyList()
        val latestFileName = files.firstOrNull()?.name

        return files.mapNotNull { loadStats(context, type, it, dataDir, latestFileName) }
    }

    /** 加载指定名称的单条记录 */
    fun loadRecord(context: Context, type: RecordType, name: String): HistoryRecord? {
        val dataDir = validDataDir(context, type) ?: return null
        val file = validFile(dataDir, name) ?: return null
        val latestFileName = dataDir.listFiles()?.filter { it.isFile }
            ?.maxByOrNull { it.lastModified() }?.name

        return loadStats(context, type, file, dataDir, latestFileName)
    }

    /** 加载记录的图表数据点，用于绘制功率曲线 */
    fun loadRecordPoints(context: Context, type: RecordType, name: String): List<ChartPoint> {
        val dataDir = validDataDir(context, type) ?: return emptyList()
        val file = validFile(dataDir, name) ?: return emptyList()
        val multiplier = getPowerMultiplier(context, type)

        return file.readLines()
            .asSequence()
            .filter { it.isNotBlank() }
            .mapNotNull { line ->
                val parts = line.split(",")
                if (parts.size < 5) return@mapNotNull null
                ChartPoint(
                    timestamp = parts[0].toLongOrNull() ?: return@mapNotNull null,
                    power = (parts[1].toDoubleOrNull() ?: return@mapNotNull null) * multiplier,
                    capacity = parts[3].toIntOrNull() ?: return@mapNotNull null,
                    isDisplayOn = parts[4] == "1"
                )
            }
            .sortedBy { it.timestamp }
            .toList()
    }

    /** 获取最新记录，比较充电/放电两类的最后修改时间 */
    fun loadLatestRecord(context: Context): HistoryRecord? {
        val charge = loadLatestRecordForType(context, RecordType.CHARGE)
        val discharge = loadLatestRecordForType(context, RecordType.DISCHARGE)
        return listOfNotNull(charge, discharge).maxByOrNull { it.lastModified }
    }

    /** 加载统计摘要，按时长加权计算平均功率 */
    fun loadSummary(context: Context, type: RecordType, avgPowerLimit: Int = 20): HistorySummary? {
        val dataDir = validDataDir(context, type) ?: return null
        val files = dataDir.listFiles()?.filter { it.isFile }
            ?.sortedByDescending { it.lastModified() } ?: return null

        val recordCount = files.size
        if (recordCount == 0) return null

        val latestFileName = files.first().name
        val sampleFiles = files.take(avgPowerLimit.coerceAtLeast(0))
        if (sampleFiles.isEmpty()) return null

        val records = sampleFiles.mapNotNull { loadStats(context, type, it, dataDir, latestFileName) }
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
            recordCount = recordCount,
            averagePower = averagePower,
            totalDurationMs = totalDurationMs,
            totalScreenOnMs = totalScreenOnMs,
            totalScreenOffMs = totalScreenOffMs
        )
    }

    /** 删除记录及其缓存文件 */
    fun deleteRecord(context: Context, type: RecordType, name: String): Boolean {
        val dataDir = validDataDir(context, type) ?: return false
        val recordFile = validFile(dataDir, name) ?: return false

        if (runCatching { recordFile.delete() }.getOrDefault(false)) {
            // 同步删除缓存文件
            runCatching { File(getCacheDir(context), name).delete() }
            return true
        }
        return false
    }

    private fun loadLatestRecordForType(context: Context, type: RecordType): HistoryRecord? {
        val dataDir = validDataDir(context, type) ?: return null
        val latestFile = dataDir.listFiles()?.filter { it.isFile }
            ?.maxByOrNull { it.lastModified() } ?: return null
        return loadRecord(context, type, latestFile.name)
    }
}
