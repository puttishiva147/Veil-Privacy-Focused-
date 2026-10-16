package com.veil.privacy.ui

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.lifecycleScope
import com.veil.privacy.model.ProtectedContact
import com.veil.privacy.model.ProtectionRule
import com.veil.privacy.model.SelectedContact
import com.veil.privacy.storage.VeilDatabase
import com.veil.privacy.ui.compose.ContactSelectionScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Activity hosting the [ContactSelectionScreen] Composable.
 */
class ContactSelectionActivity : ComponentActivity() {

    private lateinit var db: VeilDatabase

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        db = VeilDatabase.getInstance(this)

        setContent {
            val contacts by db.selectedContactDao().getAllFlow().collectAsState(initial = emptyList())
            val currentContact = contacts.firstOrNull()

            ContactSelectionScreen(
                currentContact = currentContact,
                contacts = contacts,
                onContactSelected = { selected ->
                    saveSelectedContact(selected)
                },
                onContactRemoved = { selected ->
                    removeSelectedContact(selected)
                },
                onNavigateBack = {
                    finish()
                }
            )
        }
    }

    private fun saveSelectedContact(selected: SelectedContact) {
        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                db.selectedContactDao().insert(selected)

                val protectedEquivalent = ProtectedContact(
                    contactId = selected.contactId,
                    displayName = selected.displayName,
                    selectedPhoneNumber = selected.selectedPhoneNumber,
                    source = selected.source,
                    isEnabled = selected.isEnabled
                )
                db.protectedContactDao().insert(protectedEquivalent)

                val ruleEquivalent = ProtectionRule(
                    contactId = selected.contactId,
                    contactName = selected.displayName,
                    contactPhoneNumber = selected.selectedPhoneNumber,
                    disguiseMode = com.veil.privacy.model.ProtectionRule.MODE_RANDOM_ROTATION,
                    decoyIdentity = "Dynamic",
                    decoyMessage = "Dynamic disguise • Rotates on arrival",
                    isEnabled = selected.isEnabled,
                    source = selected.source
                )
                db.protectionRuleDao().insert(ruleEquivalent)
            }
            Toast.makeText(
                this@ContactSelectionActivity,
                "Protected contact configured: ${selected.displayName}",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun removeSelectedContact(selected: SelectedContact) {
        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                db.selectedContactDao().delete(selected)
                val normPhone = selected.normalizedPhoneNumber
                if (normPhone.isNotBlank()) {
                    val pContact = db.protectedContactDao().findByNormalizedPhoneSync(normPhone)
                    if (pContact != null) db.protectedContactDao().delete(pContact)
                    val rule = db.protectionRuleDao().findByNormalizedPhoneSync(normPhone)
                    if (rule != null) db.protectionRuleDao().delete(rule)
                } else {
                    val pContact = db.protectedContactDao().findByNormalizedNameSync(com.veil.privacy.model.ProtectedContact.normalizeName(selected.displayName))
                    if (pContact != null) db.protectedContactDao().delete(pContact)
                    val rule = db.protectionRuleDao().findByNormalizedNameSync(com.veil.privacy.model.ProtectedContact.normalizeName(selected.displayName))
                    if (rule != null) db.protectionRuleDao().delete(rule)
                }
            }
            Toast.makeText(
                this@ContactSelectionActivity,
                "Protected contact removed",
                Toast.LENGTH_SHORT
            ).show()
        }
    }
}
