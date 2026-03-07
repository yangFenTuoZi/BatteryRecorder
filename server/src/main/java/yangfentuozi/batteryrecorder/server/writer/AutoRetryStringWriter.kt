package yangfentuozi.batteryrecorder.server.writer

import android.os.Handler
import android.os.HandlerThread
import yangfentuozi.batteryrecorder.shared.util.LoggerX
import java.io.IOException
import java.io.OutputStream

class AutoRetryStringWriter(
    private var outputStream: OutputStream,
    private val retryTimes: Int,
    private var retryIntervalMs: Long,
    private val reopenOutputStream: (() -> OutputStream)
) {
    private val thread = HandlerThread("RetryThread")
    private val handler: Handler

    private val bufferLock = Object()
    private var retryCount = -1
    private val retryRunnable = object : Runnable {
        override fun run() {
            if (++retryCount > retryTimes) {
                try {
                    outputStream.close()
                } catch (e: IOException) {
                    LoggerX.e<AutoRetryStringWriter>("retryRunnable: 关闭 OutputStream 失败", tr = e)
                }

                try {
                    outputStream = reopenOutputStream()
                } catch (e: IOException) {
                    LoggerX.e<AutoRetryStringWriter>("retryRunnable: 重新打开 OutputStream 失败，多次重试失败，强行终止", tr = e)
                    throw RuntimeException()
                }
            }
            synchronized(bufferLock) {
                try {
                    outputStream.write(buffer.toString().toByteArray())
                    outputStream.flush()
                    buffer.setLength(0)
                    retryCount = -1
                } catch (e: IOException) {
                    if (++retryCount > retryTimes) {
                        LoggerX.e<AutoRetryStringWriter>("retryRunnable: 写入 OutputStream 失败，多次重试失败，强行终止", tr = e)
                        throw RuntimeException()
                    }
                    LoggerX.e<AutoRetryStringWriter>("retryRunnable: 写入 OutputStream 失败，准备重试", tr = e)
                    handler.postDelayed(this, retryIntervalMs)
                }
            }
        }
    }


    private val buffer = StringBuilder(4096)

    init {
        thread.start()
        handler = Handler(thread.looper)
    }

    fun write(stringBuilder: StringBuilder) {
        synchronized(bufferLock) {
            buffer.append(stringBuilder)
            if (retryCount == -1) {
                handler.post(retryRunnable)
            }
        }
    }

    fun close() {
        if (hasRetry()) return
        retryIntervalMs = 100
        handler.removeCallbacks(retryRunnable)
        handler.post(retryRunnable)
        while (handler.hasCallbacks(retryRunnable)) {
            Thread.sleep(100)
        }
    }

    fun hasRetry(): Boolean {
        return retryCount == -1 || handler.hasCallbacks(retryRunnable)
    }
}