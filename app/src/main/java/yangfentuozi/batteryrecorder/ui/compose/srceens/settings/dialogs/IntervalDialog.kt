package yangfentuozi.batteryrecorder.ui.compose.srceens.settings.dialogs

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
            Column(
                modifier = Modifier
                    .padding(
                        top = 4.dp,
                        start = 8.dp,
                        end = 8.dp
                    )
            ) {
                Row(
                    Modifier.fillMaxWidth(),
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
                Spacer(modifier = Modifier.height(8.dp))
                // 重置按钮
                TextButton(
                    onClick = onReset,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("重置为默认值 (0.9秒)")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave((value * 1000).toLong()) }) {
                Text("确定")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}
