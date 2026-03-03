package yangfentuozi.batteryrecorder.server.recorder.sampler

import android.annotation.SuppressLint
import android.content.pm.ApplicationInfo
import android.os.Build
import android.system.Os
import android.util.Log
import androidx.annotation.Keep
import yangfentuozi.batteryrecorder.shared.data.BatteryStatus
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipFile

@Keep
object SysfsSampler: Sampler {

    @JvmStatic
    external fun nativeInit(): Int

    @JvmStatic
    external fun nativeGetVoltage(): Long

    @JvmStatic
    external fun nativeGetCurrent(): Long

    @JvmStatic
    external fun nativeGetCapacity(): Int

    @JvmStatic
    external fun nativeGetStatus(): Int

    @JvmStatic
    external fun nativeGetTemp(): Int

    @SuppressLint("UnsafeDynamicallyLoadedCode")
    fun init(appInfo: ApplicationInfo): Boolean {
        try {
            val libraryTmpPath = "/data/local/tmp/libbatteryrecorder.so"
            val apk = ZipFile(appInfo.sourceDir)
            apk.getInputStream(apk.getEntry("lib/${Build.SUPPORTED_ABIS[0]}/libbatteryrecorder.so"))
                .copyTo(out = FileOutputStream(libraryTmpPath, false))
            File(libraryTmpPath).apply {
                deleteOnExit()
            }
            Os.chmod(libraryTmpPath, "400".toInt(8))
            System.load(libraryTmpPath)
            return nativeInit() == 1
        } catch (e: Throwable) {
            Log.w("SysfsSampler", "load jni err", e)
            return false
        }
    }

    override fun sample(): Sampler.BatteryData {
        return Sampler.BatteryData(
            voltage = nativeGetVoltage(),
            current = nativeGetCurrent(),
            capacity = nativeGetCapacity(),
            status = when (nativeGetStatus().toChar()) {
                'C' -> BatteryStatus.Charging
                'D' -> BatteryStatus.Discharging
                'N' -> BatteryStatus.NotCharging
                'F' -> BatteryStatus.Full
                else -> BatteryStatus.Unknown
            },
            temp = nativeGetTemp()
        )
    }
}