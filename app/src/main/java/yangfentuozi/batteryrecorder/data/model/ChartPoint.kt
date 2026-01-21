package yangfentuozi.batteryrecorder.data.model

data class ChartPoint(
    val timestamp: Long,
    val power: Double,
    val capacity: Int,
    val isDisplayOn: Boolean
)
