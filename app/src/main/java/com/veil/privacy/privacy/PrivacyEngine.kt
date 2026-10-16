package com.veil.privacy.privacy

import com.veil.privacy.model.ParsedNotificationInfo
import com.veil.privacy.model.ProtectedContact
import com.veil.privacy.model.ProtectedEvent
import com.veil.privacy.storage.VeilPrefs

/**
 * Core decision and transformation engine of VEIL.
 *
 * Responsibilities:
 * - Match incoming WhatsApp sender against user's protected contacts list.
 * - Perform robust multi-attribute matching:
 *     1. Phone number digit matching (handling country codes, local formats)
 *     2. Normalized display name matching (case-insensitive, whitespace normalized)
 *     3. Punctuation-stripped fallback matching
 * - Verify VEIL master switch and call settings.
 * - Disguise events using dynamically selected independent decoys.
 */
class PrivacyEngine(
    private val decoyGenerator: DecoyGenerator = DecoyGenerator.getInstance()
) {

    /**
     * Evaluates a parsed notification against protected contacts.
     * Returns a [ProtectedEvent] if matched and disguised, or null if the notification
     * should remain untouched.
     */
    fun evaluateAndDisguise(
        parsed: ParsedNotificationInfo,
        protectedContacts: List<ProtectedContact>,
        prefs: VeilPrefs
    ): ProtectedEvent? {
        // 1. Master toggle check
        if (!prefs.isVeilEnabled) {
            return null
        }

        // 2. Call support toggle check
        if (parsed.isCall && !prefs.isProtectedCallsEnabled) {
            return null
        }

        // 3. Find matching protected contact
        val matchedContact = findMatchingContact(parsed.sender, protectedContacts) ?: return null

        // If contact is explicitly disabled by user, leave untouched
        if (!matchedContact.isEnabled) {
            return null
        }

        // 4. Generate dynamic independent decoy identity and message at arrival time
        val (decoySender, decoyMessage) = decoyGenerator.generateDynamicDecoy(
            type = parsed.getNotificationType(),
            preferredProfile = null,
            prefs = prefs
        )

        // 5. Construct protected disguised event
        return ProtectedEvent(
            timestamp = parsed.timestamp,
            sourcePackage = parsed.packageName,
            realSender = parsed.sender,
            realMessage = parsed.message,
            decoySender = decoySender,
            decoyMessage = decoyMessage,
            isCall = parsed.isCall,
            originalNotificationKey = parsed.notificationKey
        )
    }

    /**
     * Robust contact correlation matching against legitimate Android/WhatsApp data:
     * 1. Phone number match: Extracts digits and matches last 7-10 digits (ignoring international prefixes)
     * 2. Direct normalized phone string match
     * 3. Display name match: Case-insensitive, whitespace collapsed
     * 4. Punctuation-stripped name match
     */
    fun findMatchingContact(
        incomingSender: String,
        protectedContacts: List<ProtectedContact>
    ): ProtectedContact? {
        if (incomingSender.isBlank()) return null

        val incomingDigits = ProtectedContact.extractDigits(incomingSender)
        val normalizedIncomingName = ProtectedContact.normalizeName(incomingSender)
        val strippedIncoming = stripPunctuation(normalizedIncomingName)

        for (contact in protectedContacts) {
            // Check 1: Phone number digits matching (if both have digits)
            if (contact.normalizedPhoneNumber.isNotBlank() && incomingDigits.isNotBlank()) {
                val contactDigits = ProtectedContact.extractDigits(contact.normalizedPhoneNumber)
                if (contactDigits.isNotBlank()) {
                    if (isPhoneNumberMatch(incomingDigits, contactDigits)) {
                        return contact
                    }
                }
            }

            // Check 2: Direct normalized phone string match
            if (contact.normalizedPhoneNumber.isNotBlank()) {
                val normalizedIncomingPhone = ProtectedContact.normalizePhone(incomingSender)
                if (normalizedIncomingPhone.isNotBlank() && normalizedIncomingPhone == contact.normalizedPhoneNumber) {
                    return contact
                }
            }

            // Check 3: Normalized display name match
            if (contact.normalizedName.isNotBlank() && contact.normalizedName == normalizedIncomingName) {
                return contact
            }

            // Check 4: Punctuation-stripped name match
            val strippedContact = stripPunctuation(contact.normalizedName)
            if (strippedContact.isNotBlank() && strippedContact == strippedIncoming) {
                return contact
            }
        }

        return null
    }

    /**
     * Matches phone numbers taking into account international dial codes (e.g. +1, 001, +91, 0).
     * If numbers are >= 7 digits long, matches if either is a suffix of the other with at least 7 digits.
     */
    private fun isPhoneNumberMatch(digits1: String, digits2: String): Boolean {
        if (digits1 == digits2) return true

        val minLen = minOf(digits1.length, digits2.length)
        if (minLen >= 7) {
            val compareLen = minOf(minLen, 10)
            val suffix1 = digits1.takeLast(compareLen)
            val suffix2 = digits2.takeLast(compareLen)
            if (suffix1 == suffix2) return true
        }

        return false
    }

    private fun stripPunctuation(str: String): String {
        return str.replace("[^a-zA-Z0-9\\s]".toRegex(), "").trim()
    }
}
