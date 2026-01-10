package yangfentuozi.batteryrecorder.ui.compose.components

import androidx.compose.runtime.Composable
import androidx.navigation.compose.rememberNavController
import yangfentuozi.batteryrecorder.ui.compose.navigation.BatteryRecorderNavHost

@Composable
fun BatteryRecorderApp() {
    val navController = rememberNavController()
    BatteryRecorderNavHost(navController)
}
