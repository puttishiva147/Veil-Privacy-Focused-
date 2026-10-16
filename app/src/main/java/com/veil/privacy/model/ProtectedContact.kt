package com.veil.privacy.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Represents a real contact selected by the user to be protected by VEIL.
 * Stores stable contact identifiers and phone numbers so that VEIL does not rely
 * solely on display names, while supporting both phone-number and display-name matching
 * against incoming Android/WhatsApp notifications.
 */
@Entity(
    tableName = "protected_contacts",
    indices = [
        Index(value = ["normalizedPhoneNumber"]),
        Index(value = ["normalizedName"]),
        Index(value = ["contactId"])
    ]
)
data class ProtectedContact(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val contactId: String = "",
    val displayName: String,
    val selectedPhoneNumber: String = "",
    val normalizedPhoneNumber: String = normalizePhone(selectedPhoneNumber),
    val normalizedName: String = normalizeName(displayName),
    val source: String = SOURCE_ANDROID_CONTACTS,
    val isEnabled: Boolean = true,
    val lockPinHash: String = "",
    val lockPinSalt: String = "",
    val isCustomLockEnabled: Boolean = false,
    val photoUri: String = "",
    val addedTimestamp: Long = System.currentTimeMillis()
) {
    // Convenience accessor for backward compatibility
    val name: String
        get() = displayName

    // Secondary constructor for convenient manual or test creation
    constructor(
        id: Long = 0,
        name: String,
        phoneNumber: String = "",
        isEnabled: Boolean = true,
        source: String = if (phoneNumber.isNotBlank()) SOURCE_ANDROID_CONTACTS else SOURCE_MANUAL,
        addedTimestamp: Long = System.currentTimeMillis()
    ) : this(
        id = id,
        contactId = "",
        displayName = name,
        selectedPhoneNumber = phoneNumber,
        normalizedPhoneNumber = normalizePhone(phoneNumber),
        normalizedName = normalizeName(name),
        source = source,
        isEnabled = isEnabled,
        lockPinHash = "",
        lockPinSalt = "",
        isCustomLockEnabled = false,
        photoUri = "",
        addedTimestamp = addedTimestamp
    )

    companion object {
        const val SOURCE_ANDROID_CONTACTS = "ANDROID_CONTACTS"
        const val SOURCE_MANUAL = "MANUAL"
        const val SOURCE_SENSOR_LEARNED = "SENSOR_LEARNED"

        /**
         * Normalizes a contact name by trimming, collapsing spaces, and lowercasing.
         */
        fun normalizeName(raw: String): String {
            return raw.trim()
                .replace("\\s+".toRegex(), " ")
                .lowercase()
        }

        /**
         * Normalizes a phone number for comparison by removing formatting characters,
         * spaces, parentheses, and dashes.
         */
        fun normalizePhone(raw: String): String {
            return raw.replace("[^0-9+]".toRegex(), "").trim()
        }

        /**
         * Extracts only digit characters from a phone number or sender string.
         */
        fun extractDigits(raw: String): String {
            return raw.replace("[^0-9]".toRegex(), "")
        }

        /**
         * Backward compatibility with legacy code calling normalize().
         */
        fun normalize(raw: String): String = normalizeName(raw)
    }
}
