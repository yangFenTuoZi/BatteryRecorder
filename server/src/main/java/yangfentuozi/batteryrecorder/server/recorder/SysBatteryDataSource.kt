package yangfentuozi.batteryrecorder.server.recorder

import android.util.Log
import yangfentuozi.batteryrecorder.server.Server
import java.io.IOException

class SysBatteryDataSource : BatteryDataSource {
    private var lastErrorLogTimeMs = 0L

    override fun readSample(nowMs: Long): BatterySample? {
        return try {
            BatterySample(
                timestampMs = nowMs,
                powerNw = Native.power,
                capacity = Native.capacity,
                status = Native.status
            )
        } catch (e: IOException) {
            logError(nowMs, "Failed to read sys battery sample", e)
            null
        } catch (e: Throwable) {
            logError(nowMs, "Unexpected sys battery read error", e)
            null
        }
    }

    private fun logError(nowMs: Long, message: String, tr: Throwable) {
        if (nowMs - lastErrorLogTimeMs < ERROR_LOG_INTERVAL_MS) return
        lastErrorLogTimeMs = nowMs
        Log.e(Server.TAG, message, tr)
    }

    companion object {
        private const val ERROR_LOG_INTERVAL_MS = 10_000L
    }
}
