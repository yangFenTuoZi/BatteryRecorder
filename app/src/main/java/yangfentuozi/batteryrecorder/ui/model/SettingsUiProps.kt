package yangfentuozi.batteryrecorder.ui.model

data class SettingsUiProps(
    val state: SettingsUiState,
    val actions: SettingsActions,
    val serviceConnected: Boolean
)
