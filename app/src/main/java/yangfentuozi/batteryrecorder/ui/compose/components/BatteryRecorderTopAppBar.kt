package yangfentuozi.batteryrecorder.ui.compose.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults.topAppBarColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import yangfentuozi.batteryrecorder.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BatteryRecorderTopAppBar(
    onSettingsClick: () -> Unit = {},
    onStopServerClick: () -> Unit = {},
    onAboutClick: () -> Unit = {},
    showStopServer: Boolean = true,
    showBackButton: Boolean = false,
    onBackClick: () -> Unit = {}
) {
    var showMenu by remember { mutableStateOf(false) }

    TopAppBar(
        title = {
            Text(stringResource(R.string.app_name))
        },
        navigationIcon = {
            if (showBackButton) {
                IconButton(onClick = onBackClick) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, null)
                }
            }
        },
        colors = topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ),
        actions = {
            if (!showBackButton) {
                IconButton(onClick = onSettingsClick) {
                    Icon(Icons.Default.Settings, contentDescription = "设置")
                }
            }
            IconButton(onClick = { showMenu = !showMenu }) {
                Icon(Icons.Default.MoreVert, null)
            }
            DropdownMenu(
                expanded = showMenu,
                onDismissRequest = { showMenu = false }
            ) {
                if (!showBackButton && showStopServer) {
                    DropdownMenuItem(
                        text = { Text("停止服务") },
                        onClick = {
                            showMenu = false
                            onStopServerClick()
                        }
                    )
                }
                DropdownMenuItem(
                    text = { Text("关于") },
                    onClick = {
                        showMenu = false
                        onAboutClick()
                    }
                )
            }
        }
    )
}
