package yangfentuozi.batteryrecorder.data.history

import android.content.Context
import yangfentuozi.batteryrecorder.data.model.ChartPoint
import yangfentuozi.batteryrecorder.ipc.Service
import yangfentuozi.batteryrecorder.shared.Constants
import yangfentuozi.batteryrecorder.shared.data.BatteryStatus
import yangfentuozi.batteryrecorder.shared.data.RecordsFile
import yangfentuozi.batteryrecorder.shared.data.RecordsStats
import java.io.File

data class HistoryRecord(
    val name: String,
    val type: BatteryStatus,
    val stats: RecordsStats,
    val lastModified: Long
) {
    fun asRecordsFile(): RecordsFile =
        RecordsFile(type, name)
}

data class HistorySummary(
    val type: BatteryStatus,
    val recordCount: Int,
    val averagePower: Double,
    val totalDurationMs: Long,
    val totalScreenOnMs: Long,
    val totalScreenOffMs: Long
)

object HistoryRepository {

    fun RecordsFile.toFile(context: Context): File? {
        val dataDir = dataDir(context, type)
        return validFile(dataDir, name)
    }

    private fun getCacheDir(context: Context) = File(context.cacheDir, "power_stats")

    // 验证目录有效性，返回 null 表示无效
    private fun dataDir(context: Context, type: BatteryStatus): File =
        File(File(context.dataDir, Constants.APP_POWER_DATA_PATH), type.dataDirName).apply {
            if (!isDirectory) delete()
            if (!exists()) mkdirs()
        }

    // 验证文件有效性，返回 null 表示无效
    private fun validFile(dir: File, name: String): File? =
        File(dir, name).takeIf { it.isFile }

    /** 加载统计数据并构建 HistoryRecord */
    private fun loadStats(
        context: Context,
        file: File,
        needCaching: Boolean
    ): HistoryRecord? {
        val stats = runCatching {
            RecordsStats.getCachedStats(
                cacheDir = getCacheDir(context),
                file = file,
                needCaching = needCaching
            )
        }.getOrNull() ?: return null

        return HistoryRecord(
            name = file.name,
            type = BatteryStatus.fromDataDirName(file.parentFile?.name),
            stats = stats,
            lastModified = file.lastModified()
        )
    }

    /** 加载指定类型的所有记录，按修改时间降序 */
    fun loadRecords(context: Context, type: BatteryStatus): List<HistoryRecord> {
        val files = dataDir(context, type).listFiles()?.filter { it.isFile }
            ?.sortedByDescending { it.lastModified() } ?: return emptyList()
        val latestFile = files.firstOrNull()

        return files.mapNotNull { loadStats(context, it, it != latestFile) }
    }

    /** 加载指定名称的单条记录 */
    fun loadRecord(context: Context, file: File): HistoryRecord? {
        val dataDir = file.parentFile!!
        val latestFile = dataDir.listFiles()?.filter { it.isFile }
            ?.maxByOrNull { it.lastModified() }

        return loadStats(context, file, latestFile != file)
    }

    /** 加载记录的图表数据点，用于绘制功率曲线 */
    fun loadRecordPoints(context: Context, recordsFile: RecordsFile): List<ChartPoint> {
        return (recordsFile.toFile(context) ?: return emptyList()).readLines()
            .asSequence()
            .filter { it.isNotBlank() }
            .mapNotNull { line ->
                val parts = line.split(",")
                if (parts.size < 5) return@mapNotNull null
                ChartPoint(
                    timestamp = parts[0].toLongOrNull() ?: return@mapNotNull null,
                    power = parts[1].toDoubleOrNull() ?: return@mapNotNull null,
                    capacity = parts[3].toIntOrNull() ?: return@mapNotNull null,
                    isDisplayOn = parts[4] == "1",
                    temp = if (parts.size > 5) parts[5].toIntOrNull() ?: 0 else 0
                )
            }
            .sortedBy { it.timestamp }
            .toList()
    }

    /** 获取最新记录，比较充电/放电两类的最后修改时间 */
    fun loadLatestRecord(context: Context): HistoryRecord? {
        val service = Service.service ?: return null
        val serviceFile = runCatching { service.currRecordsFile }
            .getOrNull()
            ?.toFile(context)
        return serviceFile?.let { file ->
            loadStats(context, file, false)
        }
    }

    /** 加载统计摘要，按时长加权计算平均功率 */
    fun loadSummary(
        context: Context,
        type: BatteryStatus,
        avgPowerLimit: Int = 20
    ): HistorySummary? {
        val dataDir = dataDir(context, type)
        val files = dataDir.listFiles()?.filter { it.isFile }
            ?.sortedByDescending { it.lastModified() } ?: return null

        val recordCount = files.size
        if (recordCount == 0) return null

        val latestFile = files.first()
        val sampleFiles = files.take(avgPowerLimit.coerceAtLeast(0))
        if (sampleFiles.isEmpty()) return null

        val records = sampleFiles.mapNotNull { loadStats(context, it, it != latestFile) }
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
    fun deleteRecord(context: Context, recordsFile: RecordsFile): Boolean {

        if (runCatching { recordsFile.toFile(context)!!.delete() }.getOrDefault(false)) {
            // 同步删除缓存文件
            runCatching { File(getCacheDir(context), recordsFile.name).delete() }
            return true
        }
        return false
    }
}
