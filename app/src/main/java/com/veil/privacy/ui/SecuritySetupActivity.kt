package com.veil.privacy.ui

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import com.veil.privacy.R
import com.veil.privacy.databinding.ActivitySecuritySetupBinding
import com.veil.privacy.security.SecurityManager
import com.veil.privacy.storage.VeilPrefs
import java.util.concurrent.Executor

/**
 * First-launch Security Setup Gateway for VEIL.
 * Guides the user through:
 * 1. Create Master Password
 * 2. Confirm Master Password
 * 3. Biometric Registration & Verification (AndroidX BiometricPrompt)
 * 4. Setup Complete -> Transition to Unlock Screen
 */
class SecuritySetupActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySecuritySetupBinding
    private lateinit var prefs: VeilPrefs

    private var currentStep: Int = STEP_CREATE
    private var pendingPassword: String = ""
    private var biometricEnabled: Boolean = false

    private lateinit var executor: Executor
    private var testBiometricPrompt: BiometricPrompt? = null
    private var testPromptInfo: BiometricPrompt.PromptInfo? = null

    companion object {
        const val STEP_CREATE = 1
        const val STEP_CONFIRM = 2
        const val STEP_BIOMETRIC = 3
        const val STEP_COMPLETE = 4
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySecuritySetupBinding.inflate(layoutInflater)
        setContentView(binding.root)

        com.veil.privacy.util.InsetsUtils.applySystemBarInsets(binding.root)

        prefs = VeilPrefs.getInstance(this)
        executor = ContextCompat.getMainExecutor(this)

        setupStepListeners()
        setupBackNavigation()
        showStep(STEP_CREATE)
    }

    private fun setupBackNavigation() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                when (currentStep) {
                    STEP_CONFIRM -> showStep(STEP_CREATE)
                    STEP_BIOMETRIC -> showStep(STEP_CONFIRM)
                    STEP_COMPLETE -> showStep(STEP_BIOMETRIC)
                    else -> finishAffinity()
                }
            }
        })
    }

    private fun setupStepListeners() {
        // Step 1: Create PIN
        binding.btnStep1Next.setOnClickListener {
            val pin = binding.etCreatePassword.text?.toString()?.trim() ?: ""
            when (val result = SecurityManager.validatePinFormat(pin)) {
                is SecurityManager.PinValidationResult.Empty -> {
                    binding.tvStep1Error.text = "PIN cannot be empty"
                    binding.tvStep1Error.visibility = View.VISIBLE
                }
                is SecurityManager.PinValidationResult.NonNumeric -> {
                    binding.tvStep1Error.text = "PIN must contain numeric digits only"
                    binding.tvStep1Error.visibility = View.VISIBLE
                }
                is SecurityManager.PinValidationResult.TooShort -> {
                    binding.tvStep1Error.text = "PIN must be at least ${result.minLength} digits"
                    binding.tvStep1Error.visibility = View.VISIBLE
                }
                is SecurityManager.PinValidationResult.TooLong -> {
                    binding.tvStep1Error.text = "PIN cannot exceed ${result.maxLength} digits"
                    binding.tvStep1Error.visibility = View.VISIBLE
                }
                is SecurityManager.PinValidationResult.Valid -> {
                    binding.tvStep1Error.visibility = View.GONE
                    pendingPassword = pin
                    showStep(STEP_CONFIRM)
                }
            }
        }

        // Step 2: Confirm PIN
        binding.btnStep2Confirm.setOnClickListener {
            val confirmed = binding.etConfirmPassword.text?.toString()?.trim() ?: ""
            if (confirmed != pendingPassword) {
                binding.tvStep2Error.text = "PINs do not match. Please re-enter."
                binding.tvStep2Error.visibility = View.VISIBLE
            } else {
                binding.tvStep2Error.visibility = View.GONE
                showStep(STEP_BIOMETRIC)
            }
        }

        binding.btnStep2Back.setOnClickListener {
            showStep(STEP_CREATE)
        }

        // Step 3: Biometric Setup
        binding.btnEnableBiometric.setOnClickListener {
            testAndRegisterBiometric()
        }

        binding.btnOpenDeviceSettings.setOnClickListener {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    startActivity(Intent(Settings.ACTION_BIOMETRIC_ENROLL))
                } else {
                    startActivity(Intent(Settings.ACTION_SECURITY_SETTINGS))
                }
            } catch (_: Exception) {
                startActivity(Intent(Settings.ACTION_SETTINGS))
            }
        }

        binding.btnSkipBiometric.setOnClickListener {
            biometricEnabled = false
            showStep(STEP_COMPLETE)
        }

        // Step 4: Finish Setup and Transition to Unlock Screen
        binding.btnFinishSetup.setOnClickListener {
            finalizeSecurityConfiguration()
        }
    }

    private fun showStep(step: Int) {
        currentStep = step

        binding.step1Layout.visibility = if (step == STEP_CREATE) View.VISIBLE else View.GONE
        binding.step2Layout.visibility = if (step == STEP_CONFIRM) View.VISIBLE else View.GONE
        binding.step3Layout.visibility = if (step == STEP_BIOMETRIC) View.VISIBLE else View.GONE
        binding.step4Layout.visibility = if (step == STEP_COMPLETE) View.VISIBLE else View.GONE

        updateStepPills(step)

        if (step == STEP_BIOMETRIC) {
            inspectDeviceBiometricCapabilities()
        } else if (step == STEP_COMPLETE) {
            updateSummaryUI()
        }
    }

    private fun updateStepPills(step: Int) {
        val activeBg = R.drawable.bg_button_cyber_primary
        val inactiveBg = R.drawable.bg_button_cyber_secondary
        val activeTextColor = ContextCompat.getColor(this, R.color.veil_button_primary_text)
        val inactiveTextColor = ContextCompat.getColor(this, R.color.veil_text_low)

        binding.pillStep1.setBackgroundResource(if (step >= 1) activeBg else inactiveBg)
        binding.pillStep1.setTextColor(if (step >= 1) activeTextColor else inactiveTextColor)

        binding.pillStep2.setBackgroundResource(if (step >= 2) activeBg else inactiveBg)
        binding.pillStep2.setTextColor(if (step >= 2) activeTextColor else inactiveTextColor)

        binding.pillStep3.setBackgroundResource(if (step >= 3) activeBg else inactiveBg)
        binding.pillStep3.setTextColor(if (step >= 3) activeTextColor else inactiveTextColor)

        binding.pillStep4.setBackgroundResource(if (step >= 4) activeBg else inactiveBg)
        binding.pillStep4.setTextColor(if (step >= 4) activeTextColor else inactiveTextColor)
    }

    private fun inspectDeviceBiometricCapabilities() {
        val biometricManager = BiometricManager.from(this)
        val authenticators = BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.BIOMETRIC_WEAK
        val canAuth = biometricManager.canAuthenticate(authenticators)

        when (canAuth) {
            BiometricManager.BIOMETRIC_SUCCESS -> {
                binding.ivBiometricStatusIcon.setImageResource(R.drawable.ic_fingerprint_biometric)
                binding.ivBiometricStatusIcon.imageTintList = ContextCompat.getColorStateList(this, R.color.veil_accent_secure)
                binding.tvBiometricStatusTitle.text = "Biometric Sensor Ready"
                binding.tvBiometricStatusDesc.text = "Android biometric hardware (Fingerprint / Face) is enrolled and available."
                binding.btnEnableBiometric.visibility = View.VISIBLE
                binding.btnOpenDeviceSettings.visibility = View.GONE
                binding.btnSkipBiometric.text = "Skip Biometrics (PIN Only)"
            }
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> {
                binding.ivBiometricStatusIcon.setImageResource(R.drawable.ic_fingerprint_biometric)
                binding.ivBiometricStatusIcon.imageTintList = ContextCompat.getColorStateList(this, R.color.veil_accent_danger)
                binding.tvBiometricStatusTitle.text = "No Biometrics Enrolled"
                binding.tvBiometricStatusDesc.text = "Sensor detected, but no fingerprints or face profiles are enrolled in Android Settings."
                binding.btnEnableBiometric.visibility = View.GONE
                binding.btnOpenDeviceSettings.visibility = View.VISIBLE
                binding.btnSkipBiometric.text = "Continue with PIN Only"
            }
            BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE -> {
                binding.ivBiometricStatusIcon.setImageResource(R.drawable.ic_lock_closed)
                binding.ivBiometricStatusIcon.imageTintList = ContextCompat.getColorStateList(this, R.color.veil_text_medium)
                binding.tvBiometricStatusTitle.text = "No Biometric Hardware"
                binding.tvBiometricStatusDesc.text = "This device does not possess a biometric scanner. PIN protection will be used."
                binding.btnEnableBiometric.visibility = View.GONE
                binding.btnOpenDeviceSettings.visibility = View.GONE
                binding.btnSkipBiometric.text = "Continue with PIN Only"
            }
            else -> {
                binding.ivBiometricStatusIcon.setImageResource(R.drawable.ic_lock_closed)
                binding.ivBiometricStatusIcon.imageTintList = ContextCompat.getColorStateList(this, R.color.veil_text_medium)
                binding.tvBiometricStatusTitle.text = "Biometrics Unavailable"
                binding.tvBiometricStatusDesc.text = "Biometric authentication is currently unavailable. PIN protection will be used."
                binding.btnEnableBiometric.visibility = View.GONE
                binding.btnOpenDeviceSettings.visibility = View.GONE
                binding.btnSkipBiometric.text = "Continue with PIN Only"
            }
        }
    }

    private fun testAndRegisterBiometric() {
        val authenticators = BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.BIOMETRIC_WEAK
        testPromptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Confirm Biometric Registration")
            .setSubtitle("Touch the biometric sensor to verify identity")
            .setDescription("VEIL requires a test scan to activate biometric unlock.")
            .setNegativeButtonText("Cancel")
            .setAllowedAuthenticators(authenticators)
            .build()

        testBiometricPrompt = BiometricPrompt(this, executor, object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                super.onAuthenticationSucceeded(result)
                biometricEnabled = true
                binding.tvBiometricTestResult.text = "✓ Biometric test verified successfully!"
                binding.tvBiometricTestResult.visibility = View.VISIBLE
                binding.root.postDelayed({
                    showStep(STEP_COMPLETE)
                }, 400)
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                super.onAuthenticationError(errorCode, errString)
                if (errorCode != BiometricPrompt.ERROR_USER_CANCELED && errorCode != BiometricPrompt.ERROR_NEGATIVE_BUTTON) {
                    binding.tvBiometricTestResult.text = "Biometric check error: $errString"
                    binding.tvBiometricTestResult.visibility = View.VISIBLE
                }
            }

            override fun onAuthenticationFailed() {
                super.onAuthenticationFailed()
                binding.tvBiometricTestResult.text = "Biometric not recognized. Please try again."
                binding.tvBiometricTestResult.visibility = View.VISIBLE
            }
        })

        testPromptInfo?.let {
            testBiometricPrompt?.authenticate(it)
        }
    }

    private fun updateSummaryUI() {
        binding.tvSummaryPassword.text = "• Master PIN: ✓ Configured & Hashed (Salted SHA-256)"
        binding.tvSummaryBiometric.text = if (biometricEnabled) {
            "• Fingerprint: ✓ Enabled"
        } else {
            "• Fingerprint: ○ Disabled (PIN Only)"
        }
    }

    private fun finalizeSecurityConfiguration() {
        // 1. Save PIN with cryptographic salt and hash
        val success = SecurityManager.savePin(this, pendingPassword)
        if (!success) {
            Toast.makeText(this, "Failed to save PIN. Please retry.", Toast.LENGTH_SHORT).show()
            showStep(STEP_CREATE)
            return
        }

        // 2. Save biometric preference
        prefs.isBiometricEnabled = biometricEnabled

        // 3. Mark setup as explicitly completed
        prefs.isSecuritySetupCompleted = true
        prefs.isAppLockEnabled = true
        VeilPrefs.isSessionUnlocked = false // Requires authentication on unlock screen

        Toast.makeText(this, "Security Setup Complete!", Toast.LENGTH_SHORT).show()

        // 4. Transition to the normal authentication/unlock screen
        val unlockIntent = Intent(this, AuthLockActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        startActivity(unlockIntent)
        finish()
    }
}
