package yangfentuozi.batteryrecorder.ui.screens.prediction

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import yangfentuozi.batteryrecorder.ui.components.global.LazySplicedColumnGroup
import yangfentuozi.batteryrecorder.ui.theme.AppShape
import yangfentuozi.batteryrecorder.ui.viewmodel.PredictionDetailUiEntry
import yangfentuozi.batteryrecorder.ui.viewmodel.PredictionDetailViewModel
import yangfentuozi.batteryrecorder.ui.viewmodel.SettingsViewModel
import yangfentuozi.batteryrecorder.utils.formatPower
import yangfentuozi.batteryrecorder.utils.formatRemainingTime

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PredictionDetailScreen(
    settingsViewModel: SettingsViewModel,
    viewModel: PredictionDetailViewModel = viewModel()
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val statisticsRequest by settingsViewModel.statisticsRequest.collectAsState()
    val dualCellEnabled by settingsViewModel.dualCellEnabled.collectAsState()
    val calibrationValue by settingsViewModel.calibrationValue.collectAsState()
    val dischargeDisplayPositive by settingsViewModel.dischargeDisplayPositive.collectAsState()

    // 统计请求变化时重新加载；显示正负值配置只影响 UI 映射，不触发重算。
    LaunchedEffect(statisticsRequest) {
        viewModel.load(context, statisticsRequest)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("应用预测") }
            )
        }
    ) { paddingValues ->
        when {
            uiState.isLoading && uiState.entries.isEmpty() -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }

            uiState.errorMessage != null -> {
                PredictionDetailMessage(
                    paddingValues = paddingValues,
                    message = uiState.errorMessage ?: "加载应用预测失败"
                )
            }

            uiState.entries.isEmpty() -> {
                PredictionDetailMessage(
                    paddingValues = paddingValues,
                    message = "暂无应用预测数据"
                )
            }

            else -> {
                LazySplicedColumnGroup(
                    items = uiState.entries,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                        .padding(horizontal = 16.dp),
                    key = { entry -> entry.packageName },
                    contentPadding = PaddingValues(vertical = 8.dp)
                ) {
                    PredictionDetailRow(
                        entry = it,
                        dischargeDisplayPositive = dischargeDisplayPositive,
                        dualCellEnabled = dualCellEnabled,
                        calibrationValue = calibrationValue
                    )
                }
            }
        }
    }
}

@Composable
private fun PredictionDetailMessage(
    paddingValues: PaddingValues,
    message: String
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
            .padding(24.dp)
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun PredictionDetailRow(
    entry: PredictionDetailUiEntry,
    dischargeDisplayPositive: Boolean,
    dualCellEnabled: Boolean,
    calibrationValue: Int
) {
    // 详情页直接消费原始功率均值，按当前设置决定是否将放电视为正值展示。
    val displayMultiplier = if (dischargeDisplayPositive) -1.0 else 1.0

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AppPredictionIcon(packageName = entry.packageName)

        Spacer(Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = entry.appLabel,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = entry.packageName,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Spacer(Modifier.width(12.dp))

        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = entry.currentHours?.let(::formatRemainingTime) ?: "数据不足",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.End
            )
            Text(
                text = formatPower(entry.averagePowerRaw * displayMultiplier, dualCellEnabled, calibrationValue)
                    .replace(" ", ""),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.End
            )
        }
    }
}

@Composable
private fun AppPredictionIcon(packageName: String) {
    val context = LocalContext.current
    val icon by produceState<ImageBitmap?>(initialValue = null, key1 = packageName) {
        // 图标解码放到后台线程，避免滚动时阻塞主线程。
        value = withContext(Dispatchers.IO) {
            runCatching {
                val packageManager = context.packageManager
                val appInfo = packageManager.getApplicationInfo(packageName, 0)
                appInfo.loadIcon(packageManager).toBitmap(48, 48).asImageBitmap()
            }.getOrNull()
        }
    }

    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(AppShape.medium)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
    ) {
        if (icon != null) {
            Image(
                bitmap = icon!!,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        }
    }
}
