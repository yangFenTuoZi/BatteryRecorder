package yangfentuozi.batteryrecorder.utils

import android.content.Context
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import yangfentuozi.batteryrecorder.shared.util.LoggerX

object AppIconMemoryCache {
    val chartIconSizeDp = 9.dp

    private data class CacheKey(
        val packageName: String,
        val iconSizePx: Int
    )

    private val lock = Any()
    private val bitmapCache = LinkedHashMap<CacheKey, ImageBitmap>()
    private val failedKeys = LinkedHashSet<CacheKey>()

    // 图标缓存只负责“按包名+尺寸”复用已加载结果，不承担历史扫描或启动期预热职责。
    fun get(packageName: String, iconSizePx: Int): ImageBitmap? = synchronized(lock) {
        bitmapCache[CacheKey(packageName, iconSizePx)]
    }

    fun shouldLoad(packageName: String, iconSizePx: Int): Boolean = synchronized(lock) {
        val key = CacheKey(packageName, iconSizePx)
        key !in bitmapCache && key !in failedKeys
    }

    suspend fun loadAndCache(
        context: Context,
        packageName: String,
        iconSizePx: Int
    ): ImageBitmap? {
        val key = CacheKey(packageName, iconSizePx)
        synchronized(lock) {
            bitmapCache[key]?.let { return it }
            if (key in failedKeys) return null
        }

        // 图标解码走 IO 线程；失败包名会进入 failedKeys，避免同一会话反复触发 PackageManager 查询。
        val bitmap = withContext(Dispatchers.IO) {
            runCatching {
                val packageManager = context.packageManager
                val appInfo = packageManager.getApplicationInfo(packageName, 0)
                appInfo.loadIcon(packageManager)
                    .toBitmap(iconSizePx, iconSizePx)
                    .asImageBitmap()
            }.onFailure { error ->
                LoggerX.w<AppIconMemoryCache>(
                    "[APP_ICON] 加载失败 package=$packageName",
                    error
                )
            }.getOrNull()
        }

        synchronized(lock) {
            if (bitmap != null) {
                bitmapCache[key] = bitmap
            } else {
                failedKeys += key
            }
        }
        return bitmap
    }
}
