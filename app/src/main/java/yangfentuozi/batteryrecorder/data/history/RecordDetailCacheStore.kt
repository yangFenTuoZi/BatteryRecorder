package yangfentuozi.batteryrecorder.data.history

import android.content.Context
import yangfentuozi.batteryrecorder.data.model.ChartPoint
import yangfentuozi.batteryrecorder.shared.Constants
import yangfentuozi.batteryrecorder.shared.data.BatteryStatus
import yangfentuozi.batteryrecorder.shared.data.RecordsStats
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File

object RecordDetailCacheStore {
    private const val CACHE_VERSION = 1
    private const val CACHE_FILE_SUFFIX = ".detail"
    private const val CACHE_DIR_NAME = "record_detail_cache"

    fun read(context: Context, file: File): HistoryRecordDetail? {
        if (!file.isFile) return null
        val cacheFile = cacheFile(context, file)
        if (!cacheFile.isFile) return null

        return runCatching {
            DataInputStream(BufferedInputStream(cacheFile.inputStream())).use { input ->
                val version = input.readInt()
                if (version != CACHE_VERSION) {
                    cacheFile.delete()
                    null
                } else {
                    val cachedLastModified = input.readLong()
                    val cachedLength = input.readLong()
                    if (cachedLastModified != file.lastModified() || cachedLength != file.length()) {
                        cacheFile.delete()
                        null
                    } else {
                        val stats = RecordsStats(
                            startTime = input.readLong(),
                            endTime = input.readLong(),
                            startCapacity = input.readInt(),
                            endCapacity = input.readInt(),
                            screenOnTimeMs = input.readLong(),
                            screenOffTimeMs = input.readLong(),
                            averagePower = input.readDouble()
                        )

                        val pointCount = input.readInt()
                        require(pointCount >= 0) { "Invalid point count: $pointCount" }
                        val points = ArrayList<ChartPoint>(pointCount)
                        repeat(pointCount) {
                            points.add(
                                ChartPoint(
                                    timestamp = input.readLong(),
                                    power = input.readDouble(),
                                    capacity = input.readInt(),
                                    isDisplayOn = input.readBoolean(),
                                    temp = input.readInt()
                                )
                            )
                        }

                        buildHistoryRecordDetail(file, stats, points)
                    }
                }
            }
        }.getOrElse {
            cacheFile.delete()
            null
        }
    }

    fun write(
        context: Context,
        file: File,
        stats: RecordsStats,
        points: List<ChartPoint>
    ) {
        if (!file.isFile) return
        val cacheFile = cacheFile(context, file)
        cacheFile.parentFile?.mkdirs()

        DataOutputStream(BufferedOutputStream(cacheFile.outputStream())).use { output ->
            output.writeInt(CACHE_VERSION)
            output.writeLong(file.lastModified())
            output.writeLong(file.length())

            output.writeLong(stats.startTime)
            output.writeLong(stats.endTime)
            output.writeInt(stats.startCapacity)
            output.writeInt(stats.endCapacity)
            output.writeLong(stats.screenOnTimeMs)
            output.writeLong(stats.screenOffTimeMs)
            output.writeDouble(stats.averagePower)

            output.writeInt(points.size)
            points.forEach { point ->
                output.writeLong(point.timestamp)
                output.writeDouble(point.power)
                output.writeInt(point.capacity)
                output.writeBoolean(point.isDisplayOn)
                output.writeInt(point.temp)
            }
            output.flush()
        }
    }

    fun delete(context: Context, file: File) {
        cacheFile(context, file).delete()
    }

    private fun cacheFile(context: Context, file: File): File {
        val typeDirName = file.parentFile?.name ?: throw IllegalArgumentException("Missing parent dir")
        return File(detailCacheDir(context, typeDirName), "${file.name}$CACHE_FILE_SUFFIX")
    }

    private fun detailCacheDir(context: Context, typeDirName: String): File =
        File(File(context.dataDir, CACHE_DIR_NAME), typeDirName)
            .apply {
                if (!exists()) mkdirs()
            }

    private fun buildHistoryRecordDetail(
        file: File,
        stats: RecordsStats,
        points: List<ChartPoint>
    ): HistoryRecordDetail {
        return HistoryRecordDetail(
            record = HistoryRecord(
                name = file.name,
                type = when (file.parentFile?.name) {
                    Constants.CHARGE_DATA_DIR -> BatteryStatus.Charging
                    Constants.DISCHARGE_DATA_DIR -> BatteryStatus.Discharging
                    else -> throw IllegalArgumentException("Unknown record type dir: ${file.parentFile?.name}")
                },
                stats = stats,
                lastModified = file.lastModified()
            ),
            points = points
        )
    }
}
