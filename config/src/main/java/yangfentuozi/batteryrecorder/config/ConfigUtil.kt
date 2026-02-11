package yangfentuozi.batteryrecorder.config

import android.content.SharedPreferences
import android.os.Build
import android.os.RemoteException
import android.util.Log
import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException
import yangfentuozi.hiddenapi.compat.ActivityManagerCompat
import java.io.File
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.io.IOException

object ConfigUtil {
    const val TAG = "ConfigUtil"

    fun getConfigByContentProvider(): Config? {
        return try {
            val reply = ActivityManagerCompat.contentProviderCall(
                "yangfentuozi.batteryrecorder.configProvider",
                "requestConfig",
                null,
                null
            )
            if (reply == null) throw NullPointerException("reply is null")
            reply.classLoader = Config::class.java.classLoader
            val config = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                reply.getParcelable("config", Config::class.java)
            } else {
                @Suppress("DEPRECATION")
                reply.getParcelable("config")
            }
            if (config == null) throw NullPointerException("config is null")
            Log.i(TAG, "Requested config")
            coerceConfigValue(config)
        } catch (e: RemoteException) {
            Log.e(TAG, "Failed to request config", e)
            null
        } catch (e: NullPointerException) {
            Log.e(TAG, "Failed to request config", e)
            null
        }
    }

    fun getConfigByReading(configFile: File): Config? {
        if (!configFile.exists()) {
            Log.e(TAG, "Config file not found")
            return null
        }

        return try {
            FileInputStream(configFile).use { fis ->
                val parser = Xml.newPullParser()
                parser.setInput(fis, "UTF-8")

                var eventType = parser.eventType
                var recordIntervalMs = Constants.DEF_RECORD_INTERVAL_MS
                var batchSize = Constants.DEF_BATCH_SIZE
                var writeLatencyMs = Constants.DEF_WRITE_LATENCY_MS
                var screenOffRecordEnabled = Constants.DEF_SCREEN_OFF_RECORD_ENABLED
                var segmentDurationMin = Constants.DEF_SEGMENT_DURATION_MIN

                while (eventType != XmlPullParser.END_DOCUMENT) {
                    if (eventType == XmlPullParser.START_TAG) {
                        val nameAttr = parser.getAttributeValue(null, "name")
                        val valueAttr = parser.getAttributeValue(null, "value")

                        when (nameAttr) {
                            Constants.KEY_RECORD_INTERVAL_MS ->
                                recordIntervalMs = valueAttr.toLongOrNull() ?: Constants.DEF_RECORD_INTERVAL_MS

                            Constants.KEY_BATCH_SIZE ->
                                batchSize = valueAttr.toIntOrNull() ?: Constants.DEF_BATCH_SIZE

                            Constants.KEY_WRITE_LATENCY_MS ->
                                writeLatencyMs = valueAttr.toLongOrNull() ?: Constants.DEF_WRITE_LATENCY_MS

                            Constants.KEY_SCREEN_OFF_RECORD_ENABLED -> {
                                screenOffRecordEnabled = valueAttr.toBooleanStrictOrNull() ?: Constants.DEF_SCREEN_OFF_RECORD_ENABLED
                            }

                            Constants.KEY_SEGMENT_DURATION_MIN ->
                                segmentDurationMin = valueAttr.toLongOrNull() ?: Constants.DEF_SEGMENT_DURATION_MIN
                        }
                    }
                    eventType = parser.next()
                }

                coerceConfigValue(Config(
                    recordIntervalMs = recordIntervalMs,
                    writeLatencyMs = writeLatencyMs,
                    batchSize = batchSize,
                    screenOffRecordEnabled = screenOffRecordEnabled,
                    segmentDurationMin = segmentDurationMin
                ))
            }
        } catch (e: FileNotFoundException) {
            Log.e(TAG, "Config file not found", e)
            null
        } catch (e: IOException) {
            Log.e(TAG, "Error reading config file", e)
            null
        } catch (e: XmlPullParserException) {
            Log.e(TAG, "Error parsing config file", e)
            null
        }
    }

    fun getConfigBySharedPreferences(prefs: SharedPreferences): Config {
        return coerceConfigValue(Config(
            recordIntervalMs = prefs.getLong(Constants.KEY_RECORD_INTERVAL_MS, Constants.DEF_RECORD_INTERVAL_MS),
            writeLatencyMs = prefs.getLong(Constants.KEY_WRITE_LATENCY_MS, Constants.DEF_WRITE_LATENCY_MS),
            batchSize = prefs.getInt(Constants.KEY_BATCH_SIZE, Constants.DEF_BATCH_SIZE),
            screenOffRecordEnabled = prefs.getBoolean(
                Constants.KEY_SCREEN_OFF_RECORD_ENABLED,
                Constants.DEF_SCREEN_OFF_RECORD_ENABLED
            ),
            segmentDurationMin = prefs.getLong(Constants.KEY_SEGMENT_DURATION_MIN, Constants.DEF_SEGMENT_DURATION_MIN)
        ))
    }

    fun coerceConfigValue(config: Config): Config {
        return config.copy(
            recordIntervalMs = config.recordIntervalMs.coerceIn(
                Constants.MIN_RECORD_INTERVAL_MS,
                Constants.MAX_RECORD_INTERVAL_MS
            ),
            batchSize = config.batchSize.coerceIn(
                Constants.MIN_BATCH_SIZE,
                Constants.MAX_BATCH_SIZE
            ),
            writeLatencyMs = config.writeLatencyMs.coerceIn(
                Constants.MIN_WRITE_LATENCY_MS,
                Constants.MAX_WRITE_LATENCY_MS
            ),
            segmentDurationMin = config.segmentDurationMin.coerceIn(
                Constants.MIN_SEGMENT_DURATION_MIN,
                Constants.MAX_SEGMENT_DURATION_MIN
            )
        )
    }
}