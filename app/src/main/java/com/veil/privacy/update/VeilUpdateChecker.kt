package com.veil.privacy.update

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.veil.privacy.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Checks the official VEIL GitHub Releases endpoint and offers a direct APK download.
 * Android still shows its normal installer confirmation; VEIL never silently installs APKs.
 */
object VeilUpdateChecker {
    private const val RELEASE_API =
        "https://api.github.com/repos/puttishiva147/Veil-Privacy-Focused-/releases/latest"
    private const val PREFS = "veil_update_preferences"
    private const val KEY_LAST_ATTEMPT = "last_check_attempt"
    private const val CHECK_INTERVAL_MS = 24L * 60L * 60L * 1000L

    fun check(activity: AppCompatActivity) {
        if (activity.isFinishing || activity.isDestroyed) return
        val prefs = activity.getSharedPreferences(PREFS, AppCompatActivity.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        if (now - prefs.getLong(KEY_LAST_ATTEMPT, 0L) < CHECK_INTERVAL_MS) return

        // Save before launching the request so repeated onResume calls don't spam GitHub,
        // including when no public release exists yet or the device is offline.
        prefs.edit().putLong(KEY_LAST_ATTEMPT, now).apply()

        activity.lifecycleScope.launch {
            val release = withContext(Dispatchers.IO) { fetchLatestRelease() } ?: return@launch

            val currentVersion = BuildConfig.VERSION_NAME
            if (!isNewerVersion(release.version, currentVersion)) return@launch
            if (activity.isFinishing || activity.isDestroyed) return@launch

            val notes = release.notes
                .replace(Regex("(?m)^#+\\s*"), "")
                .replace(Regex("[*_\u0060>]"), "")
                .trim()
                .take(700)
                .ifBlank { "Bug fixes and improvements are available." }

            AlertDialog.Builder(activity)
                .setTitle("VEIL Update Available")
                .setMessage(
                    "Version ${release.version} is available. You have version ${currentVersion}.\n\n" +
                        notes + "\n\nDownload the official APK and follow Android's installer prompts."
                )
                .setPositiveButton("Download Update") { _, _ ->
                    try {
                        activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(release.apkUrl)))
                    } catch (_: ActivityNotFoundException) {
                        // No browser/handler is available; the user can still visit the Releases page manually.
                    }
                }
                .setNegativeButton("Later", null)
                .show()
        }
    }

    private data class Release(val version: String, val apkUrl: String, val notes: String)

    private fun fetchLatestRelease(): Release? {
        var connection: HttpURLConnection? = null
        return try {
            connection = (URL(RELEASE_API).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 7_000
                readTimeout = 7_000
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("User-Agent", "VEIL-Android-UpdateChecker")
            }
            if (connection.responseCode !in 200..299) return null
            val json = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            val releaseJson = JSONObject(json)
            val tag = releaseJson.optString("tag_name").removePrefix("v").trim()
            if (tag.isBlank()) return null

            val assets = releaseJson.optJSONArray("assets") ?: return null
            var apkUrl = ""
            var fallbackUrl = ""
            for (index in 0 until assets.length()) {
                val asset = assets.optJSONObject(index) ?: continue
                val name = asset.optString("name")
                val url = asset.optString("browser_download_url")
                if (!name.endsWith(".apk", ignoreCase = true) || !url.startsWith("https://")) continue
                if (name.equals("VEIL-$tag.apk", ignoreCase = true)) {
                    apkUrl = url
                    break
                }
                if (name.equals("VEIL.apk", ignoreCase = true)) fallbackUrl = url
            }
            if (apkUrl.isBlank()) apkUrl = fallbackUrl
            if (apkUrl.isBlank()) return null

            Release(tag, apkUrl, releaseJson.optString("body"))
        } catch (_: Exception) {
            null
        } finally {
            connection?.disconnect()
        }
    }

    private fun isNewerVersion(remote: String, local: String): Boolean {
        val remoteParts = remote.split('.', '-', '+').map { it.toIntOrNull() ?: 0 }
        val localParts = local.split('.', '-', '+').map { it.toIntOrNull() ?: 0 }
        val length = maxOf(remoteParts.size, localParts.size)
        for (index in 0 until length) {
            val remotePart = remoteParts.getOrElse(index) { 0 }
            val localPart = localParts.getOrElse(index) { 0 }
            if (remotePart != localPart) return remotePart > localPart
        }
        return false
    }
}
