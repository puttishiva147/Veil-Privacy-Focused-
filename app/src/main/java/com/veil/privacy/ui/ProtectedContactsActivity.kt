package com.veil.privacy.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.veil.privacy.R
import com.veil.privacy.databinding.ActivityProtectedContactsBinding
import com.veil.privacy.model.ProtectedContact
import com.veil.privacy.model.ProtectionRule
import com.veil.privacy.model.SelectedContact
import com.veil.privacy.storage.VeilDatabase
import com.veil.privacy.ui.adapter.ProtectedContactAdapter
import com.veil.privacy.util.ContactSelectionHelper
import com.veil.privacy.util.VeilDialogs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ProtectedContactsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityProtectedContactsBinding
    private lateinit var db: VeilDatabase
    private lateinit var adapter: ProtectedContactAdapter

    private val pickContactLauncher = registerForActivityResult(
        ActivityResultContracts.PickContact()
    ) { uri: Uri? ->
        if (uri != null) {
            handleContactPicked(uri)
        }
    }

    private val requestContactsPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            pickContactLauncher.launch(null)
        } else {
            showContactsPermissionDeniedDialog()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityProtectedContactsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        com.veil.privacy.util.InsetsUtils.applySystemBarInsets(binding.root)

        db = VeilDatabase.getInstance(this)

        setupRecyclerView()
        setupListeners()
        observeContacts()
    }

    private fun setupRecyclerView() {
        adapter = ProtectedContactAdapter(
            onToggleEnabled = { contact, isEnabled ->
                lifecycleScope.launch(Dispatchers.IO) {
                    db.protectedContactDao().update(contact.copy(isEnabled = isEnabled))
                    val normalizedPhone = ProtectedContact.normalizePhone(contact.selectedPhoneNumber)
                    val rule = if (normalizedPhone.isNotBlank()) {
                        db.protectionRuleDao().findByNormalizedPhoneSync(normalizedPhone)
                    } else {
                        db.protectionRuleDao().findByNormalizedNameSync(ProtectedContact.normalizeName(contact.displayName))
                    }
                    if (rule != null) {
                        db.protectionRuleDao().updateEnabled(rule.id, isEnabled)
                    }
                    val selected = if (normalizedPhone.isNotBlank()) {
                        db.selectedContactDao().findByNormalizedPhoneSync(normalizedPhone)
                    } else {
                        db.selectedContactDao().findByDisplayNameSync(contact.displayName)
                    }
                    if (selected != null) {
                        db.selectedContactDao().update(selected.copy(isEnabled = isEnabled))
                    }
                }
            },
            onDeleteClicked = { contact ->
                showDeleteConfirmDialog(contact)
            },
            onItemClick = { contact ->
                showEditDecoyDialog(contact)
            }
        )

        binding.rvProtectedContacts.layoutManager = LinearLayoutManager(this)
        binding.rvProtectedContacts.adapter = adapter
    }

    private fun setupListeners() {
        binding.btnBack.setOnClickListener { finish() }

        binding.btnAddContactTop.setOnClickListener {
            launchContactPickerFlow()
        }
        binding.fabAddContact.setOnClickListener {
            launchContactPickerFlow()
        }
        binding.btnEmptyAddContact.setOnClickListener {
            launchContactPickerFlow()
        }

        binding.btnShortcutLearn.setOnClickListener {
            startActivity(Intent(this, LearnContactActivity::class.java))
        }
    }

    private fun showAddContactChoiceDialog() {
        VeilDialogs.showProtectTargetChooser(
            activity = this,
            onQuickManual = { showManualEntryDialog() },
            onPickContacts = { launchContactPickerFlow() },
            onAdvancedRule = { startActivity(Intent(this, RuleEditActivity::class.java)) }
        )
    }

    private fun launchContactPickerFlow() {
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
                promptDecoyConfiguration(
                    displayName = contactInfo.displayName,
                    phoneNumber = chosenNumber,
                    contactId = contactInfo.contactId,
                    source = ProtectedContact.SOURCE_ANDROID_CONTACTS
                )
            },
            onManualFallbackRequested = {
                showManualEntryDialog(initialName = contactInfo.displayName)
            }
        )
    }

    private fun promptDecoyConfiguration(
        displayName: String,
        phoneNumber: String,
        contactId: String = "",
        source: String = ProtectedContact.SOURCE_ANDROID_CONTACTS
    ) {
        VeilDialogs.showQuickProtectBottomSheet(
            activity = this,
            initialName = displayName,
            initialPhone = phoneNumber,
            onConfirmed = { name, phone, decoyIdentity, decoyMessage, contactPin ->
                saveNewProtectedContact(
                    displayName = if (name.isNotBlank()) name else displayName,
                    phoneNumber = if (phone.isNotBlank()) phone else phoneNumber,
                    contactId = contactId,
                    decoyIdentity = decoyIdentity,
                    decoyMessage = decoyMessage,
                    source = source,
                    contactPin = contactPin
                )
            }
        )
    }

    private fun showEditDecoyDialog(contact: ProtectedContact) {
        lifecycleScope.launch {
            val normalizedPhone = ProtectedContact.normalizePhone(contact.selectedPhoneNumber)
            val existingRule = withContext(Dispatchers.IO) {
                if (normalizedPhone.isNotBlank()) {
                    db.protectionRuleDao().findByNormalizedPhoneSync(normalizedPhone)
                } else {
                    db.protectionRuleDao().findByNormalizedNameSync(ProtectedContact.normalizeName(contact.displayName))
                }
            }

            VeilDialogs.showQuickProtectBottomSheet(
                activity = this@ProtectedContactsActivity,
                initialName = contact.displayName,
                initialPhone = contact.selectedPhoneNumber,
                onConfirmed = { name, phone, decoyIdentity, decoyMessage, contactPin ->
                    saveNewProtectedContact(
                        displayName = name,
                        phoneNumber = phone,
                        contactId = contact.contactId,
                        decoyIdentity = decoyIdentity,
                        decoyMessage = decoyMessage,
                        source = contact.source,
                        contactPin = contactPin
                    )
                }
            )
        }
    }

    private fun saveNewProtectedContact(
        displayName: String,
        phoneNumber: String,
        contactId: String = "",
        decoyIdentity: String = "Work",
        decoyMessage: String = "New message",
        source: String = ProtectedContact.SOURCE_ANDROID_CONTACTS,
        contactPin: String = ""
    ) {
        lifecycleScope.launch {
            val normalizedName = ProtectedContact.normalizeName(displayName)
            val normalizedPhone = ProtectedContact.normalizePhone(phoneNumber)

            withContext(Dispatchers.IO) {
                val existing = if (normalizedPhone.isNotBlank()) {
                    db.protectedContactDao().findByNormalizedPhoneSync(normalizedPhone)
                } else {
                    db.protectedContactDao().findByNormalizedNameSync(normalizedName)
                }

                val (pinHash, pinSalt) = if (contactPin.isNotBlank()) {
                    com.veil.privacy.security.SecurityManager.hashPassword(contactPin)
                } else Pair(existing?.lockPinHash.orEmpty(), existing?.lockPinSalt.orEmpty())

                val contactToSave = existing?.copy(
                    displayName = displayName.trim(),
                    selectedPhoneNumber = phoneNumber.trim(),
                    isEnabled = true,
                    lockPinHash = pinHash,
                    lockPinSalt = pinSalt,
                    isCustomLockEnabled = pinHash.isNotBlank()
                ) ?: ProtectedContact(
                    contactId = contactId,
                    displayName = displayName.trim(),
                    selectedPhoneNumber = phoneNumber.trim(),
                    source = source,
                    isEnabled = true,
                    lockPinHash = pinHash,
                    lockPinSalt = pinSalt,
                    isCustomLockEnabled = pinHash.isNotBlank()
                )
                db.protectedContactDao().insert(contactToSave)

                val existingRule = if (normalizedPhone.isNotBlank()) {
                    db.protectionRuleDao().findByNormalizedPhoneSync(normalizedPhone)
                } else {
                    db.protectionRuleDao().findByNormalizedNameSync(normalizedName)
                }
                val ruleMode = when {
                    decoyIdentity.equals("VEIL", ignoreCase = true) -> ProtectionRule.MODE_SILENT
                    decoyIdentity.equals("Dynamic", ignoreCase = true) -> ProtectionRule.MODE_RANDOM_ROTATION
                    else -> ProtectionRule.MODE_CUSTOM_DECOY
                }
                val ruleToSave = existingRule?.copy(
                    contactName = displayName.trim(),
                    contactPhoneNumber = phoneNumber.trim(),
                    disguiseMode = ruleMode,
                    decoyIdentity = decoyIdentity,
                    decoyMessage = decoyMessage,
                    isEnabled = true
                ) ?: ProtectionRule(
                    contactId = contactId,
                    contactName = displayName.trim(),
                    contactPhoneNumber = phoneNumber.trim(),
                    disguiseMode = ruleMode,
                    decoyIdentity = decoyIdentity,
                    decoyMessage = decoyMessage,
                    isEnabled = true,
                    source = source
                )
                db.protectionRuleDao().insert(ruleToSave)

                val existingSelected = if (normalizedPhone.isNotBlank()) {
                    db.selectedContactDao().findByNormalizedPhoneSync(normalizedPhone)
                } else {
                    db.selectedContactDao().findByDisplayNameSync(displayName.trim())
                }
                val selectedToSave = existingSelected?.copy(
                    displayName = displayName.trim(),
                    selectedPhoneNumber = phoneNumber.trim(),
                    isEnabled = true
                ) ?: SelectedContact(
                    contactId = contactId,
                    displayName = displayName.trim(),
                    selectedPhoneNumber = phoneNumber.trim(),
                    source = source,
                    isEnabled = true
                )
                db.selectedContactDao().insert(selectedToSave)
            }

            Toast.makeText(
                this@ProtectedContactsActivity,
                "Protection active for « $displayName » ($decoyIdentity)",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun showManualEntryDialog(initialName: String = "") {
        VeilDialogs.showQuickProtectBottomSheet(
            activity = this,
            initialName = initialName,
            onConfirmed = { name, phone, decoyIdentity, decoyMessage, contactPin ->
                val finalDisplayName = if (name.isNotBlank()) name else phone
                saveNewProtectedContact(
                    displayName = finalDisplayName,
                    phoneNumber = phone,
                    decoyIdentity = decoyIdentity,
                    decoyMessage = decoyMessage,
                    source = ProtectedContact.SOURCE_MANUAL,
                    contactPin = contactPin
                )
            }
        )
    }

    private fun showContactsPermissionDeniedDialog() {
        VeilDialogs.showConfirmationDialog(
            context = this,
            title = getString(R.string.contact_permission_title),
            message = "VEIL requires access to contacts so you can choose a real contact and phone number from your device.\n\nYou can also enter the person's name and phone number manually without contacts permission.",
            confirmText = "Enter Manually",
            onConfirm = { showManualEntryDialog() }
        )
    }

    private fun observeContacts() {
        lifecycleScope.launch {
            db.protectedContactDao().getAllFlow().collectLatest { contacts ->
                adapter.submitList(contacts)
                val count = contacts.size

                binding.tvContactsCountSubtitle.text = "$count contact${if (count == 1) "" else "s"} protected"

                if (contacts.isEmpty()) {
                    binding.rvProtectedContacts.visibility = View.GONE
                    binding.layoutEmptyContacts.visibility = View.VISIBLE
                } else {
                    binding.rvProtectedContacts.visibility = View.VISIBLE
                    binding.layoutEmptyContacts.visibility = View.GONE
                }
            }
        }
    }

    private fun showDeleteConfirmDialog(contact: ProtectedContact) {
        VeilDialogs.showConfirmationDialog(
            context = this,
            title = getString(R.string.action_delete),
            message = getString(R.string.contacts_delete_confirm, contact.displayName),
            confirmText = "Delete",
            isDanger = true,
            onConfirm = {
                lifecycleScope.launch(Dispatchers.IO) {
                    db.protectedContactDao().delete(contact)
                    val normalizedPhone = ProtectedContact.normalizePhone(contact.selectedPhoneNumber)
                    val rule = if (normalizedPhone.isNotBlank()) {
                        db.protectionRuleDao().findByNormalizedPhoneSync(normalizedPhone)
                    } else {
                        db.protectionRuleDao().findByNormalizedNameSync(ProtectedContact.normalizeName(contact.displayName))
                    }
                    if (rule != null) {
                        db.protectionRuleDao().delete(rule)
                    }
                    val selected = if (normalizedPhone.isNotBlank()) {
                        db.selectedContactDao().findByNormalizedPhoneSync(normalizedPhone)
                    } else {
                        db.selectedContactDao().findByDisplayNameSync(contact.displayName)
                    }
                    if (selected != null) {
                        db.selectedContactDao().delete(selected)
                    }
                }
                Toast.makeText(this@ProtectedContactsActivity, "Protection removed for « ${contact.displayName} »", Toast.LENGTH_SHORT).show()
            }
        )
    }
}
