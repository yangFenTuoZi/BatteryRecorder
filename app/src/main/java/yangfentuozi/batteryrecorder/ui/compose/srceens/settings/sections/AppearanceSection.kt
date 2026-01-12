package yangfentuozi.batteryrecorder.ui.compose.srceens.settings.sections

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import yangfentuozi.batteryrecorder.ui.compose.srceens.settings.SettingsItemContainer
import yangfentuozi.batteryrecorder.ui.compose.srceens.settings.SettingsRadioItem
import yangfentuozi.batteryrecorder.ui.compose.srceens.settings.SettingsTitle
import yangfentuozi.batteryrecorder.ui.compose.theme.DarkThemeMode

@Composable
fun AppearanceSection(
    darkThemeMode: DarkThemeMode,
    onDarkThemeModeChange: (DarkThemeMode) -> Unit
) {
    Column {
        SettingsTitle("外观")

        SettingsItemContainer(
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // 跟随系统
            SettingsRadioItem(
                text = "跟随系统",
                selected = darkThemeMode == DarkThemeMode.SYSTEM,
                onClick = { onDarkThemeModeChange(DarkThemeMode.SYSTEM) }
            )

            // 浅色模式
            SettingsRadioItem(
                text = "浅色",
                selected = darkThemeMode == DarkThemeMode.LIGHT,
                onClick = { onDarkThemeModeChange(DarkThemeMode.LIGHT) }
            )

            // 深色模式
            SettingsRadioItem(
                text = "深色",
                selected = darkThemeMode == DarkThemeMode.DARK,
                onClick = { onDarkThemeModeChange(DarkThemeMode.DARK) }
            )
        }
    }
}
