package yangfentuozi.batteryrecorder.ui.compose.components

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.rememberNavController
import yangfentuozi.batteryrecorder.ui.compose.navigation.BatteryRecorderNavHost
import yangfentuozi.batteryrecorder.ui.compose.viewmodel.MainViewModel
import yangfentuozi.batteryrecorder.ui.compose.viewmodel.SettingsViewModel

@Composable
fun BatteryRecorderApp(
    mainViewModel: MainViewModel = viewModel(),
    settingsViewModel: SettingsViewModel = viewModel()
) {
    val navController = rememberNavController()
    Surface(
        modifier = Modifier.fillMaxSize()
    ) {
        BatteryRecorderNavHost(
            navController = navController,
            mainViewModel = mainViewModel,
            settingsViewModel = settingsViewModel
        )
    }
}
