package com.veil.privacy.ui

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.lifecycleScope
import com.veil.privacy.R
import com.veil.privacy.databinding.ActivityRuleEditBinding
import com.veil.privacy.model.ProtectionRule
import com.veil.privacy.model.SelectedContact
import com.veil.privacy.storage.VeilDatabase
import com.veil.privacy.util.ContactSelectionHelper
import com.veil.privacy.util.VeilDialogs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Activity allowing users to create or edit an independent VEIL protection rule:
 * Contact A → Decoy A ("Work" / "New message")
 */
class RuleEditActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_RULE_ID = "extra_rule_id"
        const val EXTRA_APP_PACKAGE = "extra_app_package"
    }

    private lateinit var binding: ActivityRuleEditBinding
    private lateinit var db: VeilDatabase

    private var editingRuleId: Long = 0
    private var appPackage: String = com.veil.privacy.model.SupportedApp.PKG_WHATSAPP
    private var selectedContactId: String = ""
    private var selectedContactName: String = ""
    private var selectedContactPhone: String = ""
    private var selectedSource: String = ProtectionRule.SOURCE_ANDROID_CONTACTS

    // Contact Picker Launcher
    private val pickContactLauncher = registerForActivityResult(
        ActivityResultContracts.PickContact()
    ) { uri: Uri? ->
        if (uri != null) {
            handleContactPicked(uri)
        }
    }

    // Permission Launcher
    private val requestContactsPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            pickContactLauncher.launch(null)
        } else {
            showPermissionRationale()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRuleEditBinding.inflate(layoutInflater)
        setContentView(binding.root)

        com.veil.privacy.util.InsetsUtils.applySystemBarInsets(binding.root)

        db = VeilDatabase.getInstance(this)
        editingRuleId = intent.getLongExtra(EXTRA_RULE_ID, 0)
        appPackage = intent.getStringExtra(EXTRA_APP_PACKAGE) ?: com.veil.privacy.model.SupportedApp.PKG_WHATSAPP

        setupUI()
        setupListeners()

        if (editingRuleId > 0) {
            loadExistingRule(editingRuleId)
        } else {
            // Default preset
            binding.etDecoyIdentity.setText("Work")
            binding.etDecoyMessage.setText("New message")
            updatePreview()
        }
    }

    private fun setupUI() {
        binding.toolbarRuleEdit.setNavigationOnClickListener { finish() }

        if (editingRuleId > 0) {
            binding.toolbarRuleEdit.title = "Edit Protection Rule"
            binding.btnDeleteRule.visibility = View.VISIBLE
        } else {
            val appName = when (appPackage) {
                com.veil.privacy.model.SupportedApp.PKG_WHATSAPP -> "WhatsApp"
                com.veil.privacy.model.SupportedApp.PKG_INSTAGRAM -> "Instagram"
                com.veil.privacy.model.SupportedApp.PKG_TELEGRAM -> "Telegram"
                com.veil.privacy.model.SupportedApp.PKG_SNAPCHAT -> "Snapchat"
                else -> "Social App"
            }
            binding.toolbarRuleEdit.title = "New $appName Protection Rule"
            if (appPackage != com.veil.privacy.model.SupportedApp.PKG_WHATSAPP) {
                binding.btnPickContact.visibility = View.GONE
                binding.btnManualContact.text = "Enter username"
                binding.tvSelectedContactPhone.text = "Enter the exact username or display name for $appName"
            }
            binding.btnDeleteRule.visibility = View.GONE
        }
    }

    private var existingPinHash: String = ""
    private var existingPinSalt: String = ""

    private fun setupListeners() {
        // Pick contact buttons
        binding.btnPickContact.setOnClickListener {
            launchContactSelectionFlow()
        }

        binding.btnManualContact.setOnClickListener {
            showManualEntryDialog()
        }

        binding.switchCustomPinEnabled.setOnCheckedChangeListener { _, isChecked ->
            binding.tilContactLockPin.visibility = if (isChecked) View.VISIBLE else View.GONE
        }

        // Live preview updates
        binding.etDecoyIdentity.doAfterTextChanged {
            updatePreview()
        }

        binding.etDecoyMessage.doAfterTextChanged {
            updatePreview()
        }

        // Identity preset chips
        binding.chipDecoyDynamic.setOnClickListener {
            binding.etDecoyIdentity.setText("Dynamic")
            binding.etDecoyMessage.setText("Auto-rotating disguise")
        }

        binding.chipDecoyWork.setOnClickListener {
            binding.etDecoyIdentity.setText("Work")
        }

        binding.chipDecoyCalendar.setOnClickListener {
            binding.etDecoyIdentity.setText("Calendar")
        }

        binding.chipDecoyDelivery.setOnClickListener {
            binding.etDecoyIdentity.setText("Delivery")
        }

        binding.chipDecoySystem.setOnClickListener {
            binding.etDecoyIdentity.setText("System Alert")
        }

        // Message preset chips
        binding.chipMsgNewMessage.setOnClickListener {
            binding.etDecoyMessage.setText("New message")
        }

        binding.chipMsgTask.setOnClickListener {
            binding.etDecoyMessage.setText("Task reminder")
        }

        binding.chipMsgSync.setOnClickListener {
            binding.etDecoyMessage.setText("Sync completed")
        }

        binding.chipMsgDoc.setOnClickListener {
            binding.etDecoyMessage.setText("Document shared")
        }

        // Save & Delete
        binding.btnSaveRule.setOnClickListener {
            saveRule()
        }

        binding.btnDeleteRule.setOnClickListener {
            confirmDeleteRule()
        }
    }

    private fun updatePreview() {
        val identity = binding.etDecoyIdentity.text?.toString()?.trim()
        val message = binding.etDecoyMessage.text?.toString()?.trim()

        binding.tvPreviewTitle.text = if (!identity.isNullOrBlank()) identity else "Decoy Name"
        binding.tvPreviewBody.text = if (!message.isNullOrBlank()) message else "Decoy Message"
    }

    private fun launchContactSelectionFlow() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED) {
            pickContactLauncher.launch(null)
        } else {
            requestContactsPermissionLauncher.launch(Manifest.permission.READ_CONTACTS)
        }
    }

    private fun handleContactPicked(uri: Uri) {
        val contactInfo = ContactSelectionHelper.resolveContactFromUri(this, uri)
        if (contactInfo == null) {
            Toast.makeText(this, "Could not read contact data", Toast.LENGTH_SHORT).show()
            return
        }

        ContactSelectionHelper.promptNumberSelection(
            context = this,
            contactInfo = contactInfo,
            onNumberSelected = { chosenNumber ->
                setContactTarget(
                    name = contactInfo.displayName,
                    phone = chosenNumber,
                    contactId = contactInfo.contactId,
                    source = ProtectionRule.SOURCE_ANDROID_CONTACTS
                )
            },
            onManualFallbackRequested = {
                showManualEntryDialog(initialName = contactInfo.displayName)
            }
        )
    }

    private fun setContactTarget(name: String, phone: String, contactId: String, source: String) {
        selectedContactName = name
        selectedContactPhone = phone
        selectedContactId = contactId
        selectedSource = source

        binding.tvSelectedContactName.text = "« $name »"
        binding.tvSelectedContactPhone.text = if (phone.isNotBlank()) phone else "Direct display name matching"
    }

    private fun showManualEntryDialog(initialName: String = "") {
        VeilDialogs.showQuickProtectBottomSheet(
            activity = this,
            initialName = initialName,
            onConfirmed = { name, phone, decoyIdentity, decoyMessage, pin ->
                setContactTarget(
                    name = name,
                    phone = phone,
                    contactId = "",
                    source = ProtectionRule.SOURCE_MANUAL
                )
                binding.etDecoyIdentity.setText(decoyIdentity)
                binding.etDecoyMessage.setText(decoyMessage)
                if (pin.isNotBlank()) {
                    binding.switchCustomPinEnabled.isChecked = true
                    binding.tilContactLockPin.visibility = View.VISIBLE
                    binding.etContactLockPin.setText(pin)
                }
                updatePreview()
            }
        )
    }

    private fun showPermissionRationale() {
        VeilDialogs.showConfirmationDialog(
            context = this,
            title = getString(R.string.contact_permission_title),
            message = "VEIL requires contacts access to select the real person and phone number to protect from your address book.\n\nYou can also choose to enter the person's name and phone number manually without contacts permission.",
            confirmText = "Enter Manually",
            onConfirm = { showManualEntryDialog() }
        )
    }

    private fun loadExistingRule(ruleId: Long) {
        lifecycleScope.launch {
            val rule = withContext(Dispatchers.IO) {
                db.protectionRuleDao().getRuleByIdSync(ruleId)
            }
            if (rule != null) {
                setContactTarget(
                    name = rule.contactName,
                    phone = rule.contactPhoneNumber,
                    contactId = rule.contactId,
                    source = rule.source
                )
                binding.etDecoyIdentity.setText(rule.decoyIdentity)
                binding.etDecoyMessage.setText(rule.decoyMessage)
                binding.switchRuleSound.isChecked = rule.soundEnabled
                binding.switchRuleVibration.isChecked = rule.vibrationEnabled
                binding.switchRuleCallDisguise.isChecked = rule.isCallDisguiseEnabled
                binding.switchRuleActive.isChecked = rule.isEnabled

                existingPinHash = rule.lockPinHash
                existingPinSalt = rule.lockPinSalt
                if (rule.isCustomLockEnabled) {
                    binding.switchCustomPinEnabled.isChecked = true
                    binding.tilContactLockPin.visibility = View.VISIBLE
                    binding.etContactLockPin.setText("••••")
                } else {
                    binding.switchCustomPinEnabled.isChecked = false
                    binding.tilContactLockPin.visibility = View.GONE
                }

                updatePreview()
            }
        }
    }

    private fun saveRule() {
        if (selectedContactName.isBlank()) {
            Toast.makeText(this, "Please select or enter a contact first", Toast.LENGTH_SHORT).show()
            return
        }

        val decoyIdentity = binding.etDecoyIdentity.text?.toString()?.trim() ?: ""
        if (decoyIdentity.isBlank()) {
            binding.tilDecoyIdentity.error = "Decoy name cannot be empty"
            return
        }

        val decoyMessage = binding.etDecoyMessage.text?.toString()?.trim() ?: ""
        if (decoyMessage.isBlank()) {
            binding.tilDecoyMessage.error = "Decoy message cannot be empty"
            return
        }

        val isLockEnabled = binding.switchCustomPinEnabled.isChecked
        val enteredPin = binding.etContactLockPin.text?.toString()?.trim().orEmpty()
        var finalPinHash = ""
        var finalPinSalt = ""
        var finalIsLock = false

        if (isLockEnabled) {
            if (enteredPin.isNotBlank() && enteredPin != "••••") {
                val (h, s) = com.veil.privacy.security.SecurityManager.hashPassword(enteredPin)
                finalPinHash = h
                finalPinSalt = s
                finalIsLock = true
            } else if (existingPinHash.isNotBlank()) {
                finalPinHash = existingPinHash
                finalPinSalt = existingPinSalt
                finalIsLock = true
            }
        }

        val mode = when {
            decoyIdentity.equals("VEIL", ignoreCase = true) -> ProtectionRule.MODE_SILENT
            decoyIdentity.equals("Dynamic", ignoreCase = true) -> ProtectionRule.MODE_RANDOM_ROTATION
            decoyIdentity.isNotBlank() || decoyMessage.isNotBlank() -> ProtectionRule.MODE_CUSTOM_DECOY
            else -> ProtectionRule.MODE_GENERIC_PROTECTED
        }

        val rule = ProtectionRule(
            id = editingRuleId,
            contactId = selectedContactId,
            contactName = selectedContactName,
            contactPhoneNumber = selectedContactPhone,
            decoyIdentity = decoyIdentity,
            decoyMessage = decoyMessage,
            disguiseMode = mode,
            soundEnabled = binding.switchRuleSound.isChecked,
            vibrationEnabled = binding.switchRuleVibration.isChecked,
            isCallDisguiseEnabled = binding.switchRuleCallDisguise.isChecked,
            isEnabled = binding.switchRuleActive.isChecked,
            source = selectedSource,
            lockPinHash = finalPinHash,
            lockPinSalt = finalPinSalt,
            isCustomLockEnabled = finalIsLock,
            appPackage = appPackage
        )

        lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val normalizedPhone = ProtectionRule.normalizePhone(rule.contactPhoneNumber)
                    val duplicateByPhone = if (normalizedPhone.isNotBlank()) {
                        db.protectionRuleDao().findByNormalizedPhoneSync(normalizedPhone)
                    } else null
                    val duplicateByName = db.protectionRuleDao().findByNormalizedNameSync(
                        ProtectionRule.normalizeName(rule.contactName)
                    )
                    val duplicate = listOfNotNull(duplicateByPhone, duplicateByName)
                        .firstOrNull { it.id != editingRuleId && it.appPackage == appPackage }
                    if (duplicate != null) {
                        throw IllegalStateException("A protection rule already exists for this contact or number")
                    }

                    if (editingRuleId > 0) {
                        db.protectionRuleDao().update(rule)
                    } else {
                        db.protectionRuleDao().insert(rule)
                    }

                    // Keep only WhatsApp phone contacts in the legacy global contact tables.
                if (appPackage == com.veil.privacy.model.SupportedApp.PKG_WHATSAPP) {
                val selectedModel = SelectedContact(
                    contactId = selectedContactId,
                    displayName = selectedContactName,
                    selectedPhoneNumber = selectedContactPhone,
                    source = selectedSource,
                    isEnabled = rule.isEnabled
                )
                db.selectedContactDao().insert(selectedModel)

                val protectedModel = com.veil.privacy.model.ProtectedContact(
                    contactId = selectedContactId,
                    displayName = selectedContactName,
                    selectedPhoneNumber = selectedContactPhone,
                    source = selectedSource,
                    isEnabled = rule.isEnabled,
                    lockPinHash = finalPinHash,
                    lockPinSalt = finalPinSalt,
                    isCustomLockEnabled = finalIsLock
                )
                    db.protectedContactDao().insert(protectedModel)
                }
                }

                Toast.makeText(
                    this@RuleEditActivity,
                    "Protection rule saved for ${rule.contactName}",
                    Toast.LENGTH_SHORT
                ).show()
                finish()
            } catch (error: Exception) {
                val message = error.message
                    ?.takeIf { it.contains("already exists", ignoreCase = true) }
                    ?: "Could not save protection rule. Please try again."
                Toast.makeText(this@RuleEditActivity, message, Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun confirmDeleteRule() {
        VeilDialogs.showConfirmationDialog(
            context = this,
            title = "Delete Protection Rule",
            message = "Are you sure you want to delete the protection rule for \"$selectedContactName\"? Subsequent messages will appear undisguised.",
            confirmText = "Delete Rule",
            isDanger = true,
            onConfirm = {
                lifecycleScope.launch {
                    withContext(Dispatchers.IO) {
                        db.protectionRuleDao().deleteById(editingRuleId)
                        val normPhone = ProtectionRule.normalizePhone(selectedContactPhone)
                        if (normPhone.isNotBlank()) {
                            val pContact = db.protectedContactDao().findByNormalizedPhoneSync(normPhone)
                            if (pContact != null) db.protectedContactDao().delete(pContact)
                            val sContact = db.selectedContactDao().findByNormalizedPhoneSync(normPhone)
                            if (sContact != null) db.selectedContactDao().delete(sContact)
                        } else {
                            val pContact = db.protectedContactDao().findByNormalizedNameSync(ProtectionRule.normalizeName(selectedContactName))
                            if (pContact != null) db.protectedContactDao().delete(pContact)
                            val sContact = db.selectedContactDao().findByDisplayNameSync(selectedContactName)
                            if (sContact != null) db.selectedContactDao().delete(sContact)
                        }
                    }
                    Toast.makeText(this@RuleEditActivity, "Rule deleted", Toast.LENGTH_SHORT).show()
                    finish()
                }
            }
        )
    }
}
