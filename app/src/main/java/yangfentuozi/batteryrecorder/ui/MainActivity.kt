package yangfentuozi.batteryrecorder.ui

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import yangfentuozi.batteryrecorder.ui.compose.components.BatteryRecorderApp
import yangfentuozi.batteryrecorder.ui.compose.theme.BatteryRecorderTheme
import yangfentuozi.batteryrecorder.ui.compose.viewmodel.ThemeViewModel

class MainActivity : BaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            val themeViewModel: ThemeViewModel = viewModel()
            val darkThemeMode by themeViewModel.darkThemeMode.collectAsState()

            BatteryRecorderTheme(
                darkThemeMode = darkThemeMode
            ) {
                BatteryRecorderApp()
            }
        }
    }
}