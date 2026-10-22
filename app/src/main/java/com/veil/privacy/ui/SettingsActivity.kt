package com.veil.privacy.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.veil.privacy.BuildConfig
import com.veil.privacy.R
import com.veil.privacy.databinding.ActivitySettingsBinding
import com.veil.privacy.databinding.DialogPrivacyInfoBinding
import com.veil.privacy.model.ParsedNotificationInfo
import com.veil.privacy.model.ProtectedContact
import com.veil.privacy.model.ProtectedEvent
import com.veil.privacy.notification.VeilNotificationManager
import com.veil.privacy.privacy.PrivacyEngine
import com.veil.privacy.storage.VeilDatabase
import com.veil.privacy.storage.VeilInbox
import com.veil.privacy.storage.VeilPrefs
import com.veil.privacy.util.NotificationUtils
import com.veil.privacy.util.VeilDialogs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private lateinit var prefs: VeilPrefs
    private lateinit var inbox: VeilInbox
    private lateinit var db: VeilDatabase

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        if (!BuildConfig.DEBUG) {
            binding.tvDiagnosticsSectionTitle.visibility = android.view.View.GONE
            binding.cardDiagnostics.visibility = android.view.View.GONE
        }

        com.veil.privacy.util.InsetsUtils.applySystemBarInsets(binding.root)

        prefs = VeilPrefs.getInstance(this)
        inbox = VeilInbox.getInstance(this)
        db = VeilDatabase.getInstance(this)

        setupUI()
        setupListeners()
    }

    override fun onResume() {
        super.onResume()
        updateStatus()
        updateDecoyCounts()
    }

    private fun setupUI() {
        binding.switchVeilEnabled.isChecked = prefs.isVeilEnabled
        binding.switchHideOriginal.isChecked = prefs.isHideOriginalEnabled
        binding.switchProtectedCalls.isChecked = prefs.isProtectedCallsEnabled

        when (prefs.protectionNotificationDisplay) {
            VeilPrefs.NOTIF_DISPLAY_ALWAYS_HIDE -> binding.rbNotifAlwaysHide.isChecked = true
            VeilPrefs.NOTIF_DISPLAY_SHOW_WHEN_PAUSED -> binding.rbNotifShowWhenPaused.isChecked = true
            else -> binding.rbNotifAlwaysShow.isChecked = true
        }

        updateDecoyCounts()
        updateStatus()
    }

    private fun updateStatus() {
        val isGranted = NotificationUtils.isNotificationAccessGranted(this)
        if (isGranted) {
            binding.tvSettingsAccessStatus.text = getString(R.string.dashboard_access_granted)
            binding.tvSettingsAccessStatus.setTextColor(ContextCompat.getColor(this, R.color.veil_accent_secure))
        } else {
            binding.tvSettingsAccessStatus.text = getString(R.string.dashboard_access_disabled)
            binding.tvSettingsAccessStatus.setTextColor(ContextCompat.getColor(this, R.color.veil_accent_amber))
        }
    }

    private fun updateDecoyCounts() {
        val namesCount = prefs.getDecoyNames().size
        val msgsCount = prefs.getDecoyMessages().size

        binding.tvDecoyNamesTitle.text = getString(R.string.settings_decoy_names_title, namesCount)
        binding.tvDecoyMessagesTitle.text = getString(R.string.settings_decoy_messages_title, msgsCount)
    }

    private fun setupListeners() {
        binding.btnBack.setOnClickListener { finish() }

        // Master switch
        binding.switchVeilEnabled.setOnCheckedChangeListener { _, isChecked ->
            prefs.isVeilEnabled = isChecked
            com.veil.privacy.service.VeilProtectionService.refresh(this)
        }

        // Hide original
        binding.switchHideOriginal.setOnCheckedChangeListener { _, isChecked ->
            prefs.isHideOriginalEnabled = isChecked
        }

        // Protected calls
        binding.switchProtectedCalls.setOnCheckedChangeListener { _, isChecked ->
            prefs.isProtectedCallsEnabled = isChecked
        }

        // VEIL Protection Notification Display setting
        val applyNotifMode = { mode: String ->
            prefs.protectionNotificationDisplay = mode
            binding.rbNotifAlwaysShow.isChecked = (mode == VeilPrefs.NOTIF_DISPLAY_ALWAYS_SHOW)
            binding.rbNotifAlwaysHide.isChecked = (mode == VeilPrefs.NOTIF_DISPLAY_ALWAYS_HIDE)
            binding.rbNotifShowWhenPaused.isChecked = (mode == VeilPrefs.NOTIF_DISPLAY_SHOW_WHEN_PAUSED)
            com.veil.privacy.service.VeilProtectionService.refresh(this)
            val label = when (mode) {
                VeilPrefs.NOTIF_DISPLAY_ALWAYS_HIDE -> "Always Hide"
                VeilPrefs.NOTIF_DISPLAY_SHOW_WHEN_PAUSED -> "Show Only When Protection Is Paused"
                else -> "Always Show"
            }
            Toast.makeText(this, "Notification display: $label", Toast.LENGTH_SHORT).show()
        }

        binding.rowNotifAlwaysShow.setOnClickListener {
            applyNotifMode(VeilPrefs.NOTIF_DISPLAY_ALWAYS_SHOW)
        }
        binding.rbNotifAlwaysShow.setOnClickListener {
            applyNotifMode(VeilPrefs.NOTIF_DISPLAY_ALWAYS_SHOW)
        }

        binding.rowNotifAlwaysHide.setOnClickListener {
            applyNotifMode(VeilPrefs.NOTIF_DISPLAY_ALWAYS_HIDE)
        }
        binding.rbNotifAlwaysHide.setOnClickListener {
            applyNotifMode(VeilPrefs.NOTIF_DISPLAY_ALWAYS_HIDE)
        }

        binding.rowNotifShowWhenPaused.setOnClickListener {
            applyNotifMode(VeilPrefs.NOTIF_DISPLAY_SHOW_WHEN_PAUSED)
        }
        binding.rbNotifShowWhenPaused.setOnClickListener {
            applyNotifMode(VeilPrefs.NOTIF_DISPLAY_SHOW_WHEN_PAUSED)
        }

        // Explain Android's manual restricted-settings approval before opening access settings.
        binding.btnOpenNotificationSettings.setOnClickListener {
            showNotificationAccessHelp()
        }
        binding.btnRowNotificationAccess.setOnClickListener {
            showNotificationAccessHelp()
        }

        // Decoy Names Editor
        binding.btnManageDecoyNames.setOnClickListener {
            val intent = Intent(this, DecoyListEditActivity::class.java).apply {
                putExtra(DecoyListEditActivity.EXTRA_MODE, DecoyListEditActivity.MODE_NAMES)
            }
            startActivity(intent)
        }

        // Decoy Messages Editor
        binding.btnManageDecoyMessages.setOnClickListener {
            val intent = Intent(this, DecoyListEditActivity::class.java).apply {
                putExtra(DecoyListEditActivity.EXTRA_MODE, DecoyListEditActivity.MODE_MESSAGES)
            }
            startActivity(intent)
        }

        // Pipeline Diagnostic Execution -> Opens Real-Time Test Studio
        if (BuildConfig.DEBUG) {
            binding.btnRunDiagnostic.setOnClickListener {
                startActivity(Intent(this, RealtimeTestStudioActivity::class.java))
            }
            binding.btnExecuteDiagnostic.setOnClickListener {
                startActivity(Intent(this, RealtimeTestStudioActivity::class.java))
            }
        }

        // Reconfigure Security Credentials
        binding.btnResetSecuritySetup.setOnClickListener {
            val intent = Intent(this, SecuritySetupActivity::class.java)
            startActivity(intent)
        }

        // Clear Private Vault
        binding.btnClearHistory.setOnClickListener {
            showClearHistoryDialog()
        }

        // Privacy Policy / Architecture
        binding.btnPrivacyPolicy.setOnClickListener {
            showPrivacyInfoDialog()
        }

        // About VEIL
        binding.btnAboutVeil.setOnClickListener {
            showAboutDialog()
        }
    }

    private fun showNotificationAccessHelp() {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Enable notification access")
            .setMessage(
                "If Android shows “Controlled by restricted setting”:\n\n" +
                    "1. Tap Open app info below.\n" +
                    "2. Tap the three-dot menu (⋮).\n" +
                    "3. Choose Allow restricted settings and confirm.\n" +
                    "4. Return to VEIL and open notification settings to enable access.\n\n" +
                    "Only allow this for an app you trust. Android requires this step to be completed manually."
            )
            .setPositiveButton("Open app info") { _, _ ->
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.parse("package:$packageName")
                }
                startActivity(intent)
            }
            .setNeutralButton("Open notification settings") { _, _ ->
                NotificationUtils.openNotificationListenerSettings(this)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun executePipelineDiagnostic() {
        startActivity(Intent(this, RealtimeTestStudioActivity::class.java))
    }

    private fun showClearHistoryDialog() {
        VeilDialogs.showConfirmationDialog(
            context = this,
            title = getString(R.string.settings_clear_history_title),
            message = getString(R.string.inbox_clear_confirm),
            confirmText = "Purge Vault",
            isDanger = true,
            onConfirm = {
                lifecycleScope.launch {
                    inbox.clearAll()
                    Toast.makeText(this@SettingsActivity, "Private vault purged", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }

    private fun showPrivacyInfoDialog() {
        val dialogBinding = DialogPrivacyInfoBinding.inflate(LayoutInflater.from(this))
        val dialog = androidx.appcompat.app.AlertDialog.Builder(this)
            .setView(dialogBinding.root)
            .create()

        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        dialogBinding.btnClosePrivacyDialog.setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun showAboutDialog() {
        VeilDialogs.showInfoDialog(
            context = this,
            title = "System Architecture • NEXRO Series",
            message = "VEIL (Variable Event Identity Layer)\n\n" +
                "Version: " + com.veil.privacy.BuildConfig.VERSION_NAME + "\n" +
                "Package: com.veil.privacy\n\n" +
                "Target Subsystem: Multi-App Messaging Layer (WhatsApp, Telegram, Signal, SMS, Instagram)\n" +
                "Interception Engine: Android NotificationListenerService & Foreground Shield\n" +
                "Decoy Algorithm: Arrival-Time Dynamic Multi-Profile Rotation (Work, Calendar, Delivery, System)\n" +
                "Persistence: Sandboxed Encrypted SQLite Room\n" +
                "Telemetry: 0 bytes (Zero-Network Boundary)"
        )
    }
}
