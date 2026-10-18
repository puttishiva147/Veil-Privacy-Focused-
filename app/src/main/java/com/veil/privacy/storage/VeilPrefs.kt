package com.veil.privacy.storage

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray

/**
 * Thread-safe local preferences manager for VEIL settings,
 * switches, and customizable decoy identity dictionaries.
 */
class VeilPrefs(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    companion object {
        private const val PREFS_NAME = "veil_privacy_prefs"

        private const val KEY_VEIL_ENABLED = "key_veil_enabled"
        private const val KEY_HIDE_ORIGINAL = "key_hide_original"
        private const val KEY_PROTECTED_CALLS = "key_protected_calls"
        private const val KEY_PROTECT_ALL_SENDERS = "key_protect_all_senders"
        private const val KEY_LEARN_MODE = "key_learn_mode"
        private const val KEY_LAST_LEARNED_SENDER = "key_last_learned_sender"

        private const val KEY_DECOY_NAMES = "key_decoy_names"
        private const val KEY_DECOY_MESSAGES = "key_decoy_messages"

        private const val KEY_APP_LOCK_ENABLED = "key_app_lock_enabled"
        private const val KEY_APP_LOCK_PIN = "key_app_lock_pin"
        private const val KEY_BIOMETRIC_ENABLED = "key_biometric_enabled"
        private const val KEY_SECURITY_SETUP_COMPLETED = "key_security_setup_completed"
        private const val KEY_PASSWORD_HASH = "key_password_hash"
        private const val KEY_PASSWORD_SALT = "key_password_salt"
        private const val KEY_MASTER_PIN_LENGTH = "key_master_pin_length"
        private const val KEY_PRIVACY_NOTIF_TITLE = "key_privacy_notif_title"
        private const val KEY_PRIVACY_NOTIF_BODY = "key_privacy_notif_body"

        private const val KEY_PERSISTENT_SHIELD_ENABLED = "key_persistent_shield_enabled"
        private const val KEY_PROTECT_ALL_MESSAGING_APPS = "key_protect_all_messaging_apps"
        private const val KEY_PROTECT_ANY_APP = "key_protect_any_app"
        private const val KEY_ENABLED_APP_PACKAGES = "key_enabled_app_packages"
        private const val KEY_DECOY_PRESET_THEME = "key_decoy_preset_theme"
        private const val KEY_ENABLED_DECOY_PROFILES = "key_enabled_decoy_profiles"
        private const val KEY_TOTAL_INTERCEPTIONS = "key_total_interceptions"
        private const val KEY_LAST_INTERCEPTION_TIME = "key_last_interception_time"

        // Protection notification display modes
        const val NOTIF_DISPLAY_ALWAYS_SHOW = "ALWAYS_SHOW"
        const val NOTIF_DISPLAY_ALWAYS_HIDE = "ALWAYS_HIDE"
        const val NOTIF_DISPLAY_SHOW_WHEN_PAUSED = "SHOW_WHEN_PAUSED"
        private const val KEY_PROTECTION_NOTIFICATION_DISPLAY = "key_protection_notif_display"

        // Preset decoy themes
        const val THEME_DYNAMIC = "DYNAMIC"
        const val THEME_WORK = "WORK"
        const val THEME_CALENDAR = "CALENDAR"
        const val THEME_DELIVERY = "DELIVERY"
        const val THEME_SYSTEM = "SYSTEM"
        const val THEME_CASUAL = "CASUAL"

        // Dynamic disguise & persistent rotation state
        const val KEY_DYNAMIC_DISGUISE_ENABLED = "key_dynamic_disguise_enabled"
        const val KEY_ROTATION_LAST_SENDER = "key_rotation_last_sender"
        const val KEY_ROTATION_LAST_MESSAGE = "key_rotation_last_message"
        const val KEY_ROTATION_LAST_PROFILE = "key_rotation_last_profile"
        const val KEY_ROTATION_COUNTER = "key_rotation_counter"

        val DELIVERY_DECOY_NAMES = listOf(
            "Amazon Logistics",
            "FedEx Express",
            "BlueDart Courier",
            "Delivery Partner",
            "Courier Hub",
            "DHL Express",
            "Parcel Service",
            "Shipment Dispatch",
            "UPS Ground"
        )

        val DELIVERY_DECOY_MESSAGES = listOf(
            "Your shipment is out for delivery",
            "Package arriving by 5:00 PM today",
            "Delivery completed at doorstep",
            "Shipment update for order #4892",
            "Courier driver is approaching location",
            "Delivery rescheduled for tomorrow",
            "OTP required for parcel handover",
            "Order package sorted at regional hub",
            "Delivery attempted: signature required"
        )

        val WORK_DECOY_NAMES = listOf(
            "Slack",
            "Project Lead",
            "Team Sync",
            "Jira Notification",
            "Meeting Assistant",
            "Product Manager",
            "DevOps Alert",
            "Sprint Coordinator",
            "Engineering Lead"
        )

        val WORK_DECOY_MESSAGES = listOf(
            "Meeting starting in 10 minutes",
            "PR review requested on repository",
            "Standup sync notes posted",
            "Please review the updated design doc",
            "Task assigned to you for current sprint",
            "Can you jump on a quick huddle?",
            "Deployment completed for staging",
            "Action items from today's sprint retro",
            "Quarterly report draft ready for review",
            "Merged pull request into main"
        )

        val CALENDAR_DECOY_NAMES = listOf(
            "Google Calendar",
            "Calendar Event",
            "Schedule Assistant",
            "Meeting Reminder",
            "Outlook Calendar",
            "Agenda Bot",
            "Daily Planner",
            "Calendar Sync"
        )

        val CALENDAR_DECOY_MESSAGES = listOf(
            "Reminder: Team Sync at 3:00 PM",
            "Event updated: Product roadmap discussion",
            "Meeting with engineering team in 15 mins",
            "Calendar invite accepted for tomorrow",
            "All-hands meeting starting shortly",
            "Event rescheduled to 4:30 PM",
            "10-minute alert: Strategy briefing",
            "Upcoming appointment on your schedule",
            "Reminder: Review sprint backlog today",
            "New calendar invitation received"
        )

        val SYSTEM_DECOY_NAMES = listOf(
            "Android System",
            "Security Core",
            "Cloud Storage",
            "Device Optimization",
            "Battery Manager",
            "System Update",
            "Storage Care",
            "Network Security"
        )

        val SYSTEM_DECOY_MESSAGES = listOf(
            "Background synchronization finished",
            "Security integrity verified: Protected",
            "Storage optimization active",
            "System patch status: Up to date",
            "Cloud backup synced successfully",
            "App optimization cycle finished",
            "Security definition update applied",
            "Network diagnostics healthy",
            "Battery health check completed",
            "Device cache cleared successfully"
        )

        // Separate Call Decoy Collections (Voice & Video Calls)
        val WORK_CALL_NAMES = listOf(
            "Work Conference Bridge",
            "Team Sync Dial-in",
            "Project Standup Call",
            "Slack Huddle",
            "Engineering Voice Room",
            "Product Sync Audio"
        )
        val WORK_CALL_MESSAGES = listOf(
            "Incoming conference bridge call",
            "Team huddle voice call",
            "Scheduled standup dial-in",
            "Sprint planning audio call",
            "Work voice call in progress"
        )

        val CALENDAR_CALL_NAMES = listOf(
            "Calendar Meeting Audio",
            "Scheduled Call Dial-in",
            "Conference Call Bridge",
            "Calendar Voice Reminder",
            "Meeting Call Bot"
        )
        val CALENDAR_CALL_MESSAGES = listOf(
            "Incoming scheduled meeting audio",
            "Calendar conference bridge call",
            "Automated meeting reminder call",
            "Scheduled appointment voice call"
        )

        val DELIVERY_CALL_NAMES = listOf(
            "Delivery Partner",
            "Courier Dispatch",
            "Amazon Driver",
            "Logistics Delivery Call",
            "Courier Support"
        )
        val DELIVERY_CALL_MESSAGES = listOf(
            "Delivery driver is calling you",
            "Incoming courier verification call",
            "Logistics delivery driver calling",
            "Courier handover audio call"
        )

        val SYSTEM_CALL_NAMES = listOf(
            "Automated System Line",
            "Voice Verification Service",
            "Security Audio Alert",
            "System Hotline"
        )
        val SYSTEM_CALL_MESSAGES = listOf(
            "Automated voice alert call",
            "System verification line calling",
            "Security callback service audio"
        )

        // Separate Media / Attachment Decoy Collections
        val WORK_MEDIA_MESSAGES = listOf(
            "Shared presentation deck (PDF)",
            "New architectural diagram attached",
            "Meeting whiteboard export uploaded",
            "Project spreadsheet attachment shared"
        )
        val CALENDAR_MEDIA_MESSAGES = listOf(
            "Meeting agenda file attached",
            "Calendar summary export shared",
            "Session slides attached to event"
        )
        val DELIVERY_MEDIA_MESSAGES = listOf(
            "Delivery proof photo attached",
            "Package barcode scan uploaded",
            "Shipping label image received"
        )
        val SYSTEM_MEDIA_MESSAGES = listOf(
            "System diagnostic report attached",
            "Encrypted backup archive saved",
            "Security verification log attached"
        )

        // In-memory unlocked session state
        @Volatile
        var isSessionUnlocked: Boolean = false

        // In-memory set of individually unlocked contact identifiers (names or phone numbers)
        private val unlockedContacts = mutableSetOf<String>()

        fun isContactUnlocked(identifier: String): Boolean {
            val key = identifier.lowercase().trim()
            return key.isNotBlank() && synchronized(unlockedContacts) { unlockedContacts.contains(key) }
        }

        fun unlockContact(identifier: String) {
            val key = identifier.lowercase().trim()
            if (key.isNotBlank()) {
                synchronized(unlockedContacts) {
                    unlockedContacts.add(key)
                }
            }
        }

        fun lockContact(identifier: String) {
            val key = identifier.lowercase().trim()
            synchronized(unlockedContacts) {
                unlockedContacts.remove(key)
            }
        }

        fun lockAllContacts() {
            synchronized(unlockedContacts) {
                unlockedContacts.clear()
            }
        }

        val DEFAULT_DECOY_NAMES = listOf(
            "Ayush",
            "Kiran",
            "Rahul",
            "Rohan",
            "Amit",
            "Priya",
            "Vikas",
            "Sneha",
            "Neha",
            "Rohit",
            "Ankit",
            "Pooja"
        )

        val DEFAULT_DECOY_MESSAGES = listOf(
            "Kya kar raha hai bhai?",
            "Bhai kaha hai?",
            "Kal milte hai?",
            "Kaha ho?",
            "Kya scene hai?",
            "Call me when free",
            "Okay sure",
            "Sounds good!",
            "Ha bhai",
            "Will check and tell you",
            "Got it thanks",
            "Let me know once done"
        )

        @Volatile
        private var INSTANCE: VeilPrefs? = null

        fun getInstance(context: Context): VeilPrefs {
            return INSTANCE ?: synchronized(this) {
                val instance = VeilPrefs(context)
                INSTANCE = instance
                instance
            }
        }

        fun resetInstance() {
            synchronized(this) {
                INSTANCE = null
            }
        }
    }

    var isVeilEnabled: Boolean
        get() = prefs.getBoolean(KEY_VEIL_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_VEIL_ENABLED, value).apply()

    var isPersistentShieldEnabled: Boolean
        get() = prefs.getBoolean(KEY_PERSISTENT_SHIELD_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_PERSISTENT_SHIELD_ENABLED, value).apply()

    var protectionNotificationDisplay: String
        get() = prefs.getString(KEY_PROTECTION_NOTIFICATION_DISPLAY, NOTIF_DISPLAY_ALWAYS_SHOW) ?: NOTIF_DISPLAY_ALWAYS_SHOW
        set(value) {
            prefs.edit().putString(KEY_PROTECTION_NOTIFICATION_DISPLAY, value).commit()
        }

    var isProtectAllMessagingApps: Boolean
        get() = prefs.getBoolean(KEY_PROTECT_ALL_MESSAGING_APPS, true)
        set(value) = prefs.edit().putBoolean(KEY_PROTECT_ALL_MESSAGING_APPS, value).apply()

    var isProtectAnyApp: Boolean
        get() = prefs.getBoolean(KEY_PROTECT_ANY_APP, false)
        set(value) = prefs.edit().putBoolean(KEY_PROTECT_ANY_APP, value).apply()

    var decoyPresetTheme: String
        get() = prefs.getString(KEY_DECOY_PRESET_THEME, THEME_DYNAMIC) ?: THEME_DYNAMIC
        set(value) = prefs.edit().putString(KEY_DECOY_PRESET_THEME, value).apply()

    var isDynamicDisguiseEnabled: Boolean
        get() = prefs.getBoolean(KEY_DYNAMIC_DISGUISE_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_DYNAMIC_DISGUISE_ENABLED, value).apply()

    var lastRotationSender: String?
        get() = prefs.getString(KEY_ROTATION_LAST_SENDER, null)
        set(value) = prefs.edit().putString(KEY_ROTATION_LAST_SENDER, value).apply()

    var lastRotationMessage: String?
        get() = prefs.getString(KEY_ROTATION_LAST_MESSAGE, null)
        set(value) = prefs.edit().putString(KEY_ROTATION_LAST_MESSAGE, value).apply()

    var lastRotationProfileKey: String?
        get() = prefs.getString(KEY_ROTATION_LAST_PROFILE, null)
        set(value) = prefs.edit().putString(KEY_ROTATION_LAST_PROFILE, value).apply()

    var rotationCounter: Int
        get() = prefs.getInt(KEY_ROTATION_COUNTER, 0)
        set(value) = prefs.edit().putInt(KEY_ROTATION_COUNTER, value).apply()

    fun saveRotationState(profileKey: String, sender: String, message: String) {
        val nextCount = rotationCounter + 1
        prefs.edit()
            .putString(KEY_ROTATION_LAST_PROFILE, profileKey)
            .putString(KEY_ROTATION_LAST_SENDER, sender)
            .putString(KEY_ROTATION_LAST_MESSAGE, message)
            .putInt(KEY_ROTATION_COUNTER, nextCount)
            .apply()
    }

    fun getEnabledDecoyProfiles(): Set<String> {
        val stringSet = prefs.getStringSet(KEY_ENABLED_DECOY_PROFILES, null)
        return if (stringSet.isNullOrEmpty()) {
            setOf(THEME_WORK, THEME_CALENDAR, THEME_DELIVERY, THEME_SYSTEM)
        } else {
            stringSet
        }
    }

    fun setEnabledDecoyProfiles(profiles: Set<String>) {
        prefs.edit().putStringSet(KEY_ENABLED_DECOY_PROFILES, profiles).apply()
    }

    var totalInterceptionsCount: Long
        get() = prefs.getLong(KEY_TOTAL_INTERCEPTIONS, 0L)
        set(value) = prefs.edit().putLong(KEY_TOTAL_INTERCEPTIONS, value).apply()

    fun incrementInterceptionsCount() {
        val next = totalInterceptionsCount + 1
        prefs.edit()
            .putLong(KEY_TOTAL_INTERCEPTIONS, next)
            .putLong(KEY_LAST_INTERCEPTION_TIME, System.currentTimeMillis())
            .apply()
    }

    var lastInterceptionTimestamp: Long
        get() = prefs.getLong(KEY_LAST_INTERCEPTION_TIME, 0L)
        set(value) = prefs.edit().putLong(KEY_LAST_INTERCEPTION_TIME, value).apply()

    var isHideOriginalEnabled: Boolean
        get() = prefs.getBoolean(KEY_HIDE_ORIGINAL, true)
        set(value) = prefs.edit().putBoolean(KEY_HIDE_ORIGINAL, value).apply()

    var isProtectedCallsEnabled: Boolean
        get() = prefs.getBoolean(KEY_PROTECTED_CALLS, true)
        set(value) = prefs.edit().putBoolean(KEY_PROTECTED_CALLS, value).apply()

    var isProtectAllSenders: Boolean
        get() = prefs.getBoolean(KEY_PROTECT_ALL_SENDERS, false)
        set(value) = prefs.edit().putBoolean(KEY_PROTECT_ALL_SENDERS, value).apply()

    var isLearnModeActive: Boolean
        get() = prefs.getBoolean(KEY_LEARN_MODE, false)
        set(value) = prefs.edit().putBoolean(KEY_LEARN_MODE, value).apply()

    var lastLearnedSender: String?
        get() = prefs.getString(KEY_LAST_LEARNED_SENDER, null)
        set(value) = prefs.edit().putString(KEY_LAST_LEARNED_SENDER, value).apply()

    var isAppLockEnabled: Boolean
        get() = prefs.getBoolean(KEY_APP_LOCK_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_APP_LOCK_ENABLED, value).apply()

    var isBiometricEnabled: Boolean
        get() = prefs.getBoolean(KEY_BIOMETRIC_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_BIOMETRIC_ENABLED, value).apply()

    var isSecuritySetupCompleted: Boolean
        get() = prefs.getBoolean(KEY_SECURITY_SETUP_COMPLETED, false)
        set(value) = prefs.edit().putBoolean(KEY_SECURITY_SETUP_COMPLETED, value).apply()

    val passwordHash: String?
        get() = prefs.getString(KEY_PASSWORD_HASH, null)

    val passwordSalt: String?
        get() = prefs.getString(KEY_PASSWORD_SALT, null)

    var masterPinLength: Int
        get() = prefs.getInt(KEY_MASTER_PIN_LENGTH, 0)
        set(value) { if (value in 4..8) prefs.edit().putInt(KEY_MASTER_PIN_LENGTH, value).apply() }

    fun hasPasswordConfigured(): Boolean {
        return !passwordHash.isNullOrBlank() && !passwordSalt.isNullOrBlank()
    }

    fun savePasswordCredentials(hash: String, salt: String) {
        prefs.edit()
            .putString(KEY_PASSWORD_HASH, hash)
            .putString(KEY_PASSWORD_SALT, salt)
            .remove(KEY_APP_LOCK_PIN)
            .putBoolean(KEY_SECURITY_SETUP_COMPLETED, true)
            .putBoolean(KEY_APP_LOCK_ENABLED, true)
            .apply()
    }

    fun clearPasswordCredentials() {
        prefs.edit()
            .remove(KEY_PASSWORD_HASH)
            .remove(KEY_PASSWORD_SALT)
            .remove(KEY_MASTER_PIN_LENGTH)
            .remove(KEY_APP_LOCK_PIN)
            .putBoolean(KEY_SECURITY_SETUP_COMPLETED, false)
            .apply()
    }

    fun performSecurityMigrationIfNeeded() {
        // If securitySetupCompleted is not true, ensure no legacy default PIN exists
        if (!prefs.getBoolean(KEY_SECURITY_SETUP_COMPLETED, false)) {
            prefs.edit()
                .remove(KEY_APP_LOCK_PIN)
                .remove(KEY_PASSWORD_HASH)
                .remove(KEY_PASSWORD_SALT)
                .putBoolean(KEY_SECURITY_SETUP_COMPLETED, false)
                .apply()
            isSessionUnlocked = false
        } else if (prefs.getString(KEY_APP_LOCK_PIN, null) == "1234") {
            // Force reset any legacy hardcoded 1234 PIN
            prefs.edit()
                .remove(KEY_APP_LOCK_PIN)
                .remove(KEY_PASSWORD_HASH)
                .remove(KEY_PASSWORD_SALT)
                .putBoolean(KEY_SECURITY_SETUP_COMPLETED, false)
                .apply()
            isSessionUnlocked = false
        }
    }

    var privacyNotificationTitle: String
        get() = prefs.getString(KEY_PRIVACY_NOTIF_TITLE, "🔒 VEIL — Protected message") ?: "🔒 VEIL — Protected message"
        set(value) = prefs.edit().putString(KEY_PRIVACY_NOTIF_TITLE, value).apply()

    var privacyNotificationBody: String
        get() = prefs.getString(KEY_PRIVACY_NOTIF_BODY, "New message in Protected Chats") ?: "New message in Protected Chats"
        set(value) = prefs.edit().putString(KEY_PRIVACY_NOTIF_BODY, value).apply()

    fun getThemeDefaultNames(theme: String = decoyPresetTheme): List<String> {
        return when (theme) {
            THEME_DELIVERY -> DELIVERY_DECOY_NAMES
            THEME_WORK -> WORK_DECOY_NAMES
            THEME_CALENDAR -> CALENDAR_DECOY_NAMES
            THEME_SYSTEM -> SYSTEM_DECOY_NAMES
            THEME_DYNAMIC -> WORK_DECOY_NAMES + CALENDAR_DECOY_NAMES + DELIVERY_DECOY_NAMES + SYSTEM_DECOY_NAMES
            else -> DEFAULT_DECOY_NAMES
        }
    }

    fun getThemeDefaultMessages(theme: String = decoyPresetTheme): List<String> {
        return when (theme) {
            THEME_DELIVERY -> DELIVERY_DECOY_MESSAGES
            THEME_WORK -> WORK_DECOY_MESSAGES
            THEME_CALENDAR -> CALENDAR_DECOY_MESSAGES
            THEME_SYSTEM -> SYSTEM_DECOY_MESSAGES
            THEME_DYNAMIC -> WORK_DECOY_MESSAGES + CALENDAR_DECOY_MESSAGES + DELIVERY_DECOY_MESSAGES + SYSTEM_DECOY_MESSAGES
            else -> DEFAULT_DECOY_MESSAGES
        }
    }

    fun getThemeDefaultCallNames(theme: String = decoyPresetTheme): List<String> {
        return when (theme) {
            THEME_DELIVERY -> DELIVERY_CALL_NAMES
            THEME_WORK -> WORK_CALL_NAMES
            THEME_CALENDAR -> CALENDAR_CALL_NAMES
            THEME_SYSTEM -> SYSTEM_CALL_NAMES
            THEME_DYNAMIC -> WORK_CALL_NAMES + CALENDAR_CALL_NAMES + DELIVERY_CALL_NAMES + SYSTEM_CALL_NAMES
            else -> WORK_CALL_NAMES
        }
    }

    fun getThemeDefaultCallMessages(theme: String = decoyPresetTheme): List<String> {
        return when (theme) {
            THEME_DELIVERY -> DELIVERY_CALL_MESSAGES
            THEME_WORK -> WORK_CALL_MESSAGES
            THEME_CALENDAR -> CALENDAR_CALL_MESSAGES
            THEME_SYSTEM -> SYSTEM_CALL_MESSAGES
            THEME_DYNAMIC -> WORK_CALL_MESSAGES + CALENDAR_CALL_MESSAGES + DELIVERY_CALL_MESSAGES + SYSTEM_CALL_MESSAGES
            else -> WORK_CALL_MESSAGES
        }
    }

    fun getThemeDefaultMediaMessages(theme: String = decoyPresetTheme): List<String> {
        return when (theme) {
            THEME_DELIVERY -> DELIVERY_MEDIA_MESSAGES
            THEME_WORK -> WORK_MEDIA_MESSAGES
            THEME_CALENDAR -> CALENDAR_MEDIA_MESSAGES
            THEME_SYSTEM -> SYSTEM_MEDIA_MESSAGES
            THEME_DYNAMIC -> WORK_MEDIA_MESSAGES + CALENDAR_MEDIA_MESSAGES + DELIVERY_MEDIA_MESSAGES + SYSTEM_MEDIA_MESSAGES
            else -> WORK_MEDIA_MESSAGES
        }
    }

    fun getDecoyNames(): List<String> {
        val defaultList = getThemeDefaultNames()
        val jsonString = prefs.getString(KEY_DECOY_NAMES, null) ?: return defaultList
        return try {
            val array = JSONArray(jsonString)
            val list = mutableListOf<String>()
            for (i in 0 until array.length()) {
                val item = array.getString(i).trim()
                if (item.isNotEmpty() && !list.contains(item)) {
                    list.add(item)
                }
            }
            if (list.isEmpty()) defaultList else list
        } catch (e: Exception) {
            defaultList
        }
    }

    val defaultDecoySender: String
        get() = getDecoyNames().firstOrNull() ?: "Notification"

    val defaultDecoyMessage: String
        get() = getDecoyMessages().firstOrNull() ?: "New message received"

    fun setDecoyNames(names: List<String>) {
        val cleanList = names.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        val array = JSONArray(cleanList)
        prefs.edit().putString(KEY_DECOY_NAMES, array.toString()).apply()
    }

    fun addDecoyName(name: String): Boolean {
        val clean = name.trim()
        if (clean.isEmpty()) return false
        val current = getDecoyNames().toMutableList()
        if (!current.contains(clean)) {
            current.add(clean)
            setDecoyNames(current)
            return true
        }
        return false
    }

    fun removeDecoyName(name: String): Boolean {
        val current = getDecoyNames().toMutableList()
        val removed = current.remove(name.trim())
        if (removed) {
            setDecoyNames(current)
        }
        return removed
    }

    fun resetDecoyNames() {
        setDecoyNames(getThemeDefaultNames())
    }

    fun getDecoyMessages(): List<String> {
        val defaultList = getThemeDefaultMessages()
        val jsonString = prefs.getString(KEY_DECOY_MESSAGES, null) ?: return defaultList
        return try {
            val array = JSONArray(jsonString)
            val list = mutableListOf<String>()
            for (i in 0 until array.length()) {
                val item = array.getString(i).trim()
                if (item.isNotEmpty() && !list.contains(item)) {
                    list.add(item)
                }
            }
            if (list.isEmpty()) defaultList else list
        } catch (e: Exception) {
            defaultList
        }
    }

    fun setDecoyMessages(messages: List<String>) {
        val cleanList = messages.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        val array = JSONArray(cleanList)
        prefs.edit().putString(KEY_DECOY_MESSAGES, array.toString()).apply()
    }

    fun addDecoyMessage(message: String): Boolean {
        val clean = message.trim()
        if (clean.isEmpty()) return false
        val current = getDecoyMessages().toMutableList()
        if (!current.contains(clean)) {
            current.add(clean)
            setDecoyMessages(current)
            return true
        }
        return false
    }

    fun removeDecoyMessage(message: String): Boolean {
        val current = getDecoyMessages().toMutableList()
        val removed = current.remove(message.trim())
        if (removed) {
            setDecoyMessages(current)
        }
        return removed
    }

    fun resetDecoyMessages() {
        setDecoyMessages(getThemeDefaultMessages())
    }
}
