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
            binding.tvLiveCallTitle.text = "Incoming Call from $contactName"
        } else {
            binding.cardLiveCallBanner.visibility = View.GONE
        }
    }

    private fun handleAnswerIncomingCall() {
        val liveCall = NotificationReplyRegistry.getLiveCallAction(contactName)
        if (liveCall?.answerIntent != null) {
            try {
                liveCall.answerIntent.send()
                NotificationReplyRegistry.removeCallByKey(liveCall.notificationKey)
                Toast.makeText(this, "Answer action handed to the source app; call state is not confirmed by VEIL", Toast.LENGTH_LONG).show()
                binding.cardLiveCallBanner.visibility = View.GONE
            } catch (_: Exception) {
                Toast.makeText(this, "Could not answer from this notification. Open the source app to check the call.", Toast.LENGTH_LONG).show()
                updateCallStatus()
            }
        } else {
            Toast.makeText(this, "No active answer action. Open the source app to check the call.", Toast.LENGTH_LONG).show()
            updateCallStatus()
        }
    }

    private fun handleDeclineIncomingCall() {
        val liveCall = NotificationReplyRegistry.getLiveCallAction(contactName)
        if (liveCall?.declineIntent != null) {
            try {
                liveCall.declineIntent.send()
                NotificationReplyRegistry.removeCallByKey(liveCall.notificationKey)
                Toast.makeText(this, "Decline action handed to the source app; call state is not confirmed by VEIL", Toast.LENGTH_LONG).show()
                binding.cardLiveCallBanner.visibility = View.GONE
            } catch (_: Exception) {
                Toast.makeText(this, "Could not decline from this notification", Toast.LENGTH_LONG).show()
                updateCallStatus()
            }
        } else {
            Toast.makeText(this, "No active decline action is available", Toast.LENGTH_LONG).show()
            updateCallStatus()
        }
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
        val categories = linkedMapOf(
            "Recent" to emptyList(),
            "Reactions" to listOf("😀", "😎", "🥹", "🤔", "🙌", "👍", "👎", "🙏", "❤️", "😂", "😭", "😡"),
            "Hello" to listOf("👋", "😊", "🤗", "💬", "☀️", "🌙", "✨", "🫶"),
            "Love" to listOf("❤️", "💖", "💕", "💘", "🥰", "😘", "💌", "🫶"),
            "Laugh" to listOf("😂", "🤣", "😆", "😹", "🤭", "😜", "🙃", "💀"),
            "Sad" to listOf("😔", "🥺", "😭", "💔", "😞", "🫂", "🌧️", "🩶"),
            "Celebrate" to listOf("🎉", "🥳", "🎂", "🏆", "👏", "🎊", "⭐", "✨"),
            "Security" to listOf("🔒", "🛡️", "🔐", "🕶️", "🗝️", "🔎", "✅", "🤫")
        )
        val recentPrefs = getSharedPreferences("veil_stickers", MODE_PRIVATE)
        val recent = recentPrefs.getString("recent", "").orEmpty().split("|").filter { it.isNotBlank() }.distinct().take(12)
        val density = resources.displayMetrics.density
        fun dp(value: Int) = (value * density).toInt()

        val root = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(dp(16), dp(8), dp(16), dp(12))
        }
        val categoryScroll = android.widget.HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            clipToPadding = false
        }
        val categoryRow = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.HORIZONTAL
        }
        val content = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
        }
        val scroll = android.widget.ScrollView(this).apply {
            isFillViewport = true
            addView(content)
        }
        fun addLabel(text: String) {
            content.addView(android.widget.TextView(this).apply {
                this.text = text
                textSize = 12f
                setTextColor(ContextCompat.getColor(this@ProtectedChatConversationActivity, R.color.veil_text_medium))
                setPadding(dp(2), dp(10), dp(2), dp(8))
            })
        }
        fun insertSticker(sticker: String, dialog: androidx.appcompat.app.AlertDialog) {
            val current = binding.etReplyInput.text?.toString().orEmpty()
            val separator = if (current.isNotBlank() && !current.endsWith(" ")) " " else ""
            binding.etReplyInput.setText(current + separator + sticker + " ")
            binding.etReplyInput.setSelection(binding.etReplyInput.text?.length ?: 0)
            val updated = (listOf(sticker) + recentPrefs.getString("recent", "").orEmpty().split("|").filter { it.isNotBlank() })
                .distinct().take(12)
            recentPrefs.edit().putString("recent", updated.joinToString("|")).apply()
            dialog.dismiss()
            Toast.makeText(this, "Sticker emoji added to reply. Use Send to send it as text.", Toast.LENGTH_LONG).show()
        }
        lateinit var dialog: androidx.appcompat.app.AlertDialog
        fun renderCategory(category: String) {
            content.removeAllViews()
            val items = if (category == "Recent") recent else categories[category].orEmpty()
            if (items.isEmpty()) {
                addLabel(if (category == "Recent") "Your recently used stickers will appear here." else "No stickers in this category.")
                return
            }
            if (category == "Recent") addLabel("RECENTLY USED") else addLabel(category.uppercase())
            val grid = android.widget.GridLayout(this).apply {
                columnCount = 4
                useDefaultMargins = false
            }
            items.forEachIndexed { index, sticker ->
                val tile = android.widget.TextView(this).apply {
                    text = sticker
                    textSize = 27f
                    gravity = android.view.Gravity.CENTER
                    contentDescription = "Insert $sticker sticker emoji"
                    background = ContextCompat.getDrawable(this@ProtectedChatConversationActivity, R.drawable.bg_circle_icon)
                    isClickable = true
                    isFocusable = true
                    setOnClickListener { insertSticker(sticker, dialog) }
                }
                val params = android.widget.GridLayout.LayoutParams().apply {
                    width = 0
                    height = dp(58)
                    columnSpec = android.widget.GridLayout.spec(index % 4, 1f)
                    setMargins(dp(3), dp(3), dp(3), dp(3))
                }
                grid.addView(tile, params)
            }
            content.addView(grid)
        }
        categories.keys.filter { it != "Recent" }.let { names ->
            names.forEach { category ->
                val button = com.google.android.material.button.MaterialButton(this).apply {
                    text = category
                    textSize = 10f
                    minWidth = 0
                    minHeight = dp(34)
                    insetTop = 0
                    insetBottom = 0
                    setPadding(dp(9), 0, dp(9), 0)
                    setOnClickListener { renderCategory(category) }
                }
                categoryRow.addView(button, android.widget.LinearLayout.LayoutParams(-2, dp(38)).apply {
                    marginEnd = dp(6)
                })
            }
        }
        val recentButton = com.google.android.material.button.MaterialButton(this).apply {
            text = "Recent"
            textSize = 10f
            minWidth = 0
            minHeight = dp(34)
            insetTop = 0
            insetBottom = 0
            setPadding(dp(9), 0, dp(9), 0)
            setOnClickListener { renderCategory("Recent") }
        }
        categoryRow.addView(recentButton, 0, android.widget.LinearLayout.LayoutParams(-2, dp(38)).apply {
            marginEnd = dp(6)
        })
        categoryScroll.addView(categoryRow)
        root.addView(categoryScroll, android.widget.LinearLayout.LayoutParams(-1, dp(44)))
        root.addView(scroll, android.widget.LinearLayout.LayoutParams(-1, dp(270)))
        root.addView(com.google.android.material.button.MaterialButton(this).apply {
            text = "Choose photo or video"
            setOnClickListener {
                dialog.dismiss()
                pickVisualMediaLauncher.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)
                )
            }
        }, android.widget.LinearLayout.LayoutParams(-1, dp(44)).apply { topMargin = dp(8) })
        root.addView(com.google.android.material.button.MaterialButton(this).apply {
            text = "Open Source App"
            setOnClickListener {
                dialog.dismiss()
                openWhatsAppChatDirectly()
            }
        }, android.widget.LinearLayout.LayoutParams(-1, dp(44)).apply { topMargin = dp(4) })

        dialog = androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Stickers")
            .setView(root)
            .setNegativeButton("Close", null)
            .create()
        renderCategory("Recent")
        dialog.show()
    }

    private fun shareMediaOrStickerToWhatsApp(imageUri: Uri) {
        try {
            val mimeType = contentResolver.getType(imageUri)?.takeIf { it.startsWith("image/") || it.startsWith("video/") }
                ?: "image/*"
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = mimeType
                putExtra(Intent.EXTRA_STREAM, imageUri)
                clipData = android.content.ClipData.newUri(contentResolver, "VEIL media", imageUri)
                setPackage("com.whatsapp")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(shareIntent, "Share media to WhatsApp"))
        } catch (_: android.content.ActivityNotFoundException) {
            Toast.makeText(this, "WhatsApp is not installed or cannot accept this media", Toast.LENGTH_LONG).show()
        } catch (_: Exception) {
            Toast.makeText(this, "Could not share this media with WhatsApp", Toast.LENGTH_LONG).show()
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
                    sourcePackage = action?.sourcePackage
                        ?: com.veil.privacy.model.SupportedApp.PKG_WHATSAPP,
                    realSender = "You",
                    realMessage = replyText,
                    isOutgoing = true,
                    replyStatus = "DISPATCHED_NOT_CONFIRMED",
                    conversationId = conversationId,
                    timestamp = System.currentTimeMillis()
                )
                database.protectedEventDao().insert(outgoingEvent)
            }

            Toast.makeText(this, "Reply action handed to $targetApp. Delivery/read status is not available to VEIL.", Toast.LENGTH_LONG).show()
        } else {
            Toast.makeText(this, "Failed to send: notification action token expired", Toast.LENGTH_LONG).show()
            updateReplyTokenStatus()
        }
    }

    private fun openWhatsAppChatDirectly(prefilledText: String = "") {
        // Resolve the source app from the conversation's stored events. The old fallback
        // always launched WhatsApp, even when this thread came from Telegram, Instagram or Snapchat.
        lifecycleScope.launch {
            val sourcePackage = withContext(Dispatchers.IO) {
                database.protectedEventDao()
                    .getMessagesForConversationSync(conversationId)
                    .lastOrNull { it.sourcePackage.isNotBlank() }
                    ?.sourcePackage
            }
            val supported = com.veil.privacy.model.SupportedApp
            val resolvedSourcePackage = sourcePackage ?: supported.PKG_WHATSAPP
            val targetPackage = when (resolvedSourcePackage) {
                supported.PKG_WHATSAPP, supported.PKG_WHATSAPP_BUSINESS,
                supported.PKG_TELEGRAM, supported.PKG_INSTAGRAM, supported.PKG_SNAPCHAT -> resolvedSourcePackage
                else -> supported.PKG_WHATSAPP
            }

            try {
                if ((targetPackage == supported.PKG_WHATSAPP ||
                        targetPackage == supported.PKG_WHATSAPP_BUSINESS) &&
                    contactPhoneNumber.isNotBlank()
                ) {
                    val phoneDigits = ProtectedContact.extractDigits(contactPhoneNumber)
                    if (phoneDigits.isNotBlank()) {
                        val encodedText = if (prefilledText.isNotBlank()) "&text=" + Uri.encode(prefilledText) else ""
                        val uri = Uri.parse("https://api.whatsapp.com/send?phone=$phoneDigits$encodedText")
                        startActivity(Intent(Intent.ACTION_VIEW, uri).setPackage(targetPackage))
                        return@launch
                    }
                }

                val launchIntent = packageManager.getLaunchIntentForPackage(targetPackage)
                if (launchIntent != null) {
                    startActivity(launchIntent)
                } else {
                    Toast.makeText(
                        this@ProtectedChatConversationActivity,
                        "${com.veil.privacy.model.SupportedApp.getAppDisplayName(targetPackage)} is not installed",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            } catch (_: Exception) {
                Toast.makeText(this@ProtectedChatConversationActivity, "Unable to open the source messaging app", Toast.LENGTH_SHORT).show()
            }
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
                    "• When a protected message arrives, Android's NotificationListenerService detects it and VEIL immediately disguises the system notification.\n\n" +
                    "• Messages displayed here are received exclusively through authorized Android notification events.\n\n" +
                    "• Replies typed here are dispatched through Android's official RemoteInput action exposed by the source app.\n\n" +
                    "• Stickers and media attachments are shared via Android's official Photo Picker.\n\n" +
                    "• Calls can be answered or declined directly when call notifications are active.\n\n" +
                    "• All captured messages remain 100% locally on your device in encrypted sandboxed storage."
        )
    }
}
