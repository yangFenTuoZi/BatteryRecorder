package yangfentuozi.batteryrecorder.ui

import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.rememberNavController
import yangfentuozi.batteryrecorder.BuildConfig
import yangfentuozi.batteryrecorder.shared.util.LoggerX
import yangfentuozi.batteryrecorder.ui.dialog.home.UpdateDialog
import yangfentuozi.batteryrecorder.ui.navigation.BatteryRecorderNavHost
import yangfentuozi.batteryrecorder.ui.viewmodel.MainViewModel
import yangfentuozi.batteryrecorder.ui.viewmodel.SettingsViewModel
import yangfentuozi.batteryrecorder.utils.AppUpdate
import yangfentuozi.batteryrecorder.utils.UpdateUtils

private object BatteryRecorderAppLogger

@Composable
fun BatteryRecorderApp(
    mainViewModel: MainViewModel = viewModel(),
    settingsViewModel: SettingsViewModel = viewModel()
) {
    val context = LocalContext.current
    var hasCheckedUpdateOnStartup by rememberSaveable { mutableStateOf(false) }
    var pendingUpdate by remember { mutableStateOf<AppUpdate?>(null) }

    LaunchedEffect(settingsViewModel, context) {
        settingsViewModel.init(context)

        if (hasCheckedUpdateOnStartup) return@LaunchedEffect
        if (!settingsViewModel.settingsUiState.value.checkUpdateOnStartup) {
            LoggerX.i<BatteryRecorderAppLogger>("启动更新检测已关闭，跳过检查")
            return@LaunchedEffect
        }
        hasCheckedUpdateOnStartup = true
        LoggerX.i<BatteryRecorderAppLogger>("启动更新检测开始，请求最新 release")

        val update = UpdateUtils.fetchUpdate() ?: run {
            LoggerX.w<BatteryRecorderAppLogger>("启动更新检测失败，未获取到可用更新信息")
            Toast.makeText(context, "检测更新失败", Toast.LENGTH_SHORT).show()
            return@LaunchedEffect
        }
        if (BuildConfig.VERSION_CODE >= update.versionCode) {
            LoggerX.i<BatteryRecorderAppLogger>(
                "启动更新检测完成，无需更新，remote=${update.versionCode} local=${BuildConfig.VERSION_CODE}"
            )
            return@LaunchedEffect
        }

        LoggerX.i<BatteryRecorderAppLogger>(
            "启动更新检测完成，发现新版本，remote=${update.versionCode} local=${BuildConfig.VERSION_CODE}"
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
