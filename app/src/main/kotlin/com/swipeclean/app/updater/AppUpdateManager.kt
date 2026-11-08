package com.swipeclean.app.updater

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import com.swipeclean.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

data class UpdateInfo(
    val versionCode: Int,
    val versionName: String,
    val apkUrl: String,
    val changelog: String,
    val isUpdateAvailable: Boolean
)

sealed class DownloadState {
    object Idle : DownloadState()
    data class Downloading(val progress: Float, val downloadedMb: Float, val totalMb: Float) : DownloadState()
    data class ReadyToInstall(val apkFile: File) : DownloadState()
    data class Error(val message: String) : DownloadState()
}

object AppUpdateManager {

    private const val GITHUB_REPO = "hasan-dhanish/SwipeClean"
    private const val RAW_VERSION_URL = "https://raw.githubusercontent.com/$GITHUB_REPO/main/version.json"
    private const val GITHUB_RELEASE_API_URL = "https://api.github.com/repos/$GITHUB_REPO/releases/latest"

    /**
     * Checks if a new update is available on GitHub.
     */
    suspend fun checkForUpdate(): UpdateInfo? = withContext(Dispatchers.IO) {
        try {
            // Check GitHub Releases API first (instant, no CDN caching)
            val releaseApiUpdate = fetchGitHubReleaseApi()
            if (releaseApiUpdate != null && releaseApiUpdate.isUpdateAvailable) {
                return@withContext releaseApiUpdate
            }

            // Check version.json from repository
            val versionJsonUpdate = fetchVersionJson()
            if (versionJsonUpdate != null && versionJsonUpdate.isUpdateAvailable) {
                return@withContext versionJsonUpdate
            }

            // If neither has an update, return the latest info found
            return@withContext releaseApiUpdate ?: versionJsonUpdate
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun fetchVersionJson(): UpdateInfo? {
        return try {
            val conn = openConnectionWithRedirects(RAW_VERSION_URL)
            if (conn.responseCode == HttpURLConnection.HTTP_OK) {
                val jsonStr = conn.inputStream.bufferedReader().use { it.readText() }
                conn.disconnect()
                val json = JSONObject(jsonStr)
                val remoteCode = json.optInt("versionCode", 0)
                val remoteName = json.optString("versionName", "1.0")
                val apkUrl = json.optString("apkUrl", "")
                val changelog = json.optString("changelog", "Performance improvements and bug fixes.")

                val isNewer = remoteCode > BuildConfig.VERSION_CODE || isVersionHigher(remoteName, BuildConfig.VERSION_NAME)
                if (apkUrl.isNotBlank()) {
                    UpdateInfo(
                        versionCode = remoteCode,
                        versionName = remoteName,
                        apkUrl = apkUrl,
                        changelog = changelog,
                        isUpdateAvailable = isNewer
                    )
                } else null
            } else {
                conn.disconnect()
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun fetchGitHubReleaseApi(): UpdateInfo? {
        return try {
            val conn = openConnectionWithRedirects(GITHUB_RELEASE_API_URL)
            if (conn.responseCode == HttpURLConnection.HTTP_OK) {
                val jsonStr = conn.inputStream.bufferedReader().use { it.readText() }
                conn.disconnect()
                val json = JSONObject(jsonStr)

                val tagName = json.optString("tag_name", "").removePrefix("v").trim()
                val body = json.optString("body", "New update available.")
                val assets = json.optJSONArray("assets")

                var apkDownloadUrl = ""
                if (assets != null) {
                    for (i in 0 until assets.length()) {
                        val asset = assets.getJSONObject(i)
                        val name = asset.optString("name", "")
                        if (name.endsWith(".apk", ignoreCase = true)) {
                            apkDownloadUrl = asset.optString("browser_download_url", "")
                            break
                        }
                    }
                }

                val isNewer = isVersionHigher(tagName, BuildConfig.VERSION_NAME)
                if (apkDownloadUrl.isNotBlank()) {
                    UpdateInfo(
                        versionCode = BuildConfig.VERSION_CODE + 1,
                        versionName = tagName,
                        apkUrl = apkDownloadUrl,
                        changelog = body,
                        isUpdateAvailable = isNewer
                    )
                } else null
            } else {
                conn.disconnect()
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun isVersionHigher(remote: String, local: String): Boolean {
        try {
            val remoteParts = remote.split(".").map { it.filter { char -> char.isDigit() }.toIntOrNull() ?: 0 }
            val localParts = local.split(".").map { it.filter { char -> char.isDigit() }.toIntOrNull() ?: 0 }
            val maxLen = maxOf(remoteParts.size, localParts.size)
            for (i in 0 until maxLen) {
                val r = remoteParts.getOrElse(i) { 0 }
                val l = localParts.getOrElse(i) { 0 }
                if (r > l) return true
                if (r < l) return false
            }
        } catch (_: Exception) {}
        return false
    }

    /**
     * Downloads the APK file with reactive progress reporting
     */
    fun downloadApk(context: Context, apkUrl: String): Flow<DownloadState> = flow {
        emit(DownloadState.Downloading(0f, 0f, 0f))
        try {
            val conn = openConnectionWithRedirects(apkUrl)
            if (conn.responseCode != HttpURLConnection.HTTP_OK) {
                emit(DownloadState.Error("Server returned code ${conn.responseCode}"))
                conn.disconnect()
                return@flow
            }

            val totalBytes = conn.contentLengthLong
            val totalMb = if (totalBytes > 0) totalBytes / (1024f * 1024f) else 0f

            val outputFile = File(context.cacheDir, "SwipeClean-update.apk")
            if (outputFile.exists()) {
                outputFile.delete()
            }

            conn.inputStream.use { input ->
                FileOutputStream(outputFile).use { output ->
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    var totalDownloaded = 0L

                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        totalDownloaded += bytesRead

                        val progress = if (totalBytes > 0) totalDownloaded.toFloat() / totalBytes else 0f
                        val downloadedMb = totalDownloaded / (1024f * 1024f)

                        emit(DownloadState.Downloading(progress, downloadedMb, totalMb))
                    }
                    output.flush()
                }
            }
            conn.disconnect()

            emit(DownloadState.ReadyToInstall(outputFile))
        } catch (e: Exception) {
            emit(DownloadState.Error(e.localizedMessage ?: "Download failed"))
        }
    }.flowOn(Dispatchers.IO)

    /**
     * Check if app has permission to trigger unknown package installs
     */
    fun canInstallApk(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.packageManager.canRequestPackageInstalls()
        } else {
            true
        }
    }

    /**
     * Opens the system settings toggle to allow installation from SwipeClean
     */
    fun openInstallPermissionSettings(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                data = Uri.parse("package:${context.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }
    }

    /**
     * Triggers the native Android package installer
     */
    fun launchApkInstall(context: Context, apkFile: File) {
        val apkUri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            apkFile
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(apkUri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(intent)
    }

    private fun openConnectionWithRedirects(urlStr: String, maxRedirects: Int = 6): HttpURLConnection {
        var curUrl = urlStr
        var redirects = 0
        while (redirects < maxRedirects) {
            val conn = URL(curUrl).openConnection() as HttpURLConnection
            conn.instanceFollowRedirects = true
            conn.connectTimeout = 15000
            conn.readTimeout = 30000
            conn.setRequestProperty("User-Agent", "SwipeClean-Android-App")
            val responseCode = conn.responseCode
            if (responseCode == HttpURLConnection.HTTP_MOVED_PERM ||
                responseCode == HttpURLConnection.HTTP_MOVED_TEMP ||
                responseCode == 307 || responseCode == 308) {
                val location = conn.getHeaderField("Location")
                if (location != null) {
                    curUrl = location
                    redirects++
                    conn.disconnect()
                    continue
                }
            }
            return conn
        }
        throw IllegalStateException("Too many redirects: $urlStr")
    }
}

