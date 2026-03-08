package yangfentuozi.batteryrecorder.ui.components.settings.sections

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import yangfentuozi.batteryrecorder.ui.components.global.M3ESwitchWidget
import yangfentuozi.batteryrecorder.ui.components.global.SplicedColumnGroup
import yangfentuozi.batteryrecorder.ui.model.SettingsUiProps

@Composable
fun AppSection(
    props: SettingsUiProps
) {
    val state = props.state
    val actions = props.actions

    SplicedColumnGroup(
        title = "应用",
        modifier = Modifier.padding(horizontal = 16.dp)
    ) {
        item {
            M3ESwitchWidget(
                text = "启动时检测更新",
                checked = state.checkUpdateOnStartup,
                onCheckedChange = actions.setCheckUpdateOnStartup
            )
        }
    }
}
