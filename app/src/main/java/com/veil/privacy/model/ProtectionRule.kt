package com.veil.privacy.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Represents an independent, dynamic VEIL protection rule for a specific WhatsApp contact/chat.
 *
 * Example:
 * Real Contact: "Rahul"
 * Disguise Mode: GENERIC_PROTECTED ("🔒 VEIL — Protected message") or CUSTOM_DECOY ("Work: New message")
 */
@Entity(
    tableName = "protection_rules",
    indices = [
        Index(value = ["normalizedPhoneNumber"]),
        Index(value = ["normalizedContactName"]),
        Index(value = ["contactId"])
    ]
)
data class ProtectionRule(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val contactId: String = "",
    val contactName: String,
    val contactPhoneNumber: String = "",
    val normalizedPhoneNumber: String = normalizePhone(contactPhoneNumber),
    val normalizedContactName: String = normalizeName(contactName),
    val disguiseMode: String = MODE_GENERIC_PROTECTED,
    val decoyIdentity: String = "VEIL",
    val decoyMessage: String = "Protected message",
    val isEnabled: Boolean = true,
    val soundEnabled: Boolean = true,
    val vibrationEnabled: Boolean = true,
    val isCallDisguiseEnabled: Boolean = true,
    val lockscreenVisibility: Int = 1, // 1: Disguised visible, 0: Private, -1: Secret
    val source: String = SOURCE_MANUAL,
    val lockPinHash: String = "",
    val lockPinSalt: String = "",
    val isCustomLockEnabled: Boolean = false,
    val photoUri: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val appPackage: String = SupportedApp.PKG_WHATSAPP
) {
    companion object {
        const val MODE_GENERIC_PROTECTED = "GENERIC_PROTECTED"
        const val MODE_CUSTOM_DECOY = "CUSTOM_DECOY"
        const val MODE_RANDOM_ROTATION = "RANDOM_ROTATION"
        const val MODE_SILENT = "SILENT"

        const val SOURCE_ANDROID_CONTACTS = "ANDROID_CONTACTS"
        const val SOURCE_MANUAL = "MANUAL"
        const val SOURCE_NOTIFICATION_INTERCEPT = "NOTIFICATION_INTERCEPT"
        const val SOURCE_SENSOR_LEARN = "SENSOR_LEARN"

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
