package yangfentuozi.batteryrecorder.utils

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import yangfentuozi.batteryrecorder.shared.util.LoggerX

data class AppUpdate(
    val versionName: String,
    val versionCode: Int,
    val body: String,
    val downloadUrl: String
)

object UpdateUtils {
    private val versionCodeRegex = Regex("""<!--\s*versionCode:(\d+)\s*-->""")
    private val commitPrefixRegex =
        Regex("""(?m)^-\s+\[`[0-9a-f]{7,40}`]\(https://github\.com/Itosang/BatteryRecorder/commit/[0-9a-f]{7,40}\)\s+""")

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

    private fun normalizeBody(body: String): String {
        return body
            .let { versionCodeRegex.replace(it, "") }
            .let { commitPrefixRegex.replace(it, "- ") }
            .trim()
    }

    suspend fun fetchUpdate(): AppUpdate? = withContext(Dispatchers.IO) {
        var connection: HttpURLConnection? = null
        try {
            LoggerX.i<UpdateUtils>("准备请求 GitHub 最新 release")
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
                LoggerX.e<UpdateUtils>("GitHub 获取更新信息失败，响应码=$responseCode")
                return@withContext null
            }

            val responseBody = connection.inputStream.bufferedReader().use { it.readText() }
            if (responseBody.isEmpty()) {
                LoggerX.e<UpdateUtils>("GitHub 最新 release 响应内容为空")
                return@withContext null
            }

            val json = JSONObject(responseBody)
            val tagName = json.optString("tag_name", "")
            val rawBody = json.optString("body", "")
            val versionCode = parseVersionCode(rawBody)
            val body = normalizeBody(rawBody)
            val versionName = parseVersionName(tagName)
            val downloadUrl = findApkDownloadUrl(json.optJSONArray("assets"))

            if (downloadUrl.isBlank()) {
                LoggerX.e<UpdateUtils>("更新资源缺少下载地址，tag=$tagName")
                return@withContext null
            }

            return@withContext if (versionCode > 0) {
                LoggerX.i<UpdateUtils>("GitHub 最新 release 解析成功，tag=$tagName versionCode=$versionCode")
                AppUpdate(versionName, versionCode, body, downloadUrl)
            } else {
                LoggerX.e<UpdateUtils>("解析 versionCode 失败，tag=$tagName")
                null
            }
        } catch (e: Exception) {
            LoggerX.e<UpdateUtils>("GitHub 检查更新失败", tr = e)
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
