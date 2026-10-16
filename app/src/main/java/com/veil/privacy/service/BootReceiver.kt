package com.veil.privacy.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.veil.privacy.storage.VeilPrefs
import com.veil.privacy.util.NotificationUtils

/**
 * Boot and app update receiver to automatically start the VEIL Real-Time Protection Shield
 * on device boot or after package updates.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action == Intent.ACTION_BOOT_COMPLETED || action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            val prefs = VeilPrefs.getInstance(context)
            if (prefs.isVeilEnabled && prefs.isPersistentShieldEnabled) {
                VeilProtectionService.startService(context)
            }
            if (NotificationUtils.isNotificationAccessGranted(context)) {
                NotificationUtils.ensureListenerBound(context)
            }
        }
    }
}
