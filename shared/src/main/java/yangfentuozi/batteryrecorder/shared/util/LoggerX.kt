package yangfentuozi.batteryrecorder.shared.util

import android.util.Log
import yangfentuozi.batteryrecorder.shared.BuildConfig
import yangfentuozi.batteryrecorder.shared.config.ConfigConstants
import java.io.BufferedWriter
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStreamWriter
import java.nio.file.Files
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

object LoggerX {

    @Volatile
    private var writer: DailyLineRotateFileWriter? = null

    // 只允许传入
    var logDir: File?
        get() = null
        set(value) {
            writer = if (value == null) null else try {
                DailyLineRotateFileWriter(value)
            } catch (e: IOException) {
                Log.e(this::class.java.simpleName, "init writer err", e)
                null
            }
        }

    // 只允许传入
    var logDirPath: String?
        get() = null
        set(value) {
            logDir = if (value == null) null else File(value)
        }

    @Volatile
    var fixFileOwner: ((File) -> Unit)? = null

    @Volatile
    var maxLinesPerFile: Int = 5000

    @Volatile
    var maxHistoryDays: Long = 7

    @Volatile
    var logLevel: LogLevel = ConfigConstants.DEF_LOG_LEVEL

    fun isLoggable(level: LogLevel): Boolean {
        val allowedPriority =
            if (BuildConfig.DEBUG) logLevel.coerceAtMost(LogLevel.Debug)
            else logLevel
        return level.priority >= allowedPriority.priority
    }

    inline fun <reified T> v(msg: String?, vararg args: Any?, tr: Throwable? = null) {
        log(T::class.java.simpleName, LogLevel.Verbose, msg, args, tr = tr)
    }

    inline fun <reified T> d(msg: String?, vararg args: Any?, tr: Throwable? = null) {
        log(T::class.java.simpleName, LogLevel.Debug, msg, args, tr = tr)
    }

    inline fun <reified T> i(msg: String?, vararg args: Any?, tr: Throwable? = null) {
        log(T::class.java.simpleName, LogLevel.Info, msg, args, tr = tr)
    }

    inline fun <reified T> w(msg: String?, vararg args: Any?, tr: Throwable? = null) {
        log(T::class.java.simpleName, LogLevel.Warning, msg, args, tr = tr)
    }

    inline fun <reified T> e(msg: String?, vararg args: Any?, tr: Throwable? = null) {
        log(T::class.java.simpleName, LogLevel.Error, msg, args, tr = tr)
    }

    inline fun <reified T> a(msg: String?, vararg args: Any?, tr: Throwable? = null) {
        log(T::class.java.simpleName, LogLevel.Assert, msg, args, tr = tr)
    }

    fun log(tag: String, level: LogLevel, msg: String?, vararg args: Any?, tr: Throwable?) {
        if (!isLoggable(level)) return
        val base = if (args.isEmpty()) msg.toString() else String.format(
            Locale.ENGLISH,
            msg ?: "null",
            args
        )
        val content = if (tr == null) base else "$base\n${Log.getStackTraceString(tr)}"
        println(tag, level, content)
    }

    fun println(tag: String, priority: LogLevel, msg: String): Int {
        writer?.write(tag, priority, msg)
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
            fun fromPriority(priority: Int): LogLevel =
                priorityMap[priority] ?: ConfigConstants.DEF_LOG_LEVEL
        }

        fun coerceAtMost(maximumPriority: LogLevel): LogLevel {
            return fromPriority(priority.coerceAtMost(maximumPriority.priority))
        }
    }

    private class DailyLineRotateFileWriter(private val logDir: File) {

        private val fileNameRegex = Regex("""^(\d{4}-\d{2}-\d{2})(?:_(\d+))?\.log$""")
        private val dateFileFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
        private val lineTimeFormatter = DateTimeFormatter.ofPattern("dd HH:mm:ss.SSS")
        private var activeDate = ""
        private var activeIndex = 0
        private var activeLines = 0
        private lateinit var writer: BufferedWriter

        init {
            if (!logDir.exists() && !logDir.mkdirs()) throw IOException("logDir.mkdirs() failed: ${logDir.absolutePath}")
            if (!logDir.isDirectory) throw IOException("logDir is not a directory: ${logDir.absolutePath}")
            fixFileOwner?.invoke(logDir)
        }

        fun write(tag: String, level: LogLevel, message: String) {
            Handlers.getHandler("LoggingThread").post {
                try {
                    val today = LocalDate.now()
                    val todayKey = today.format(dateFileFormatter)
                    if (activeDate != todayKey || !::writer.isInitialized) {
                        if (::writer.isInitialized) writer.close()
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
                                    match.groupValues[2].takeIf { it.isNotBlank() }?.toIntOrNull()
                                        ?: 0
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
                        if (!logFile.exists() && !logFile.createNewFile()) throw IOException("logFile.createNewFile() failed: ${logFile.absolutePath}")
                        if (logFile.isDirectory) throw IOException("logFile is a directory: ${logFile.absolutePath}")
                        fixFileOwner?.invoke(logFile)
                        writer = BufferedWriter(OutputStreamWriter(FileOutputStream(logFile, true)))
                    }
                    if (activeLines >= maxLinesPerFile) {
                        writer.close()
                        activeIndex += 1
                        activeLines = 0
                        val fileName =
                            if (activeIndex == 0) "$activeDate.log" else "${activeDate}_${activeIndex}.log"
                        val logFile = File(logDir, fileName)
                        if (!logFile.exists() && !logFile.createNewFile()) throw IOException("logFile.createNewFile() failed: ${logFile.absolutePath}")
                        if (logFile.isDirectory) throw IOException("logFile is a directory: ${logFile.absolutePath}")
                        fixFileOwner?.invoke(logFile)
                        writer = BufferedWriter(OutputStreamWriter(FileOutputStream(logFile, true)))
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
                    writer.write(sb.toString())
                    writer.newLine()
                    writer.flush()
                    activeLines += sb.count { it == '\n' } + 1
                } catch (e: Exception) {
                    Log.e(this::class.java.simpleName, "writing error", e)
                }
            }
        }
    }
}
