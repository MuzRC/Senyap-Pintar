package com.muzrc.data

import com.muzrc.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class AppReleaseInfo(
    val latestVersion: String,
    val versionCode: Int,
    val releaseNotes: String,
    val downloadUrl: String,
    val isUpdateAvailable: Boolean
)

object AppUpdateChecker {
    const val DEFAULT_VERSION_JSON_URL =
        "https://raw.githubusercontent.com/muzrc/Senyap-Pintar/main/version.json"

    suspend fun checkForUpdate(
        versionJsonUrl: String = DEFAULT_VERSION_JSON_URL
    ): Result<AppReleaseInfo> = withContext(Dispatchers.IO) {
        var connection: HttpURLConnection? = null
        try {
            val url = URL(versionJsonUrl)
            connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 10_000
                readTimeout = 10_000
                setRequestProperty("Accept", "application/json")
                setRequestProperty("Cache-Control", "no-cache")
            }

            val responseCode = connection.responseCode
            if (responseCode !in 200..299) {
                return@withContext Result.failure(
                    IllegalStateException("Pelayan memulangkan ralat HTTP $responseCode")
                )
            }

            val body = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            val json = JSONObject(body)

            val latestVersion = json.optString("latestVersion").trim()
            val remoteVersionCode = json.optInt("versionCode", 0)
            val releaseNotes = json.optString("releaseNotes")
                .trim()
                .ifEmpty { "Kemaskini baharu tersedia untuk SenyapPintar." }
            val downloadUrl = json.optString("downloadUrl").trim()

            if (latestVersion.isEmpty() || downloadUrl.isEmpty()) {
                return@withContext Result.failure(
                    IllegalStateException("Format version.json tidak lengkap.")
                )
            }

            val currentVersionCode = BuildConfig.VERSION_CODE
            val currentVersionName = BuildConfig.VERSION_NAME
            val hasNewerCode = remoteVersionCode > currentVersionCode
            val hasNewerSemver = compareVersions(latestVersion, currentVersionName) > 0

            Result.success(
                AppReleaseInfo(
                    latestVersion = latestVersion,
                    versionCode = remoteVersionCode,
                    releaseNotes = releaseNotes,
                    downloadUrl = downloadUrl,
                    isUpdateAvailable = hasNewerCode || hasNewerSemver
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            connection?.disconnect()
        }
    }

    private fun compareVersions(remote: String, local: String): Int {
        val remoteParts = remote.removePrefix("v").split(".").map { it.toIntOrNull() ?: 0 }
        val localParts = local.removePrefix("v").split(".").map { it.toIntOrNull() ?: 0 }
        val maxLen = maxOf(remoteParts.size, localParts.size)
        for (i in 0 until maxLen) {
            val r = remoteParts.getOrElse(i) { 0 }
            val l = localParts.getOrElse(i) { 0 }
            if (r != l) return r.compareTo(l)
        }
        return 0
    }
}
