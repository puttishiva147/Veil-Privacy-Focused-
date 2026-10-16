package com.veil.privacy.ui.compose

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.provider.ContactsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.veil.privacy.model.SelectedContact

// Theme Colors matching Clean Minimalist Monochromatic Architecture (Zero Green & Zero Blue)
private val VeilCanvas = Color(0xFF0E0E10)
private val VeilSurfaceCard = Color(0xFF1A1B1F)
private val VeilSurfaceElevated = Color(0xFF22242A)
private val VeilBorder = Color(0xFF32353D)
private val VeilButtonPrimary = Color(0xFFFFFFFF)
private val VeilButtonPrimaryText = Color(0xFF0E0E10)
private val VeilAccentLight = Color(0xFFF9FAFB)
private val VeilAccentDanger = Color(0xFFEF4444)
private val VeilTextHigh = Color(0xFFF9FAFB)
private val VeilTextMedium = Color(0xFFA1A1AA)
private val VeilTextLow = Color(0xFF71717A)

private data class ParsedPhoneOption(
    val number: String,
    val label: String
)

private data class ParsedContactData(
    val contactId: String,
    val displayName: String,
    val phoneNumbers: List<ParsedPhoneOption>
)

/**
 * Composable 'ContactSelectionScreen' that:
 * 1. Handles the READ_CONTACTS permission request
 * 2. Triggers the ActivityResultContracts.PickContact intent to let users select a contact
 * 3. Parses the result into the SelectedContact data model
 * 4. Displays real-time configuration and technical transparency details
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactSelectionScreen(
    currentContact: SelectedContact? = null,
    contacts: List<SelectedContact> = currentContact?.let { listOf(it) } ?: emptyList(),
    onContactSelected: (SelectedContact) -> Unit,
    onContactRemoved: (SelectedContact) -> Unit = {},
    onNavigateBack: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    // State for disambiguation dialog if contact has multiple phone numbers
    var multiNumberOptions by remember { mutableStateOf<ParsedContactData?>(null) }
    var selectedMultiNumberIndex by remember { mutableStateOf(0) }

    // State for permission explanation dialog
    var showPermissionRationale by remember { mutableStateOf(false) }

    // State for manual contact entry dialog
    var showManualEntryDialog by remember { mutableStateOf(false) }
    var manualNameInput by remember { mutableStateOf("") }
    var manualPhoneInput by remember { mutableStateOf("") }

    // State for contact removal confirmation
    var showRemoveConfirmDialog by remember { mutableStateOf(false) }
    var contactToRemoveTarget by remember { mutableStateOf<SelectedContact?>(null) }

    // Helper function to parse Contact Uri into SelectedContact or prompt for number
    fun parseContactUriAndProcess(contactUri: Uri) {
        val parsedData = parseContactUri(context, contactUri)
        if (parsedData == null) {
            return
        }

        when {
            parsedData.phoneNumbers.size > 1 -> {
                selectedMultiNumberIndex = 0
                multiNumberOptions = parsedData
            }
            parsedData.phoneNumbers.size == 1 -> {
                val primaryNumber = parsedData.phoneNumbers.first().number
                val model = SelectedContact(
                    contactId = parsedData.contactId,
                    displayName = parsedData.displayName,
                    selectedPhoneNumber = primaryNumber,
                    normalizedPhoneNumber = SelectedContact.normalizePhone(primaryNumber),
                    source = "ANDROID_CONTACTS",
                    isEnabled = true
                )
                onContactSelected(model)
            }
            else -> {
                // No numbers found on this contact
                manualNameInput = parsedData.displayName
                manualPhoneInput = ""
                showManualEntryDialog = true
            }
        }
    }

    // 1. Android System Contact Picker Launcher
    val pickContactLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickContact()
    ) { uri: Uri? ->
        if (uri != null) {
            parseContactUriAndProcess(uri)
        }
    }

    // 2. READ_CONTACTS Permission Request Launcher
    val contactsPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            pickContactLauncher.launch(null)
        } else {
            showPermissionRationale = true
        }
    }

    // Trigger contact picker with permission check
    fun launchContactPicker() {
        val permissionState = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_CONTACTS
        )
        if (permissionState == PackageManager.PERMISSION_GRANTED) {
            pickContactLauncher.launch(null)
        } else {
            contactsPermissionLauncher.launch(Manifest.permission.READ_CONTACTS)
        }
    }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .testTag("contact_selection_screen"),
        containerColor = VeilCanvas,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Contact Target",
                        color = VeilTextHigh,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    if (onNavigateBack != null) {
                        IconButton(
                            onClick = onNavigateBack,
                            modifier = Modifier.testTag("btn_back")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = VeilTextHigh
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = VeilCanvas
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            // Header summary
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (contacts.isEmpty()) "TARGET CONFIGURATION" else "PROTECTED TARGETS (${contacts.size})",
                    color = VeilAccentLight,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )

                if (contacts.isNotEmpty()) {
                    TextButton(onClick = { launchContactPicker() }) {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = VeilButtonPrimary
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "+ Add",
                            color = VeilButtonPrimary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            if (contacts.isEmpty()) {
                // Empty State Card
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, VeilBorder, RoundedCornerShape(12.dp))
                        .testTag("card_empty_contacts"),
                    colors = CardDefaults.cardColors(containerColor = VeilSurfaceCard),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp)
                    ) {
                        Text(
                            text = "NO CONTACTS PROTECTED",
                            color = VeilTextLow,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = "No contact selected",
                            color = VeilTextHigh,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        Text(
                            text = "Select one or more contacts whose incoming WhatsApp messages and voice calls should be disguised with synthetic decoys.",
                            color = VeilTextMedium,
                            fontSize = 13.sp,
                            lineHeight = 18.sp
                        )

                        Spacer(modifier = Modifier.height(18.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Button(
                                onClick = { launchContactPicker() },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(44.dp)
                                    .testTag("btn_select_contact"),
                                shape = RoundedCornerShape(6.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = VeilButtonPrimary,
                                    contentColor = VeilButtonPrimaryText
                                ),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE5E7EB))
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Person,
                                    contentDescription = null,
                                    tint = VeilButtonPrimaryText,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Select Contact",
                                    color = VeilButtonPrimaryText,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            OutlinedButton(
                                onClick = {
                                    manualNameInput = ""
                                    manualPhoneInput = ""
                                    showManualEntryDialog = true
                                },
                                modifier = Modifier
                                    .height(44.dp)
                                    .testTag("btn_manual_entry"),
                                shape = RoundedCornerShape(6.dp),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = VeilTextHigh
                                ),
                                border = androidx.compose.foundation.BorderStroke(1.dp, VeilBorder)
                            ) {
                                Text("Manual")
                            }
                        }
                    }
                }
            } else {
                // List of All Protected Contacts
                contacts.forEachIndexed { index, contact ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, VeilBorder, RoundedCornerShape(12.dp))
                            .testTag("card_selected_contact_${index}"),
                        colors = CardDefaults.cardColors(containerColor = VeilSurfaceCard),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "TARGET #${index + 1}",
                                    color = VeilTextLow,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.sp
                                )

                                Surface(
                                    color = VeilSurfaceElevated,
                                    shape = RoundedCornerShape(4.dp)
                                ) {
                                    Text(
                                        text = if (contact.isEnabled) "ACTIVE" else "PAUSED",
                                        color = if (contact.isEnabled) VeilTextHigh else VeilTextLow,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            Text(
                                text = "« ${contact.displayName} »",
                                color = VeilTextHigh,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold
                            )

                            Spacer(modifier = Modifier.height(6.dp))

                            if (contact.selectedPhoneNumber.isNotBlank()) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Phone,
                                        contentDescription = null,
                                        tint = VeilTextMedium,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = contact.selectedPhoneNumber,
                                        color = VeilTextHigh,
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 14.sp
                                    )
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                            }

                            Text(
                                text = "Source: ${contact.source}",
                                color = VeilTextLow,
                                fontSize = 11.sp
                            )

                            Spacer(modifier = Modifier.height(16.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                OutlinedButton(
                                    onClick = { launchContactPicker() },
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(40.dp)
                                        .testTag("btn_change_contact_${index}"),
                                    shape = RoundedCornerShape(6.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        contentColor = VeilTextHigh
                                    ),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, VeilBorder)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Edit,
                                        contentDescription = null,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Add / Switch", fontSize = 12.sp)
                                }

                                Button(
                                    onClick = {
                                        contactToRemoveTarget = contact
                                        showRemoveConfirmDialog = true
                                    },
                                    modifier = Modifier
                                        .height(40.dp)
                                        .testTag("btn_remove_contact_${index}"),
                                    shape = RoundedCornerShape(6.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = VeilAccentDanger.copy(alpha = 0.15f),
                                        contentColor = VeilAccentDanger
                                    ),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, VeilAccentDanger.copy(alpha = 0.4f))
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = null,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Remove", fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }

                // Add Another Target Card
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = { launchContactPicker() },
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                            .testTag("btn_add_another_contact"),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = VeilButtonPrimary,
                            contentColor = VeilButtonPrimaryText
                        )
                    ) {
                        Icon(imageVector = Icons.Default.Person, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("+ Add Contact", fontWeight = FontWeight.Bold)
                    }

                    OutlinedButton(
                        onClick = {
                            manualNameInput = ""
                            manualPhoneInput = ""
                            showManualEntryDialog = true
                        },
                        modifier = Modifier
                            .height(44.dp)
                            .testTag("btn_add_manual_target"),
                        shape = RoundedCornerShape(8.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, VeilBorder),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = VeilTextHigh)
                    ) {
                        Text("Manual")
                    }
                }
            }

            // Architecture Transparency Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, VeilBorder.copy(alpha = 0.6f), RoundedCornerShape(12.dp)),
                colors = CardDefaults.cardColors(containerColor = VeilSurfaceElevated.copy(alpha = 0.5f)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Security,
                            contentDescription = null,
                            tint = VeilTextHigh,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Notification Listener Architecture",
                            color = VeilTextHigh,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Text(
                        text = "• Incoming alerts from WhatsApp are captured via Android's NotificationListenerService.\n" +
                               "• VEIL queries the 'SelectedContact' database to match the incoming sender's phone number digits or display name.\n" +
                               "• When matched, VEIL transforms the notification with a synthetic decoy and logs the original alert securely to your Private Vault.\n" +
                               "• Zero telemetry: Contact selection and matching runs entirely on-device.",
                        color = VeilTextMedium,
                        fontSize = 12.sp,
                        lineHeight = 17.sp
                    )
                }
            }
        }
    }

    // Dialog: Multiple Phone Numbers Selection
    if (multiNumberOptions != null) {
        val options = multiNumberOptions!!
        AlertDialog(
            onDismissRequest = { multiNumberOptions = null },
            containerColor = VeilSurfaceCard,
            title = {
                Text(
                    text = "Select Phone Number",
                    color = VeilTextHigh,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column {
                    Text(
                        text = "Multiple numbers were found for \"${options.displayName}\". Choose which number to protect:",
                        color = VeilTextMedium,
                        fontSize = 13.sp
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    options.phoneNumbers.forEachIndexed { index, phoneOption ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = (index == selectedMultiNumberIndex),
                                onClick = { selectedMultiNumberIndex = index },
                                colors = RadioButtonDefaults.colors(
                                    selectedColor = VeilAccentLight,
                                    unselectedColor = VeilTextLow
                                )
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Column {
                                Text(
                                    text = phoneOption.number,
                                    color = VeilTextHigh,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 14.sp
                                )
                                Text(
                                    text = phoneOption.label,
                                    color = VeilTextLow,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val chosen = options.phoneNumbers[selectedMultiNumberIndex]
                        val model = SelectedContact(
                            contactId = options.contactId,
                            displayName = options.displayName,
                            selectedPhoneNumber = chosen.number,
                            normalizedPhoneNumber = SelectedContact.normalizePhone(chosen.number),
                            source = "ANDROID_CONTACTS",
                            isEnabled = true
                        )
                        multiNumberOptions = null
                        onContactSelected(model)
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = VeilButtonPrimary,
                        contentColor = VeilButtonPrimaryText
                    )
                ) {
                    Text("Confirm", color = VeilButtonPrimaryText, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { multiNumberOptions = null }) {
                    Text("Cancel", color = VeilTextLow)
                }
            }
        )
    }

    // Dialog: Permission Rationale
    if (showPermissionRationale) {
        AlertDialog(
            onDismissRequest = { showPermissionRationale = false },
            containerColor = VeilSurfaceCard,
            icon = {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    tint = VeilTextHigh
                )
            },
            title = {
                Text(
                    text = "Contacts Permission Required",
                    color = VeilTextHigh,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = "Android requires contacts access so you can pick a contact from your phonebook. VEIL never transmits contacts off your device.\n\n" +
                           "You can also choose to enter the contact's name and phone number manually.",
                    color = VeilTextMedium,
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showPermissionRationale = false
                        contactsPermissionLauncher.launch(Manifest.permission.READ_CONTACTS)
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = VeilButtonPrimary,
                        contentColor = VeilButtonPrimaryText
                    )
                ) {
                    Text("Grant Permission", color = VeilButtonPrimaryText, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showPermissionRationale = false
                        showManualEntryDialog = true
                    }
                ) {
                    Text("Enter Manually", color = VeilTextHigh)
                }
            }
        )
    }

    // Dialog: Manual Entry Fallback
    if (showManualEntryDialog) {
        AlertDialog(
            onDismissRequest = { showManualEntryDialog = false },
            containerColor = VeilSurfaceCard,
            title = {
                Text(
                    text = "Add Contact Manually",
                    color = VeilTextHigh,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Enter the contact's display name and phone number as formatted in WhatsApp:",
                        color = VeilTextMedium,
                        fontSize = 12.sp
                    )

                    OutlinedTextField(
                        value = manualNameInput,
                        onValueChange = { manualNameInput = it },
                        label = { Text("Display Name") },
                        placeholder = { Text("e.g. Jane Doe") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = VeilTextHigh,
                            unfocusedBorderColor = VeilBorder,
                            focusedTextColor = VeilTextHigh,
                            unfocusedTextColor = VeilTextHigh
                        ),
                        modifier = Modifier.fillMaxWidth().testTag("input_manual_name")
                    )

                    OutlinedTextField(
                        value = manualPhoneInput,
                        onValueChange = { manualPhoneInput = it },
                        label = { Text("Phone Number") },
                        placeholder = { Text("e.g. +1 555-019-2834") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = VeilTextHigh,
                            unfocusedBorderColor = VeilBorder,
                            focusedTextColor = VeilTextHigh,
                            unfocusedTextColor = VeilTextHigh
                        ),
                        modifier = Modifier.fillMaxWidth().testTag("input_manual_phone")
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val name = manualNameInput.trim()
                        val phone = manualPhoneInput.trim()
                        if (name.isNotBlank() || phone.isNotBlank()) {
                            val finalDisplayName = if (name.isNotBlank()) name else phone
                            val model = SelectedContact(
                                displayName = finalDisplayName,
                                selectedPhoneNumber = phone,
                                normalizedPhoneNumber = SelectedContact.normalizePhone(phone),
                                source = "MANUAL",
                                isEnabled = true
                            )
                            showManualEntryDialog = false
                            onContactSelected(model)
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = VeilButtonPrimary,
                        contentColor = VeilButtonPrimaryText
                    ),
                    enabled = (manualNameInput.isNotBlank() || manualPhoneInput.isNotBlank()),
                    modifier = Modifier.testTag("btn_confirm_manual")
                ) {
                    Text("Save Target", color = VeilButtonPrimaryText, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showManualEntryDialog = false }) {
                    Text("Cancel", color = VeilTextLow)
                }
            }
        )
    }

    // Dialog: Remove Confirmation
    val targetToRemove = contactToRemoveTarget ?: currentContact
    if (showRemoveConfirmDialog && targetToRemove != null) {
        AlertDialog(
            onDismissRequest = {
                showRemoveConfirmDialog = false
                contactToRemoveTarget = null
            },
            containerColor = VeilSurfaceCard,
            title = {
                Text(
                    text = "Remove Protected Contact",
                    color = VeilTextHigh,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = "Remove \"${targetToRemove.displayName}\" from VEIL protection? Subsequent WhatsApp notifications from this person will appear undisguised.",
                    color = VeilTextMedium,
                    fontSize = 13.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showRemoveConfirmDialog = false
                        onContactRemoved(targetToRemove)
                        contactToRemoveTarget = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = VeilAccentDanger)
                ) {
                    Text("Remove", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showRemoveConfirmDialog = false
                    contactToRemoveTarget = null
                }) {
                    Text("Cancel", color = VeilTextLow)
                }
            }
        )
    }
}

/**
 * Parses a Contact Uri from Android's Contact Picker into a [ParsedContactData] object
 * containing the contactId, displayName, and all associated phone numbers.
 */
private fun parseContactUri(context: Context, contactUri: Uri): ParsedContactData? {
    val contentResolver = context.contentResolver
    var contactId = ""
    var displayName = ""

    // 1. Query contact ID and display name
    var cursor: Cursor? = null
    try {
        cursor = contentResolver.query(
            contactUri,
            arrayOf(
                ContactsContract.Contacts._ID,
                ContactsContract.Contacts.DISPLAY_NAME_PRIMARY
            ),
            null,
            null,
            null
        )

        if (cursor != null && cursor.moveToFirst()) {
            val idIndex = cursor.getColumnIndex(ContactsContract.Contacts._ID)
            val nameIndex = cursor.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME_PRIMARY)

            if (idIndex != -1) contactId = cursor.getString(idIndex) ?: ""
            if (nameIndex != -1) displayName = cursor.getString(nameIndex) ?: ""
        }
    } catch (_: Exception) {
        // Handled gracefully
    } finally {
        cursor?.close()
    }

    if (contactId.isBlank() && displayName.isBlank()) {
        return null
    }

    // 2. Query all phone numbers for this contactId
    val phoneNumbers = mutableListOf<ParsedPhoneOption>()
    if (contactId.isNotBlank()) {
        var phoneCursor: Cursor? = null
        try {
            phoneCursor = contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                arrayOf(
                    ContactsContract.CommonDataKinds.Phone.NUMBER,
                    ContactsContract.CommonDataKinds.Phone.TYPE,
                    ContactsContract.CommonDataKinds.Phone.LABEL
                ),
                "${ContactsContract.CommonDataKinds.Phone.CONTACT_ID} = ?",
                arrayOf(contactId),
                null
            )

            if (phoneCursor != null) {
                val numIndex = phoneCursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                val typeIndex = phoneCursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.TYPE)
                val labelIndex = phoneCursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.LABEL)

                while (phoneCursor.moveToNext()) {
                    val number = if (numIndex != -1) phoneCursor.getString(numIndex) ?: "" else ""
                    val type = if (typeIndex != -1) phoneCursor.getInt(typeIndex) else ContactsContract.CommonDataKinds.Phone.TYPE_OTHER
                    val customLabel = if (labelIndex != -1) phoneCursor.getString(labelIndex) ?: "" else ""

                    if (number.isNotBlank()) {
                        val label = ContactsContract.CommonDataKinds.Phone.getTypeLabel(
                            context.resources,
                            type,
                            customLabel
                        ).toString()
                        phoneNumbers.add(ParsedPhoneOption(number = number.trim(), label = label))
                    }
                }
            }
        } catch (_: Exception) {
            // Handled gracefully
        } finally {
            phoneCursor?.close()
        }
    }

    return ParsedContactData(
        contactId = contactId,
        displayName = if (displayName.isNotBlank()) displayName else "Selected Contact",
        phoneNumbers = phoneNumbers.distinctBy { it.number.replace("[^0-9]".toRegex(), "") }
    )
}
