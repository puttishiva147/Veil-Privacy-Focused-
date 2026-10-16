package com.veil.privacy.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.veil.privacy.R
import com.veil.privacy.notification.VeilNotificationListener
import com.veil.privacy.notification.VeilNotificationManager
import com.veil.privacy.storage.VeilDatabase
import com.veil.privacy.storage.VeilPrefs
import com.veil.privacy.ui.AuthLockActivity
import com.veil.privacy.ui.DashboardActivity
import com.veil.privacy.ui.RealtimeTestStudioActivity
import com.veil.privacy.util.NotificationUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Persistent Real-Time Foreground Protection Service for VEIL.
 *
 * Keeps VEIL's process active in memory to prevent aggressive OEM / Android OS
 * task killers and Doze modes from freezing or unbinding the NotificationListenerService.
 *
 * Includes an automated watchdog heartbeat that verifies listener connectivity
 * and issues rebind requests if disconnection is detected.
 */
class VeilProtectionService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.Main + Job())
    private var watchdogJob: Job? = null
    private lateinit var prefs: VeilPrefs
    private lateinit var database: VeilDatabase

    companion object {
        const val NOTIFICATION_ID = 90001
        const val ACTION_START = "com.veil.privacy.action.START_SHIELD"
        const val ACTION_STOP = "com.veil.privacy.action.STOP_SHIELD"
        const val ACTION_TOGGLE_MASTER = "com.veil.privacy.action.TOGGLE_MASTER"
        const val ACTION_LOCK_VAULT = "com.veil.privacy.action.LOCK_VAULT"
        const val ACTION_REFRESH = "com.veil.privacy.action.REFRESH_STATUS"

        fun startService(context: Context) {
            val intent = Intent(context, VeilProtectionService::class.java).apply {
                action = ACTION_START
            }
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (_: Exception) {}
        }

        fun stopService(context: Context) {
            val intent = Intent(context, VeilProtectionService::class.java).apply {
                action = ACTION_STOP
            }
            try {
                context.startService(intent)
            } catch (_: Exception) {}
        }

        fun refresh(context: Context) {
            val intent = Intent(context, VeilProtectionService::class.java).apply {
                action = ACTION_REFRESH
            }
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (_: Exception) {}
        }

        fun lockVault(context: Context) {
            val intent = Intent(context, VeilProtectionService::class.java).apply {
                action = ACTION_LOCK_VAULT
            }
            try {
                context.startService(intent)
            } catch (_: Exception) {}
        }
    }

    override fun onCreate() {
        super.onCreate()
        prefs = VeilPrefs.getInstance(this)
        database = VeilDatabase.getInstance(this)
        VeilNotificationManager.getInstance(this).createNotificationChannels()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: ACTION_START

        when (action) {
            ACTION_STOP -> {
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                        stopForeground(STOP_FOREGROUND_REMOVE)
                    } else {
                        @Suppress("DEPRECATION")
                        stopForeground(true)
                    }
                } catch (_: Exception) {}
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_TOGGLE_MASTER -> {
                prefs.isVeilEnabled = !prefs.isVeilEnabled
                updateNotification()
            }
            ACTION_LOCK_VAULT -> {
                VeilPrefs.isSessionUnlocked = false
                updateNotification()
            }
            ACTION_REFRESH, ACTION_START -> {
                startForegroundWithNotification()
                startWatchdog()
            }
        }

        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        watchdogJob?.cancel()
    }

    private fun shouldDisplayNotification(): Boolean {
        return when (prefs.protectionNotificationDisplay) {
            VeilPrefs.NOTIF_DISPLAY_ALWAYS_SHOW -> true
            VeilPrefs.NOTIF_DISPLAY_ALWAYS_HIDE -> false
            VeilPrefs.NOTIF_DISPLAY_SHOW_WHEN_PAUSED -> !prefs.isVeilEnabled
            else -> true
        }
    }

    private fun startForegroundWithNotification() {
        val initialNotification = buildOngoingNotification(0)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ServiceCompat.startForeground(
                    this,
                    NOTIFICATION_ID,
                    initialNotification,
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                    } else {
                        0
                    }
                )
            } else {
                startForeground(NOTIFICATION_ID, initialNotification)
            }
        } catch (_: Exception) {
            try {
                startForeground(NOTIFICATION_ID, initialNotification)
            } catch (_: Exception) {}
        }
        updateNotification()
    }

    private fun updateNotification() {
        serviceScope.launch {
            val shouldShow = shouldDisplayNotification()
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as? android.app.NotificationManager
            if (!shouldShow) {
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                        stopForeground(STOP_FOREGROUND_REMOVE)
                    } else {
                        @Suppress("DEPRECATION")
                        stopForeground(true)
                    }
                } catch (_: Exception) {}
                nm?.cancel(NOTIFICATION_ID)
            } else {
                val ruleCount = withContext(Dispatchers.IO) {
                    database.protectionRuleDao().getEnabledRulesSync().size
                }
                val notification = buildOngoingNotification(ruleCount)
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        ServiceCompat.startForeground(
                            this@VeilProtectionService,
                            NOTIFICATION_ID,
                            notification,
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                            } else {
                                0
                            }
                        )
                    } else {
                        startForeground(NOTIFICATION_ID, notification)
                    }
                } catch (_: Exception) {}
                nm?.notify(NOTIFICATION_ID, notification)
            }
        }
    }

    private fun buildOngoingNotification(ruleCount: Int): Notification {
        val isMasterOn = prefs.isVeilEnabled
        val isConnected = NotificationUtils.isListenerConnected()
        val totalCount = prefs.totalInterceptionsCount

        val title = if (isMasterOn) {
            if (isConnected) "🛡️ VEIL Protection Shield Active" else "🛡️ VEIL Active • Reconnecting Listener..."
        } else {
            "⏸️ VEIL Protection Paused"
        }

        val statusText = if (isMasterOn) {
            "Armed: $ruleCount rules • $totalCount disguised • Multi-app guard"
        } else {
            "Protection paused • Tap to resume real-time shield"
        }

        // Tap content intent -> Opens Dashboard
        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, DashboardActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Action 1: Lock Vault
        val lockIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, VeilProtectionService::class.java).apply { action = ACTION_LOCK_VAULT },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Action 2: Launch Real-Time Test Studio
        val testIntent = PendingIntent.getActivity(
            this,
            2,
            Intent(this, RealtimeTestStudioActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Action 3: Toggle Master
        val toggleIntent = PendingIntent.getService(
            this,
            3,
            Intent(this, VeilProtectionService::class.java).apply { action = ACTION_TOGGLE_MASTER },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(this, VeilNotificationManager.CHANNEL_SERVICE_ID)
            .setSmallIcon(R.drawable.ic_veil_notification)
            .setContentTitle(title)
            .setContentText(statusText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(statusText))
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(contentIntent)
            .addAction(R.drawable.ic_lock_closed, "Lock Vault", lockIntent)
            .addAction(R.drawable.ic_shield_status, "Test Alert", testIntent)
            .addAction(
                if (isMasterOn) R.drawable.ic_shield_status else R.drawable.ic_veil_visor_glyph,
                if (isMasterOn) "Pause" else "Resume",
                toggleIntent
            )
            .setColor(getColor(R.color.veil_accent_secure))

        return builder.build()
    }

    private fun startWatchdog() {
        watchdogJob?.cancel()
        watchdogJob = serviceScope.launch {
            while (isActive) {
                delay(20_000L) // Check every 20 seconds

                // 1. Check listener status
                if (prefs.isVeilEnabled && NotificationUtils.isNotificationAccessGranted(this@VeilProtectionService)) {
                    if (!NotificationUtils.isListenerConnected()) {
                        NotificationUtils.ensureListenerBound(this@VeilProtectionService)
                    }
                }

                // 2. Refresh notification text with latest stats
                updateNotification()
            }
        }
    }
}
