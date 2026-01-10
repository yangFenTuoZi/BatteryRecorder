package yangfentuozi.batteryrecorder.ui.compose.navigation

sealed class Screen(val route: String) {
    object Home : Screen("home")
    object Settings : Screen("settings")
}
