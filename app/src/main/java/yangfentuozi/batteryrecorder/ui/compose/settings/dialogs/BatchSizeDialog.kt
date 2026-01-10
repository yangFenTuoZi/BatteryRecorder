package yangfentuozi.batteryrecorder.ui.compose.settings.dialogs

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun BatchSizeDialog(
    currentValue: Int,
    onDismiss: () -> Unit,
    onSave: (Int) -> Unit,
    onReset: () -> Unit
) {
    var value by remember { mutableStateOf(currentValue.toString()) }
    var isError by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("批量大小") },
        text = {
            Column(modifier = Modifier.padding(16.dp)) {
                OutlinedTextField(
                    value = value,
                    onValueChange = { newValue: String ->
                        value = newValue
                        isError = newValue.toIntOrNull() == null || newValue.toInt() < 0 || newValue.toInt() > 1000
                    },
                    label = { Text("批量大小") },
                    isError = isError,
                    supportingText = if (isError) {
                        { Text("请输入 0-1000 之间的整数") }
                    } else null,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(16.dp))
                TextButton(
                    onClick = onReset,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("重置为默认值 (20)")
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    value.toIntOrNull()?.let { intValue ->
                        if (intValue >= 0 && intValue <= 1000) {
                            onSave(intValue)
                        }
                    }
                },
                enabled = !isError
            ) {
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
