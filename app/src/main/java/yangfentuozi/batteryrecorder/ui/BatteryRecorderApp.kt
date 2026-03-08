package yangfentuozi.batteryrecorder.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.rememberNavController
import yangfentuozi.batteryrecorder.ui.navigation.BatteryRecorderNavHost
import yangfentuozi.batteryrecorder.ui.viewmodel.MainViewModel
import yangfentuozi.batteryrecorder.ui.viewmodel.SettingsViewModel

@Composable
fun BatteryRecorderApp(
    mainViewModel: MainViewModel = viewModel(),
    settingsViewModel: SettingsViewModel = viewModel()
) {
    val context = LocalContext.current
    LaunchedEffect(settingsViewModel, context) {
        settingsViewModel.init(context)
    }

    val navController = rememberNavController()
    BatteryRecorderNavHost(
        navController = navController,
        mainViewModel = mainViewModel,
        settingsViewModel = settingsViewModel
    )
}
