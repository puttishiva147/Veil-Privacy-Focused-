package com.veil.privacy

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.veil.privacy.security.SecurityManager
import com.veil.privacy.storage.VeilPrefs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SecurityManagerTest {

    private lateinit var context: Context
    private lateinit var prefs: VeilPrefs

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        prefs = VeilPrefs.getInstance(context)
        SecurityManager.resetSecuritySetup(context)
    }

    @Test
    fun testInitialStateRequiresSetup() {
        assertFalse("Initial state must not be marked as setup completed", SecurityManager.isSecuritySetupCompleted(context))
        assertFalse("Session must not be unlocked initially", VeilPrefs.isSessionUnlocked)
    }

    @Test
    fun testPasswordValidation() {
        assertEquals(SecurityManager.PasswordValidationResult.Empty, SecurityManager.validatePasswordFormat(""))
        assertEquals(SecurityManager.PasswordValidationResult.Empty, SecurityManager.validatePasswordFormat("   "))
        assertTrue(SecurityManager.validatePasswordFormat("12") is SecurityManager.PasswordValidationResult.TooShort)
        assertTrue(SecurityManager.validatePasswordFormat("123") is SecurityManager.PasswordValidationResult.TooShort)
        assertEquals(SecurityManager.PasswordValidationResult.Valid, SecurityManager.validatePasswordFormat("1234"))
        assertEquals(SecurityManager.PasswordValidationResult.Valid, SecurityManager.validatePasswordFormat("CyberShield#99"))
    }

    @Test
    fun testSaveAndVerifyPassword() {
        val testPassword = "MySecurePassword2026!"
        val saved = SecurityManager.savePassword(context, testPassword)
        assertTrue("Saving valid password must return true", saved)

        // Setup must be marked completed
        assertTrue("Security setup must be completed after saving password", SecurityManager.isSecuritySetupCompleted(context))

        // Plaintext must not be saved
        assertNotEquals(testPassword, prefs.passwordHash)
        assertTrue("Hash must be non-null and non-empty", !prefs.passwordHash.isNullOrBlank())
        assertTrue("Salt must be non-null and non-empty", !prefs.passwordSalt.isNullOrBlank())
        assertTrue("New hashes must use PBKDF2-HMAC-SHA256", prefs.passwordHash!!.startsWith("pbkdf2-sha256"))

        // Correct password verifies successfully
        assertTrue("Verification with correct password must succeed", SecurityManager.verifyPassword(context, testPassword))

        // Incorrect password fails
        assertFalse("Verification with incorrect password must fail", SecurityManager.verifyPassword(context, "WrongPassword"))
        assertFalse("Verification with empty password must fail", SecurityManager.verifyPassword(context, ""))
    }

    @Test
    fun testLegacyMasterHashIsVerifiedAndUpgradedOnUnlock() {
        // Legacy 5,000-round salted SHA-256 hash for this fixed test password and salt.
        prefs.savePasswordCredentials(
            "990116f7a079961668fb8aa7ea356b7bc3e836b8113a0209ca6692a679bdc9bf",
            "000102030405060708090a0b0c0d0e0f"
        )

        assertTrue(SecurityManager.verifyPassword(context, "LegacyPassword2026!"))
        assertTrue("Successful unlock should upgrade legacy credentials", prefs.passwordHash!!.startsWith("pbkdf2-sha256"))
        assertTrue("Upgraded credential should continue to verify", SecurityManager.verifyPassword(context, "LegacyPassword2026!"))
        assertFalse(SecurityManager.verifyPassword(context, "WrongPassword"))
    }

    @Test
    fun testResetSecuritySetup() {
        SecurityManager.savePassword(context, "SecretPin4321")
        assertTrue(SecurityManager.isSecuritySetupCompleted(context))

        SecurityManager.resetSecuritySetup(context)
        assertFalse("Security setup must not be completed after reset", SecurityManager.isSecuritySetupCompleted(context))
        assertFalse("Verify must fail after reset", SecurityManager.verifyPassword(context, "SecretPin4321"))
    }

    @Test
    fun testMigrationPurgesLegacyHardcodedPin() {
        // Simulate legacy unmigrated state with default "1234"
        prefs.clearPasswordCredentials()
        prefs.performSecurityMigrationIfNeeded()

        assertFalse("Legacy default must be purged", SecurityManager.isSecuritySetupCompleted(context))
        assertFalse("Verification with legacy 1234 must fail", SecurityManager.verifyPassword(context, "1234"))
    }
}
