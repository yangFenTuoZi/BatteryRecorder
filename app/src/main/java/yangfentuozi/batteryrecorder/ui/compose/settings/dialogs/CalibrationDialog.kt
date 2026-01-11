package yangfentuozi.batteryrecorder.ui.compose.settings.dialogs

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun CalibrationDialog(
    currentValue: Int,
    onDismiss: () -> Unit,
    onSave: (Int) -> Unit,
    onReset: () -> Unit
) {
    var value by remember(currentValue) { mutableIntStateOf(currentValue) }
    val maxValue = 100000000

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("电流单位校准") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        top = 4.dp,
                        start = 8.dp,
                        end = 8.dp
                    )
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 减小按钮
                    IconButton(
                        onClick = {
                            if (value < 0) value *= 10
                            else value /= 10
                            if (value == 0) value = -1
                            if (value < -maxValue) value = -maxValue
                        },
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(Icons.Default.Remove, contentDescription = null)
                    }

                    Spacer(modifier = Modifier.width(24.dp))

                    Box(
                        modifier = Modifier.weight(1f),
                        contentAlignment = Alignment.Center
                    ) {

                        // 显示当前值
                        Text(
                            text = value.toString(),
                            style = androidx.compose.material3.MaterialTheme.typography.bodyLarge,
                        )
                    }

                    Spacer(modifier = Modifier.width(24.dp))

                    // 增大按钮
                    IconButton(
                        onClick = {
                            if (value > 0) value *= 10
                            else value /= 10
                            if (value == 0) value = 1
                            if (value > maxValue) value = maxValue
                        },
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null)
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // 重置按钮
                TextButton(
                    onClick = onReset,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("重置为默认值 (-1)")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(value) }) {
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
