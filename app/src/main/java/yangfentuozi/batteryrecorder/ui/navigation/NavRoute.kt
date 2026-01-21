package yangfentuozi.batteryrecorder.ui.navigation

sealed class Screen(val route: String) {
    object Home : Screen("home")
    object Settings : Screen("settings")
    object HistoryList : Screen("history/{type}") {
        fun createRoute(type: String): String = "history/$type"
    }
    object RecordDetail : Screen("record/{type}/{name}") {
        fun createRoute(type: String, name: String): String = "record/$type/$name"
    }
}
