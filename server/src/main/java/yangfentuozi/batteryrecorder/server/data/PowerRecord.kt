package yangfentuozi.batteryrecorder.server.data

data class PowerRecord(
    val timestamp: Long, val power: Long, val packageName: String?,
    val capacity: Int, val isDisplayOn: Int, val status: BatteryStatus,
    val temp: Int
) {
    override fun toString(): String {
        return "$timestamp,$power,$packageName,$capacity,$isDisplayOn,$temp"
    }
}