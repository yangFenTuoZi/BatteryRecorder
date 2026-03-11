package yangfentuozi.batteryrecorder.ui.dialog.home

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri

private const val DOCS_URL = "https://itosang.github.io/BatteryRecorder"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocsIntroDialog(
    onOpenDocs: () -> Unit
) {
    val context = LocalContext.current

    BasicAlertDialog(onDismissRequest = {}) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "首次使用请先看文档",
                    style = MaterialTheme.typography.titleLarge
                )
                Text(
                    text = "请先阅读使用说明，再继续使用应用。\n文档包含启动方式、校准、排除高负载 App、续航预测、历史记录手势和图表交互说明。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                TextButton(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        context.startActivity(Intent(Intent.ACTION_VIEW, DOCS_URL.toUri()))
                        onOpenDocs()
                    }
                ) {
                    Text("查看使用文档")
                }
            }
        }
    }
}

@Preview
@Composable
fun LIGHT() {
    DocsIntroDialog { }
}