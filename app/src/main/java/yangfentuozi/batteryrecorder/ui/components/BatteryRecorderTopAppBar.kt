package yangfentuozi.batteryrecorder.ui.components

import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import yangfentuozi.batteryrecorder.R
import yangfentuozi.batteryrecorder.ui.theme.AppShape

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
                 onDismissRequest = { showMenu = false },
                 shape = AppShape.large,
                 offset = DpOffset(x = 0.dp, y = (-48).dp),
                 modifier = Modifier.widthIn(min = 160.dp)
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
