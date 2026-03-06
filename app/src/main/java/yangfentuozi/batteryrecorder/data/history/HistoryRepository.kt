package yangfentuozi.batteryrecorder.data.history

import android.content.Context
import android.net.Uri
import yangfentuozi.batteryrecorder.data.model.ChartPoint
import yangfentuozi.batteryrecorder.ipc.Service
import yangfentuozi.batteryrecorder.shared.Constants
import yangfentuozi.batteryrecorder.shared.data.BatteryStatus
import yangfentuozi.batteryrecorder.shared.data.LineRecord
import yangfentuozi.batteryrecorder.shared.data.RecordsFile
import yangfentuozi.batteryrecorder.shared.data.RecordsStats
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException

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

data class HistoryRecordDetail(
    val record: HistoryRecord,
    val points: List<ChartPoint>
)

object HistoryRepository {

    fun RecordsFile.toFile(context: Context): File? {
        val dataDir = dataDir(context, type)
        return validFile(dataDir, name)
    }

    private fun getCacheDir(context: Context) = File(context.cacheDir, "power_stats")

    // 确保目录存在且为目录，返回目录对象
    private fun dataDir(context: Context, type: BatteryStatus): File =
        File(File(context.dataDir, Constants.APP_POWER_DATA_PATH), type.dataDirName).apply {
            if (!isDirectory) delete()
            if (!exists()) mkdirs()
        }

    // 验证文件有效性，返回 null 表示无效
    private fun validFile(dir: File, name: String): File? =
        File(dir, name).takeIf { it.isFile }

    /** 加载统计数据并构建 HistoryRecord */
    fun loadStats(
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

    /** 仅列出文件，按修改时间降序，不加载 stats */
    fun listRecordFiles(context: Context, type: BatteryStatus): List<File> {
        return dataDir(context, type).listFiles()?.filter { it.isFile }
            ?.sortedByDescending { it.lastModified() } ?: emptyList()
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
        return loadRecordDetail(context, file)?.record
    }

    /** 加载指定名称的详情（统计 + 图表点） */
    fun loadRecordDetail(context: Context, file: File): HistoryRecordDetail? {
        return runCatching {
            if (isCurrentActiveRecord(context, file)) {
                return@runCatching parseRecordDetail(file)
            }

            val cachedDetail = RecordDetailCacheStore.read(context, file)
            if (cachedDetail != null) {
                return@runCatching cachedDetail
            }

            val detail = parseRecordDetail(file)
            RecordDetailCacheStore.write(context, file, detail.record.stats, detail.points)
            detail
        }.getOrNull()
    }

    fun prebuildRecordDetailCache(context: Context, file: File) {
        if (!file.isFile || isCurrentActiveRecord(context, file)) return
        if (RecordDetailCacheStore.read(context, file) != null) return
        val detail = parseRecordDetail(file)
        RecordDetailCacheStore.write(context, file, detail.record.stats, detail.points)
    }

    /** 加载记录的图表数据点，用于绘制功率曲线 */
    fun loadRecordPoints(context: Context, recordsFile: RecordsFile): List<ChartPoint> {
        val file = recordsFile.toFile(context) ?: return emptyList()
        return loadRecordDetail(context, file)?.points ?: emptyList()
    }

    /** 从 service.currRecordsFile 加载当前记录 */
    fun loadLatestRecord(
        context: Context
    ): HistoryRecord? {
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
        val file = recordsFile.toFile(context) ?: return false

        if (runCatching { file.delete() }.getOrDefault(false)) {
            runCatching { File(getCacheDir(context), recordsFile.name).delete() }
            runCatching { RecordDetailCacheStore.delete(context, file) }
            return true
        }
        return false
    }

    /** 导出单条记录到用户选择位置 */
    @Throws(IOException::class)
    fun exportRecord(
        context: Context,
        recordsFile: RecordsFile,
        destinationUri: Uri
    ) {
        val sourceFile = recordsFile.toFile(context)
            ?: throw FileNotFoundException("Record file not found: ${recordsFile.name}")
        val outputStream = context.contentResolver.openOutputStream(destinationUri, "w")
            ?: throw IOException("Failed to open destination: $destinationUri")

        sourceFile.inputStream().use { input ->
            outputStream.use { output ->
                input.copyTo(output)
            }
        }
    }

    private fun isCurrentActiveRecord(context: Context, file: File): Boolean {
        val currentFile = runCatching { Service.service?.currRecordsFile }
            .getOrNull()
            ?.toFile(context)
            ?: return false
        return currentFile.absolutePath == file.absolutePath
    }

    private fun parseRecordDetail(file: File): HistoryRecordDetail {
        if (!file.exists()) {
            throw IllegalArgumentException("File not found: ${file.absolutePath}")
        }

        var startTime = Long.MAX_VALUE
        var endTime = Long.MIN_VALUE
        var startCapacity = -1
        var endCapacity = -1
        var screenOnTime = 0L
        var screenOffTime = 0L
        var totalEnergy = 0.0
        var totalDuration = 0L

        var lastTimestamp: Long? = null
        var lastPower: Long? = null
        var lastDisplayOn: Int? = null

        val points = ArrayList<ChartPoint>()
        file.bufferedReader().useLines { lines ->
            lines.forEach { raw ->
                val line = raw.trim()
                if (line.isEmpty()) return@forEach

                val record = LineRecord.fromString(line) ?: return@forEach
                points.add(
                    ChartPoint(
                        timestamp = record.timestamp,
                        power = record.power.toDouble(),
                        capacity = record.capacity,
                        isDisplayOn = record.isDisplayOn == 1,
                        temp = record.temp
                    )
                )

                if (record.timestamp < startTime) {
                    startTime = record.timestamp
                    startCapacity = record.capacity
                }
                if (record.timestamp > endTime) {
                    endTime = record.timestamp
                    endCapacity = record.capacity
                }

                val prevTs = lastTimestamp
                val prevPower = lastPower
                val prevDisplay = lastDisplayOn
                if (prevTs != null && prevPower != null && prevDisplay != null) {
                    val dt = record.timestamp - prevTs
                    if (dt > 0) {
                        if (prevDisplay == 1) {
                            screenOnTime += dt
                        } else {
                            screenOffTime += dt
                        }
                        totalEnergy += prevPower * dt
                        totalDuration += dt
                    }
                }

                lastTimestamp = record.timestamp
                lastPower = record.power
                lastDisplayOn = record.isDisplayOn
            }
        }

        if (totalDuration <= 0 || points.isEmpty()) {
            throw IllegalStateException("Not enough valid samples")
        }

        return HistoryRecordDetail(
            record = HistoryRecord(
                name = file.name,
                type = BatteryStatus.fromDataDirName(file.parentFile?.name),
                stats = RecordsStats(
                    startTime = startTime,
                    endTime = endTime,
                    startCapacity = startCapacity,
                    endCapacity = endCapacity,
                    screenOnTimeMs = screenOnTime,
                    screenOffTimeMs = screenOffTime,
                    averagePower = totalEnergy / totalDuration
                ),
                lastModified = file.lastModified()
            ),
            points = points
        )
    }
}
