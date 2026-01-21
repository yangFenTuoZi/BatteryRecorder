package yangfentuozi.batteryrecorder.ui.compose.screens.settings.dialogs

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import yangfentuozi.batteryrecorder.ui.compose.theme.AppShape
import kotlin.math.roundToInt

@Composable
fun IntervalDialog(
    currentValueMs: Long,
    onDismiss: () -> Unit,
    onSave: (Long) -> Unit,
    onReset: () -> Unit
) {
    var value by remember {
        val initial = (currentValueMs / 1000f).coerceIn(0.1f, 60f)
        mutableFloatStateOf(initial)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("采样间隔") },
        text = {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(
                        top = 4.dp,
                        start = 8.dp,
                        end = 8.dp
                    ),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Slider(
                    value = value,
                    onValueChange = { value = it },
                    valueRange = 0.1f..60f,
                    modifier = Modifier.weight(1F)
                )
                Text(
                    modifier = Modifier
                        .width(60.dp)
                        .padding(start = 8.dp),
                    text = "${(value * 10).roundToInt() / 10.0} 秒",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave((value * 1000).toLong()) }) {
                Text("确定")
            }
        },
        dismissButton = {
            TextButton(onClick = onReset) {
                Text("重置")
            }
        },
        shape = AppShape.extraLarge
    )
}
