package yangfentuozi.batteryrecorder.data.history

import android.content.Context
import android.util.Log
import yangfentuozi.batteryrecorder.ipc.Service
import yangfentuozi.batteryrecorder.server.sync.PfdFileReceiver
import java.io.File

object SyncUtil {
    fun sync(context: Context) {
        val service = Service.service ?: return
        val readPfd = service.sync() ?: return
        val outDir = File(context.dataDir, "power_data")

        Thread {
            try {
                PfdFileReceiver.receiveToDir(readPfd, outDir)
            } catch (e: Exception) {
                Log.e("Transfer", "Client receive error", e)
            }
        }.start()
    }
}