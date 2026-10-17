package com.veil.privacy.ui

import android.app.PendingIntent
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.veil.privacy.R
import com.veil.privacy.databinding.ActivityProtectedChatConversationBinding
import com.veil.privacy.model.ProtectedContact
import com.veil.privacy.model.ProtectedEvent
import com.veil.privacy.notification.NotificationReplyRegistry
import com.veil.privacy.storage.VeilDatabase
import com.veil.privacy.storage.VeilPrefs
import com.veil.privacy.ui.adapter.ProtectedChatAdapter
import com.veil.privacy.util.InsetsUtils
import com.veil.privacy.util.VeilDialogs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Dedicated conversation interface for a specific protected contact.
 *
 * Implements genuine Android & WhatsApp privacy layer capabilities:
 * - Independent contact authentication check (PIN protection).
 * - Displays incoming messages captured securely via NotificationListenerService.
 * - Outgoing messages dispatched via Android's RemoteInput PendingIntent or direct WhatsApp intent.
 * - Receive and respond to WhatsApp calls (Answer/Decline via intercepted notification actions).
 * - Send stickers, emojis, and media attachments via Android Photo Picker and WhatsApp share.
 * - Displays contact profile photos from Android address book.
 */
class ProtectedChatConversationActivity : AppCompatActivity() {

    private lateinit var binding: ActivityProtectedChatConversationBinding
    private lateinit var database: VeilDatabase
    private lateinit var chatAdapter: ProtectedChatAdapter

    private var contactName: String = ""
    private var conversationId: String = ""
    private var contactPhoneNumber: String = ""
    private var isUnlockedForCurrentSession: Boolean = false

    private val pickVisualMediaLauncher = registerForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            shareMediaOrStickerToWhatsApp(uri)
        }
    }

    companion object {
        const val EXTRA_CONTACT_NAME = "extra_contact_name"
        const val EXTRA_EVENT_ID = "extra_event_id"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityProtectedChatConversationBinding.inflate(layoutInflater)
        setContentView(binding.root)

        InsetsUtils.applySystemBarInsets(binding.root)

        contactName = intent.getStringExtra(EXTRA_CONTACT_NAME)?.trim() ?: "Protected Contact"
        conversationId = contactName.lowercase().trim()
        database = VeilDatabase.getInstance(this)

        setupUI()
        verifyContactAuthentication()
        observeConversation()
        updateReplyTokenStatus()
        updateCallStatus()
    }

    override fun onResume() {
        super.onResume()
        updateReplyTokenStatus()
        updateCallStatus()
        // If the contact became locked while app was backgrounded, re-verify
        if (isUnlockedForCurrentSession && !VeilPrefs.isContactUnlocked(contactName)) {
            verifyContactAuthentication()
        }
    }

    override fun onStop() {
        super.onStop()
        // VEIL Chat Privacy: Restore hidden state when leaving the chat
        if (::chatAdapter.isInitialized) {
            chatAdapter.resetRevealedStates()
        }
    }

    private fun verifyContactAuthentication() {
        lifecycleScope.launch {
            val contact = withContext(Dispatchers.IO) {
                database.protectedContactDao().findByNormalizedNameSync(ProtectedContact.normalizeName(contactName))
            }

            if (contact != null) {
                contactPhoneNumber = contact.selectedPhoneNumber
                loadContactProfilePhoto(contact.photoUri)

                if (contact.isCustomLockEnabled && contact.lockPinHash.isNotBlank()) {
                    if (!VeilPrefs.isContactUnlocked(contactName)) {
                        // Contact has an independent lock that is not unlocked yet
                        binding.rvMessages.visibility = View.INVISIBLE
                        binding.etReplyInput.isEnabled = false
                        binding.btnSendReply.isEnabled = false

                        VeilDialogs.showContactPinUnlockDialog(
                            activity = this@ProtectedChatConversationActivity,
                            contactName = contactName,
                            storedPinHash = contact.lockPinHash,
                            storedPinSalt = contact.lockPinSalt,
                            onUnlocked = {
                                isUnlockedForCurrentSession = true
                                binding.rvMessages.visibility = View.VISIBLE
                                binding.etReplyInput.isEnabled = true
                                binding.btnSendReply.isEnabled = true
                            },
                            onDismiss = {
                                if (!VeilPrefs.isContactUnlocked(contactName)) {
                                    finish()
                                }
                            }
                        )
                        return@launch
                    }
                }
            }

            isUnlockedForCurrentSession = true
            binding.rvMessages.visibility = View.VISIBLE
            binding.etReplyInput.isEnabled = true
            binding.btnSendReply.isEnabled = true
        }
    }

    private fun loadContactProfilePhoto(photoUriStr: String) {
        val firstChar = contactName.trim().firstOrNull()?.uppercaseChar()
        if (firstChar == 'V' || contactName.trim().equals("VEIL", ignoreCase = true)) {
            binding.ivContactAvatar.visibility = View.GONE
            binding.tvContactAvatarInitial.visibility = View.GONE
            binding.ivContactVeilLogo.visibility = View.VISIBLE
        } else if (photoUriStr.isNotBlank()) {
            try {
                binding.ivContactAvatar.setImageURI(Uri.parse(photoUriStr))
                binding.ivContactAvatar.visibility = View.VISIBLE
                binding.tvContactAvatarInitial.visibility = View.GONE
                binding.ivContactVeilLogo.visibility = View.GONE
            } catch (_: Exception) {
                binding.ivContactAvatar.visibility = View.GONE
                binding.ivContactVeilLogo.visibility = View.GONE
                binding.tvContactAvatarInitial.visibility = View.VISIBLE
                binding.tvContactAvatarInitial.text = (firstChar ?: 'P').toString()
            }
        } else {
            binding.ivContactAvatar.visibility = View.GONE
            binding.ivContactVeilLogo.visibility = View.GONE
            binding.tvContactAvatarInitial.visibility = View.VISIBLE
            binding.tvContactAvatarInitial.text = (firstChar ?: 'P').toString()
        }
    }

    private fun setupUI() {
        binding.tvChatContactName.text = contactName

        binding.btnBack.setOnClickListener {
            finish()
        }

        binding.btnChatInfo.setOnClickListener {
            showArchitectureExplanationDialog()
        }

        binding.btnWhatsAppCall.setOnClickListener {
            handleInitiateCall()
        }

        binding.btnOpenWhatsApp.setOnClickListener {
            openWhatsAppChatDirectly()
        }

        binding.btnWipeChat.setOnClickListener {
            confirmWipeConversation()
        }

        binding.btnAttachSticker.setOnClickListener {
            showStickerAndMediaPicker()
        }

        binding.btnAnswerCall.setOnClickListener {
            handleAnswerIncomingCall()
        }

        binding.btnDeclineCall.setOnClickListener {
            handleDeclineIncomingCall()
        }

        chatAdapter = ProtectedChatAdapter()
        val layoutManager = LinearLayoutManager(this).apply {
            stackFromEnd = true
        }
        binding.rvMessages.layoutManager = layoutManager
        binding.rvMessages.adapter = chatAdapter

        binding.btnSendReply.setOnClickListener {
            val text = binding.etReplyInput.text?.toString()?.trim().orEmpty()
            if (text.isNotBlank()) {
                handleSendReply(text)
            }
        }
    }

    private fun updateReplyTokenStatus() {
        val action = NotificationReplyRegistry.getLiveReplyAction(contactName)
        val isLive = action != null
        val targetApp = if (action != null) com.veil.privacy.model.SupportedApp.getAppDisplayName(action.sourcePackage) else "Messaging app"

        if (isLive) {
            binding.viewLiveDot.backgroundTintList = ContextCompat.getColorStateList(this, R.color.veil_accent_secure)
            binding.ivContactProfileIndicator.imageTintList = ContextCompat.getColorStateList(this, R.color.veil_accent_secure)
            binding.ivContactProfileIndicator.contentDescription = "Protected Profile • Live Reply Active"
            binding.tvChatStatusSubtitle.text = "PROTECTED CHAT • LIVE REPLY ACTIVE"
            binding.tvReplyStatusTitle.text = "🟢 $targetApp reply action ready"
            binding.tvReplyStatusTitle.setTextColor(ContextCompat.getColor(this, R.color.veil_accent_secure))
            binding.tvReplyStatusDesc.text = "Active notification from $targetApp captured. Replies typed below will be dispatched through Android's RemoteInput mechanism directly to $targetApp."
            binding.tvReplyDisclaimerPill.text = "RemoteInput Ready"
        } else {
            binding.viewLiveDot.backgroundTintList = ContextCompat.getColorStateList(this, R.color.veil_accent_amber)
            binding.ivContactProfileIndicator.imageTintList = ContextCompat.getColorStateList(this, R.color.veil_accent_titanium)
            binding.ivContactProfileIndicator.contentDescription = "Protected Profile"
            binding.tvChatStatusSubtitle.text = "PROTECTED CHAT • REPLY TOKEN INACTIVE"
            binding.tvReplyStatusTitle.text = "⚠️ No active notification reply token"
            binding.tvReplyStatusTitle.setTextColor(ContextCompat.getColor(this, R.color.veil_accent_amber))
            binding.tvReplyStatusDesc.text = "A notification must be currently active in Android's notification bar to dispatch inline replies. You can also open the messaging app directly."
            binding.tvReplyDisclaimerPill.text = "Token Inactive"
        }
    }

    private fun updateCallStatus() {
        val liveCall = NotificationReplyRegistry.getLiveCallAction(contactName)
        if (liveCall != null && (liveCall.answerIntent != null || liveCall.declineIntent != null)) {
            binding.cardLiveCallBanner.visibility = View.VISIBLE
            binding.tvLiveCallTitle.text = "Incoming WhatsApp Call from $contactName"
        } else {
            binding.cardLiveCallBanner.visibility = View.GONE
        }
    }

    private fun handleAnswerIncomingCall() {
        val liveCall = NotificationReplyRegistry.getLiveCallAction(contactName)
        if (liveCall?.answerIntent != null) {
            try {
                liveCall.answerIntent.send()
                Toast.makeText(this, "Answering WhatsApp call...", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                openWhatsAppChatDirectly()
            }
        } else {
            openWhatsAppChatDirectly()
        }
        binding.cardLiveCallBanner.visibility = View.GONE
    }

    private fun handleDeclineIncomingCall() {
        val liveCall = NotificationReplyRegistry.getLiveCallAction(contactName)
        if (liveCall?.declineIntent != null) {
            try {
                liveCall.declineIntent.send()
                Toast.makeText(this, "WhatsApp call declined", Toast.LENGTH_SHORT).show()
            } catch (_: Exception) {}
        }
        binding.cardLiveCallBanner.visibility = View.GONE
    }

    private fun handleInitiateCall() {
        val liveCall = NotificationReplyRegistry.getLiveCallAction(contactName)
        if (liveCall != null) {
            handleAnswerIncomingCall()
            return
        }

        val phoneDigits = ProtectedContact.extractDigits(contactPhoneNumber)
        if (phoneDigits.isNotBlank()) {
            try {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://api.whatsapp.com/send?phone=$phoneDigits"))
                intent.setPackage("com.whatsapp")
                startActivity(intent)
            } catch (_: Exception) {
                openWhatsAppChatDirectly()
            }
        } else {
            openWhatsAppChatDirectly()
        }
    }

    private fun showStickerAndMediaPicker() {
        val options = arrayOf(
            "Choose image from device (shared as image)",
            "Open WhatsApp to send a sticker"
        )

        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Send Sticker or Media")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> pickVisualMediaLauncher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                    1 -> openWhatsAppChatDirectly()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun shareMediaOrStickerToWhatsApp(imageUri: Uri) {
        try {
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "image/*"
                putExtra(Intent.EXTRA_STREAM, imageUri)
                setPackage("com.whatsapp")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(shareIntent, "Share image to WhatsApp"))

        } catch (_: Exception) {
            Toast.makeText(this, "Could not share this image with WhatsApp", Toast.LENGTH_SHORT).show()
        }
    }

    private fun observeConversation() {
        lifecycleScope.launch {
            database.protectedEventDao().getMessagesForConversationFlow(conversationId)
                .collectLatest { events ->
                    chatAdapter.submitList(events) {
                        if (events.isNotEmpty()) {
                            binding.rvMessages.scrollToPosition(events.size - 1)
                            binding.layoutEmptyMessages.visibility = View.GONE
                        } else {
                            binding.layoutEmptyMessages.visibility = View.VISIBLE
                        }
                    }
                }
        }
    }

    private fun handleSendReply(replyText: String) {
        val action = NotificationReplyRegistry.getLiveReplyAction(contactName)
        val isLive = action != null
        val targetApp = if (action != null) com.veil.privacy.model.SupportedApp.getAppDisplayName(action.sourcePackage) else "Messaging app"

        if (!isLive) {
            VeilDialogs.showConfirmationDialog(
                context = this,
                title = "Active Notification Required",
                message = "Android's RemoteInput notification reply mechanism requires an active notification from $targetApp in your notification tray.\n\nBecause no live notification token is currently pending, would you like to open the app directly to send your message?",
                confirmText = "Open App",
                onConfirm = { openWhatsAppChatDirectly(replyText) }
            )
            return
        }

        // Dispatch via Notification RemoteInput
        val dispatched = NotificationReplyRegistry.sendReply(this, contactName, replyText)

        if (dispatched) {
            binding.etReplyInput.text?.clear()

            // Record outgoing message in conversation thread
            lifecycleScope.launch(Dispatchers.IO) {
                val outgoingEvent = ProtectedEvent(
                    realSender = "You",
                    realMessage = replyText,
                    isOutgoing = true,
                    replyStatus = "SENT_VIA_REMOTE_INPUT",
                    conversationId = conversationId,
                    timestamp = System.currentTimeMillis()
                )
                database.protectedEventDao().insert(outgoingEvent)
            }

            Toast.makeText(this, "Reply dispatched to $targetApp", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Failed to send: notification action token expired", Toast.LENGTH_LONG).show()
            updateReplyTokenStatus()
        }
    }

    private fun openWhatsAppChatDirectly(prefilledText: String = "") {
        try {
            val phoneDigits = ProtectedContact.extractDigits(contactPhoneNumber)
            if (phoneDigits.isNotBlank()) {
                val encodedText = if (prefilledText.isNotBlank()) "&text=" + Uri.encode(prefilledText) else ""
                val uri = Uri.parse("https://api.whatsapp.com/send?phone=$phoneDigits$encodedText")
                val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                    setPackage("com.whatsapp")
                }
                startActivity(intent)
                return
            }

            val launchIntent = packageManager.getLaunchIntentForPackage("com.whatsapp")
                ?: packageManager.getLaunchIntentForPackage("com.whatsapp.w4b")

            if (launchIntent != null) {
                startActivity(launchIntent)
            } else {
                val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://api.whatsapp.com/"))
                startActivity(browserIntent)
            }
        } catch (_: Exception) {
            Toast.makeText(this, "WhatsApp is not installed on this device", Toast.LENGTH_SHORT).show()
        }
    }

    private fun confirmWipeConversation() {
        VeilDialogs.showConfirmationDialog(
            context = this,
            title = "Wipe Conversation",
            message = "Are you sure you want to delete all local message logs for « $contactName »?\n\nThis will permanently remove intercepted messages from VEIL's local encrypted storage.",
            confirmText = "Wipe Messages",
            onConfirm = {
                lifecycleScope.launch(Dispatchers.IO) {
                    database.protectedEventDao().deleteConversation(conversationId)
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@ProtectedChatConversationActivity, "Conversation cleared", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        )
    }

    private fun showArchitectureExplanationDialog() {
        VeilDialogs.showInfoDialog(
            context = this,
            title = "VEIL Protected Architecture",
            message = "• VEIL is an autonomous notification disguise layer.\n\n" +
                    "• When a protected WhatsApp message arrives, Android's NotificationListenerService detects it and VEIL immediately disguises the system notification.\n\n" +
                    "• Messages displayed here are received exclusively through authorized Android notification events.\n\n" +
                    "• Replies typed here are dispatched via Android's official RemoteInput inline-reply mechanism directly to WhatsApp's notification PendingIntent.\n\n" +
                    "• Stickers and media attachments are shared via Android's official Photo Picker.\n\n" +
                    "• Calls can be answered or declined directly when call notifications are active.\n\n" +
                    "• All captured messages remain 100% locally on your device in encrypted sandboxed storage."
        )
    }
}
