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
 * Checks the official website's update manifest. Users are sent to the website,
 * where the current APK download is hosted; this does not publish GitHub Releases.
 */
object VeilUpdateChecker {
    private const val UPDATE_MANIFEST =
        "https://puttishiva147.github.io/Veil-Privacy-Focused-/version.json"
    private const val OFFICIAL_WEBSITE =
        "https://puttishiva147.github.io/Veil-Privacy-Focused-/"
    private const val PREFS = "veil_update_preferences"
    private const val KEY_LAST_ATTEMPT = "last_check_attempt"
    private const val KEY_LAST_SUCCESS = "last_check_success"
    private const val CHECK_INTERVAL_MS = 24L * 60L * 60L * 1000L
    private const val RETRY_INTERVAL_MS = 15L * 60L * 1000L

    fun check(activity: AppCompatActivity) {
        if (activity.isFinishing || activity.isDestroyed) return
        val prefs = activity.getSharedPreferences(PREFS, AppCompatActivity.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        if (now - prefs.getLong(KEY_LAST_SUCCESS, 0L) < CHECK_INTERVAL_MS) return
        if (now - prefs.getLong(KEY_LAST_ATTEMPT, 0L) < RETRY_INTERVAL_MS) return
        prefs.edit().putLong(KEY_LAST_ATTEMPT, now).apply()

        activity.lifecycleScope.launch {
            val update = withContext(Dispatchers.IO) { fetchWebsiteUpdate() } ?: return@launch
            prefs.edit().putLong(KEY_LAST_SUCCESS, System.currentTimeMillis()).apply()
            val currentVersion = BuildConfig.VERSION_NAME
            if (!isNewerVersion(update.version, currentVersion)) return@launch
            if (activity.isFinishing || activity.isDestroyed) return@launch

            val notes = update.notes
                .replace(Regex("(?m)^#+\\s*"), "")
                .replace(Regex("[*_\\u0060>]"), "")
                .trim()
                .take(700)
                .ifBlank { "Bug fixes and improvements are available." }

            AlertDialog.Builder(activity)
                .setTitle("VEIL Update Available")
                .setMessage(
                    "Version ${update.version} is available. You have version ${currentVersion}.\n\n" +
                        notes + "\n\nOpen the official VEIL website to download the update."
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

    private data class WebsiteUpdate(val version: String, val notes: String)

    private fun fetchWebsiteUpdate(): WebsiteUpdate? {
        var connection: HttpURLConnection? = null
        return try {
            connection = (URL(UPDATE_MANIFEST).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 7_000
                readTimeout = 7_000
                setRequestProperty("Accept", "application/json")
                setRequestProperty("Cache-Control", "no-cache")
                setRequestProperty("User-Agent", "VEIL-Android-UpdateChecker")
            }
            if (connection.responseCode !in 200..299) return null
            val json = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            val updateJson = JSONObject(json)
            val version = updateJson.optString("version").trim()
            if (version.isBlank()) return null
            WebsiteUpdate(version, updateJson.optString("notes"))
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
