package yangfentuozi.batteryrecorder.ui

import android.util.Log
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.rememberNavController
import yangfentuozi.batteryrecorder.BuildConfig
import yangfentuozi.batteryrecorder.ui.dialog.home.UpdateDialog
import yangfentuozi.batteryrecorder.ui.navigation.BatteryRecorderNavHost
import yangfentuozi.batteryrecorder.ui.viewmodel.MainViewModel
import yangfentuozi.batteryrecorder.ui.viewmodel.SettingsViewModel
import yangfentuozi.batteryrecorder.utils.AppUpdate
import yangfentuozi.batteryrecorder.utils.UpdateUtils

private const val TAG = "BatteryRecorderApp"

private object StartupUpdateCheckGate {
    var hasChecked = false
}

@Composable
fun BatteryRecorderApp(
    mainViewModel: MainViewModel = viewModel(),
    settingsViewModel: SettingsViewModel = viewModel()
) {
    val context = LocalContext.current
    var pendingUpdate by remember { mutableStateOf<AppUpdate?>(null) }

    LaunchedEffect(settingsViewModel, context) {
        settingsViewModel.init(context)

        if (StartupUpdateCheckGate.hasChecked) return@LaunchedEffect
        StartupUpdateCheckGate.hasChecked = true

        if (!settingsViewModel.settingsUiState.value.checkUpdateOnStartup) return@LaunchedEffect

        val update = UpdateUtils.fetchUpdate() ?: run {
            Toast.makeText(context, "检测更新失败", Toast.LENGTH_SHORT).show()
            return@LaunchedEffect
        }
        if (BuildConfig.VERSION_CODE >= update.versionCode) return@LaunchedEffect

        Log.i(
            TAG,
            "检测到新版本: remote=${update.versionCode}, local=${BuildConfig.VERSION_CODE}"
        )
        pendingUpdate = update
    }

    val navController = rememberNavController()
    BatteryRecorderNavHost(
        navController = navController,
        mainViewModel = mainViewModel,
        settingsViewModel = settingsViewModel
    )

    pendingUpdate?.let { update ->
        UpdateDialog(
            update = update,
            onDismiss = { pendingUpdate = null }
        )
    }
}
