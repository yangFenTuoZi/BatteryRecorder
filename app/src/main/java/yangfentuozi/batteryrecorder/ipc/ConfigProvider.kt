package yangfentuozi.batteryrecorder.ipc

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.util.Log
import yangfentuozi.batteryrecorder.server.Config

class ConfigProvider : ContentProvider() {

    override fun onCreate(): Boolean = true

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle? {
        if (method == "requestConfig") {
            Log.i("BatteryRecorderApp", "requestConfig")

            val prefs = requireContext().getSharedPreferences(
                "yangfentuozi.batteryrecorder_preferences",
                Context.MODE_PRIVATE
            )
            return Bundle().apply {
                putParcelable(
                    "config", Config(
                        recordInterval = prefs.getLong("interval", 1000),
                        flushInterval = prefs.getLong("flush_interval", 30000),
                        batchSize = prefs.getInt("batch_size", 200),
                        screenOffRecord = prefs.getBoolean("record_screen_off", false),
                        segmentDuration = prefs.getLong("segment_duration", 1440)
                    )
                )
            }
        }
        return null
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?
    ): Cursor? = null

    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?
    ): Int = 0
}