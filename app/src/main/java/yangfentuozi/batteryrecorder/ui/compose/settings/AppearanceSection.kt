package yangfentuozi.batteryrecorder.ui.compose.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import yangfentuozi.batteryrecorder.ui.compose.theme.DarkThemeMode

@Composable
fun AppearanceSection(
    darkThemeMode: DarkThemeMode,
    onDarkThemeModeChange: (DarkThemeMode) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(16.dp)
    ) {
        Text(
            text = "外观",
            style = androidx.compose.material3.MaterialTheme.typography.titleMedium,
            color = androidx.compose.material3.MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        // 跟随系统
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "跟随系统",
                modifier = Modifier.weight(1f)
            )
            RadioButton(
                selected = darkThemeMode == DarkThemeMode.SYSTEM,
                onClick = { onDarkThemeModeChange(DarkThemeMode.SYSTEM) }
            )
        }

        // 浅色模式
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "浅色",
                modifier = Modifier.weight(1f)
            )
            RadioButton(
                selected = darkThemeMode == DarkThemeMode.LIGHT,
                onClick = { onDarkThemeModeChange(DarkThemeMode.LIGHT) }
            )
        }

        // 深色模式
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "深色",
                modifier = Modifier.weight(1f)
            )
            RadioButton(
                selected = darkThemeMode == DarkThemeMode.DARK,
                onClick = { onDarkThemeModeChange(DarkThemeMode.DARK) }
            )
        }
    }
}
