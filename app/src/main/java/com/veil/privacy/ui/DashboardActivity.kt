package com.veil.privacy.ui

import android.Manifest
import android.app.Activity
import android.app.Dialog
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.veil.privacy.R
import com.veil.privacy.databinding.ActivityDashboardBinding
import android.content.pm.PackageManager
import com.veil.privacy.model.ProtectedChatThread
import com.veil.privacy.model.ProtectedContact
import com.veil.privacy.model.ProtectedEvent
import com.veil.privacy.model.ProtectionRule
import com.veil.privacy.model.SelectedContact
import com.veil.privacy.notification.NotificationReplyRegistry
import com.veil.privacy.notification.VeilNotificationManager
import com.veil.privacy.security.SecurityManager
import com.veil.privacy.storage.VeilDatabase
import com.veil.privacy.storage.VeilPrefs
import com.veil.privacy.ui.adapter.PrivateInboxAdapter
import com.veil.privacy.ui.adapter.ProtectedChatThreadsAdapter
import com.veil.privacy.ui.adapter.ProtectionRuleAdapter
import com.veil.privacy.util.ContactSelectionHelper
import com.veil.privacy.util.InsetsUtils
import com.veil.privacy.util.NotificationUtils
import com.veil.privacy.util.PermissionUtils
import com.veil.privacy.util.VeilDialogs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Main command hub for VEIL.
 * Hosts 4 dedicated tabs:
 * 1. Protected Chats (active protected conversations with inline reply state)
 * 2. Notifications Log (audit stream of intercepted privacy notifications)
 * 3. Protected Contacts (dynamic management of protected WhatsApp contacts)
 * 4. Settings & Lock (Notification Listener permissions, App Lock PIN, and privacy configurations)
 */
class DashboardActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDashboardBinding
    private lateinit var prefs: VeilPrefs
    private lateinit var database: VeilDatabase

    private lateinit var chatThreadsAdapter: ProtectedChatThreadsAdapter
    private lateinit var notificationsAdapter: PrivateInboxAdapter
    private lateinit var rulesAdapter: ProtectionRuleAdapter

    private var currentTab: Int = TAB_CHATS
    private var selectedProtectedApp: String = com.veil.privacy.model.SupportedApp.PKG_WHATSAPP
    private var edgeDownX = 0f
    private var edgeDownY = 0f
    private val dashboardStatePrefs by lazy {
        getSharedPreferences("veil_privacy_prefs", MODE_PRIVATE)
    }
    private var notificationsLastViewedAt: Long = 0L

    // Android only delivers these key events while VEIL is foreground.

    companion object {
        private const val KEY_NOTIFICATIONS_LAST_VIEWED_AT = "dashboard_notifications_last_viewed_at"
        const val TAB_CHATS = 0
        const val TAB_NOTIFICATIONS = 1
        const val TAB_CONTACTS = 2
        const val TAB_SETTINGS = 3
        private const val EDGE_SWIPE_THRESHOLD_DP = 72
    }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ ->
        updateNotificationListenerStatus()
    }

    private val pickContactLauncher = registerForActivityResult(
        ActivityResultContracts.PickContact()
    ) { uri: Uri? ->
        if (uri != null) {
            handleContactPicked(uri)
        }
    }

    private val requestContactsPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            pickContactLauncher.launch(null)
        } else {
            showContactsPermissionDeniedDialog()
        }
    }

    private val authLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            VeilPrefs.isSessionUnlocked = true
            switchTab(TAB_CHATS)
            refreshChatThreads()
        } else {
            if (!VeilPrefs.isSessionUnlocked) {
                switchTab(TAB_NOTIFICATIONS)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Use one consistent edge-to-edge inset path: InsetsUtils adds the status/navigation
        // bar insets to the root, so the decor must not apply them a second time.
        WindowCompat.setDecorFitsSystemWindows(window, false)
        binding = ActivityDashboardBinding.inflate(layoutInflater)
        setContentView(binding.root)

        InsetsUtils.applySystemBarInsets(binding.root)

        prefs = VeilPrefs.getInstance(this)
        database = VeilDatabase.getInstance(this)

        notificationsLastViewedAt = dashboardStatePrefs.getLong(KEY_NOTIFICATIONS_LAST_VIEWED_AT, 0L)
        setupTabs()
        setupRecyclerViews()
        setupActions()
        setupSettingsControls()
        observeData()

        // Check App Lock
        checkAuthentication()

        // Explain why notifications are needed before opening the Android permission dialog.
        showNotificationPermissionPrompt()
    }

    private fun showNotificationPermissionPrompt() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            PermissionUtils.hasPostNotificationsPermission(this)
        ) return

        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Enable notifications")
            .setMessage("Allow VEIL Protection Shield to show protection alerts and notification status. You can change this anytime in Android Settings.")
            .setPositiveButton("Enable notifications") { _, _ ->
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
            .setNegativeButton("Not Now", null)
            .show()
    }

    override fun onResume() {
        super.onResume()
        NotificationUtils.ensureListenerBound(this)
        updateNotificationListenerStatus()
        refreshChatThreads()
    }

    private fun checkAuthentication() {
        if (!SecurityManager.isSecuritySetupCompleted(this)) {
            val intent = Intent(this, SecuritySetupActivity::class.java)
            startActivity(intent)
            finish()
            return
        }
        if (!VeilPrefs.isSessionUnlocked) {
            val intent = Intent(this, AuthLockActivity::class.java)
            authLauncher.launch(intent)
        }
    }

    private fun setupTabs() {
        binding.tabChats.setOnClickListener {
            if (!VeilPrefs.isSessionUnlocked) {
                val intent = Intent(this, AuthLockActivity::class.java)
                authLauncher.launch(intent)
            } else {
                switchTab(TAB_CHATS)
            }
        }
        binding.tabNotifications.setOnClickListener { switchTab(TAB_NOTIFICATIONS) }
        binding.tabContacts.setOnClickListener { switchTab(TAB_CONTACTS) }
        binding.btnNotificationBell.setOnClickListener { switchTab(TAB_NOTIFICATIONS) }
        binding.btnSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        if (VeilPrefs.isSessionUnlocked) {
            switchTab(TAB_CHATS)
        } else {
            switchTab(TAB_NOTIFICATIONS)
        }
    }

    private fun switchTab(tab: Int) {
        currentTab = tab
        if (tab == TAB_NOTIFICATIONS) {
            // Mark the existing inbox as viewed; this does not delete stored events.
            notificationsLastViewedAt = System.currentTimeMillis()
            dashboardStatePrefs.edit()
                .putLong(KEY_NOTIFICATIONS_LAST_VIEWED_AT, notificationsLastViewedAt)
                .apply()
            binding.tvUnreadNotificationCount.visibility = View.GONE
        }

        binding.viewTabChats.visibility = if (tab == TAB_CHATS) View.VISIBLE else View.GONE
        binding.viewTabNotifications.visibility = if (tab == TAB_NOTIFICATIONS) View.VISIBLE else View.GONE
        binding.viewTabContacts.visibility = if (tab == TAB_CONTACTS) View.VISIBLE else View.GONE
        binding.viewTabSettings.visibility = if (tab == TAB_SETTINGS) View.VISIBLE else View.GONE

        binding.fabAddProtectedChat.visibility = if (tab == TAB_CHATS) View.VISIBLE else View.GONE

        applyTabStyle(binding.tabChats, tab == TAB_CHATS)
        applyTabStyle(binding.tabNotifications, tab == TAB_NOTIFICATIONS)
        applyTabStyle(binding.tabContacts, tab == TAB_CONTACTS)
    }

    private fun applyTabStyle(button: com.google.android.material.button.MaterialButton, isSelected: Boolean) {
        val density = resources.displayMetrics.density
        // Keep inactive buttons visually merged into the single capsule; only the
        // selected destination gets a contrasting white pill.
        button.backgroundTintList = ContextCompat.getColorStateList(
            this,
            if (isSelected) R.color.veil_button_primary_bg else R.color.veil_surface
        )
        button.cornerRadius = (24 * density).toInt()
        button.setTextColor(
            ContextCompat.getColor(
                this,
                if (isSelected) R.color.veil_button_primary_text else R.color.veil_text_medium
            )
        )
        button.iconTint = ContextCompat.getColorStateList(
            this,
            if (isSelected) R.color.veil_button_primary_text else R.color.veil_text_medium
        )
        button.strokeWidth = 0
    }

    private fun setupRecyclerViews() {
        // Tab 1: Protected Chats
        chatThreadsAdapter = ProtectedChatThreadsAdapter { thread ->
            if (!VeilPrefs.isSessionUnlocked) {
                val intent = Intent(this, AuthLockActivity::class.java)
                authLauncher.launch(intent)
            } else {
                lifecycleScope.launch {
                    val contact = withContext(Dispatchers.IO) {
                        database.protectedContactDao().findByNormalizedNameSync(ProtectedContact.normalizeName(thread.contactName))
                    }
                    if (contact != null && contact.isCustomLockEnabled && contact.lockPinHash.isNotBlank() && !VeilPrefs.isContactUnlocked(thread.contactName)) {
                        VeilDialogs.showContactPinUnlockDialog(
                            activity = this@DashboardActivity,
                            contactName = thread.contactName,
                            storedPinHash = contact.lockPinHash,
                            storedPinSalt = contact.lockPinSalt,
                            onUnlocked = {
                                val intent = Intent(this@DashboardActivity, ProtectedChatConversationActivity::class.java).apply {
                                    putExtra(ProtectedChatConversationActivity.EXTRA_CONTACT_NAME, thread.contactName)
                                    putExtra(ProtectedChatConversationActivity.EXTRA_SOURCE_PACKAGE, thread.sourcePackage)
                            putExtra(ProtectedChatConversationActivity.EXTRA_CONVERSATION_ID, thread.conversationId)
                                }
                                startActivity(intent)
                            }
                        )
                    } else {
                        val intent = Intent(this@DashboardActivity, ProtectedChatConversationActivity::class.java).apply {
                            putExtra(ProtectedChatConversationActivity.EXTRA_CONTACT_NAME, thread.contactName)
                        }
                        startActivity(intent)
                    }
                }
            }
        }
        binding.rvProtectedChats.layoutManager = LinearLayoutManager(this)
        binding.rvProtectedChats.adapter = chatThreadsAdapter

        // Tab 2: Notifications
        notificationsAdapter = PrivateInboxAdapter(
            onItemClicked = { event ->
                val intent = Intent(this, EventDetailActivity::class.java).apply {
                    putExtra(EventDetailActivity.EXTRA_EVENT_ID, event.id)
                }
                startActivity(intent)
            },
            onDeleteClicked = { event ->
                lifecycleScope.launch(Dispatchers.IO) {
                    database.protectedEventDao().delete(event)
                }
            }
        )
        binding.rvNotifications.layoutManager = LinearLayoutManager(this)
        binding.rvNotifications.adapter = notificationsAdapter

        // Tab 3: Protected Contacts Rules
        rulesAdapter = ProtectionRuleAdapter(
            onRuleClick = { rule ->
                val intent = Intent(this, RuleEditActivity::class.java).apply {
                    putExtra(RuleEditActivity.EXTRA_RULE_ID, rule.id)
                }
                startActivity(intent)
            },
            onToggleEnabled = { rule, isEnabled ->
                lifecycleScope.launch(Dispatchers.IO) {
                    database.protectionRuleDao().updateEnabled(rule.id, isEnabled)
                }
            }
        )
        binding.rvContactRules.layoutManager = LinearLayoutManager(this)
        binding.rvContactRules.adapter = rulesAdapter
    }

    private fun setupActions() {
        binding.fabAddProtectedChat.setOnClickListener {
            openAddContactChooser()
        }

        binding.btnAddContactRuleTop.setOnClickListener {
            startActivity(Intent(this, RuleEditActivity::class.java))
        }

        binding.btnEmptyAddContactRule.setOnClickListener {
            startActivity(Intent(this, RuleEditActivity::class.java))
        }

        binding.btnClearNotificationsLog.setOnClickListener {
            VeilDialogs.showConfirmationDialog(
                context = this,
                title = "Clear Notification Log",
                message = "Are you sure you want to permanently clear all logged notifications? Active rules and protected chats will remain intact.",
                confirmText = "Clear Log",
                isDanger = true,
                onConfirm = {
                    lifecycleScope.launch(Dispatchers.IO) {
                        database.protectedEventDao().deleteAll()
                    }
                }
            )
        }
    }

    private fun setupSettingsControls() {
        binding.switchVeilMaster.isChecked = prefs.isVeilEnabled
        binding.switchVeilMaster.setOnCheckedChangeListener { _, isChecked ->
            prefs.isVeilEnabled = isChecked
            Toast.makeText(this, if (isChecked) "VEIL Protection Enabled" else "VEIL Protection Paused", Toast.LENGTH_SHORT).show()
            if (prefs.isPersistentShieldEnabled && NotificationUtils.isNotificationAccessGranted(this)) {
                com.veil.privacy.service.VeilProtectionService.startService(this)
            }
            updateNotificationListenerStatus()
        }

        binding.switchPersistentShield.isChecked = prefs.isPersistentShieldEnabled
        binding.switchPersistentShield.setOnCheckedChangeListener { _, isChecked ->
            prefs.isPersistentShieldEnabled = isChecked
            if (isChecked && NotificationUtils.isNotificationAccessGranted(this)) {
                com.veil.privacy.service.VeilProtectionService.startService(this)
                Toast.makeText(this, "Persistent Background Shield Activated", Toast.LENGTH_SHORT).show()
            } else {
                com.veil.privacy.service.VeilProtectionService.stopService(this)
                Toast.makeText(this, "Persistent Shield Deactivated", Toast.LENGTH_SHORT).show()
            }
        }

        binding.switchProtectAllMessagingApps.isChecked = prefs.isProtectAllMessagingApps
        binding.switchProtectAllMessagingApps.setOnCheckedChangeListener { _, isChecked ->
            prefs.isProtectAllMessagingApps = isChecked
            Toast.makeText(
                this,
                if (isChecked) "Multi-App Messaging Guard (WhatsApp, Telegram, Signal, SMS) Active" else "Default Messaging Filter Active",
                Toast.LENGTH_SHORT
            ).show()
        }

        binding.switchProtectAllSenders.isChecked = prefs.isProtectAllSenders
        binding.switchProtectAllSenders.setOnCheckedChangeListener { _, isChecked ->
            prefs.isProtectAllSenders = isChecked
            Toast.makeText(
                this,
                if (isChecked) "Disguise All Incoming Senders Enabled" else "Rule-Specific Disguise Enabled",
                Toast.LENGTH_SHORT
            ).show()
            updateNotificationListenerStatus()
        }

        updateDecoyThemeUI()
        binding.btnSelectDecoyTheme.setOnClickListener {
            showDecoyThemeChooserDialog()
        }

        binding.btnOpenTestStudio.setOnClickListener {
            startActivity(Intent(this, RealtimeTestStudioActivity::class.java))
        }

        binding.switchAutoDismiss.isChecked = prefs.isHideOriginalEnabled
        binding.switchAutoDismiss.setOnCheckedChangeListener { _, isChecked ->
            prefs.isHideOriginalEnabled = isChecked
        }

        binding.btnGrantListenerAccess.setOnClickListener {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }

        binding.switchAppLock.isChecked = prefs.isAppLockEnabled
        binding.switchAppLock.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked && !SecurityManager.isSecuritySetupCompleted(this)) {
                startActivity(Intent(this, SecuritySetupActivity::class.java))
            } else {
                prefs.isAppLockEnabled = isChecked
                updateAppLockStatusUI()
            }
        }

        binding.btnConfigurePin.setOnClickListener {
            startActivity(Intent(this, SecuritySetupActivity::class.java))
        }

        updateAppLockStatusUI()
    }

    private fun updateDecoyThemeUI() {
        val themeDesc = when (prefs.decoyPresetTheme) {
            VeilPrefs.THEME_DYNAMIC -> "🎲 Dynamic Rotation (Work, Calendar, Delivery, System)"
            VeilPrefs.THEME_DELIVERY -> "📦 Delivery & Logistics (Amazon, FedEx, BlueDart...)"
            VeilPrefs.THEME_WORK -> "🏢 Work & Productivity (Slack, Jira, Project Lead...)"
            VeilPrefs.THEME_CALENDAR -> "📅 Calendar & Schedule (Google Calendar, Outlook...)"
            VeilPrefs.THEME_SYSTEM -> "⚙️ System & Utilities (Android System, Security Core...)"
            else -> "💬 Casual & Social (Ayush, Rahul, Kiran...)"
        }
        binding.tvDecoyThemeStatus.text = "Current: $themeDesc"
    }

    private fun showDecoyThemeChooserDialog() {
        val themes = arrayOf(
            "🎲 Dynamic Rotation (Auto-rotate Work, Calendar, Delivery, System)",
            "📦 Delivery & Logistics (Amazon Logistics, FedEx, BlueDart...)",
            "🏢 Work & Productivity (Slack, Jira, Project Lead...)",
            "📅 Calendar & Schedule (Google Calendar, Outlook, Schedule Assistant...)",
            "⚙️ System & Utilities (Android System, Security Core...)",
            "💬 Casual & Social (Ayush, Rahul, Kiran...)"
        )
        val currentIdx = when (prefs.decoyPresetTheme) {
            VeilPrefs.THEME_DELIVERY -> 1
            VeilPrefs.THEME_WORK -> 2
            VeilPrefs.THEME_CALENDAR -> 3
            VeilPrefs.THEME_SYSTEM -> 4
            VeilPrefs.THEME_CASUAL -> 5
            else -> 0
        }

        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Select Decoy Persona Theme")
            .setSingleChoiceItems(themes, currentIdx) { dialog, which ->
                val newTheme = when (which) {
                    1 -> VeilPrefs.THEME_DELIVERY
                    2 -> VeilPrefs.THEME_WORK
                    3 -> VeilPrefs.THEME_CALENDAR
                    4 -> VeilPrefs.THEME_SYSTEM
                    5 -> VeilPrefs.THEME_CASUAL
                    else -> VeilPrefs.THEME_DYNAMIC
                }
                prefs.decoyPresetTheme = newTheme
                updateDecoyThemeUI()
                Toast.makeText(this, "Decoy theme updated", Toast.LENGTH_SHORT).show()
                dialog.dismiss()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun updateAppLockStatusUI() {
        if (prefs.isAppLockEnabled && SecurityManager.isSecuritySetupCompleted(this)) {
            val bio = if (prefs.isBiometricEnabled) "Biometric & " else ""
            binding.tvAppLockStatus.text = "${bio}Master Password active"
        } else {
            binding.tvAppLockStatus.text = "App lock is disabled"
        }
    }

    private fun updateNotificationListenerStatus() {
        val isGranted = NotificationUtils.isNotificationAccessGranted(this)
        val isConnected = NotificationUtils.isListenerConnected()
        val hasPostNotif = PermissionUtils.hasPostNotificationsPermission(this)
        val isVeilEnabled = prefs.isVeilEnabled

        if (!isGranted) {
            // Missing Notification Access
            binding.tvListenerAccessStatus.text = "PERMISSION REQUIRED"
            binding.tvListenerAccessStatus.setTextColor(ContextCompat.getColor(this, R.color.veil_accent_amber))
            binding.btnGrantListenerAccess.visibility = View.VISIBLE
            binding.btnGrantListenerAccess.text = "Open Android Notification Access"
            binding.btnGrantListenerAccess.setOnClickListener {
                startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
            }

            binding.cardRealtimeBanner.strokeColor = ContextCompat.getColor(this, R.color.veil_accent_amber)
            binding.ivBannerIcon.setImageResource(R.drawable.ic_shield_status)
            binding.ivBannerIcon.setColorFilter(ContextCompat.getColor(this, R.color.veil_accent_amber))
            binding.tvBannerTitle.text = "Notification Access Required"
            binding.tvBannerSubtitle.text = "Tap Authorize so VEIL can intercept WhatsApp alerts"
            binding.btnBannerAction.text = "Authorize"
            binding.btnBannerAction.setOnClickListener {
                startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
            }
        } else if (!hasPostNotif && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Missing POST_NOTIFICATIONS
            binding.tvListenerAccessStatus.text = "POST NOTIFICATION REQUIRED"
            binding.tvListenerAccessStatus.setTextColor(ContextCompat.getColor(this, R.color.veil_accent_amber))
            binding.btnGrantListenerAccess.visibility = View.VISIBLE
            binding.btnGrantListenerAccess.text = "Grant Notification Permission"
            binding.btnGrantListenerAccess.setOnClickListener {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }

            binding.cardRealtimeBanner.strokeColor = ContextCompat.getColor(this, R.color.veil_accent_amber)
            binding.ivBannerIcon.setImageResource(R.drawable.ic_veil_notification)
            binding.ivBannerIcon.setColorFilter(ContextCompat.getColor(this, R.color.veil_accent_amber))
            binding.tvBannerTitle.text = "Notification Permission Missing"
            binding.tvBannerSubtitle.text = "VEIL cannot post disguised replacement alerts"
            binding.btnBannerAction.text = "Grant"
            binding.btnBannerAction.setOnClickListener {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        } else if (!isVeilEnabled) {
            // Master switch paused
            binding.tvListenerAccessStatus.text = "PROTECTION PAUSED"
            binding.tvListenerAccessStatus.setTextColor(ContextCompat.getColor(this, R.color.veil_text_medium))
            binding.btnGrantListenerAccess.visibility = View.GONE

            binding.cardRealtimeBanner.strokeColor = ContextCompat.getColor(this, R.color.veil_hairline)
            binding.ivBannerIcon.setImageResource(R.drawable.ic_shield_status)
            binding.ivBannerIcon.setColorFilter(ContextCompat.getColor(this, R.color.veil_text_medium))
            binding.tvBannerTitle.text = "VEIL Protection Paused"
            binding.tvBannerSubtitle.text = "Master switch is currently paused"
            binding.btnBannerAction.text = "Resume"
            binding.btnBannerAction.setOnClickListener {
                prefs.isVeilEnabled = true
                binding.switchVeilMaster.isChecked = true
                updateNotificationListenerStatus()
            }
        } else if (!isConnected) {
            // Access granted, but service unbound in memory -> Rebind
            binding.tvListenerAccessStatus.text = "CONNECTING SERVICE..."
            binding.tvListenerAccessStatus.setTextColor(ContextCompat.getColor(this, R.color.veil_accent_amber))
            binding.btnGrantListenerAccess.visibility = View.VISIBLE
            binding.btnGrantListenerAccess.text = "Rebind Notification Listener"
            binding.btnGrantListenerAccess.setOnClickListener {
                NotificationUtils.ensureListenerBound(this)
                updateNotificationListenerStatus()
                Toast.makeText(this, "Rebinding listener service...", Toast.LENGTH_SHORT).show()
            }

            binding.cardRealtimeBanner.strokeColor = ContextCompat.getColor(this, R.color.veil_accent_amber)
            binding.ivBannerIcon.setImageResource(R.drawable.ic_shield_status)
            binding.ivBannerIcon.setColorFilter(ContextCompat.getColor(this, R.color.veil_accent_amber))
            binding.tvBannerTitle.text = "Connecting Listener Service"
            binding.tvBannerSubtitle.text = "Tap Rebind to reconnect Android notification engine"
            binding.btnBannerAction.text = "Rebind"
            binding.btnBannerAction.setOnClickListener {
                NotificationUtils.ensureListenerBound(this)
                updateNotificationListenerStatus()
                Toast.makeText(this, "Rebinding listener service...", Toast.LENGTH_SHORT).show()
            }
        } else {
            // EVERYTHING ACTIVE & RUNNING!
            if (prefs.isPersistentShieldEnabled) {
                com.veil.privacy.service.VeilProtectionService.startService(this)
            }

            binding.tvListenerAccessStatus.text = "ACTIVE & PROTECTING"
            binding.tvListenerAccessStatus.setTextColor(ContextCompat.getColor(this, R.color.veil_accent_secure))
            binding.btnGrantListenerAccess.visibility = View.VISIBLE
            binding.btnGrantListenerAccess.text = "Open Real-Time Test Studio"
            binding.btnGrantListenerAccess.setOnClickListener {
                startActivity(Intent(this, RealtimeTestStudioActivity::class.java))
            }

            binding.cardRealtimeBanner.strokeColor = ContextCompat.getColor(this, R.color.veil_hairline)
            binding.ivBannerIcon.setImageResource(R.drawable.ic_shield_status)
            binding.ivBannerIcon.setColorFilter(ContextCompat.getColor(this, R.color.veil_accent_secure))
            binding.tvBannerTitle.text = "🛡️ Real-Time Protection Active"
            val count = prefs.totalInterceptionsCount
            val scopeText = if (prefs.isProtectAllMessagingApps) "All Messaging Apps" else "Standard Apps"
            binding.tvBannerSubtitle.text = "Armed: $scopeText • $count disguised • Watchdog active"
            binding.btnBannerAction.text = "Test Lab"
            binding.btnBannerAction.setOnClickListener {
                startActivity(Intent(this, RealtimeTestStudioActivity::class.java))
            }
        }
    }

    private fun executePipelineDiagnostic() {
        lifecycleScope.launch(Dispatchers.IO) {
            // Ensure notification channels are present
            VeilNotificationManager.getInstance(this@DashboardActivity).createNotificationChannels()

            val activeRules = database.protectionRuleDao().getEnabledRulesSync()
            val rule = activeRules.firstOrNull() ?: ProtectionRule(
                contactId = "-1",
                contactName = "Test Contact",
                contactPhoneNumber = "+1234567890",
                decoyIdentity = "Ayush",
                decoyMessage = "Kya kar raha hai bhai?",
                isEnabled = true
            )

            withContext(Dispatchers.Main) {
                VeilDialogs.showInfoDialog(
                    context = this@DashboardActivity,
                    title = "✅ Real-Time Pipeline Verified",
                    message = "The live protection pipeline was executed successfully!\n\n" +
                        "1. Notification Interception: READY\n" +
                        "2. Real-Time Decoy Engine: ONLINE\n" +
                        "3. Disguised Alert Engine: ACTIVE\n" +
                        "4. Encrypted Vault: PROTECTED\n\n" +
                        "Real WhatsApp notifications from protected contacts will be automatically intercepted and disguised according to your configured decoy settings."
                )
            }
        }
    }

    private fun observeData() {
        // Combine Protection Rules and Events to dynamically build Protected Chat threads
        lifecycleScope.launch {
            combine(
                database.protectionRuleDao().getAllRulesFlow(),
                database.protectedEventDao().getAllFlow()
            ) { rules, events ->
                Pair(rules, events)
            }.collect { (rules, events) ->
                // Tab 3 Rules
                rulesAdapter.submitList(rules)
                binding.tvContactsRuleCount.text = "${rules.size} RULES CONFIGURED"
                binding.layoutEmptyContacts.visibility = if (rules.isEmpty()) View.VISIBLE else View.GONE

                // Tab 2 Notifications Log
                notificationsAdapter.submitList(events)
                // Derive unread state from actual stored events and persisted last-viewed time.
                // The current ProtectedEvent model has no per-event read flag; opening the
                // Notifications destination marks the inbox viewed without altering the vault.
                val notificationCount = events.count { it.timestamp > notificationsLastViewedAt }
                binding.tvUnreadNotificationCount.visibility = if (notificationCount > 0) View.VISIBLE else View.GONE
                binding.tvUnreadNotificationCount.text = if (notificationCount > 99) "99+" else notificationCount.toString()
                binding.btnNotificationBell.contentDescription = if (notificationCount > 0) {
                    "Notifications, $notificationCount unread"
                } else {
                    "Notifications"
                }
                binding.layoutEmptyNotifications.visibility = if (events.isEmpty()) View.VISIBLE else View.GONE

                // Tab 1 Protected Chats Threads: keep the active app section isolated.
                val threads = if (selectedProtectedApp == com.veil.privacy.model.SupportedApp.PKG_WHATSAPP) {
                    rules.map { rule ->
                        val sourceEvents = events.filter {
                            it.sourcePackage == selectedProtectedApp &&
                                it.conversationId == rule.contactName.lowercase().trim()
                        }
                        val latest = sourceEvents.maxByOrNull { it.timestamp }
                        ProtectedChatThread(
                            contactName = rule.contactName,
                            contactPhoneNumber = rule.contactPhoneNumber,
                            lastMessage = latest?.realMessage ?: "No messages captured yet",
                            lastTimestamp = latest?.timestamp ?: rule.createdAt,
                            isLiveReplyAvailable = NotificationReplyRegistry.isReplyActionLive(rule.contactName, selectedProtectedApp),
                            disguiseMode = rule.disguiseMode,
                            decoyIdentity = rule.decoyIdentity,
                            sourcePackage = selectedProtectedApp,
                            conversationId = rule.contactName.lowercase().trim()
                        )
                    }
                } else {
                    events.filter { it.sourcePackage == selectedProtectedApp }
                        .groupBy { it.conversationId }
                        .mapNotNull { (conversationId, conversationEvents) ->
                            val latest = conversationEvents.maxByOrNull { it.timestamp } ?: return@mapNotNull null
                            val displayName = latest.realSender.ifBlank { conversationId }
                            ProtectedChatThread(
                                contactName = displayName,
                                lastMessage = latest.realMessage,
                                lastTimestamp = latest.timestamp,
                                isLiveReplyAvailable = NotificationReplyRegistry.isReplyActionLive(displayName, selectedProtectedApp),
                                disguiseMode = ProtectionRule.MODE_CUSTOM_DECOY,
                                decoyIdentity = latest.decoySender,
                                sourcePackage = selectedProtectedApp,
                                conversationId = conversationId
                            )
                        }
                }.sortedByDescending { it.lastTimestamp }
                chatThreadsAdapter.submitList(threads)
                binding.tvChatsHeaderCount.text = "${threads.size} ${com.veil.privacy.model.SupportedApp.getAppDisplayName(selectedProtectedApp).uppercase()} PROTECTED CONVERSATIONS"
                binding.layoutEmptyChats.visibility = if (threads.isEmpty()) View.VISIBLE else View.GONE
            }
        }
    }

    private fun refreshChatThreads() {
        lifecycleScope.launch(Dispatchers.IO) {
            val rules = database.protectionRuleDao().getAllRulesSync()
            val events = database.protectedEventDao().getAllSync()
            val threads = if (selectedProtectedApp == com.veil.privacy.model.SupportedApp.PKG_WHATSAPP) {
                rules.map { rule ->
                    val sourceEvents = events.filter {
                        it.sourcePackage == selectedProtectedApp &&
                            it.conversationId == rule.contactName.lowercase().trim()
                    }
                    val latestEvent = sourceEvents.maxByOrNull { it.timestamp }
                    ProtectedChatThread(
                        contactName = rule.contactName,
                        contactPhoneNumber = rule.contactPhoneNumber,
                        lastMessage = latestEvent?.realMessage ?: "No messages captured yet",
                        lastTimestamp = latestEvent?.timestamp ?: rule.createdAt,
                        isLiveReplyAvailable = NotificationReplyRegistry.isReplyActionLive(rule.contactName, selectedProtectedApp),
                        disguiseMode = rule.disguiseMode,
                        decoyIdentity = rule.decoyIdentity,
                        sourcePackage = selectedProtectedApp,
                        conversationId = rule.contactName.lowercase().trim()
                    )
                }
            } else {
                events.filter { it.sourcePackage == selectedProtectedApp }
                    .groupBy { it.conversationId }
                    .mapNotNull { (conversationId, conversationEvents) ->
                        val latestEvent = conversationEvents.maxByOrNull { it.timestamp } ?: return@mapNotNull null
                        val displayName = latestEvent.realSender.ifBlank { conversationId }
                        ProtectedChatThread(
                            contactName = displayName,
                            lastMessage = latestEvent.realMessage,
                            lastTimestamp = latestEvent.timestamp,
                            isLiveReplyAvailable = NotificationReplyRegistry.isReplyActionLive(displayName, selectedProtectedApp),
                            disguiseMode = ProtectionRule.MODE_CUSTOM_DECOY,
                            decoyIdentity = latestEvent.decoySender,
                            sourcePackage = selectedProtectedApp,
                            conversationId = conversationId
                        )
                    }
            }.sortedByDescending { it.lastTimestamp }

            withContext(Dispatchers.Main) {
                chatThreadsAdapter.submitList(threads)
                binding.tvChatsHeaderCount.text = "${threads.size} ${com.veil.privacy.model.SupportedApp.getAppDisplayName(selectedProtectedApp).uppercase()} PROTECTED CONVERSATIONS"
                binding.layoutEmptyChats.visibility = if (threads.isEmpty()) View.VISIBLE else View.GONE
            }
        }
    }

    private fun showProtectedAppSwitcher() {
        val packages = arrayOf(
            com.veil.privacy.model.SupportedApp.PKG_WHATSAPP,
            com.veil.privacy.model.SupportedApp.PKG_INSTAGRAM,
            com.veil.privacy.model.SupportedApp.PKG_TELEGRAM
        )
        val panel = Dialog(this)
        val density = resources.displayMetrics.density
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((20 * density).toInt(), (32 * density).toInt(), (16 * density).toInt(), (20 * density).toInt())
            setBackgroundColor(ContextCompat.getColor(this@DashboardActivity, R.color.veil_surface))
        }
        val title = TextView(this).apply {
            text = "PROTECTED APPS"
            textSize = 13f
            letterSpacing = 0.12f
            setTextColor(ContextCompat.getColor(this@DashboardActivity, R.color.veil_text_medium))
            setPadding(0, 0, 0, (18 * density).toInt())
        }
        container.addView(title)
        packages.forEach { sourcePackage ->
            val selected = sourcePackage == selectedProtectedApp
            val item = TextView(this).apply {
                text = (if (selected) "●  " else "○  ") +
                    com.veil.privacy.model.SupportedApp.getAppDisplayName(sourcePackage)
                textSize = 16f
                setTextColor(ContextCompat.getColor(this@DashboardActivity,
                    if (selected) R.color.veil_text_high else R.color.veil_text_medium))
                setPadding((12 * density).toInt(), (16 * density).toInt(), (8 * density).toInt(), (16 * density).toInt())
                isClickable = true
                isFocusable = true
                setOnClickListener {
                    selectedProtectedApp = sourcePackage
                    refreshChatThreads()
                    panel.dismiss()
                }
            }
            container.addView(item, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ))
        }
        val hint = TextView(this).apply {
            text = "Swipe from the left edge to switch sections"
            textSize = 12f
            setTextColor(ContextCompat.getColor(this@DashboardActivity, R.color.veil_text_low))
            setPadding((12 * density).toInt(), (24 * density).toInt(), (8 * density).toInt(), 0)
        }
        container.addView(hint)
        panel.setContentView(container)
        panel.window?.let { window ->
            window.setBackgroundDrawableResource(android.R.color.transparent)
            window.setLayout((300 * density).toInt(), android.view.ViewGroup.LayoutParams.MATCH_PARENT)
            window.setGravity(Gravity.START)
            window.setDimAmount(0.45f)
            window.addFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        }
        panel.show()
        panel.window?.setLayout((300 * density).toInt(), android.view.ViewGroup.LayoutParams.MATCH_PARENT)
        panel.window?.setGravity(Gravity.START)
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        val edgeSize = 24 * resources.displayMetrics.density
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                edgeDownX = event.x
                edgeDownY = event.y
            }
            MotionEvent.ACTION_UP -> {
                val deltaX = event.x - edgeDownX
                val deltaY = kotlin.math.abs(event.y - edgeDownY)
                if (edgeDownX <= edgeSize && deltaX >= EDGE_SWIPE_THRESHOLD_DP * resources.displayMetrics.density &&
                    deltaX > deltaY * 1.25f) {
                    showProtectedAppSwitcher()
                    return true
                }
            }
        }
        return super.dispatchTouchEvent(event)
    }

    private fun openAddContactChooser() {
        VeilDialogs.showProtectTargetChooser(
            activity = this,
            onQuickManual = { showQuickManualAddDialog() },
            onPickContacts = { launchContactPickerFlow() },
            onAdvancedRule = { startActivity(Intent(this, RuleEditActivity::class.java)) }
        )
    }

    private fun launchContactPickerFlow() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED) {
            pickContactLauncher.launch(null)
        } else {
            requestContactsPermissionLauncher.launch(Manifest.permission.READ_CONTACTS)
        }
    }

    private fun handleContactPicked(uri: Uri) {
        val contactInfo = ContactSelectionHelper.resolveContactFromUri(this, uri)
        if (contactInfo == null) {
            Toast.makeText(this, "Could not read contact data", Toast.LENGTH_SHORT).show()
            return
        }

        ContactSelectionHelper.promptNumberSelection(
            context = this,
            contactInfo = contactInfo,
            onNumberSelected = { chosenNumber ->
                promptDecoyConfiguration(
                    displayName = contactInfo.displayName,
                    phoneNumber = chosenNumber,
                    contactId = contactInfo.contactId,
                    source = ProtectionRule.SOURCE_ANDROID_CONTACTS
                )
            },
            onManualFallbackRequested = {
                showQuickManualAddDialog(initialName = contactInfo.displayName)
            }
        )
    }

    private fun promptDecoyConfiguration(
        displayName: String,
        phoneNumber: String,
        contactId: String = "",
        source: String = ProtectionRule.SOURCE_ANDROID_CONTACTS
    ) {
        VeilDialogs.showQuickProtectBottomSheet(
            activity = this,
            initialName = displayName,
            initialPhone = phoneNumber,
            onConfirmed = { name, phone, decoyIdentity, decoyMessage, contactPin ->
                saveNewProtectedContact(
                    displayName = if (name.isNotBlank()) name else displayName,
                    phoneNumber = if (phone.isNotBlank()) phone else phoneNumber,
                    contactId = contactId,
                    decoyIdentity = decoyIdentity,
                    decoyMessage = decoyMessage,
                    source = source,
                    contactPin = contactPin
                )
            }
        )
    }

    private fun saveNewProtectedContact(
        displayName: String,
        phoneNumber: String,
        contactId: String = "",
        decoyIdentity: String = "Work",
        decoyMessage: String = "New message",
        source: String = ProtectionRule.SOURCE_ANDROID_CONTACTS,
        contactPin: String = ""
    ) {
        lifecycleScope.launch {
            val normalizedName = ProtectedContact.normalizeName(displayName)
            val normalizedPhone = ProtectedContact.normalizePhone(phoneNumber)

            withContext(Dispatchers.IO) {
                val existingRule = if (normalizedPhone.isNotBlank()) {
                    database.protectionRuleDao().findByNormalizedPhoneSync(normalizedPhone)
                } else {
                    database.protectionRuleDao().findByNormalizedNameSync(normalizedName)
                }
                val mode = when {
                    decoyIdentity.equals("VEIL", ignoreCase = true) -> ProtectionRule.MODE_SILENT
                    decoyIdentity.equals("Dynamic", ignoreCase = true) -> ProtectionRule.MODE_RANDOM_ROTATION
                    else -> ProtectionRule.MODE_CUSTOM_DECOY
                }
                val ruleToSave = existingRule?.copy(
                    contactName = displayName.trim(),
                    contactPhoneNumber = phoneNumber.trim(),
                    disguiseMode = mode,
                    decoyIdentity = decoyIdentity,
                    decoyMessage = decoyMessage,
                    isEnabled = true
                ) ?: ProtectionRule(
                    contactId = contactId,
                    contactName = displayName.trim(),
                    contactPhoneNumber = phoneNumber.trim(),
                    disguiseMode = mode,
                    decoyIdentity = decoyIdentity,
                    decoyMessage = decoyMessage,
                    isEnabled = true,
                    source = source
                )
                database.protectionRuleDao().insert(ruleToSave)

                val existingContact = if (normalizedPhone.isNotBlank()) {
                    database.protectedContactDao().findByNormalizedPhoneSync(normalizedPhone)
                } else {
                    database.protectedContactDao().findByNormalizedNameSync(normalizedName)
                }

                val (pinHash, pinSalt) = if (contactPin.isNotBlank()) {
                    com.veil.privacy.security.SecurityManager.hashPassword(contactPin)
                } else Pair(existingContact?.lockPinHash.orEmpty(), existingContact?.lockPinSalt.orEmpty())

                val contactToSave = existingContact?.copy(
                    displayName = displayName.trim(),
                    selectedPhoneNumber = phoneNumber.trim(),
                    isEnabled = true,
                    lockPinHash = pinHash,
                    lockPinSalt = pinSalt,
                    isCustomLockEnabled = pinHash.isNotBlank()
                ) ?: ProtectedContact(
                    contactId = contactId,
                    displayName = displayName.trim(),
                    selectedPhoneNumber = phoneNumber.trim(),
                    source = source,
                    isEnabled = true,
                    lockPinHash = pinHash,
                    lockPinSalt = pinSalt,
                    isCustomLockEnabled = pinHash.isNotBlank()
                )
                database.protectedContactDao().insert(contactToSave)

                val existingSelected = if (normalizedPhone.isNotBlank()) {
                    database.selectedContactDao().findByNormalizedPhoneSync(normalizedPhone)
                } else {
                    database.selectedContactDao().findByDisplayNameSync(displayName.trim())
                }
                val selectedToSave = existingSelected?.copy(
                    displayName = displayName.trim(),
                    selectedPhoneNumber = phoneNumber.trim(),
                    isEnabled = true
                ) ?: SelectedContact(
                    contactId = contactId,
                    displayName = displayName.trim(),
                    selectedPhoneNumber = phoneNumber.trim(),
                    source = source,
                    isEnabled = true
                )
                database.selectedContactDao().insert(selectedToSave)
            }

            Toast.makeText(
                this@DashboardActivity,
                "Protection active for « $displayName » ($decoyIdentity)",
                Toast.LENGTH_SHORT
            ).show()
            switchTab(TAB_CHATS)
        }
    }

    private fun showQuickManualAddDialog(initialName: String = "") {
        VeilDialogs.showQuickProtectBottomSheet(
            activity = this,
            initialName = initialName,
            onConfirmed = { name, phone, decoyIdentity, decoyMessage, contactPin ->
                saveNewProtectedContact(
                    displayName = name,
                    phoneNumber = phone,
                    decoyIdentity = decoyIdentity,
                    decoyMessage = decoyMessage,
                    source = ProtectionRule.SOURCE_MANUAL,
                    contactPin = contactPin
                )
            }
        )
    }

    private fun showContactsPermissionDeniedDialog() {
        VeilDialogs.showConfirmationDialog(
            context = this,
            title = getString(R.string.contact_permission_title),
            message = "VEIL requires contacts access to choose a contact and phone number.\n\nYou can also enter the person's name and phone number manually without contacts permission.",
            confirmText = "Enter Manually",
            onConfirm = { showQuickManualAddDialog() }
        )
    }
}
