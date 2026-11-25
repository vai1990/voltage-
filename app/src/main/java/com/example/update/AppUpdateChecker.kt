package com.example.update

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.util.Log
import com.example.BuildConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

data class AppUpdateInfo(
    val tagName: String,
    val releaseTitle: String,
    val releaseNotes: String,
    val publishedAt: String,
    val downloadUrl: String,
    val releasePageUrl: String,
    val assetSizeBytes: Long,
    val updateAvailable: Boolean
)

sealed interface UpdateCheckState {
    data object Idle : UpdateCheckState
    data object Checking : UpdateCheckState
    data class UpdateAvailable(val info: AppUpdateInfo) : UpdateCheckState
    data class UpToDate(val currentVersion: String, val latestTag: String?) : UpdateCheckState
    data class Error(val message: String) : UpdateCheckState
}

class AppUpdateChecker(context: Context) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    val currentVersionName: String = BuildConfig.VERSION_NAME
    val currentVersionCode: Int = BuildConfig.VERSION_CODE

    fun isAutoCheckEnabled(): Boolean = prefs.getBoolean(KEY_AUTO_CHECK, true)

    fun setAutoCheckEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_AUTO_CHECK, enabled).apply()
    }

    fun getDismissedTag(): String? = prefs.getString(KEY_DISMISSED_TAG, null)

    fun dismissTag(tagName: String) {
        prefs.edit().putString(KEY_DISMISSED_TAG, tagName).apply()
    }

    fun clearDismissedTag() {
        prefs.edit().remove(KEY_DISMISSED_TAG).apply()
    }

    suspend fun checkLatestRelease(): Result<AppUpdateInfo> = withContext(Dispatchers.IO) {
        var connection: HttpURLConnection? = null
        try {
            val url = URL(GITHUB_LATEST_RELEASE_API)
            connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 8_000
                readTimeout = 8_000
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("User-Agent", "VoltLedger-Android/$currentVersionName")
            }

            val code = connection.responseCode
            if (code == HttpURLConnection.HTTP_NOT_FOUND) {
                return@withContext Result.failure(
                    IllegalStateException("No published GitHub release found yet for $GITHUB_REPO.")
                )
            }
            if (code !in 200..299) {
                return@withContext Result.failure(
                    IllegalStateException("GitHub release check returned HTTP $code.")
                )
            }

            val body = connection.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(body)

            val tagName = json.optString("tag_name", "").trim()
            val releaseTitle = json.optString("name", tagName).ifBlank { tagName }
            val releaseNotes = json.optString("body", "").trim()
            val publishedAt = json.optString("published_at", "")
            val htmlUrl = json.optString("html_url", GITHUB_RELEASES_PAGE)

            var downloadUrl = GITHUB_DIRECT_APK_URL
            var assetSizeBytes = 0L

            val assets = json.optJSONArray("assets")
            if (assets != null) {
                for (i in 0 until assets.length()) {
                    val asset = assets.optJSONObject(i) ?: continue
                    val assetName = asset.optString("name", "")
                    if (assetName.endsWith(".apk", ignoreCase = true)) {
                        val candidateUrl = asset.optString("browser_download_url", "")
                        if (candidateUrl.isNotBlank()) {
                            downloadUrl = candidateUrl
                        }
                        assetSizeBytes = asset.optLong("size", 0L)
                        if (assetName.equals("app-debug.apk", ignoreCase = true)) {
                            break
                        }
                    }
                }
            }

            val isNewer = evaluateIsNewerRelease(
                tagName = tagName,
                publishedAtIso = publishedAt
            )

            Result.success(
                AppUpdateInfo(
                    tagName = tagName.ifBlank { "latest" },
                    releaseTitle = releaseTitle.ifBlank { "Latest Release" },
                    releaseNotes = releaseNotes,
                    publishedAt = publishedAt,
                    downloadUrl = downloadUrl,
                    releasePageUrl = htmlUrl,
                    assetSizeBytes = assetSizeBytes,
                    updateAvailable = isNewer
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Update check failed: ${e.message}")
            Result.failure(e)
        } finally {
            connection?.disconnect()
        }
    }

    private fun evaluateIsNewerRelease(tagName: String, publishedAtIso: String): Boolean {
        if (tagName.isBlank()) return false

        // 1. Check semantic version tags like "v1.2.0" or "1.2.0"
        val semverMatch = Regex("^v?(\\d+)\\.(\\d+)(?:\\.(\\d+))?").find(tagName)
        if (semverMatch != null) {
            val remoteParts = listOf(
                semverMatch.groupValues[1].toIntOrNull() ?: 0,
                semverMatch.groupValues[2].toIntOrNull() ?: 0,
                semverMatch.groupValues[3].toIntOrNull() ?: 0
            )
            val localParts = currentVersionName.split(".").map { it.toIntOrNull() ?: 0 }
            for (i in 0 until 3) {
                val r = remoteParts.getOrElse(i) { 0 }
                val l = localParts.getOrElse(i) { 0 }
                if (r != l) return r > l
            }
            return false
        }

        // 2. Compare release publish timestamp against installed package update time
        // Allows automatic detection of GitHub Actions tags such as debug-apk-build-<run>-<attempt>
        val publishedMillis = parseIsoTimestampMillis(publishedAtIso)
        val installedMillis = getInstalledLastUpdateMillis()
        if (publishedMillis != null && installedMillis > 0L) {
            // Require release to be published at least 60 seconds after the installed APK timestamp
            if (publishedMillis > installedMillis + 60_000L) {
                return true
            }
        }

        // 3. Fallback: compare GitHub run number in debug-apk-build-<run>-<attempt> against versionCode
        val buildMatch = Regex("^debug-apk-build-(\\d+)-(\\d+)$").find(tagName)
        if (buildMatch != null) {
            val remoteRun = buildMatch.groupValues[1].toIntOrNull() ?: 0
            if (remoteRun > currentVersionCode) {
                return true
            }
        }

        return false
    }

    private fun getInstalledLastUpdateMillis(): Long {
        return try {
            val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                appContext.packageManager.getPackageInfo(
                    appContext.packageName,
                    PackageManager.PackageInfoFlags.of(0)
                )
            } else {
                @Suppress("DEPRECATION")
                appContext.packageManager.getPackageInfo(appContext.packageName, 0)
            }
            info.lastUpdateTime
        } catch (_: Exception) {
            0L
        }
    }

    private fun parseIsoTimestampMillis(iso: String): Long? {
        if (iso.isBlank()) return null
        return try {
            val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }
            sdf.parse(iso)?.time
        } catch (_: Exception) {
            null
        }
    }

    companion object {
        private const val TAG = "AppUpdateChecker"
        private const val PREFS_NAME = "voltledger_update_prefs"
        private const val KEY_AUTO_CHECK = "auto_check_enabled"
        private const val KEY_DISMISSED_TAG = "dismissed_tag"

        const val GITHUB_REPO = "vai1990/voltage-"
        const val GITHUB_LATEST_RELEASE_API =
            "https://api.github.com/repos/$GITHUB_REPO/releases/latest"
        const val GITHUB_RELEASES_PAGE =
            "https://github.com/$GITHUB_REPO/releases/latest"
        const val GITHUB_DIRECT_APK_URL =
            "https://github.com/$GITHUB_REPO/releases/latest/download/app-debug.apk"

        /**
         * Opens the GitHub Release APK download link in the user's browser via ACTION_VIEW
         * (complies with Google Play policy by avoiding REQUEST_INSTALL_PACKAGES / in-app DCL).
         */
        fun openExternalUrl(context: Context, url: String): Boolean {
            return runCatching {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                true
            }.getOrDefault(false)
        }

        fun formatFileSize(bytes: Long): String {
            if (bytes <= 0L) return "APK"
            val mb = bytes / (1024.0 * 1024.0)
            return String.format(Locale.US, "%.2f MB", mb)
        }
    }
}
