package yangfentuozi.batteryrecorder.ui.compose.settings

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import yangfentuozi.batteryrecorder.R
import yangfentuozi.batteryrecorder.Service
import yangfentuozi.batteryrecorder.ui.compose.viewmodel.ThemeViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    themeViewModel: ThemeViewModel = viewModel(),
    onNavigateBack: () -> Unit = {}
) {
    val darkThemeMode by themeViewModel.darkThemeMode.collectAsState()
    val context = LocalContext.current

    // 获取 SharedPreferences
    val prefs = remember {
        context.getSharedPreferences("yangfentuozi.batteryrecorder_preferences", android.content.Context.MODE_PRIVATE)
    }

    // 读取配置值
    var dualCellEnabled by remember { mutableStateOf(prefs.getBoolean("dual_cell", false)) }
    var calibrationValue by remember { mutableStateOf(prefs.getInt("current_unit_calibration", -1)) }
    var intervalMs by remember { mutableStateOf(prefs.getLong("interval", 900)) }
    var batchSize by remember { mutableStateOf(prefs.getInt("batch_size", 20)) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null)
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // 外观设置
            item {
                AppearanceSection(
                    darkThemeMode = darkThemeMode,
                    onDarkThemeModeChange = { themeViewModel.setDarkThemeMode(it) }
                )
            }

            item {
                Spacer(modifier = Modifier.height(8.dp))
            }

            // 校准设置
            item {
                CalibrationSection(
                    dualCellEnabled = dualCellEnabled,
                    onDualCellChange = { enabled ->
                        dualCellEnabled = enabled
                        prefs.edit().putBoolean("dual_cell", enabled).apply()
                    },
                    calibrationValue = calibrationValue,
                    onCalibrationChange = { value ->
                        calibrationValue = value
                        prefs.edit().putInt("current_unit_calibration", value).apply()
                    }
                )
            }

            item {
                Spacer(modifier = Modifier.height(8.dp))
            }

            // 服务器设置
            item {
                ServerSection(
                    intervalMs = intervalMs,
                    onIntervalChange = { value ->
                        intervalMs = value
                        prefs.edit().putLong("interval", value).apply()
                        Service.service?.refreshConfig()
                    },
                    batchSize = batchSize,
                    onBatchSizeChange = { value ->
                        batchSize = value
                        prefs.edit().putInt("batch_size", value).apply()
                        Service.service?.refreshConfig()
                    }
                )
            }
        }
    }
}
