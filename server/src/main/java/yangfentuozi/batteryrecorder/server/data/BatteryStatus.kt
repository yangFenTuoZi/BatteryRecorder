package yangfentuozi.batteryrecorder.server.data

enum class BatteryStatus(val value: Int) {
    Charging(0),
    Discharging(1),
    Full(2);
}