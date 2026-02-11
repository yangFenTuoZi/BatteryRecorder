package yangfentuozi.batteryrecorder.data.history

import android.content.Context
import android.util.Log
import yangfentuozi.batteryrecorder.ipc.Service
import yangfentuozi.batteryrecorder.shared.Constants
import yangfentuozi.batteryrecorder.shared.sync.PfdFileReceiver
import java.io.File

object SyncUtil {
    fun sync(context: Context) {
        val service = Service.service ?: return
        val readPfd = service.sync() ?: return
        val outDir = File(context.dataDir, Constants.APP_POWER_DATA_PATH)

        Thread {
            try {
                PfdFileReceiver.receiveToDir(readPfd, outDir)
            } catch (e: Exception) {
                Log.e("Transfer", "Client receive error", e)
            }
        }.start()
    }
}