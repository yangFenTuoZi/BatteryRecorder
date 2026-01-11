package yangfentuozi.batteryrecorder.ui.compose.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

enum class DarkThemeMode {
    SYSTEM,
    LIGHT,
    DARK
}

@Composable
fun BatteryRecorderTheme(
    darkThemeMode: DarkThemeMode = DarkThemeMode.SYSTEM,
    content: @Composable () -> Unit
) {
    val darkTheme = when (darkThemeMode) {
        DarkThemeMode.SYSTEM -> isSystemInDarkTheme()
        DarkThemeMode.LIGHT -> false
        DarkThemeMode.DARK -> true
    }

    val context = LocalContext.current
    val colorScheme = if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)

    MaterialTheme(
        colorScheme = colorScheme,
        typography = AppTypography,
        content = content
    )
}
