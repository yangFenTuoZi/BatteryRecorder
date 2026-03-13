package yangfentuozi.batteryrecorder.data.model

data class ChartPoint(
    val timestamp: Long,
    val power: Double,
    val packageName: String?,
    val capacity: Int,
    val isDisplayOn: Boolean,
    val temp: Int
)
