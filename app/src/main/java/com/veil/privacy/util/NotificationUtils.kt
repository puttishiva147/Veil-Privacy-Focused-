package com.veil.privacy.util

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.service.notification.NotificationListenerService
import androidx.core.app.NotificationManagerCompat
import com.veil.privacy.notification.VeilNotificationListener

object NotificationUtils {

    /**
     * Checks if the user has granted Notification Listener access to VEIL.
     */
    fun isNotificationAccessGranted(context: Context): Boolean {
        val packageName = context.packageName
        val flat = Settings.Secure.getString(
            context.contentResolver,
            "enabled_notification_listeners"
        )
        if (!flat.isNullOrEmpty()) {
            val names = flat.split(":")
            for (name in names) {
                val cn = ComponentName.unflattenFromString(name)
                if (cn != null && cn.packageName == packageName) {
                    return true
                }
            }
        }
        return NotificationManagerCompat.getEnabledListenerPackages(context).contains(packageName)
    }

    /**
     * Launches the Android Notification Listener Settings page.
     */
    fun openNotificationListenerSettings(context: Context) {
        val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    }

    /**
     * Checks whether the VeilNotificationListener is currently connected in memory.
     */
    fun isListenerConnected(): Boolean {
        return VeilNotificationListener.isConnected
    }

    /**
     * Ensures the NotificationListenerService is bound and active.
     * Uses Android API 24+ requestRebind and toggles component state if needed.
     */
    fun ensureListenerBound(context: Context) {
        if (!isNotificationAccessGranted(context)) return

        val cn = ComponentName(context, VeilNotificationListener::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            try {
                NotificationListenerService.requestRebind(cn)
            } catch (_: Exception) {}
        }

        // If still disconnected in memory, toggle component enabled state to nudge Android NotificationManager
        if (!VeilNotificationListener.isConnected) {
            try {
                val pm = context.packageManager
                pm.setComponentEnabledSetting(
                    cn,
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                    PackageManager.DONT_KILL_APP
                )
                pm.setComponentEnabledSetting(
                    cn,
                    PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                    PackageManager.DONT_KILL_APP
                )
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    NotificationListenerService.requestRebind(cn)
                }
            } catch (_: Exception) {}
        }
    }
}
