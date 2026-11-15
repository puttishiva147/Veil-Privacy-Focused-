package com.veil.privacy.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.veil.privacy.activation.ActivationManager
import com.veil.privacy.storage.VeilPrefs
import com.veil.privacy.util.NotificationUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Restart protection after boot/update only when the server confirms an active license. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action != Intent.ACTION_BOOT_COMPLETED && action != Intent.ACTION_MY_PACKAGE_REPLACED) return

        val appContext = context.applicationContext
        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val prefs = VeilPrefs.getInstance(appContext)
                val licensed = ActivationManager(appContext).refresh()
                if (licensed && prefs.isVeilEnabled && prefs.isPersistentShieldEnabled &&
                    NotificationUtils.isNotificationAccessGranted(appContext)
                ) {
                    VeilProtectionService.startService(appContext)
                }
                if (licensed && NotificationUtils.isNotificationAccessGranted(appContext)) {
                    NotificationUtils.ensureListenerBound(appContext)
                }
            } catch (_: Exception) {
                // Fail closed: do not restart protection when license verification fails.
            } finally {
                pendingResult.finish()
            }
        }
    }
}
