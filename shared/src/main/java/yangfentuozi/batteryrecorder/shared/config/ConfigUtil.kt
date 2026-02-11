package yangfentuozi.batteryrecorder.shared.config

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
                var recordIntervalMs = ConfigConstants.DEF_RECORD_INTERVAL_MS
                var batchSize = ConfigConstants.DEF_BATCH_SIZE
                var writeLatencyMs = ConfigConstants.DEF_WRITE_LATENCY_MS
                var screenOffRecordEnabled = ConfigConstants.DEF_SCREEN_OFF_RECORD_ENABLED
                var segmentDurationMin = ConfigConstants.DEF_SEGMENT_DURATION_MIN

                while (eventType != XmlPullParser.END_DOCUMENT) {
                    if (eventType == XmlPullParser.START_TAG) {
                        val nameAttr = parser.getAttributeValue(null, "name")
                        val valueAttr = parser.getAttributeValue(null, "value")

                        when (nameAttr) {
                            ConfigConstants.KEY_RECORD_INTERVAL_MS ->
                                recordIntervalMs = valueAttr.toLongOrNull() ?: ConfigConstants.DEF_RECORD_INTERVAL_MS

                            ConfigConstants.KEY_BATCH_SIZE ->
                                batchSize = valueAttr.toIntOrNull() ?: ConfigConstants.DEF_BATCH_SIZE

                            ConfigConstants.KEY_WRITE_LATENCY_MS ->
                                writeLatencyMs = valueAttr.toLongOrNull() ?: ConfigConstants.DEF_WRITE_LATENCY_MS

                            ConfigConstants.KEY_SCREEN_OFF_RECORD_ENABLED -> {
                                screenOffRecordEnabled = valueAttr.toBooleanStrictOrNull() ?: ConfigConstants.DEF_SCREEN_OFF_RECORD_ENABLED
                            }

                            ConfigConstants.KEY_SEGMENT_DURATION_MIN ->
                                segmentDurationMin = valueAttr.toLongOrNull() ?: ConfigConstants.DEF_SEGMENT_DURATION_MIN
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
            recordIntervalMs = prefs.getLong(ConfigConstants.KEY_RECORD_INTERVAL_MS, ConfigConstants.DEF_RECORD_INTERVAL_MS),
            writeLatencyMs = prefs.getLong(ConfigConstants.KEY_WRITE_LATENCY_MS, ConfigConstants.DEF_WRITE_LATENCY_MS),
            batchSize = prefs.getInt(ConfigConstants.KEY_BATCH_SIZE, ConfigConstants.DEF_BATCH_SIZE),
            screenOffRecordEnabled = prefs.getBoolean(
                ConfigConstants.KEY_SCREEN_OFF_RECORD_ENABLED,
                ConfigConstants.DEF_SCREEN_OFF_RECORD_ENABLED
            ),
            segmentDurationMin = prefs.getLong(ConfigConstants.KEY_SEGMENT_DURATION_MIN, ConfigConstants.DEF_SEGMENT_DURATION_MIN)
        ))
    }

    fun coerceConfigValue(config: Config): Config {
        return config.copy(
            recordIntervalMs = config.recordIntervalMs.coerceIn(
                ConfigConstants.MIN_RECORD_INTERVAL_MS,
                ConfigConstants.MAX_RECORD_INTERVAL_MS
            ),
            batchSize = config.batchSize.coerceIn(
                ConfigConstants.MIN_BATCH_SIZE,
                ConfigConstants.MAX_BATCH_SIZE
            ),
            writeLatencyMs = config.writeLatencyMs.coerceIn(
                ConfigConstants.MIN_WRITE_LATENCY_MS,
                ConfigConstants.MAX_WRITE_LATENCY_MS
            ),
            segmentDurationMin = config.segmentDurationMin.coerceIn(
                ConfigConstants.MIN_SEGMENT_DURATION_MIN,
                ConfigConstants.MAX_SEGMENT_DURATION_MIN
            )
        )
    }
}