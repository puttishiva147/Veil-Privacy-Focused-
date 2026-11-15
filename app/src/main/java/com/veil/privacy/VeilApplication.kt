package com.veil.privacy

import android.app.Application
import com.veil.privacy.activation.ActivationManager
import com.veil.privacy.notification.VeilNotificationManager
import com.veil.privacy.storage.VeilDatabase
import com.veil.privacy.storage.VeilPrefs
import com.veil.privacy.util.NotificationUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class VeilApplication : Application() {
    private var startedActivityCount = 0
    private var isAppInBackground = false

    override fun onCreate() {
        super.onCreate()
        VeilPrefs.getInstance(this)
        VeilNotificationManager.getInstance(this).createNotificationChannel()
        NotificationUtils.ensureListenerBound(this)

        val prefs = VeilPrefs.getInstance(this)
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                VeilDatabase.getInstance(applicationContext)
                val licensed = ActivationManager(applicationContext).refresh()
                withContext(Dispatchers.Main) {
                    if (licensed && prefs.isVeilEnabled && prefs.isPersistentShieldEnabled &&
                        NotificationUtils.isNotificationAccessGranted(this@VeilApplication)
                    ) {
                        com.veil.privacy.service.VeilProtectionService.startService(this@VeilApplication)
                    }
                }
            } catch (_: Exception) {
                // Fail closed: do not start the protection service when license status cannot be verified.
            }
        }

        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityCreated(activity: android.app.Activity, savedInstanceState: android.os.Bundle?) {}

            override fun onActivityStarted(activity: android.app.Activity) {
                if (startedActivityCount == 0 && isAppInBackground) {
                    isAppInBackground = false
                    VeilPrefs.isSessionUnlocked = false
                    VeilPrefs.lockAllContacts()
                    if (com.veil.privacy.security.SecurityManager.isSecuritySetupCompleted(activity)) {
                        if (activity !is com.veil.privacy.ui.AuthLockActivity &&
                            activity !is com.veil.privacy.ui.SecuritySetupActivity &&
                            activity !is com.veil.privacy.ui.MainActivity &&
                            activity !is com.veil.privacy.ui.ActivationActivity) {
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
                if (com.veil.privacy.security.SecurityManager.isSecuritySetupCompleted(activity) && !VeilPrefs.isSessionUnlocked) {
                    if (activity !is com.veil.privacy.ui.AuthLockActivity &&
                        activity !is com.veil.privacy.ui.SecuritySetupActivity &&
                        activity !is com.veil.privacy.ui.MainActivity &&
                        activity !is com.veil.privacy.ui.ActivationActivity) {
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