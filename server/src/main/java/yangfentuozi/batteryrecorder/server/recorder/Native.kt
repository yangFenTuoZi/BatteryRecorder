package yangfentuozi.batteryrecorder.server.recorder

import android.annotation.SuppressLint
import android.content.pm.ApplicationInfo
import android.os.Build
import android.system.Os
import androidx.annotation.Keep
import yangfentuozi.batteryrecorder.shared.data.BatteryStatus
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.zip.ZipFile

@Keep
object Native {

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
    fun init(appInfo: ApplicationInfo) {
        val libraryTmpPath = "/data/local/tmp/libbatteryrecorder.so"
        val apk = ZipFile(appInfo.sourceDir)
        apk.getInputStream(apk.getEntry("lib/${Build.SUPPORTED_ABIS[0]}/libbatteryrecorder.so"))
            .copyTo(out = FileOutputStream(libraryTmpPath, false))
        File(libraryTmpPath).apply {
            deleteOnExit()
        }
        Os.chmod(libraryTmpPath, "400".toInt(8))
        System.load(libraryTmpPath)
        nativeInit()
    }

    @get:Throws(IOException::class)
    val power: Long
        get() = nativeGetVoltage() * nativeGetCurrent()

    @get:Throws(IOException::class)
    val capacity: Int
        get() = nativeGetCapacity()

    @get:Throws(IOException::class)
    val status: BatteryStatus
        get() = when (nativeGetStatus().toChar()) {
            'C' -> BatteryStatus.Charging
            'D' -> BatteryStatus.Discharging
            else -> BatteryStatus.Full
        }

    @get:Throws(IOException::class)
    val temp: Int
        get() = nativeGetTemp()
}