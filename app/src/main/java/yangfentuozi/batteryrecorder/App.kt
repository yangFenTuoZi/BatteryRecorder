package yangfentuozi.batteryrecorder

import android.app.Application
import yangfentuozi.batteryrecorder.shared.Constants
import yangfentuozi.batteryrecorder.shared.util.LoggerX
import java.io.File

class App: Application() {
    override fun onCreate() {
        super.onCreate()
        LoggerX.logDir = File(cacheDir, Constants.APP_LOG_DIR_PATH)
    }
}