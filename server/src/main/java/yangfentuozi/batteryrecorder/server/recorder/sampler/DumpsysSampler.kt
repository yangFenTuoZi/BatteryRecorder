package yangfentuozi.batteryrecorder.server.recorder.sampler

import android.os.BatteryManager
import android.os.BatteryProperty
import android.os.IBatteryPropertiesRegistrar
import android.os.ParcelFileDescriptor
import android.os.ServiceManager
import android.util.Log
import yangfentuozi.batteryrecorder.shared.data.BatteryStatus


class DumpsysSampler : Sampler {
    private val batteryService = ServiceManager.getService("battery")
    private var registrar: IBatteryPropertiesRegistrar =
        IBatteryPropertiesRegistrar.Stub.asInterface(
            ServiceManager.getService("batteryproperties")
        )

    override fun sample(): Sampler.BatteryData {
        val pipe = ParcelFileDescriptor.createPipe()

        val readSide = pipe[0]
        val writeSide = pipe[1]

        // 执行 dump
        Thread {
            try {
                batteryService.dump(writeSide.fileDescriptor, arrayOf())
            } catch (e: Exception) {
                Log.e("DumpsysSampler", "dump err", e)
            } finally {
                writeSide.close()
            }
        }.start()

        var flag = false
        var voltage: Long = 0
        var current: Long = 0
        var capacity = 0
        var status: BatteryStatus = BatteryStatus.Unknown
        var temp = 0
        try {
            val prop = BatteryProperty()
            registrar.getProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW, prop)
            current = prop.long
            registrar.getProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY, prop)
            capacity = prop.long.toInt()
            registrar.getProperty(BatteryManager.BATTERY_PROPERTY_STATUS, prop)
            status = BatteryStatus.fromValue(prop.long.toInt())

            ParcelFileDescriptor.AutoCloseInputStream(readSide).bufferedReader().use { reader ->
                var line: String?
                while ((reader.readLine().also { line = it }) != null) {
                    if (line != null) if (flag) {
                        when {
                            line.contains("voltage:") -> {
                                voltage = line.substringAfter(": ").trim().toLongOrNull() ?: 0
                            }

                            line.contains("temperature:") -> {
                                temp = line.substringAfter(": ").trim().toIntOrNull() ?: 0
                            }
                        }
                    } else if (line.contains("Current Battery Service state:")) flag = true
                }
            }
        } catch (e: Exception) {
            Log.e("DumpsysSampler", "read dump out err", e)
        } finally {
            readSide.close()
        }
        BatteryManager.BATTERY_PROPERTY_CURRENT_NOW
        return Sampler.BatteryData(
            voltage = voltage * 1000, // 修正单位与内核数据一致
            current = current,
            capacity = capacity,
            status = status,
            temp = temp
        )
    }
}