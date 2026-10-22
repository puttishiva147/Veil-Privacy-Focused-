package com.veil.privacy.ui

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.chip.Chip
import com.veil.privacy.BuildConfig
import com.veil.privacy.R
import com.veil.privacy.databinding.ActivityRealtimeTestStudioBinding
import com.veil.privacy.model.ProtectionRule
import com.veil.privacy.model.SupportedApp
import com.veil.privacy.notification.VeilNotificationListener
import com.veil.privacy.notification.VeilNotificationManager
import com.veil.privacy.service.VeilProtectionService
import com.veil.privacy.storage.VeilDatabase
import com.veil.privacy.storage.VeilPrefs
import com.veil.privacy.util.InsetsUtils
import com.veil.privacy.util.NotificationUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Interactive Real-Time Test Studio & Pipeline Verification Lab.
 *
 * Allows users to simulate incoming notifications across messaging apps (WhatsApp, Telegram, Signal, SMS)
 * with configurable sender, message, and trigger delays.
 *
 * Streams live pipeline telemetry from [VeilNotificationListener] so users can visually verify
 * real-time interception, cancellation of the original alert, decoy disguise computation, and vault storage.
 */
class RealtimeTestStudioActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRealtimeTestStudioBinding
    private lateinit var prefs: VeilPrefs
    private lateinit var database: VeilDatabase
    private val logBuilder = StringBuilder()
    private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!BuildConfig.DEBUG) {
            finish()
            return
        }
        binding = ActivityRealtimeTestStudioBinding.inflate(layoutInflater)
        setContentView(binding.root)

        InsetsUtils.applySystemBarInsets(binding.root)

        prefs = VeilPrefs.getInstance(this)
        database = VeilDatabase.getInstance(this)

        setupUI()
        setupListeners()
        loadRulesIntoChips()
        observePipelineTelemetry()
    }

    override fun onResume() {
        super.onResume()
        updateHealthCard()
        NotificationUtils.ensureListenerBound(this)
    }

    private fun setupUI() {
        updateHealthCard()

        binding.btnBack.setOnClickListener { finish() }

        binding.btnQuickFixAccess.setOnClickListener {
            if (!NotificationUtils.isNotificationAccessGranted(this)) {
                startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
            } else {
                NotificationUtils.ensureListenerBound(this)
                VeilProtectionService.startService(this)
                updateHealthCard()
                Toast.makeText(this, "Rebinding Notification Listener service...", Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnSample1.setOnClickListener {
            binding.etMessageBody.setText("Hey, are you free for a confidential meeting at 4 PM?")
        }

        binding.btnSample2.setOnClickListener {
            binding.etMessageBody.setText("Can you review the private document sent to your inbox?")
        }

        binding.btnSample3.setOnClickListener {
            binding.etMessageBody.setText("Urgent: Call me when you get a chance.")
        }

        binding.chipTypeMessage.setOnClickListener {
            binding.etMessageBody.setText("Hey, are you free right now? Need to discuss privately.")
        }
        binding.chipTypeCall.setOnClickListener {
            binding.etMessageBody.setText("Incoming voice call")
        }
        binding.chipTypeMedia.setOnClickListener {
            binding.etMessageBody.setText("📷 Sent a photo (3.2 MB)")
        }

        binding.btnClearAudit.setOnClickListener {
            logBuilder.clear()
            logBuilder.append("[${timeFormat.format(Date())}] Audit log cleared. Standby.\n")
            binding.tvAuditLog.text = logBuilder.toString()
        }

        binding.btnViewChats.setOnClickListener {
            finish()
        }

        binding.btnViewLog.setOnClickListener {
            startActivity(Intent(this, PrivateInboxActivity::class.java))
        }
    }

    private fun setupListeners() {
        binding.btnFireTest.setOnClickListener {
            val sender = binding.etSenderName.text?.toString()?.trim().orEmpty()
            val message = binding.etMessageBody.text?.toString()?.trim().orEmpty()

            if (sender.isBlank()) {
                Toast.makeText(this, "Please enter a target sender name", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (message.isBlank()) {
                Toast.makeText(this, "Please enter a message payload", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val delaySeconds = when {
                binding.chipDelay3.isChecked -> 3
                binding.chipDelay5.isChecked -> 5
                else -> 0
            }

            executeRealtimeTest(sender, message, delaySeconds)
        }
    }

    private fun executeRealtimeTest(sender: String, message: String, delaySeconds: Int) {
        val selectedApp = when {
            binding.chipTelegram.isChecked -> SupportedApp.PKG_TELEGRAM
            binding.chipSignal.isChecked -> SupportedApp.PKG_SIGNAL
            binding.chipSms.isChecked -> SupportedApp.PKG_GOOGLE_MESSAGES
            else -> SupportedApp.PKG_WHATSAPP
        }

        val appName = SupportedApp.getAppDisplayName(selectedApp)
        val isCall = binding.chipTypeCall.isChecked
        val isMedia = binding.chipTypeMedia.isChecked

        val simulatedMessage = when {
            isCall -> "Incoming WhatsApp voice call"
            isMedia -> if (message.contains("photo", ignoreCase = true) || message.contains("attachment", ignoreCase = true)) message else "📷 Photo attachment: $message"
            else -> message
        }

        lifecycleScope.launch {
            binding.btnFireTest.isEnabled = false

            val typeDesc = if (isCall) "Voice Call" else if (isMedia) "Media Attachment" else "Message"
            if (delaySeconds > 0) {
                appendAudit("TEST", "Preparing simulated $appName $typeDesc alert from '$sender' in $delaySeconds seconds...")
                appendAudit("TEST", "💡 TIP: Pull down notification shade to observe dynamic disguise in real time!")
                Toast.makeText(
                    this@RealtimeTestStudioActivity,
                    "$typeDesc firing in $delaySeconds seconds. Switch to home screen or lock phone!",
                    Toast.LENGTH_LONG
                ).show()

                for (i in delaySeconds downTo 1) {
                    binding.btnFireTest.text = "Firing in ${i}s (Switch to Home/Lock)..."
                    delay(1000L)
                }
            } else {
                appendAudit("TEST", "Dispatched simulated $appName $typeDesc alert from '$sender'")
            }

            binding.btnFireTest.text = "⚡ Intercepting Alert..."

            // Trigger simulated incoming notification with live RemoteInput reply action
            VeilNotificationManager.getInstance(this@RealtimeTestStudioActivity).postSimulationNotification(
                sender = sender,
                message = simulatedMessage,
                isCall = isCall
            )

            delay(1200L)
            binding.btnFireTest.isEnabled = true
            binding.btnFireTest.text = "⚡ Fire Real-Time Test Alert"
            updateHealthCard()
        }
    }

    private fun loadRulesIntoChips() {
        lifecycleScope.launch {
            val rules = withContext(Dispatchers.IO) {
                database.protectionRuleDao().getAllRulesSync()
            }

            binding.chipGroupRules.removeAllViews()
            if (rules.isEmpty()) {
                val defaultChip = Chip(this@RealtimeTestStudioActivity).apply {
                    text = "Secret Contact (Demo)"
                    isCheckable = true
                    isChecked = true
                    setOnClickListener {
                        binding.etSenderName.setText("Secret Contact")
                    }
                }
                binding.chipGroupRules.addView(defaultChip)
            } else {
                rules.forEachIndexed { index, rule ->
                    val chip = Chip(this@RealtimeTestStudioActivity).apply {
                        text = "${rule.contactName} (${rule.decoyIdentity})"
                        isCheckable = true
                        if (index == 0) {
                            isChecked = true
                            binding.etSenderName.setText(rule.contactName)
                        }
                        setOnClickListener {
                            binding.etSenderName.setText(rule.contactName)
                        }
                    }
                    binding.chipGroupRules.addView(chip)
                }
            }
        }
    }

    private fun updateHealthCard() {
        val isGranted = NotificationUtils.isNotificationAccessGranted(this)
        val isConnected = NotificationUtils.isListenerConnected()
        val isEnabled = prefs.isVeilEnabled

        if (!isGranted) {
            binding.ivHealthDot.setColorFilter(ContextCompat.getColor(this, R.color.veil_accent_amber))
            binding.tvHealthStatus.text = "NOTIFICATION ACCESS REQUIRED"
            binding.tvHealthStatus.setTextColor(ContextCompat.getColor(this, R.color.veil_accent_amber))
            binding.tvHealthDetails.text = "Android Notification Access is not granted. Tap Authorize."
            binding.btnQuickFixAccess.text = "Authorize"
            binding.cardSystemHealth.strokeColor = ContextCompat.getColor(this, R.color.veil_accent_amber)
        } else if (!isEnabled) {
            binding.ivHealthDot.setColorFilter(ContextCompat.getColor(this, R.color.veil_text_medium))
            binding.tvHealthStatus.text = "VEIL ENGINE PAUSED"
            binding.tvHealthStatus.setTextColor(ContextCompat.getColor(this, R.color.veil_text_medium))
            binding.tvHealthDetails.text = "Master protection switch is off. Tap to resume."
            binding.btnQuickFixAccess.text = "Resume"
            binding.cardSystemHealth.strokeColor = ContextCompat.getColor(this, R.color.veil_hairline)
        } else if (!isConnected) {
            binding.ivHealthDot.setColorFilter(ContextCompat.getColor(this, R.color.veil_accent_amber))
            binding.tvHealthStatus.text = "CONNECTING LISTENER..."
            binding.tvHealthStatus.setTextColor(ContextCompat.getColor(this, R.color.veil_accent_amber))
            binding.tvHealthDetails.text = "Android listener service is rebinding in memory."
            binding.btnQuickFixAccess.text = "Rebind"
            binding.cardSystemHealth.strokeColor = ContextCompat.getColor(this, R.color.veil_accent_amber)
        } else {
            binding.ivHealthDot.setColorFilter(ContextCompat.getColor(this, R.color.veil_accent_secure))
            binding.tvHealthStatus.text = "REAL-TIME PROTECTION ARMED"
            binding.tvHealthStatus.setTextColor(ContextCompat.getColor(this, R.color.veil_accent_secure))
            binding.tvHealthDetails.text = "Listener connected • Multi-app guard active • Watchdog running"
            binding.btnQuickFixAccess.text = "Re-check"
            binding.cardSystemHealth.strokeColor = ContextCompat.getColor(this, R.color.veil_hairline)
        }
    }

    private fun observePipelineTelemetry() {
        lifecycleScope.launch {
            VeilNotificationListener.pipelineFlow.collectLatest { event ->
                appendAudit(event.stage, event.message, event.isSuccess)
            }
        }
    }

    private fun appendAudit(stage: String, message: String, isSuccess: Boolean = true) {
        val time = timeFormat.format(Date())
        val icon = if (isSuccess) "✓" else "⚠️"
        val line = "[$time] [$stage] $icon $message\n"
        logBuilder.append(line)
        binding.tvAuditLog.text = logBuilder.toString()
    }
}
