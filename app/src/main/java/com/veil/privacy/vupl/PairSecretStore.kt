package com.veil.privacy.vupl

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Stores only the 64-char pairing secret. The master encryption key is Android-Keystore-backed.
 * Never log this value and never put it in QR history, analytics, backups, or crash reports.
 */
class PairSecretStore(context: Context) {
    private val prefs = EncryptedSharedPreferences.create(
        context.applicationContext,
        "veil_pair_secret_store",
        MasterKey.Builder(context.applicationContext)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    fun save(pairId: String, secretHex: String) {
        require(pairId.isNotBlank())
        require(secretHex.matches(Regex("[0-9a-f]{64}"))) { "Expected 32-byte lowercase hex secret" }
        prefs.edit().putString("pair:$pairId", secretHex).apply()
    }

    fun get(pairId: String): String? = prefs.getString("pair:$pairId", null)

    fun remove(pairId: String) {
        prefs.edit().remove("pair:$pairId").apply()
    }
}
