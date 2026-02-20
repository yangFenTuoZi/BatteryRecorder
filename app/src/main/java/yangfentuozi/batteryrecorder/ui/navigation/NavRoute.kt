package yangfentuozi.batteryrecorder.ui.navigation

sealed class NavRoute(val route: String) {
    object Home : NavRoute("home")
    object Settings : NavRoute("settings")
    object HistoryList : NavRoute("history/{type}") {
        fun createRoute(type: String): String = "history/$type"
    }
    object RecordDetail : NavRoute("record/{type}/{name}") {
        fun createRoute(type: String, name: String): String = "record/$type/$name"
    }
}
