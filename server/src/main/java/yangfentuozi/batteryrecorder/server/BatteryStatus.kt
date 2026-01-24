package yangfentuozi.batteryrecorder.server

enum class BatteryStatus(val value: Int) {
    Charging(0),
    Discharging(1),
    Full(2);

    companion object {
        fun from(value: Int) = entries.first { it.value == value }
    }
}