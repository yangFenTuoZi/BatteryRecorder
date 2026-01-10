package yangfentuozi.batteryrecorder.ui.compose.components

import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import androidx.core.net.toUri
import yangfentuozi.batteryrecorder.BuildConfig
import yangfentuozi.batteryrecorder.R
import kotlin.math.roundToInt

@Composable
fun AboutDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current

    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = true
        ),
        confirmButton = { },
        text = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                horizontalArrangement = Arrangement.Start,
                verticalAlignment = Alignment.Top
            ) {

                // 应用图标
                val drawable = context.applicationInfo.loadIcon(context.packageManager)
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .drawBehind {
                            drawIntoCanvas { canvas ->
                                drawable?.let {
                                    it.setBounds(0, 0, size.width.roundToInt(), size.height.roundToInt())
                                    it.draw(canvas.nativeCanvas)
                                }
                            }
                        }
                )

                Spacer(modifier = Modifier.width(16.dp))

                // 文本信息列
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    // 应用名称
                    Text(
                        text = stringResource(R.string.app_name),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )

                    // 版本信息
                    Text(
                        text = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // 源码链接
                    val sourceCodeText = buildAnnotatedString {
                        val template = stringResource(R.string.about_view_source_code)
                        val parts = template.split($$"%1$s")

                        append(parts.getOrNull(0) ?: "")

                        pushStringAnnotation(
                            tag = "URL",
                            annotation = "https://github.com/yangFenTuoZi/BatteryRecorder"
                        )
                        withStyle(
                            style = SpanStyle(
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold,
                                textDecoration = TextDecoration.Underline
                            )
                        ) {
                            append("GitHub")
                        }
                        pop()

                        append(parts.getOrNull(1) ?: "")
                    }

                    Text(
                        text = sourceCodeText,
                        style = MaterialTheme.typography.bodyMedium,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.clickable {
                            sourceCodeText.getStringAnnotations(
                                tag = "URL",
                                start = 0,
                                end = sourceCodeText.length
                            ).firstOrNull()?.let { annotation ->
                                val intent = Intent(Intent.ACTION_VIEW, annotation.item.toUri())
                                context.startActivity(intent)
                            }
                        }
                    )
                }
            }
        }
    )
}
