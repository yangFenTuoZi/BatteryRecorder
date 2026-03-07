package yangfentuozi.batteryrecorder.utils

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class AppUpdate(
    val versionName: String,
    val versionCode: Int,
    val body: String,
    val downloadUrl: String
)

object UpdateUtils {

    private const val TAG = "UpdateUtils"
    private const val RELEASE_BODY_PREFIX = "[release]\n\n"

    private const val GITHUB_API_URL =
        "https://api.github.com/repos/Itosang/BatteryRecorder/releases/latest"

    private fun parseVersionCode(tagName: String): Int {
        // 格式: 1.0-release-252 -> 252
        val separatorIndex = tagName.lastIndexOf('-')
        if (separatorIndex <= 0 || separatorIndex == tagName.lastIndex) return -1
        return tagName.substring(separatorIndex + 1).toIntOrNull() ?: -1
    }

    private fun parseVersionName(tagName: String): String {
        // 格式: 1.0-release-252 -> 1.0-release
        val separatorIndex = tagName.lastIndexOf('-')
        if (separatorIndex <= 0) return tagName
        return tagName.take(separatorIndex)
    }

    private fun normalizeBody(body: String): String {
        if (!body.startsWith(RELEASE_BODY_PREFIX)) return body
        return body.removePrefix(RELEASE_BODY_PREFIX)
    }

    suspend fun fetchUpdate(): AppUpdate? = withContext(Dispatchers.IO) {
        var connection: HttpURLConnection? = null
        try {
            val url = URL(GITHUB_API_URL)
            connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 5000
            connection.readTimeout = 5000
            connection.instanceFollowRedirects = true
            connection.setRequestProperty("Accept", "application/vnd.github+json")
            connection.setRequestProperty("User-Agent", "BatteryRecorder-App")

            val responseCode = connection.responseCode
            if (responseCode != HttpURLConnection.HTTP_OK) {
                Log.e(TAG, "GitHub 获取更新信息失败，响应码: $responseCode")
                return@withContext null
            }

            val responseBody = connection.inputStream.bufferedReader().use { it.readText() }
            if (responseBody.isEmpty()) {
                Log.e(TAG, "响应内容为空")
                return@withContext null
            }

            val json = JSONObject(responseBody)
            val tagName = json.optString("tag_name", "")
            val versionCode = parseVersionCode(tagName)
            val versionName = parseVersionName(tagName)
            val body = normalizeBody(json.optString("body", ""))
            val downloadUrl = findApkDownloadUrl(json.optJSONArray("assets"))

            if (downloadUrl.isBlank()) {
                Log.e(TAG, "更新资源缺少下载地址，tag: $tagName")
                return@withContext null
            }

            return@withContext if (versionCode > 0) {
                AppUpdate(versionName, versionCode, body, downloadUrl)
            } else {
                Log.e(TAG, "解析 versionCode 失败，tag: $tagName")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "GitHub 检查更新失败: ${e.message}", e)
            return@withContext null
        } finally {
            connection?.disconnect()
        }
    }

    private fun findApkDownloadUrl(assets: org.json.JSONArray?): String {
        if (assets == null || assets.length() == 0) return ""
        for (i in 0 until assets.length()) {
            val asset = assets.getJSONObject(i)
            if (asset.optString("name", "").endsWith(".apk")) {
                return asset.optString("browser_download_url", "")
            }
        }
        return assets.getJSONObject(0).optString("browser_download_url", "")
    }
}
