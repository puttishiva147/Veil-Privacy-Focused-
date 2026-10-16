package com.veil.privacy.util

import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.ContactsContract

data class ContactPhoneNumber(
    val number: String,
    val typeLabel: String
)

data class PickedContactInfo(
    val contactId: String,
    val displayName: String,
    val phoneNumbers: List<ContactPhoneNumber>,
    val photoUri: String = ""
)

object ContactSelectionHelper {

    /**
     * Resolves the selected contact's ID, display name, photo URI, and all associated phone numbers
     * from the Uri returned by Android's Contact Picker.
     */
    fun resolveContactFromUri(context: Context, contactUri: Uri): PickedContactInfo? {
        val contentResolver = context.contentResolver
        var contactId = ""
        var displayName = ""
        var photoUri = ""

        // 1. Query contact header info
        var cursor: Cursor? = null
        try {
            cursor = contentResolver.query(
                contactUri,
                arrayOf(
                    ContactsContract.Contacts._ID,
                    ContactsContract.Contacts.DISPLAY_NAME_PRIMARY,
                    ContactsContract.Contacts.PHOTO_URI
                ),
                null,
                null,
                null
            )

            if (cursor != null && cursor.moveToFirst()) {
                val idIndex = cursor.getColumnIndex(ContactsContract.Contacts._ID)
                val nameIndex = cursor.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME_PRIMARY)
                val photoIndex = cursor.getColumnIndex(ContactsContract.Contacts.PHOTO_URI)

                if (idIndex != -1) {
                    contactId = cursor.getString(idIndex) ?: ""
                }
                if (nameIndex != -1) {
                    displayName = cursor.getString(nameIndex) ?: ""
                }
                if (photoIndex != -1) {
                    photoUri = cursor.getString(photoIndex) ?: ""
                }
            }
        } catch (_: Exception) {
            // Permission or resolution error
        } finally {
            cursor?.close()
        }

        if (contactId.isBlank() && displayName.isBlank()) {
            return null
        }

        // 2. Query all phone numbers associated with this contact
        val phoneNumbers = mutableListOf<ContactPhoneNumber>()
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
                            phoneNumbers.add(ContactPhoneNumber(number = number.trim(), typeLabel = label))
                        }
                    }
                }
            } catch (_: Exception) {
                // CommonDataKinds query error
            } finally {
                phoneCursor?.close()
            }
        }

        return PickedContactInfo(
            contactId = contactId,
            displayName = if (displayName.isNotBlank()) displayName else "Selected Contact",
            phoneNumbers = phoneNumbers.distinctBy { it.number.replace("[^0-9]".toRegex(), "") },
            photoUri = photoUri
        )
    }

    /**
     * Prompts the user to select one phone number if the contact has multiple numbers,
     * or selects the single number directly.
     */
    fun promptNumberSelection(
        context: Context,
        contactInfo: PickedContactInfo,
        onNumberSelected: (selectedNumber: String) -> Unit,
        onManualFallbackRequested: () -> Unit
    ) {
        val numbers = contactInfo.phoneNumbers

        if (numbers.isEmpty()) {
            VeilDialogs.showConfirmationDialog(
                context = context,
                title = "No Phone Number Found",
                message = "No phone numbers were found for \"${contactInfo.displayName}\".\n\nWhatsApp notifications are typically sent from phone numbers. Would you like to enter their phone number manually?",
                confirmText = "Enter Manually",
                onConfirm = onManualFallbackRequested
            )
            return
        }

        if (numbers.size == 1) {
            onNumberSelected(numbers.first().number)
            return
        }

        // Multiple numbers: show modern bottom sheet with selectable cards
        VeilDialogs.showSelectPhoneNumberSheet(
            context = context,
            contactName = contactInfo.displayName,
            numbers = numbers,
            onSelected = onNumberSelected
        )
    }
}
