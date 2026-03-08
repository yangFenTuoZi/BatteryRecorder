package yangfentuozi.batteryrecorder.shared.data

import yangfentuozi.batteryrecorder.shared.util.LoggerX
import java.io.File

object RecordFileParser {
    private const val TIMESTAMP_LENGTH = 13

    fun parseToList(file: File): List<LineRecord> {
        val records = mutableListOf<LineRecord>()
        forEachValidRecord(file) { record ->
            records += record
        }
        return records
    }

    fun forEachValidRecord(
        file: File,
        onRecord: (LineRecord) -> Unit
    ) {
        var lineNumber = 0
        var previousParsedTimestamp: Long? = null

        file.bufferedReader().useLines { lines ->
            lines.forEach { raw ->
                lineNumber++
                val line = raw.trim()
                if (line.isEmpty()) return@forEach

                val parts = line.split(",")
                if (parts.size < 6) {
                    logInvalidLine(
                        file = file,
                        lineNumber = lineNumber,
                        timestampValue = parts.firstOrNull(),
                        previousTimestamp = previousParsedTimestamp,
                        reason = "字段数量不足: ${parts.size}",
                        rawLine = line
                    )
                    return@forEach
                }

                val timestampValue = parts[0]
                if (timestampValue.length != TIMESTAMP_LENGTH) {
                    logInvalidLine(
                        file = file,
                        lineNumber = lineNumber,
                        timestampValue = timestampValue,
                        previousTimestamp = previousParsedTimestamp,
                        reason = "时间戳长度异常: ${timestampValue.length}",
                        rawLine = line
                    )
                    return@forEach
                }

                val record = LineRecord.fromParts(parts)
                if (record == null) {
                    logInvalidLine(
                        file = file,
                        lineNumber = lineNumber,
                        timestampValue = timestampValue,
                        previousTimestamp = previousParsedTimestamp,
                        reason = "字段解析失败",
                        rawLine = line
                    )
                    return@forEach
                }

                val previousTimestamp = previousParsedTimestamp
                previousParsedTimestamp = record.timestamp
                if (previousTimestamp != null && record.timestamp <= previousTimestamp) {
                    logInvalidLine(
                        file = file,
                        lineNumber = lineNumber,
                        timestampValue = timestampValue,
                        previousTimestamp = previousTimestamp,
                        reason = "时间戳未严格递增",
                        rawLine = line
                    )
                    return@forEach
                }

                onRecord(record)
            }
        }
    }

    private fun logInvalidLine(
        file: File,
        lineNumber: Int,
        timestampValue: String?,
        previousTimestamp: Long?,
        reason: String,
        rawLine: String
    ) {
        LoggerX.w<RecordFileParser>(
            "跳过损坏记录 " +
                "file=${file.absolutePath} " +
                "line=$lineNumber " +
                "timestamp=${timestampValue ?: "null"} " +
                "prevAcceptedTimestamp=${previousTimestamp ?: "null"} " +
                "reason=$reason " +
                "raw=$rawLine"
        )
    }
}
