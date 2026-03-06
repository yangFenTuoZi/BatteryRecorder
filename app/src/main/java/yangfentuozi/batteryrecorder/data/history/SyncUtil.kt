package yangfentuozi.batteryrecorder.data.history

import android.content.Context
import android.util.Log
import yangfentuozi.batteryrecorder.ipc.Service
import yangfentuozi.batteryrecorder.server.IService
import yangfentuozi.batteryrecorder.shared.Constants
import yangfentuozi.batteryrecorder.shared.sync.PfdFileReceiver
import java.io.File
import java.nio.file.Files

object SyncUtil {
    fun sync(context: Context) {
        val service = Service.service ?: return
        val readPfd = service.sync() ?: return
        val outDir = File(context.dataDir, Constants.APP_POWER_DATA_PATH)
        val syncedFiles = LinkedHashSet<File>()

        try {
            PfdFileReceiver.receiveToDir(readPfd, outDir) { savedFile, _ ->
                if (savedFile.isFile) {
                    syncedFiles.add(savedFile)
                }
            }
            prebuildHistoryDetailCaches(context, service, syncedFiles)
        } catch (e: Exception) {
            Log.e("Transfer", "Client receive error", e)
        }
    }

    private fun prebuildHistoryDetailCaches(
        context: Context,
        service: IService,
        syncedFiles: Set<File>
    ) {
        if (syncedFiles.isEmpty()) return
        val currentFile = runCatching { service.currRecordsFile }
            .getOrNull()
            ?.let { HistoryRepository.run { it.toFile(context) } }

        syncedFiles.forEach { file ->
            if (!isHistoryRecordFile(file)) return@forEach
            if (currentFile != null && runCatching { Files.isSameFile(file.toPath(), currentFile.toPath()) }.getOrDefault(false)) {
                return@forEach
            }
            runCatching {
                HistoryRepository.prebuildRecordDetailCache(context, file)
            }.onFailure { error ->
                Log.e("Transfer", "预生成详情缓存失败: ${file.name}", error)
            }
        }
    }

    private fun isHistoryRecordFile(file: File): Boolean {
        return file.parentFile?.name == Constants.CHARGE_DATA_DIR ||
            file.parentFile?.name == Constants.DISCHARGE_DATA_DIR
    }
}
