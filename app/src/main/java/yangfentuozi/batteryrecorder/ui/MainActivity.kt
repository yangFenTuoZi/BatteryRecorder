package yangfentuozi.batteryrecorder.ui

import android.os.Bundle
import androidx.activity.compose.setContent
import yangfentuozi.batteryrecorder.ui.compose.components.BatteryRecorderApp
import yangfentuozi.batteryrecorder.ui.compose.theme.BatteryRecorderTheme

class MainActivity : BaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            BatteryRecorderTheme {
                BatteryRecorderApp()
            }
        }
    }
}