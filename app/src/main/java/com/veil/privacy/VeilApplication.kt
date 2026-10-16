package com.veil.privacy

import android.app.Application
import com.veil.privacy.notification.VeilNotificationManager
import com.veil.privacy.storage.VeilDatabase
import com.veil.privacy.storage.VeilPrefs
import com.veil.privacy.util.NotificationUtils

class VeilApplication : Application() {

    private var startedActivityCount = 0
    private var isAppInBackground = false

    override fun onCreate() {
        super.onCreate()
        // Initialize preferences, database, and notification channel
        VeilPrefs.getInstance(this)
        VeilDatabase.getInstance(this)
        VeilNotificationManager.getInstance(this).createNotificationChannel()

        // Ensure notification listener service is bound if permission is granted
        NotificationUtils.ensureListenerBound(this)

        // Start Persistent Protection Shield Service if enabled
        val prefs = VeilPrefs.getInstance(this)
        if (prefs.isVeilEnabled && prefs.isPersistentShieldEnabled && NotificationUtils.isNotificationAccessGranted(this)) {
            com.veil.privacy.service.VeilProtectionService.startService(this)
        }

        // Register lifecycle callbacks for mandatory, zero-timeout app lock
        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityCreated(activity: android.app.Activity, savedInstanceState: android.os.Bundle?) {}

            override fun onActivityStarted(activity: android.app.Activity) {
                if (startedActivityCount == 0 && isAppInBackground) {
                    // App returned from background -> verify session is locked
                    isAppInBackground = false
                    VeilPrefs.isSessionUnlocked = false
                    VeilPrefs.lockAllContacts()

                    if (com.veil.privacy.security.SecurityManager.isSecuritySetupCompleted(activity)) {
                        if (activity !is com.veil.privacy.ui.AuthLockActivity &&
                            activity !is com.veil.privacy.ui.SecuritySetupActivity &&
                            activity !is com.veil.privacy.ui.MainActivity) {
                            val lockIntent = android.content.Intent(activity, com.veil.privacy.ui.AuthLockActivity::class.java).apply {
                                addFlags(android.content.Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
                            }
                            activity.startActivity(lockIntent)
                        }
                    }
                }
                startedActivityCount++
            }

            override fun onActivityResumed(activity: android.app.Activity) {
                // Secondary safeguard: if security is setup and session is locked, enforce AuthLockActivity
                if (com.veil.privacy.security.SecurityManager.isSecuritySetupCompleted(activity) && !VeilPrefs.isSessionUnlocked) {
                    if (activity !is com.veil.privacy.ui.AuthLockActivity &&
                        activity !is com.veil.privacy.ui.SecuritySetupActivity &&
                        activity !is com.veil.privacy.ui.MainActivity) {
                        val lockIntent = android.content.Intent(activity, com.veil.privacy.ui.AuthLockActivity::class.java).apply {
                            addFlags(android.content.Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
                        }
                        activity.startActivity(lockIntent)
                    }
                }
            }

            override fun onActivityPaused(activity: android.app.Activity) {}

            override fun onActivityStopped(activity: android.app.Activity) {
                startedActivityCount = maxOf(0, startedActivityCount - 1)
                if (startedActivityCount == 0) {
                    // App minimized / backgrounded / switched / phone locked
                    // Automatically lock VEIL immediately. Never keep unlocked in background.
                    isAppInBackground = true
                    VeilPrefs.isSessionUnlocked = false
                    VeilPrefs.lockAllContacts()
                }
            }

            override fun onActivitySaveInstanceState(activity: android.app.Activity, outState: android.os.Bundle) {}

            override fun onActivityDestroyed(activity: android.app.Activity) {}
        })
    }
}
