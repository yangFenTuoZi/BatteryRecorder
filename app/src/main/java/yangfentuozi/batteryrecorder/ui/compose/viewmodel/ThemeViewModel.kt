package yangfentuozi.batteryrecorder.ui.compose.viewmodel

import android.app.Application
import androidx.core.content.edit
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import yangfentuozi.batteryrecorder.ui.compose.theme.DarkThemeMode

class ThemeViewModel(application: Application) : AndroidViewModel(application) {
    private val prefs = application.getSharedPreferences("yangfentuozi.batteryrecorder_preferences", Application.MODE_PRIVATE)

    private val _darkThemeMode = MutableStateFlow(
        when (prefs.getString(KEY_DARK_THEME, null)) {
            "light" -> DarkThemeMode.LIGHT
            "dark" -> DarkThemeMode.DARK
            else -> DarkThemeMode.SYSTEM
        }
    )
    val darkThemeMode: StateFlow<DarkThemeMode> = _darkThemeMode.asStateFlow()

    fun setDarkThemeMode(mode: DarkThemeMode) {
        viewModelScope.launch {
            _darkThemeMode.value = mode
            prefs.edit {
                putString(
                    KEY_DARK_THEME, when (mode) {
                        DarkThemeMode.LIGHT -> "light"
                        DarkThemeMode.DARK -> "dark"
                        DarkThemeMode.SYSTEM -> null
                    }
                )
            }
        }
    }

    companion object {
        private const val KEY_DARK_THEME = "dark_theme"
    }
}
