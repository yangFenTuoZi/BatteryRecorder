package yangfentuozi.batteryrecorder.shared

object Constants {
    // app 包名
    const val APP_PACKAGE_NAME: String = "yangfentuozi.batteryrecorder"
    // Shell app 包名
    const val SHELL_PACKAGE_NAME: String = "com.android.shell"
    // **相对**于 app 数据目录的功率记录数据
    const val APP_POWER_DATA_PATH = "power_data"
    // **相对**于 Shell app 数据目录的功率记录数据
    const val SHELL_POWER_DATA_PATH = "batteryrecorder_power_data"

    // **相对**于 app 缓存目录的日志文件夹
    const val APP_LOG_DIR_PATH = "logs"
    // **相对**于 Shell app 数据目录的日志文件夹
    const val SHELL_LOG_DIR_PATH = "/cache/batteryrecorder_logs"

    // 数据文件夹
    const val CHARGE_DATA_DIR = "charge"
    const val DISCHARGE_DATA_DIR = "discharge"
}