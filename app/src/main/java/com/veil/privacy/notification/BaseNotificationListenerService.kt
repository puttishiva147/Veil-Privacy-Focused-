package com.veil.privacy.notification

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.veil.privacy.model.ParsedNotificationInfo
import com.veil.privacy.model.SelectedContact
import com.veil.privacy.storage.VeilDatabase
import com.veil.privacy.storage.VeilPrefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Base NotificationListenerService that captures incoming notifications
 * and checks if the sender's identifier matches any stored contacts in the
 * 'SelectedContact' database before applying the privacy transformation layer.
 */
abstract class BaseNotificationListenerService : NotificationListenerService() {

    protected val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    protected val notificationParser = NotificationParser()
    protected lateinit var database: VeilDatabase
    protected lateinit var preferences: VeilPrefs

    override fun onCreate() {
        super.onCreate()
        database = VeilDatabase.getInstance(this)
        preferences = VeilPrefs.getInstance(this)
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn == null) return

        val ownPackage = packageName

        // Parse notification into standardized payload
        val parsed = notificationParser.parse(sbn, ownPackage) ?: return

        // Process asynchronously on background IO thread
        serviceScope.launch {
            try {
                // 1. Fetch enabled stored contacts from 'SelectedContact' database
                val selectedContacts = withContext(Dispatchers.IO) {
                    database.selectedContactDao().getEnabledContactsSync()
                }

                if (selectedContacts.isEmpty()) {
                    return@launch
                }

                // 2. Check if the sender's identifier matches any stored contact
                val matchedContact = findMatchingSelectedContact(parsed.sender, selectedContacts)

                if (matchedContact != null) {
                    // 3. Apply the privacy transformation layer
                    applyPrivacyTransformation(sbn, parsed, matchedContact)
                }
            } catch (_: Exception) {
                // Defensive exception handling guarantees listener stability
            }
        }
    }

    /**
     * Correlates sender identifier with stored contacts in the 'SelectedContact' database.
     * Evaluates:
     * 1. Phone number digits match (handles international dial codes and formatting)
     * 2. Direct normalized phone string match
     * 3. Normalized display name match
     * 4. Punctuation-stripped fallback match
     */
    protected open fun findMatchingSelectedContact(
        incomingSender: String,
        contacts: List<SelectedContact>
    ): SelectedContact? {
        if (incomingSender.isBlank()) return null

        val incomingDigits = SelectedContact.extractDigits(incomingSender)
        val normalizedIncomingName = SelectedContact.normalizeName(incomingSender)
        val strippedIncoming = stripPunctuation(normalizedIncomingName)

        for (contact in contacts) {
            // Check 1: Phone number digits match
            if (contact.normalizedPhoneNumber.isNotBlank() && incomingDigits.isNotBlank()) {
                val contactDigits = SelectedContact.extractDigits(contact.normalizedPhoneNumber)
                if (contactDigits.isNotBlank() && isPhoneDigitsMatch(incomingDigits, contactDigits)) {
                    return contact
                }
            }

            // Check 2: Direct normalized phone string match
            if (contact.normalizedPhoneNumber.isNotBlank()) {
                val normalizedIncomingPhone = SelectedContact.normalizePhone(incomingSender)
                if (normalizedIncomingPhone.isNotBlank() && normalizedIncomingPhone == contact.normalizedPhoneNumber) {
                    return contact
                }
            }

            // Check 3: Display name match
            val contactNormalizedName = SelectedContact.normalizeName(contact.displayName)
            if (contactNormalizedName.isNotBlank() && contactNormalizedName == normalizedIncomingName) {
                return contact
            }

            // Check 4: Punctuation-stripped name match
            val strippedContact = stripPunctuation(contactNormalizedName)
            if (strippedContact.isNotBlank() && strippedContact == strippedIncoming) {
                return contact
            }
        }

        return null
    }

    /**
     * Matches phone number digits taking into account international dial codes.
     */
    private fun isPhoneDigitsMatch(digits1: String, digits2: String): Boolean {
        if (digits1 == digits2) return true
        val minLen = minOf(digits1.length, digits2.length)
        if (minLen >= 7) {
            val compareLen = minOf(minLen, 10)
            return digits1.takeLast(compareLen) == digits2.takeLast(compareLen)
        }
        return false
    }

    private fun stripPunctuation(str: String): String {
        return str.replace("[^a-zA-Z0-9\\s]".toRegex(), "").trim()
    }

    /**
     * Subclasses implement this method to apply the privacy transformation layer
     * (e.g., generating decoys, posting disguised alerts, logging in vault,
     * and dismissing original alerts).
     */
    abstract suspend fun applyPrivacyTransformation(
        sbn: StatusBarNotification,
        parsed: ParsedNotificationInfo,
        matchedContact: SelectedContact
    )
}
