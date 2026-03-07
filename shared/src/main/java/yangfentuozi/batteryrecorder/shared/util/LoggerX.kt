package yangfentuozi.batteryrecorder.shared.util

import android.util.Log
import yangfentuozi.batteryrecorder.shared.BuildConfig
import java.io.BufferedWriter
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardOpenOption
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

class LoggerX(
    private val tag: String,
    private val fileWriter: DailyLineRotateFileWriter? = null
) {

    companion object {
        @Volatile
        var logDirPath: String? = null

        inline fun <reified T> logger(
            maxLinesPerFile: Int = 5000,
            maxHistoryDays: Long = 7
        ): LoggerX {
            val logDirPath = this.logDirPath
            val tag = T::class.java.simpleName
            val fileWriter = if (logDirPath == null) null else try {
                DailyLineRotateFileWriter(logDirPath, maxLinesPerFile, maxHistoryDays)
            } catch (e: IOException) {
                e.printStackTrace()
                null
            }
            return LoggerX(tag, fileWriter)
        }
    }

    @Volatile
    var logLevel: LogLevel = LogLevel.Info

    fun isLoggable(level: LogLevel): Boolean {
        val allowedPriority =
            if (BuildConfig.DEBUG) logLevel.coerceAtMost(LogLevel.Info)
            else logLevel
        return level.priority >= allowedPriority.priority
    }

    fun v(msg: String?, vararg args: Any?, tr: Throwable? = null) {
        log(LogLevel.Verbose, msg, tr, args)
    }

    fun d(msg: String?, vararg args: Any?, tr: Throwable? = null) {
        log(LogLevel.Debug, msg, tr, args)
    }

    fun i(msg: String?, vararg args: Any?, tr: Throwable? = null) {
        log(LogLevel.Info, msg, tr, args)
    }

    fun w(msg: String?, vararg args: Any?, tr: Throwable? = null) {
        log(LogLevel.Warning, msg, tr, args)
    }

    fun e(msg: String?, vararg args: Any?, tr: Throwable? = null) {
        log(LogLevel.Error, msg, tr, args)
    }

    fun a(msg: String?, vararg args: Any?, tr: Throwable? = null) {
        log(LogLevel.Assert, msg, tr, args)
    }

    private fun log(level: LogLevel, msg: String?, tr: Throwable?, args: Array<out Any?>) {
        if (!isLoggable(level)) return
        val base = if (args.isEmpty()) msg.toString() else String.format(
            Locale.ENGLISH,
            msg ?: "null",
            *args
        )
        val content = if (tr == null) base else "$base\n${Log.getStackTraceString(tr)}"
        println(level, content)
    }

    fun println(priority: LogLevel, msg: String): Int {
        fileWriter?.write(tag, priority, msg)
        return Log.println(priority.priority, tag, msg)
    }

    enum class LogLevel(val priority: Int, val shortName: String) {
        Verbose(Log.VERBOSE, "V"),
        Debug(Log.DEBUG, "D"),
        Info(Log.INFO, "I"),
        Warning(Log.WARN, "W"),
        Error(Log.ERROR, "E"),
        Assert(Log.ASSERT, "A"),
        Disabled(Int.MAX_VALUE, "null");

        companion object {
            private val priorityMap = entries.associateBy { it.priority }
            fun fromPriority(priority: Int): LogLevel = priorityMap[priority] ?: Info
        }

        fun coerceAtMost(maximumPriority: LogLevel): LogLevel {
            return LogLevel.fromPriority(priority.coerceAtMost(maximumPriority.priority))
        }
    }
}

class DailyLineRotateFileWriter(
    logDirPath: String,
    private val maxLinesPerFile: Int,
    private val maxHistoryDays: Long
) {

    private val logDir = File(logDirPath)
    private val fileNameRegex = Regex("""^(\d{4}-\d{2}-\d{2})(?:_(\d+))?\.log$""")
    private val dateFileFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    private val lineTimeFormatter = DateTimeFormatter.ofPattern("dd HH:mm:ss.SSS")
    private val lock = Any()
    private var activeDate = ""
    private var activeIndex = 0
    private var activeLines = 0
    private var writer: BufferedWriter? = null

    init {
        if (!logDir.exists()) logDir.mkdirs()
        if (!logDir.isDirectory) throw IOException("logDir is not a directory: $logDirPath")
    }

    fun write(tag: String, level: LoggerX.LogLevel, message: String) {
        synchronized(lock) {
            try {
                val today = LocalDate.now()
                val todayKey = today.format(dateFileFormatter)
                if (activeDate != todayKey || writer == null) {
                    writer?.close()
                    activeDate = todayKey
                    activeIndex = 0
                    activeLines = 0
                    val files = logDir.listFiles()
                    if (files != null) {
                        val cutoff = today.minusDays(maxHistoryDays)
                        for (file in files) {
                            val match = fileNameRegex.matchEntire(file.name) ?: continue
                            val fileDate = try {
                                LocalDate.parse(match.groupValues[1], dateFileFormatter)
                            } catch (_: Exception) {
                                continue
                            }
                            if (fileDate.isBefore(cutoff)) file.delete()
                        }
                        var maxIndex = -1
                        var matched: File? = null
                        for (file in files) {
                            val match = fileNameRegex.matchEntire(file.name) ?: continue
                            if (match.groupValues[1] != todayKey) continue
                            val index =
                                match.groupValues[2].takeIf { it.isNotBlank() }?.toIntOrNull() ?: 0
                            if (index > maxIndex) {
                                maxIndex = index
                                matched = file
                            }
                        }
                        if (matched != null) {
                            activeIndex = maxIndex
                            activeLines = try {
                                Files.lines(matched.toPath())
                                    .use { stream -> stream.count().toInt() }
                            } catch (_: Exception) {
                                0
                            }
                        }
                    }
                    val fileName =
                        if (activeIndex == 0) "$todayKey.log" else "${todayKey}_${activeIndex}.log"
                    val logFile = File(logDir, fileName)
                    writer = Files.newBufferedWriter(
                        logFile.toPath(),
                        StandardOpenOption.CREATE,
                        StandardOpenOption.APPEND
                    )
                }
                if (activeLines >= maxLinesPerFile) {
                    writer?.close()
                    activeIndex += 1
                    activeLines = 0
                    val fileName =
                        if (activeIndex == 0) "$activeDate.log" else "${activeDate}_${activeIndex}.log"
                    val logFile = File(logDir, fileName)
                    writer = Files.newBufferedWriter(
                        logFile.toPath(),
                        StandardOpenOption.CREATE,
                        StandardOpenOption.APPEND
                    )
                }
                val timestamp = Instant.ofEpochMilli(System.currentTimeMillis())
                    .atZone(ZoneId.systemDefault())
                    .toLocalDateTime()
                    .format(lineTimeFormatter)
                val sb = StringBuilder()
                sb.append(timestamp)
                sb.append(' ')
                sb.append(level.shortName)
                sb.append('/')
                sb.append(tag)
                sb.append(": ")
                sb.append(message)
                writer?.write(sb.toString())
                writer?.newLine()
                writer?.flush()
                activeLines += sb.count { it == '\n' } + 1
            } catch (_: Exception) {
            }
        }
    }
}
