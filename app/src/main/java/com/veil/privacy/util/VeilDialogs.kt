package com.veil.privacy.util

import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.widget.doAfterTextChanged
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.veil.privacy.R

/**
 * Next-Generation UI/UX Dialog & Modal Sheet System for VEIL.
 *
 * Eliminates all legacy Android 8 / Holo / Material 1 system dialogs and replaces them with
 * bespoke, high-polish Material 3 bottom sheets and OLED modal cards adhering strictly to the
 * VEIL Cybernetic dark design language.
 */
object VeilDialogs {

    /**
     * Modern BottomSheet chooser for protecting WhatsApp targets.
     * Replaces the archaic AlertDialog.setItems(...) option list.
     */
    fun showProtectTargetChooser(
        activity: Activity,
        onQuickManual: () -> Unit,
        onPickContacts: () -> Unit,
        onAdvancedRule: () -> Unit
    ) {
        val dialog = BottomSheetDialog(activity, R.style.Theme_Veil_BottomSheetDialog)
        val view = LayoutInflater.from(activity).inflate(R.layout.bottom_sheet_protect_chooser, null)
        dialog.setContentView(view)

        view.findViewById<MaterialCardView>(R.id.cardQuickProtect).setOnClickListener {
            dialog.dismiss()
            onQuickManual()
        }

        view.findViewById<MaterialCardView>(R.id.cardImportContacts).setOnClickListener {
            dialog.dismiss()
            onPickContacts()
        }

        view.findViewById<MaterialCardView>(R.id.cardAdvancedRule).setOnClickListener {
            dialog.dismiss()
            onAdvancedRule()
        }

        view.findViewById<MaterialButton>(R.id.btnCloseChooser).setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }

    /**
     * Modern BottomSheet for quick-protecting a WhatsApp contact with real-time persona preview.
     * Replaces the bare unstyled programmatic EditText AlertDialog.
     */
    /**
     * Modern BottomSheet for quick-protecting a WhatsApp contact with real-time persona preview
     * and optional independent contact PIN.
     */
    fun showQuickProtectBottomSheet(
        activity: Activity,
        initialName: String = "",
        initialPhone: String = "",
        onConfirmed: (name: String, phone: String, decoyIdentity: String, decoyMessage: String) -> Unit
    ) {
        showQuickProtectBottomSheet(
            activity = activity,
            initialName = initialName,
            initialPhone = initialPhone,
            initialPin = "",
            onConfirmed = { name, phone, decoyId, decoyMsg, _ ->
                onConfirmed(name, phone, decoyId, decoyMsg)
            }
        )
    }

    fun showQuickProtectBottomSheet(
        activity: Activity,
        initialName: String = "",
        initialPhone: String = "",
        initialPin: String = "",
        targetNameHint: String = "WhatsApp Contact or Chat Name *",
        targetPhoneHint: String = "Phone Number (Optional - High Confidence)",
        showPhoneField: Boolean = true,
        onConfirmed: (name: String, phone: String, decoyIdentity: String, decoyMessage: String, contactPin: String) -> Unit
    ) {
        val dialog = BottomSheetDialog(activity, R.style.Theme_Veil_BottomSheetDialog)
        val view = LayoutInflater.from(activity).inflate(R.layout.bottom_sheet_quick_protect, null)
        dialog.setContentView(view)

        dialog.window?.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)

        val tilName = view.findViewById<TextInputLayout>(R.id.tilContactName)
        val etName = view.findViewById<TextInputEditText>(R.id.etContactName)
        val tilPhone = view.findViewById<TextInputLayout>(R.id.tilContactPhone)
        val etPhone = view.findViewById<TextInputEditText>(R.id.etContactPhone)
        val etPin = view.findViewById<TextInputEditText>(R.id.etContactPin)
        tilName.hint = targetNameHint
        tilPhone.hint = targetPhoneHint
        tilPhone.visibility = if (showPhoneField) View.VISIBLE else View.GONE
        val chipGroup = view.findViewById<ChipGroup>(R.id.chipGroupPersona)
        val tvPreviewTitle = view.findViewById<TextView>(R.id.tvPreviewDecoyTitle)
        val tvPreviewBody = view.findViewById<TextView>(R.id.tvPreviewDecoyBody)
        val btnCancel = view.findViewById<MaterialButton>(R.id.btnCancelQuickProtect)
        val btnConfirm = view.findViewById<MaterialButton>(R.id.btnConfirmQuickProtect)

        if (initialName.isNotBlank()) etName.setText(initialName)
        if (initialPhone.isNotBlank()) etPhone.setText(initialPhone)
        if (initialPin.isNotBlank()) etPin?.setText(initialPin)

        var selectedDecoyName = "Dynamic"
        var selectedDecoyMsg = "Dynamic disguise • Rotates on arrival"

        fun updatePreview() {
            tvPreviewTitle.text = selectedDecoyName
            tvPreviewBody.text = selectedDecoyMsg
        }

        chipGroup.setOnCheckedStateChangeListener { _, checkedIds ->
            when (checkedIds.firstOrNull()) {
                R.id.chipPersonaDynamic -> {
                    selectedDecoyName = "Dynamic"
                    selectedDecoyMsg = "Dynamic disguise • Rotates on arrival"
                }
                R.id.chipPersonaWork -> {
                    selectedDecoyName = "Work"
                    selectedDecoyMsg = "Work disguise • Rotates on arrival"
                }
                R.id.chipPersonaCalendar -> {
                    selectedDecoyName = "Calendar"
                    selectedDecoyMsg = "Calendar disguise • Rotates on arrival"
                }
                R.id.chipPersonaDelivery -> {
                    selectedDecoyName = "Delivery"
                    selectedDecoyMsg = "Delivery disguise • Rotates on arrival"
                }
                R.id.chipPersonaSystem -> {
                    selectedDecoyName = "System"
                    selectedDecoyMsg = "System disguise • Rotates on arrival"
                }
                R.id.chipPersonaStealth -> {
                    selectedDecoyName = "VEIL"
                    selectedDecoyMsg = "Protected message received"
                }
            }
            updatePreview()
        }

        btnCancel.setOnClickListener { dialog.dismiss() }

        btnConfirm.setOnClickListener {
            val name = etName.text?.toString()?.trim().orEmpty()
            val phone = etPhone.text?.toString()?.trim().orEmpty()
            val pin = etPin?.text?.toString()?.trim().orEmpty()

            if (name.isBlank() && phone.isBlank()) {
                tilName.error = "Please enter a contact name or phone number"
                return@setOnClickListener
            }

            tilName.error = null
            dialog.dismiss()
            onConfirmed(name, phone, selectedDecoyName, selectedDecoyMsg, pin)
        }

        dialog.show()
    }

    /**
     * Modern BottomSheet replacing Android 8 single choice radio list for selecting phone numbers.
     */
    fun showSelectPhoneNumberSheet(
        context: Context,
        contactName: String,
        numbers: List<ContactPhoneNumber>,
        onSelected: (String) -> Unit
    ) {
        val dialog = BottomSheetDialog(context, R.style.Theme_Veil_BottomSheetDialog)
        val view = LayoutInflater.from(context).inflate(R.layout.bottom_sheet_select_phone, null)
        dialog.setContentView(view)

        view.findViewById<TextView>(R.id.tvContactDisplayName).text =
            "Select target number for « $contactName »"

        val container = view.findViewById<LinearLayout>(R.id.layoutPhoneNumbersList)
        var selectedNumber = numbers.firstOrNull()?.number.orEmpty()
        val cards = mutableListOf<MaterialCardView>()

        for ((index, item) in numbers.withIndex()) {
            val card = MaterialCardView(context).apply {
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    topMargin = if (index == 0) 0 else (8 * resources.displayMetrics.density).toInt()
                }
                radius = 10 * resources.displayMetrics.density
                strokeWidth = (1 * resources.displayMetrics.density).toInt()
                isClickable = true
                isFocusable = true
            }

            val cardContent = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(
                    (14 * resources.displayMetrics.density).toInt(),
                    (12 * resources.displayMetrics.density).toInt(),
                    (14 * resources.displayMetrics.density).toInt(),
                    (12 * resources.displayMetrics.density).toInt()
                )
            }

            val textLayout = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            }

            val tvNumber = TextView(context).apply {
                text = item.number
                setTextColor(ContextCompat.getColor(context, R.color.veil_text_high))
                textSize = 14f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
            }

            val tvLabel = TextView(context).apply {
                text = item.typeLabel.uppercase()
                setTextColor(ContextCompat.getColor(context, R.color.veil_text_medium))
                textSize = 10f
                fontFeatureSettings = "monospace"
            }

            textLayout.addView(tvNumber)
            textLayout.addView(tvLabel)

            val ivIndicator = ImageView(context).apply {
                layoutParams = LinearLayout.LayoutParams(
                    (20 * resources.displayMetrics.density).toInt(),
                    (20 * resources.displayMetrics.density).toInt()
                )
                setImageResource(R.drawable.ic_check_circle)
            }

            cardContent.addView(textLayout)
            cardContent.addView(ivIndicator)
            card.addView(cardContent)

            fun updateCardSelection(isSelected: Boolean) {
                if (isSelected) {
                    card.setCardBackgroundColor(ContextCompat.getColor(context, R.color.veil_surface_card))
                    card.strokeColor = ContextCompat.getColor(context, R.color.veil_accent_secure)
                    ivIndicator.visibility = View.VISIBLE
                    ivIndicator.imageTintList = ColorStateList.valueOf(ContextCompat.getColor(context, R.color.veil_accent_secure))
                } else {
                    card.setCardBackgroundColor(ContextCompat.getColor(context, R.color.veil_surface))
                    card.strokeColor = ContextCompat.getColor(context, R.color.veil_hairline)
                    ivIndicator.visibility = View.INVISIBLE
                }
            }

            updateCardSelection(index == 0)
            card.setOnClickListener {
                selectedNumber = item.number
                cards.forEachIndexed { i, c ->
                    val isCurrent = (i == index)
                    c.strokeColor = if (isCurrent) ContextCompat.getColor(context, R.color.veil_accent_secure) else ContextCompat.getColor(context, R.color.veil_hairline)
                    c.setCardBackgroundColor(if (isCurrent) ContextCompat.getColor(context, R.color.veil_surface_card) else ContextCompat.getColor(context, R.color.veil_surface))
                    val indicator = (c.getChildAt(0) as LinearLayout).getChildAt(1) as ImageView
                    indicator.visibility = if (isCurrent) View.VISIBLE else View.INVISIBLE
                }
            }

            cards.add(card)
            container.addView(card)
        }

        view.findViewById<MaterialButton>(R.id.btnCancelSelectPhone).setOnClickListener { dialog.dismiss() }
        view.findViewById<MaterialButton>(R.id.btnConfirmSelectPhone).setOnClickListener {
            if (selectedNumber.isNotBlank()) {
                dialog.dismiss()
                onSelected(selectedNumber)
            }
        }

        dialog.show()
    }

    /**
     * Next-generation modal confirmation dialog matching VEIL's cyber dark aesthetic.
     * Replaces standard AlertDialog.Builder across activities.
     */
    fun showConfirmationDialog(
        context: Context,
        title: String,
        message: String,
        confirmText: String = "Confirm",
        cancelText: String = "Cancel",
        isDanger: Boolean = false,
        onConfirm: () -> Unit
    ) {
        val dialog = Dialog(context)
        val view = LayoutInflater.from(context).inflate(R.layout.dialog_veil_confirm, null)
        dialog.setContentView(view)

        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        val maxDialogWidth = (440 * context.resources.displayMetrics.density).toInt()
        val dialogWidth = minOf((context.resources.displayMetrics.widthPixels * 0.90).toInt(), maxDialogWidth)
        dialog.window?.setLayout(dialogWidth, ViewGroup.LayoutParams.WRAP_CONTENT)

        val ivIcon = view.findViewById<ImageView>(R.id.ivDialogIcon)
        val tvTitle = view.findViewById<TextView>(R.id.tvDialogTitle)
        val tvMessage = view.findViewById<TextView>(R.id.tvDialogMessage)
        val btnCancel = view.findViewById<MaterialButton>(R.id.btnDialogCancel)
        val btnConfirm = view.findViewById<MaterialButton>(R.id.btnDialogConfirm)

        tvTitle.text = title
        tvMessage.text = message
        btnConfirm.text = confirmText
        btnCancel.text = cancelText

        if (isDanger) {
            ivIcon.setImageResource(R.drawable.ic_delete_trash)
            ivIcon.imageTintList = ColorStateList.valueOf(ContextCompat.getColor(context, R.color.veil_accent_danger))
            btnConfirm.backgroundTintList = ColorStateList.valueOf(
                ContextCompat.getColor(context, R.color.veil_accent_danger)
            )
            btnConfirm.setTextColor(ContextCompat.getColor(context, R.color.veil_button_primary_text))
        } else {
            ivIcon.setImageResource(R.drawable.ic_shield_status)
            ivIcon.imageTintList = ColorStateList.valueOf(ContextCompat.getColor(context, R.color.veil_accent_secure))
        }

        btnCancel.setOnClickListener { dialog.dismiss() }
        btnConfirm.setOnClickListener {
            dialog.dismiss()
            onConfirm()
        }

        dialog.show()
    }

    /**
     * Next-generation modal information dialog for announcements and architectural notices.
     */
    fun showInfoDialog(
        context: Context,
        title: String,
        message: String,
        buttonText: String = "Understood",
        onAcknowledge: (() -> Unit)? = null
    ) {
        val dialog = Dialog(context)
        val view = LayoutInflater.from(context).inflate(R.layout.dialog_veil_confirm, null)
        dialog.setContentView(view)

        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        val maxDialogWidth = (440 * context.resources.displayMetrics.density).toInt()
        val dialogWidth = minOf((context.resources.displayMetrics.widthPixels * 0.90).toInt(), maxDialogWidth)
        dialog.window?.setLayout(dialogWidth, ViewGroup.LayoutParams.WRAP_CONTENT)

        val ivIcon = view.findViewById<ImageView>(R.id.ivDialogIcon)
        val tvTitle = view.findViewById<TextView>(R.id.tvDialogTitle)
        val tvMessage = view.findViewById<TextView>(R.id.tvDialogMessage)
        val btnCancel = view.findViewById<MaterialButton>(R.id.btnDialogCancel)
        val btnConfirm = view.findViewById<MaterialButton>(R.id.btnDialogConfirm)

        tvTitle.text = title
        tvMessage.text = message
        btnConfirm.text = buttonText
        btnCancel.visibility = View.GONE

        ivIcon.setImageResource(R.drawable.ic_veil_visor_glyph)
        ivIcon.imageTintList = ColorStateList.valueOf(ContextCompat.getColor(context, R.color.veil_accent_secure))

        btnConfirm.setOnClickListener {
            dialog.dismiss()
            onAcknowledge?.invoke()
        }

        dialog.show()
    }

    /**
     * Dedicated Modal Dialog for verifying an independent contact-specific PIN.
     * Prevents access to this specific contact's protected chat without the correct PIN.
     */
    fun showContactPinUnlockDialog(
        activity: Activity,
        contactName: String,
        storedPinHash: String,
        storedPinSalt: String,
        onUnlocked: () -> Unit,
        onDismiss: () -> Unit = {}
    ) {
        val dialog = Dialog(activity)
        val view = LayoutInflater.from(activity).inflate(R.layout.dialog_contact_pin_unlock, null)
        dialog.setContentView(view)

        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        val maxDialogWidth = (440 * activity.resources.displayMetrics.density).toInt()
        val dialogWidth = minOf((activity.resources.displayMetrics.widthPixels * 0.90).toInt(), maxDialogWidth)
        dialog.window?.setLayout(dialogWidth, ViewGroup.LayoutParams.WRAP_CONTENT)

        val tvSubtitle = view.findViewById<TextView>(R.id.tvContactTargetSubtitle)
        val tvPrompt = view.findViewById<TextView>(R.id.tvUnlockPrompt)
        val tilPin = view.findViewById<TextInputLayout>(R.id.tilContactUnlockPin)
        val etPin = view.findViewById<TextInputEditText>(R.id.etContactUnlockPin)
        val btnCancel = view.findViewById<MaterialButton>(R.id.btnCancelUnlock)
        val btnConfirm = view.findViewById<MaterialButton>(R.id.btnConfirmUnlock)

        tvSubtitle.text = "Target: « $contactName »"
        tvPrompt.text = "This conversation requires the independent PIN set for $contactName."

        fun attemptUnlock() {
            val entered = etPin.text?.toString()?.trim().orEmpty()
            if (entered.isBlank()) {
                tilPin.error = "Please enter contact PIN"
                return
            }

            val isValid = com.veil.privacy.security.SecurityManager.verifyContactPin(
                enteredPin = entered,
                storedHash = storedPinHash,
                storedSaltHex = storedPinSalt
            )

            if (isValid) {
                com.veil.privacy.storage.VeilPrefs.unlockContact(contactName)
                dialog.dismiss()
                onUnlocked()
            } else {
                tilPin.error = "Incorrect contact PIN"
                etPin.setText("")
            }
        }

        btnCancel.setOnClickListener { dialog.dismiss() }
        btnConfirm.setOnClickListener { attemptUnlock() }

        etPin.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_DONE ||
                actionId == android.view.inputmethod.EditorInfo.IME_ACTION_GO) {
                attemptUnlock()
                true
            } else false
        }

        dialog.setOnDismissListener {
            onDismiss()
        }

        dialog.show()
    }
}
