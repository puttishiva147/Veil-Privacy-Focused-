package com.veil.privacy.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import com.veil.privacy.R
import com.veil.privacy.databinding.ActivityAuthLockBinding
import com.veil.privacy.security.SecurityManager
import com.veil.privacy.storage.VeilPrefs
import com.veil.privacy.util.InsetsUtils
import java.util.concurrent.Executor

/**
 * Normal Authentication & Unlock Gateway for VEIL.
 * Supports:
 * 1. Password / PIN verification using salted cryptographic hash
 * 2. Native Android BiometricPrompt (Fingerprint / Face)
 * 3. Graceful fallback and error handling
 */
class AuthLockActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAuthLockBinding
    private lateinit var prefs: VeilPrefs

    private lateinit var executor: Executor
    private var biometricPrompt: BiometricPrompt? = null
    private var promptInfo: BiometricPrompt.PromptInfo? = null
    private var isBiometricAvailable: Boolean = false

    companion object {
        const val REQUEST_CODE_AUTH = 1001
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 1. Guard against unconfigured security state
        // If security has not been set up yet, redirect immediately to Security Setup
        if (!SecurityManager.isSecuritySetupCompleted(this)) {
            val setupIntent = Intent(this, SecuritySetupActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
            startActivity(setupIntent)
            finish()
            return
        }

        binding = ActivityAuthLockBinding.inflate(layoutInflater)
        setContentView(binding.root)

        InsetsUtils.applySystemBarInsets(binding.root)

        prefs = VeilPrefs.getInstance(this)
        executor = ContextCompat.getMainExecutor(this)

        setupBiometricPrompt()
        setupInputControls()

        // Automatically prompt biometrics on launch if enabled and available
        if (isBiometricAvailable && prefs.isBiometricEnabled) {
            binding.root.post {
                launchBiometricPrompt()
            }
        }
    }

    private fun setupBiometricPrompt() {
        val biometricManager = BiometricManager.from(this)
        val authenticators = BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.BIOMETRIC_WEAK
        val canAuthenticate = biometricManager.canAuthenticate(authenticators)

        isBiometricAvailable = (canAuthenticate == BiometricManager.BIOMETRIC_SUCCESS)

        if (isBiometricAvailable && prefs.isBiometricEnabled) {
            biometricPrompt = BiometricPrompt(this, executor, object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    when (errorCode) {
                        BiometricPrompt.ERROR_USER_CANCELED,
                        BiometricPrompt.ERROR_NEGATIVE_BUTTON -> {
                            binding.tvLockPrompt.text = "Enter master PIN to unlock"
                            binding.tvLockPrompt.setTextColor(ContextCompat.getColor(this@AuthLockActivity, R.color.veil_text_medium))
                        }
                        BiometricPrompt.ERROR_LOCKOUT,
                        BiometricPrompt.ERROR_LOCKOUT_PERMANENT -> {
                            binding.tvLockPrompt.text = "Fingerprint locked out. Please enter PIN."
                            binding.tvLockPrompt.setTextColor(ContextCompat.getColor(this@AuthLockActivity, R.color.veil_accent_danger))
                        }
                        else -> {
                            binding.tvLockPrompt.text = errString
                            binding.tvLockPrompt.setTextColor(ContextCompat.getColor(this@AuthLockActivity, R.color.veil_accent_danger))
                        }
                    }
                }

                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    handleUnlockSuccess("Fingerprint identity confirmed")
                }

                override fun onAuthenticationFailed() {
                    super.onAuthenticationFailed()
                    triggerErrorVibration()
                    binding.tvLockPrompt.text = "Fingerprint not recognized. Try again or enter PIN."
                    binding.tvLockPrompt.setTextColor(ContextCompat.getColor(this@AuthLockActivity, R.color.veil_accent_danger))
                }
            })

            promptInfo = BiometricPrompt.PromptInfo.Builder()
                .setTitle("Unlock VEIL Protected Chats")
                .setSubtitle("Confirm device fingerprint")
                .setDescription("Verification required to access private conversations")
                .setNegativeButtonText("Use PIN")
                .setAllowedAuthenticators(authenticators)
                .build()

            binding.btnKeyBiometric.visibility = View.VISIBLE
        } else {
            binding.btnKeyBiometric.visibility = View.GONE
            binding.tvLockPrompt.text = "Enter master PIN to access Protected Chats"
        }
    }

    private fun launchBiometricPrompt() {
        if (isBiometricAvailable && prefs.isBiometricEnabled && !isFinishing && !isDestroyed) {
            try {
                promptInfo?.let { biometricPrompt?.authenticate(it) }
            } catch (_: Exception) {
                binding.tvLockPrompt.text = "Enter master PIN to unlock"
            }
        } else {
            Toast.makeText(this, "Fingerprint sensor not available. Please use PIN.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupInputControls() {
        val numberButtons = listOf(
            binding.btnKey0 to "0",
            binding.btnKey1 to "1",
            binding.btnKey2 to "2",
            binding.btnKey3 to "3",
            binding.btnKey4 to "4",
            binding.btnKey5 to "5",
            binding.btnKey6 to "6",
            binding.btnKey7 to "7",
            binding.btnKey8 to "8",
            binding.btnKey9 to "9"
        )

        for ((button, digit) in numberButtons) {
            button.setOnClickListener {
                binding.etPassword.append(digit)
            }
        }

        binding.btnKeyDelete.setOnClickListener {
            val text = binding.etPassword.text
            if (!text.isNullOrEmpty()) {
                text.delete(text.length - 1, text.length)
            }
        }

        binding.btnKeyBiometric.setOnClickListener {
            launchBiometricPrompt()
        }

        binding.btnUnlockSubmit.setOnClickListener {
            verifyPasswordAndUnlock()
        }

        binding.etPassword.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE || actionId == EditorInfo.IME_ACTION_GO) {
                verifyPasswordAndUnlock()
                true
            } else {
                false
            }
        }

        binding.btnKeyCancel.setOnClickListener {
            setResult(Activity.RESULT_CANCELED)
            finishAffinity()
        }
    }

    private fun verifyPasswordAndUnlock() {
        val entered = binding.etPassword.text?.toString()?.trim() ?: ""
        if (entered.isEmpty()) {
            binding.tvLockPrompt.text = "Please enter your master PIN"
            binding.tvLockPrompt.setTextColor(ContextCompat.getColor(this, R.color.veil_accent_danger))
            return
        }

        val isValid = SecurityManager.verifyPin(this, entered)
        if (isValid) {
            handleUnlockSuccess("PIN verified")
        } else {
            triggerErrorVibration()
            binding.tvLockPrompt.text = "Incorrect PIN. Please try again."
            binding.tvLockPrompt.setTextColor(ContextCompat.getColor(this, R.color.veil_accent_danger))
            binding.etPassword.setText("")
        }
    }

    private fun handleUnlockSuccess(reason: String) {
        VeilPrefs.isSessionUnlocked = true
        Toast.makeText(this, "🔒 VEIL: $reason", Toast.LENGTH_SHORT).show()

        setResult(Activity.RESULT_OK)

        // If this activity is the root task (e.g. launched from cold start), open DashboardActivity
        if (isTaskRoot) {
            val dashboardIntent = Intent(this, DashboardActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            startActivity(dashboardIntent)
        }

        finish()
    }

    private fun triggerErrorVibration() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vibratorManager?.defaultVibrator?.vibrate(
                    VibrationEffect.createOneShot(120, VibrationEffect.DEFAULT_AMPLITUDE)
                )
            } else {
                @Suppress("DEPRECATION")
                val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                @Suppress("DEPRECATION")
                vibrator?.vibrate(120)
            }
        } catch (_: Exception) {
            // Safe fallback
        }
    }
}
