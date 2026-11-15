package com.example.data.remote

import com.example.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class AppUpdateInfo(
    val latestTag: String,
    val downloadUrl: String,
    val releaseNotes: String
)

object AppUpdateChecker {
    private const val LATEST_RELEASE_API =
        "https://api.github.com/repos/SONUSHARMABJLDRBKH/babu/releases/latest"

    suspend fun fetchLatestApkDownloadUrl(): String? = withContext(Dispatchers.IO) {
        runCatching {
            val conn = (URL(LATEST_RELEASE_API).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 5000
                readTimeout = 5000
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("User-Agent", "RoyalBullet350-Android/${BuildConfig.VERSION_NAME}")
            }
            try {
                if (conn.responseCode !in 200..299) return@runCatching null
                val body = conn.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(body)
                val htmlUrl = json.optString("html_url", "").trim()
                var apkDownloadUrl = ""
                val assets = json.optJSONArray("assets")
                if (assets != null) {
                    for (i in 0 until assets.length()) {
                        val asset = assets.optJSONObject(i) ?: continue
                        val name = asset.optString("name", "")
                        val url = asset.optString("browser_download_url", "")
                        if (name.endsWith(".apk", ignoreCase = true) && url.isNotBlank()) {
                            apkDownloadUrl = url
                            break
                        }
                    }
                }
                apkDownloadUrl.ifBlank { null }
            } finally {
                conn.disconnect()
            }
        }.getOrNull()
    }

    suspend fun checkForNewerRelease(fallbackUrl: String): AppUpdateInfo? = withContext(Dispatchers.IO) {
        runCatching {
            val conn = (URL(LATEST_RELEASE_API).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 5000
                readTimeout = 5000
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("User-Agent", "RoyalBullet350-Android/${BuildConfig.VERSION_NAME}")
            }
            try {
                if (conn.responseCode !in 200..299) return@runCatching null
                val body = conn.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(body)
                val tag = json.optString("tag_name", "").trim()
                val htmlUrl = json.optString("html_url", "").trim()
                val releaseBody = json.optString("body", "").trim()

                var apkDownloadUrl = ""
                val assets = json.optJSONArray("assets")
                if (assets != null) {
                    for (i in 0 until assets.length()) {
                        val asset = assets.optJSONObject(i) ?: continue
                        val name = asset.optString("name", "")
                        val url = asset.optString("browser_download_url", "")
                        if (name.endsWith(".apk", ignoreCase = true) && url.isNotBlank()) {
                            apkDownloadUrl = url
                            break
                        }
                    }
                }

                val targetUrl = apkDownloadUrl.ifBlank { htmlUrl.ifBlank { fallbackUrl } }
                if (tag.isNotBlank() && isRemoteNewer(tag, targetUrl, BuildConfig.VERSION_NAME)) {
                    AppUpdateInfo(
                        latestTag = tag,
                        downloadUrl = targetUrl,
                        releaseNotes = releaseBody
                    )
                } else {
                    null
                }
            } finally {
                conn.disconnect()
            }
        }.getOrNull()
    }

    private const val BASELINE_SHARE_URL =
        "https://github.com/SONUSHARMABJLDRBKH/babu/releases/download/debug-apk-build-1-1/babu-debug-build-1.apk"

    internal fun isRemoteNewer(
        remoteTag: String,
        remoteDownloadUrl: String,
        currentVersionName: String
    ): Boolean {
        val cleanRemote = remoteTag.lowercase().removePrefix("v").trim()
        val remoteNumbers = Regex("\\d+").findAll(cleanRemote).mapNotNull { it.value.toIntOrNull() }.toList()
        if (remoteNumbers.isEmpty()) return false

        // GitHub Actions tag pattern: debug-apk-build-<runNumber>-<attempt>
        // First build on babu is build 1 attempt 1; any run > 1 or attempt > 1 triggers an update.
        if (cleanRemote.startsWith("debug-apk-build")) {
            val runNum = remoteNumbers.getOrElse(0) { 0 }
            val attemptNum = remoteNumbers.getOrElse(1) { 1 }
            if (runNum > 1) return true
            if (runNum == 1 && attemptNum > 1) return true
            return false
        }

        val currentNumbers = Regex("\\d+").findAll(currentVersionName).mapNotNull { it.value.toIntOrNull() }.toList()
        val maxLen = maxOf(remoteNumbers.size, currentNumbers.size)
        for (i in 0 until maxLen) {
            val r = remoteNumbers.getOrElse(i) { 0 }
            val c = currentNumbers.getOrElse(i) { 0 }
            if (r > c) return true
            if (r < c) return false
        }
        return false
    }
}
