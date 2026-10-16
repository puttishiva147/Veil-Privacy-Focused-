package com.veil.privacy.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Persists the user's chosen contact with normalized phone number and display name.
 * Stored in Room database and retrieved by the base NotificationListenerService
 * to correlate incoming notification sender identifiers.
 */
@Entity(
    tableName = "selected_contacts",
    indices = [
        Index(value = ["normalizedPhoneNumber"]),
        Index(value = ["displayName"]),
        Index(value = ["contactId"])
    ]
)
data class SelectedContact(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val contactId: String = "",
    val displayName: String,
    val selectedPhoneNumber: String = "",
    val normalizedPhoneNumber: String = normalizePhone(selectedPhoneNumber),
    val source: String = "ANDROID_CONTACTS",
    val isEnabled: Boolean = true,
    val addedTimestamp: Long = System.currentTimeMillis()
) {
    companion object {
        fun normalizePhone(raw: String): String {
            return raw.replace("[^0-9+]".toRegex(), "").trim()
        }

        fun extractDigits(raw: String): String {
            return raw.replace("[^0-9]".toRegex(), "")
        }

        fun normalizeName(raw: String): String {
            return raw.trim().replace("\\s+".toRegex(), " ").lowercase()
        }
    }
}
