package com.veil.privacy.privacy

import com.veil.privacy.model.DecoyProfile
import com.veil.privacy.model.NotificationType
import com.veil.privacy.storage.VeilPrefs
import java.security.SecureRandom

/**
 * Dynamic Decoy Notification Engine.
 *
 * Responsibilities:
 * - Dynamically generates disguise sender identities and messages at notification arrival time.
 * - Randomly selects from available decoy profiles (Work, Calendar, Delivery, System, Casual)
 *   ensuring coherent matching between identity and message.
 * - Enforces anti-repetition: avoids repeating the immediately previous combination whenever alternatives exist.
 * - Supports separate, dedicated decoy combinations for:
 *     1. Regular chat messages
 *     2. Voice / video calls
 *     3. Media / attachment notifications (photos, audio notes, documents, files, stickers)
 *     4. Other notification types (system alerts, group status, tracking updates)
 * - Remains dynamic, unpredictable, customizable, and thread-safe.
 */
class DecoyGenerator {

    private val random = SecureRandom()

    // History tracking to guarantee anti-repetition across consecutive arrivals
    private var lastProfileKey: String? = null
    private var lastDecoySender: String? = null
    private var lastDecoyMessage: String? = null
    private var lastCombination: Pair<String, String>? = null
    private val lastCombinationPerType = mutableMapOf<NotificationType, Pair<String, String>>()

    companion object {
        const val FALLBACK_DECOY_SENDER = "Delivery Notice"
        const val FALLBACK_DECOY_MESSAGE = "Package update available"

        // Decoy Profiles
        val PROFILE_WORK = DecoyProfile(
            key = VeilPrefs.THEME_WORK,
            displayName = "Work & Productivity",
            iconEmoji = "🏢",
            messageSenders = VeilPrefs.WORK_DECOY_NAMES,
            messageTexts = VeilPrefs.WORK_DECOY_MESSAGES,
            callSenders = VeilPrefs.WORK_CALL_NAMES,
            callTexts = VeilPrefs.WORK_CALL_MESSAGES,
            mediaTexts = VeilPrefs.WORK_MEDIA_MESSAGES,
            otherTexts = listOf(
                "Project board status updated",
                "Repository branch merged",
                "Team channel announcement posted",
                "Sprint task assigned to you",
                "Staging build deploy completed"
            )
        )

        val PROFILE_CALENDAR = DecoyProfile(
            key = VeilPrefs.THEME_CALENDAR,
            displayName = "Calendar & Schedule",
            iconEmoji = "📅",
            messageSenders = VeilPrefs.CALENDAR_DECOY_NAMES,
            messageTexts = VeilPrefs.CALENDAR_DECOY_MESSAGES,
            callSenders = VeilPrefs.CALENDAR_CALL_NAMES,
            callTexts = VeilPrefs.CALENDAR_CALL_MESSAGES,
            mediaTexts = VeilPrefs.CALENDAR_MEDIA_MESSAGES,
            otherTexts = listOf(
                "Event schedule updated",
                "Daily agenda published",
                "Meeting room booked",
                "Calendar invite accepted",
                "Time zone adjustment alert"
            )
        )

        val PROFILE_DELIVERY = DecoyProfile(
            key = VeilPrefs.THEME_DELIVERY,
            displayName = "Delivery & Logistics",
            iconEmoji = "📦",
            messageSenders = VeilPrefs.DELIVERY_DECOY_NAMES,
            messageTexts = VeilPrefs.DELIVERY_DECOY_MESSAGES,
            callSenders = VeilPrefs.DELIVERY_CALL_NAMES,
            callTexts = VeilPrefs.DELIVERY_CALL_MESSAGES,
            mediaTexts = VeilPrefs.DELIVERY_MEDIA_MESSAGES,
            otherTexts = listOf(
                "Package tracking status refreshed",
                "Dispatch route updated",
                "Parcel estimated arrival updated",
                "Out for final delivery leg",
                "Locker pickup code generated"
            )
        )

        val PROFILE_SYSTEM = DecoyProfile(
            key = VeilPrefs.THEME_SYSTEM,
            displayName = "System & Utilities",
            iconEmoji = "⚙️",
            messageSenders = VeilPrefs.SYSTEM_DECOY_NAMES,
            messageTexts = VeilPrefs.SYSTEM_DECOY_MESSAGES,
            callSenders = VeilPrefs.SYSTEM_CALL_NAMES,
            callTexts = VeilPrefs.SYSTEM_CALL_MESSAGES,
            mediaTexts = VeilPrefs.SYSTEM_MEDIA_MESSAGES,
            otherTexts = listOf(
                "Device diagnostics completed",
                "Security definitions updated",
                "System maintenance finished",
                "Memory cache cleared",
                "Battery optimization complete"
            )
        )

        val PROFILE_CASUAL = DecoyProfile(
            key = VeilPrefs.THEME_CASUAL,
            displayName = "Casual & Social",
            iconEmoji = "💬",
            messageSenders = VeilPrefs.DEFAULT_DECOY_NAMES,
            messageTexts = VeilPrefs.DEFAULT_DECOY_MESSAGES,
            callSenders = listOf("Voice Call", "Audio Call", "Phone Line"),
            callTexts = listOf("Incoming voice call", "Incoming audio call", "Missed voice call"),
            mediaTexts = listOf("Photo received", "Audio file received", "Attachment received"),
            otherTexts = listOf("Status update", "New activity", "Notification alert")
        )

        val ALL_PROFILES = listOf(
            PROFILE_WORK,
            PROFILE_CALENDAR,
            PROFILE_DELIVERY,
            PROFILE_SYSTEM
        )

        @Volatile
        private var INSTANCE: DecoyGenerator? = null

        fun getInstance(): DecoyGenerator {
            return INSTANCE ?: synchronized(this) {
                val instance = DecoyGenerator()
                INSTANCE = instance
                instance
            }
        }
    }

    /**
     * Finds a registered profile by key, case-insensitively.
     */
    fun getProfile(key: String): DecoyProfile? {
        val clean = key.uppercase().trim()
        return when (clean) {
            VeilPrefs.THEME_WORK -> PROFILE_WORK
            VeilPrefs.THEME_CALENDAR -> PROFILE_CALENDAR
            VeilPrefs.THEME_DELIVERY -> PROFILE_DELIVERY
            VeilPrefs.THEME_SYSTEM -> PROFILE_SYSTEM
            VeilPrefs.THEME_CASUAL -> PROFILE_CASUAL
            else -> ALL_PROFILES.firstOrNull { it.key.equals(clean, ignoreCase = true) }
        }
    }

    /**
     * Primary entry point called at notification arrival time.
     *
     * Randomly selects an available decoy profile (Work, Calendar, Delivery, System)
     * and a corresponding message for the specified [NotificationType], guaranteeing
     * that the combination is not identical to the immediately preceding arrival.
     *
     * @param type The type of incoming notification (MESSAGE, CALL, MEDIA_ATTACHMENT, OTHER)
     * @param preferredProfile Optional profile constraint from user settings or protection rule
     * @param prefs Optional VeilPrefs for user customizations
     * @return Pair of (decoySender, decoyMessage)
     */
    @Synchronized
    fun generateDynamicDecoy(
        type: NotificationType = NotificationType.MESSAGE,
        preferredProfile: String? = null,
        prefs: VeilPrefs? = null
    ): Pair<String, String> {
        val targetProfile = selectProfile(preferredProfile, prefs)

        // Retrieve available senders and messages matching this profile and type
        val rawSenders = targetProfile.getSendersForType(type)
        val rawMessages = targetProfile.getMessagesForType(type)

        // If user customized decoy names/messages exist in prefs and theme is casual/custom, merge them
        val sendersPool = if (prefs != null && targetProfile.key == VeilPrefs.THEME_CASUAL) {
            val custom = prefs.getDecoyNames().filter { it.isNotBlank() }
            if (custom.isNotEmpty()) custom else rawSenders
        } else {
            rawSenders
        }

        val messagesPool = if (prefs != null && targetProfile.key == VeilPrefs.THEME_CASUAL) {
            val custom = prefs.getDecoyMessages().filter { it.isNotBlank() }
            if (custom.isNotEmpty()) custom else rawMessages
        } else {
            rawMessages
        }

        val cleanSenders = sendersPool.filter { it.isNotBlank() }
        val cleanMessages = messagesPool.filter { it.isNotBlank() }

        // Restore rotation state from persistent prefs if initial launch after restart
        if (prefs != null) {
            if (lastProfileKey == null) lastProfileKey = prefs.lastRotationProfileKey
            if (lastDecoySender == null) lastDecoySender = prefs.lastRotationSender
            if (lastDecoyMessage == null) lastDecoyMessage = prefs.lastRotationMessage
            if (lastCombination == null && lastDecoySender != null && lastDecoyMessage != null) {
                lastCombination = Pair(lastDecoySender!!, lastDecoyMessage!!)
            }
        }

        // Select sender and message with multi-tier anti-repetition
        val sender = selectIndependentSender(cleanSenders, type)
        val message = selectIndependentMessage(cleanMessages, type)

        // Additional safeguard: verify the exact combination against the immediately previous one
        var finalSender = sender
        var finalMessage = message

        val previousCombination = lastCombinationPerType[type] ?: lastCombination
        if (previousCombination != null &&
            previousCombination.first == finalSender &&
            previousCombination.second == finalMessage &&
            (cleanSenders.size > 1 || cleanMessages.size > 1)
        ) {
            if (cleanMessages.size > 1) {
                val alternateMessages = cleanMessages.filter { it != finalMessage }
                if (alternateMessages.isNotEmpty()) {
                    finalMessage = alternateMessages[random.nextInt(alternateMessages.size)]
                }
            } else if (cleanSenders.size > 1) {
                val alternateSenders = cleanSenders.filter { it != finalSender }
                if (alternateSenders.isNotEmpty()) {
                    finalSender = alternateSenders[random.nextInt(alternateSenders.size)]
                }
            }
        }

        // Record history for subsequent notification anti-repetition
        lastProfileKey = targetProfile.key
        lastDecoySender = finalSender
        lastDecoyMessage = finalMessage
        val combination = Pair(finalSender, finalMessage)
        lastCombination = combination
        lastCombinationPerType[type] = combination

        // Persist rotation state to VeilPrefs so it survives process deaths and app restarts
        prefs?.saveRotationState(targetProfile.key, finalSender, finalMessage)

        return combination
    }

    /**
     * Resolves the profile to draw from, avoiding immediately previous profile repetition
     * whenever alternatives are available.
     */
    private fun selectProfile(preferredProfile: String?, prefs: VeilPrefs?): DecoyProfile {
        if (!preferredProfile.isNullOrBlank()) {
            val specific = getProfile(preferredProfile)
            if (specific != null) return specific
        }

        // If user locked to a non-dynamic theme in prefs, respect their choice
        val activeTheme = prefs?.decoyPresetTheme ?: VeilPrefs.THEME_DYNAMIC
        if (activeTheme != VeilPrefs.THEME_DYNAMIC) {
            val themeProfile = getProfile(activeTheme)
            if (themeProfile != null) return themeProfile
        }

        // Available profiles pool (Work, Calendar, Delivery, System)
        val enabledKeys = prefs?.getEnabledDecoyProfiles() ?: setOf(
            VeilPrefs.THEME_WORK,
            VeilPrefs.THEME_CALENDAR,
            VeilPrefs.THEME_DELIVERY,
            VeilPrefs.THEME_SYSTEM
        )

        val availableProfiles = ALL_PROFILES.filter { enabledKeys.contains(it.key) }
        val candidateProfiles = if (availableProfiles.isEmpty()) ALL_PROFILES else availableProfiles

        // Avoid repeating the immediately previous profile when multiple exist
        val filtered = if (candidateProfiles.size > 1 && lastProfileKey != null) {
            candidateProfiles.filter { it.key != lastProfileKey }
        } else {
            candidateProfiles
        }

        val pool = if (filtered.isNotEmpty()) filtered else candidateProfiles
        return pool[random.nextInt(pool.size)]
    }

    /**
     * Legacy & custom-list decoy generator.
     */
    @Synchronized
    fun generateDecoy(
        availableSenders: List<String>,
        availableMessages: List<String>
    ): Pair<String, String> {
        val cleanSenders = availableSenders.filter { it.isNotBlank() }
        val cleanMessages = availableMessages.filter { it.isNotBlank() }

        val sender = selectIndependentSender(cleanSenders, NotificationType.MESSAGE)
        val message = selectIndependentMessage(cleanMessages, NotificationType.MESSAGE)

        var finalSender = sender
        var finalMessage = message

        if (lastCombination != null &&
            lastCombination!!.first == finalSender &&
            lastCombination!!.second == finalMessage &&
            (cleanSenders.size > 1 || cleanMessages.size > 1)
        ) {
            if (cleanMessages.size > 1) {
                val alternateMessages = cleanMessages.filter { it != finalMessage }
                if (alternateMessages.isNotEmpty()) {
                    finalMessage = alternateMessages[random.nextInt(alternateMessages.size)]
                }
            } else if (cleanSenders.size > 1) {
                val alternateSenders = cleanSenders.filter { it != finalSender }
                if (alternateSenders.isNotEmpty()) {
                    finalSender = alternateSenders[random.nextInt(alternateSenders.size)]
                }
            }
        }

        lastDecoySender = finalSender
        lastDecoyMessage = finalMessage
        val combination = Pair(finalSender, finalMessage)
        lastCombination = combination
        lastCombinationPerType[NotificationType.MESSAGE] = combination

        return combination
    }

    /**
     * Convenience generator using VeilPrefs (dispatches to arrival-time dynamic engine).
     */
    fun generateDecoy(prefs: VeilPrefs): Pair<String, String> {
        return generateDynamicDecoy(NotificationType.MESSAGE, null, prefs)
    }

    private fun selectIndependentSender(candidates: List<String>, type: NotificationType): String {
        if (candidates.isEmpty()) return FALLBACK_DECOY_SENDER
        if (candidates.size == 1) return candidates.first()

        val lastForType = lastCombinationPerType[type]?.first ?: lastDecoySender

        val available = if (lastForType != null && candidates.size > 1) {
            candidates.filter { it != lastForType }
        } else {
            candidates
        }

        val pool = if (available.isNotEmpty()) available else candidates
        return pool[random.nextInt(pool.size)]
    }

    private fun selectIndependentMessage(candidates: List<String>, type: NotificationType): String {
        if (candidates.isEmpty()) return FALLBACK_DECOY_MESSAGE
        if (candidates.size == 1) return candidates.first()

        val lastForType = lastCombinationPerType[type]?.second ?: lastDecoyMessage

        val available = if (lastForType != null && candidates.size > 1) {
            candidates.filter { it != lastForType }
        } else {
            candidates
        }

        val pool = if (available.isNotEmpty()) available else candidates
        return pool[random.nextInt(pool.size)]
    }

    /**
     * Clears generator state (useful for tests and fresh app launches).
     */
    @Synchronized
    fun resetHistory() {
        lastProfileKey = null
        lastDecoySender = null
        lastDecoyMessage = null
        lastCombination = null
        lastCombinationPerType.clear()
    }
}
