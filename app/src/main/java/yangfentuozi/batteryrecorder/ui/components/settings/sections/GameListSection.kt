package yangfentuozi.batteryrecorder.ui.components.settings.sections

import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import yangfentuozi.batteryrecorder.ui.components.global.SplicedColumnGroup
import yangfentuozi.batteryrecorder.ui.components.settings.SettingsItem

private data class GameAppInfo(
    val packageName: String,
    val label: String,
    val isSystemDetected: Boolean
)

@Composable
fun GameListSection(
    gamePackages: Set<String>,
    onGamePackagesChange: (Set<String>) -> Unit
) {
    var showDialog by remember { mutableStateOf(false) }

    val summary = if (gamePackages.isEmpty()) "未选择" else "已选 ${gamePackages.size} 个"

    SplicedColumnGroup(
        title = "续航预测",
        modifier = Modifier.padding(horizontal = 16.dp)
    ) {
        item {
            SettingsItem(
                title = "排除高负载 App",
                summary = summary
            ) { showDialog = true }
        }
    }

    if (showDialog) {
        GamePickerDialog(
            currentSelection = gamePackages,
            onDismiss = { showDialog = false },
            onConfirm = { selected ->
                onGamePackagesChange(selected)
                showDialog = false
            }
        )
    }
}

@Composable
private fun GamePickerDialog(
    currentSelection: Set<String>,
    onDismiss: () -> Unit,
    onConfirm: (Set<String>) -> Unit
) {
    val context = LocalContext.current
    var selected by remember { mutableStateOf(currentSelection.toMutableSet()) }

    val gameApps = remember {
        val pm = context.packageManager
        val installed = pm.getInstalledApplications(PackageManager.GET_META_DATA)

        // 系统标记为游戏的 App
        val detected = installed
            .filter { it.category == ApplicationInfo.CATEGORY_GAME }
            .map {
                GameAppInfo(
                    packageName = it.packageName,
                    label = it.loadLabel(pm).toString(),
                    isSystemDetected = true
                )
            }

        val detectedPkgs = detected.map { it.packageName }.toSet()

        // 用户手动添加但不在系统游戏列表中的
        val manual = currentSelection
            .filter { it !in detectedPkgs }
            .map { pkg ->
                val label = runCatching {
                    pm.getApplicationInfo(pkg, 0).loadLabel(pm).toString()
                }.getOrDefault(pkg)
                GameAppInfo(
                    packageName = pkg,
                    label = label,
                    isSystemDetected = false
                )
            }

        (detected + manual).sortedBy { it.label }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("选择游戏 App") },
        text = {
            Column {
                Text(
                    text = "已选 App 不参与续航预测统计",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))

                if (gameApps.isEmpty()) {
                    Text(
                        text = "未检测到游戏 App",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 16.dp)
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 400.dp)
                    ) {
                        items(gameApps, key = { it.packageName }) { app ->
                            val checked = app.packageName in selected
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        selected = if (checked) {
                                            (selected - app.packageName).toMutableSet()
                                        } else {
                                            (selected + app.packageName).toMutableSet()
                                        }
                                    }
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = checked,
                                    onCheckedChange = null
                                )
                                Spacer(Modifier.width(8.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = app.label,
                                        style = MaterialTheme.typography.bodyMedium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = app.packageName,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(selected) }) {
                Text(stringResource(android.R.string.ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(android.R.string.cancel))
            }
        }
    )
}
