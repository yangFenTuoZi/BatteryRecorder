package yangfentuozi.batteryrecorder.server.writer

import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import java.io.IOException
import java.io.OutputStream

class AutoRetryStringWriter(
    private var outputStream: OutputStream,
    private val retryTimes: Int,
    private var retryIntervalMs: Long,
    private val reopenOutputStream: (() -> OutputStream?)
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
                    Log.e(TAG, "Failed to close output stream", e)
                }

                try {
                    outputStream = reopenOutputStream()
                        ?: throw IOException("The output stream which was reopened is null.")
                } catch (e: IOException) {
                    Log.e(TAG, "Failed to reopen output stream", e)
                    throw RuntimeException("Failed too times to write")
                }
            }
            synchronized(bufferLock) {
                try {
                    outputStream.write(buffer.toString().toByteArray())
                    outputStream.flush()
                    buffer.setLength(0)
                    retryCount = -1
                } catch (e: IOException) {
                    Log.e(TAG, "Failed to write to output stream", e)
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

    companion object {
        const val TAG = "AutoRetryStringWriter"
    }
}