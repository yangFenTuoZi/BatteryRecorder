package yangfentuozi.batteryrecorder.shared.data

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import java.io.File

@Parcelize
data class RecordsFile(
    val type: BatteryStatus,
    val name: String
) : Parcelable {
    companion object {
        fun fromFile(file: File): RecordsFile {
            return RecordsFile(
                type = BatteryStatus.fromDataDirName(file.parentFile?.name),
                name = file.name
            )
        }
    }
}