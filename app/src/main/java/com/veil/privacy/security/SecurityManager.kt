package com.veil.privacy.security

import android.content.Context
import com.veil.privacy.storage.VeilPrefs
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * Cryptographic security manager for VEIL.
 * Implements salted SHA-256 multi-round key stretching (5,000 iterations)
 * with constant-time verification. Never stores or logs plaintext passwords.
 */
object SecurityManager {

    private const val HASH_ITERATIONS = 5000
    private const val SALT_BYTE_LENGTH = 16
    const val MIN_PASSWORD_LENGTH = 4
    const val MIN_PIN_LENGTH = 4
    const val MAX_PIN_LENGTH = 8

    /**
     * Checks if security setup has been completed by the user.
     * Returns true ONLY if setup was explicitly completed and credentials exist.
     */
    fun isSecuritySetupCompleted(context: Context): Boolean {
        val prefs = VeilPrefs.getInstance(context)
        return prefs.isSecuritySetupCompleted && prefs.hasPasswordConfigured()
    }

    /**
     * Validates that PIN meets security requirements (4-8 digits numeric).
     */
    fun validatePinFormat(pin: String): PinValidationResult {
        return when {
            pin.isBlank() -> PinValidationResult.Empty
            !pin.all { it.isDigit() } -> PinValidationResult.NonNumeric
            pin.length < MIN_PIN_LENGTH -> PinValidationResult.TooShort(MIN_PIN_LENGTH)
            pin.length > MAX_PIN_LENGTH -> PinValidationResult.TooLong(MAX_PIN_LENGTH)
            else -> PinValidationResult.Valid
        }
    }

    /**
     * Validates that password meets security requirements.
     */
    fun validatePasswordFormat(password: String): PasswordValidationResult {
        return when {
            password.isBlank() -> PasswordValidationResult.Empty
            password.length < MIN_PASSWORD_LENGTH -> PasswordValidationResult.TooShort(MIN_PASSWORD_LENGTH)
            else -> PasswordValidationResult.Valid
        }
    }

    /**
     * Hashes any PIN or password with a fresh cryptographic salt and 5,000 SHA-256 iterations.
     * Returns Pair(hashHex, saltHex).
     */
    fun hashPassword(password: String): Pair<String, String> {
        val saltBytes = ByteArray(SALT_BYTE_LENGTH)
        SecureRandom().nextBytes(saltBytes)
        val saltHex = saltBytes.joinToString("") { "%02x".format(it) }
        val hashHex = computeHash(password, saltBytes)
        return Pair(hashHex, saltHex)
    }

    /**
     * Verifies entered PIN or password against a stored salted hash in constant time.
     */
    fun verifyHash(password: String, storedHash: String, storedSaltHex: String): Boolean {
        if (storedHash.isBlank() || storedSaltHex.isBlank()) return false
        val saltBytes = hexToBytes(storedSaltHex) ?: return false
        val computedHash = computeHash(password, saltBytes)
        return constantTimeEquals(storedHash, computedHash)
    }

    /**
     * Verifies an independent contact-specific PIN.
     */
    fun verifyContactPin(enteredPin: String, storedHash: String, storedSaltHex: String): Boolean {
        return verifyHash(enteredPin, storedHash, storedSaltHex)
    }

    /**
     * Saves a new master password securely using a fresh cryptographic salt and hash.
     */
    fun savePassword(context: Context, password: String): Boolean {
        if (password.length < MIN_PASSWORD_LENGTH) return false
        val prefs = VeilPrefs.getInstance(context)
        val (hashHex, saltHex) = hashPassword(password)
        prefs.savePasswordCredentials(hashHex, saltHex)
        return true
    }

    /**
     * Verifies entered password against stored salted master hash in constant time.
     */
    fun verifyPassword(context: Context, password: String): Boolean {
        val prefs = VeilPrefs.getInstance(context)
        val storedHash = prefs.passwordHash ?: return false
        val storedSaltHex = prefs.passwordSalt ?: return false
        return verifyHash(password, storedHash, storedSaltHex)
    }

    /**
     * Resets security configuration so user can re-establish new credentials.
     */
    fun resetSecuritySetup(context: Context) {
        val prefs = VeilPrefs.getInstance(context)
        prefs.clearPasswordCredentials()
        prefs.isSecuritySetupCompleted = false
        VeilPrefs.isSessionUnlocked = false
        VeilPrefs.lockAllContacts()
    }

    private fun computeHash(password: String, salt: ByteArray): String {
        val md = MessageDigest.getInstance("SHA-256")
        md.update(salt)
        var digest = md.digest(password.toByteArray(Charsets.UTF_8))
        for (i in 1 until HASH_ITERATIONS) {
            md.reset()
            digest = md.digest(digest)
        }
        return digest.joinToString("") { "%02x".format(it) }
    }

    private fun constantTimeEquals(a: String, b: String): Boolean {
        return MessageDigest.isEqual(a.toByteArray(Charsets.UTF_8), b.toByteArray(Charsets.UTF_8))
    }

    private fun hexToBytes(hex: String): ByteArray? {
        if (hex.length % 2 != 0) return null
        return try {
            ByteArray(hex.length / 2) { i ->
                hex.substring(i * 2, i * 2 + 2).toInt(16).toByte()
            }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Saves a new master PIN securely using salted SHA-256 hash.
     */
    fun savePin(context: Context, pin: String): Boolean {
        if (pin.length < MIN_PIN_LENGTH || !pin.all { it.isDigit() }) return false
        val saved = savePassword(context, pin)
        if (saved) VeilPrefs.getInstance(context).masterPinLength = pin.length
        return saved
    }

    /**
     * Verifies entered PIN against stored salted master hash in constant time.
     */
    fun verifyPin(context: Context, pin: String): Boolean {
        return verifyPassword(context, pin)
    }

    sealed class PinValidationResult {
        object Valid : PinValidationResult()
        object Empty : PinValidationResult()
        object NonNumeric : PinValidationResult()
        data class TooShort(val minLength: Int) : PinValidationResult()
        data class TooLong(val maxLength: Int) : PinValidationResult()
    }

    sealed class PasswordValidationResult {
        object Valid : PasswordValidationResult()
        object Empty : PasswordValidationResult()
        data class TooShort(val minLength: Int) : PasswordValidationResult()
    }
}
