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
    private val versionCodeRegex = Regex("""<!--\s*versionCode:(\d+)\s*-->""")

    private const val GITHUB_API_URL =
        "https://api.github.com/repos/Itosang/BatteryRecorder/releases/latest"

    private fun parseVersionCode(body: String): Int {
        // versionCode 由 release notes 隐藏元数据提供。
        return versionCodeRegex.find(body)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: -1
    }

    private fun parseVersionName(tagName: String): String {
        // 新格式: v1.0.0-release -> 1.0.0-release
        return tagName.removePrefix("v")
    }

    suspend fun fetchUpdate(): AppUpdate? = withContext(Dispatchers.IO) {
        var connection: HttpURLConnection? = null
        try {
            val url = URL(GITHUB_API_URL)
            connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 10000
            connection.readTimeout = 10000
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
            val body = json.optString("body", "")
            val versionCode = parseVersionCode(body)
            val versionName = parseVersionName(tagName)
            val downloadUrl = findApkDownloadUrl(json.optJSONArray("assets"))

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
