package yangfentuozi.batteryrecorder.ipc

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.os.IBinder
import android.util.Log

class BinderProvider : ContentProvider() {

    override fun onCreate(): Boolean = true

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle? {
        if (method != "setBinder" || extras == null) {
            return Bundle().apply { putBoolean("accepted", false) }
        }

        val binder: IBinder? = extras.getBinder("binder")
        val binderAlive = binder?.pingBinder() == true
        if (binderAlive) {
            Service.binder = binder
        }
        Log.i("BatteryRecorderApp", "[BINDER] received=${binderAlive}")
        return Bundle().apply { putBoolean("accepted", binderAlive) }
    }

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? = null
    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0
}
