package yangfentuozi.batteryrecorder.ui

import android.os.Bundle
import androidx.activity.compose.setContent
import yangfentuozi.batteryrecorder.ui.components.BatteryRecorderApp
import yangfentuozi.batteryrecorder.ui.theme.BatteryRecorderTheme

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
