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
 * Checks the official VEIL GitHub Releases endpoint for newer versions.
 * The popup sends users to the official VEIL website for download instructions.
 * Android still requires its normal installer confirmation; VEIL never silently installs APKs.
 */
object VeilUpdateChecker {
    private const val RELEASE_API =
        "https://api.github.com/repos/puttishiva147/Veil-Privacy-Focused-/releases/latest"
    private const val OFFICIAL_WEBSITE =
        "https://puttishiva147.github.io/Veil-Privacy-Focused-/"
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
                        notes + "\n\nVisit the official VEIL website to get the update."
                )
                .setPositiveButton("Open Website") { _, _ ->
                    try {
                        activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(OFFICIAL_WEBSITE)))
                    } catch (_: ActivityNotFoundException) {
                        // Keep the app stable if no browser is available.
                    }
                }
                .setNegativeButton("Later", null)
                .show()
        }
    }

    private data class Release(val version: String, val notes: String)

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
            Release(tag, releaseJson.optString("body"))
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
