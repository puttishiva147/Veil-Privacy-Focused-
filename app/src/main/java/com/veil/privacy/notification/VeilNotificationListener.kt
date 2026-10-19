package com.veil.privacy.notification

import android.service.notification.StatusBarNotification
import com.veil.privacy.model.ParsedNotificationInfo
import com.veil.privacy.model.ProtectedEvent
import com.veil.privacy.model.ProtectionRule
import com.veil.privacy.model.SelectedContact
import com.veil.privacy.privacy.RuleMatchingEngine
import com.veil.privacy.storage.VeilInbox
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Production Android NotificationListenerService for VEIL.
 *
 * Responsibilities:
 * - Detects incoming WhatsApp notifications.
 * - Extracts Android notification inline-reply RemoteInput action and registers it in [NotificationReplyRegistry].
 * - Resolves the notification against independent configured [ProtectionRule]s.
 * - Dismisses/cancels the original WhatsApp notification where Android permits.
 * - Immediately creates and posts the replacement VEIL privacy-safe notification.
 * - Saves incoming message to Room database for VEIL's separate Protected Chats UI.
 */
class VeilNotificationListener : BaseNotificationListenerService() {

    private val ruleMatchingEngine = RuleMatchingEngine()
    private lateinit var inbox: VeilInbox
    private lateinit var notificationManager: VeilNotificationManager

    companion object {
        const val WHATSAPP_PACKAGE = "com.whatsapp"
        const val WHATSAPP_BUSINESS_PACKAGE = "com.whatsapp.w4b"

        @Volatile
        var isConnected: Boolean = false
            private set

        // Shared flow for real-time learned sender detection in LearnContactActivity
        private val _learnedSenderFlow = MutableSharedFlow<String?>(extraBufferCapacity = 1)
        val learnedSenderFlow: SharedFlow<String?> = _learnedSenderFlow.asSharedFlow()

        // Shared flow for real-time pipeline diagnostic inspection
        data class PipelineEvent(
            val timestamp: Long = System.currentTimeMillis(),
            val stage: String,
            val message: String,
            val isSuccess: Boolean = true
        )

        private val _pipelineFlow = MutableSharedFlow<PipelineEvent>(extraBufferCapacity = 32)
        val pipelineFlow: SharedFlow<PipelineEvent> = _pipelineFlow.asSharedFlow()

        // Prevent concurrent callbacks for the same notification/event from racing through
        // the database deduplication check before either callback persists its event.
        private val inFlightEventKeys = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()

        fun emitDiagnostic(stage: String, message: String, isSuccess: Boolean = true) {
            _pipelineFlow.tryEmit(PipelineEvent(stage = stage, message = message, isSuccess = isSuccess))
        }
    }

    override fun onCreate() {
        super.onCreate()
        inbox = VeilInbox.getInstance(this)
        notificationManager = VeilNotificationManager.getInstance(this)
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        isConnected = true
        emitDiagnostic("ENGINE", "VeilNotificationListener connected to Android OS", true)
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        isConnected = false
        emitDiagnostic("ENGINE", "VeilNotificationListener disconnected", false)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
            try {
                requestRebind(android.content.ComponentName(this, VeilNotificationListener::class.java))
            } catch (_: Exception) {}
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        super.onNotificationRemoved(sbn)
        // Notification RemoteInput and call PendingIntents are valid only while their
        // originating notification is active. Do not show stale reply/call controls.
        sbn?.key?.let(NotificationReplyRegistry::removeCallByKey)
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn == null) return

        val pkg = sbn.packageName ?: return
        val ownPackage = packageName

        // 1. Filter: Ignore VEIL's own notifications unless it is an explicit simulation test notification
        val isSimulation = (pkg == com.veil.privacy.model.SupportedApp.PKG_SIMULATION || sbn.tag == "VEIL_SIMULATION")
        if (pkg == ownPackage && !isSimulation) return

        // 2. Multi-App validation
        val isRecognized = NotificationParser.isSupportedPackage(pkg, preferences.isProtectAnyApp)
        if (!isRecognized && !isSimulation) return

        // 3. Master toggle check
        if (!preferences.isVeilEnabled) {
            emitDiagnostic("FILTER", "VEIL is currently paused. Passing notification through.")
            return
        }

        emitDiagnostic("DETECT", "Captured supported-app notification")

        // 4. Parse incoming notification exposed by Android
        val parsed = notificationParser.parse(sbn, ownPackage, preferences.isProtectAnyApp)
        if (parsed == null) {
            emitDiagnostic("PARSE", "Notification payload had no extractable message content", false)
            return
        }

        emitDiagnostic("PARSE", "Notification content parsed successfully")

        // 5. Capture live RemoteInput reply action if exposed by messaging app
        val replyActionPair = NotificationReplyRegistry.findReplyAction(sbn.notification)
        if (replyActionPair != null && sbn.key != null) {
            NotificationReplyRegistry.register(
                contactName = parsed.sender,
                notificationKey = sbn.key,
                action = replyActionPair.first,
                remoteInput = replyActionPair.second,
                sourcePackage = pkg
            )
            emitDiagnostic("REMOTE_INPUT", "Live Android RemoteInput inline reply action captured and registered")
        }

        val callActionsPair = NotificationReplyRegistry.findCallActions(sbn.notification)
        if (callActionsPair != null && sbn.key != null) {
            NotificationReplyRegistry.registerCall(
                contactName = parsed.sender,
                notificationKey = sbn.key,
                answerIntent = callActionsPair.first,
                declineIntent = callActionsPair.second,
                sourcePackage = pkg
            )
            emitDiagnostic("CALL_ACTION", "Live call action captured")
        }

        // 6. Capture for Sensor Learning if active
        if (preferences.isLearnModeActive) {
            preferences.lastLearnedSender = parsed.sender
            _learnedSenderFlow.tryEmit(parsed.sender)
            emitDiagnostic("SENSOR", "Sender passed to Sensor Learning")
        }

        // 7. Evaluate and process against configured VEIL rules.
        // Android can repost/update the same notification quickly. Claim an in-flight
        // key before launching asynchronous work so two callbacks cannot both pass the
        // database deduplication check and create duplicate protected events.
        val eventGuardKey = parsed.notificationKey?.takeIf { it.isNotBlank() }
            ?: listOf(
                parsed.packageName,
                parsed.sender.trim().lowercase(),
                parsed.message.trim(),
                parsed.timestamp.toString()
            ).joinToString("|")

        if (!inFlightEventKeys.add(eventGuardKey)) {
            emitDiagnostic("DEDUP", "Ignored notification already being processed")
            return
        }

        serviceScope.launch {
            try {
                processIncomingProtectedNotification(sbn, parsed, replyActionPair)
            } catch (e: Exception) {
                emitDiagnostic("PROCESS", "Notification processing failed (${e.javaClass.simpleName})", false)
            } finally {
                inFlightEventKeys.remove(eventGuardKey)
            }
        }
    }

    /**
     * Resolves the incoming notification against active rules and applies the privacy transformation.
     */
    private suspend fun processIncomingProtectedNotification(
        sbn: StatusBarNotification,
        parsed: ParsedNotificationInfo,
        replyActionPair: Pair<android.app.Notification.Action, android.app.RemoteInput>? = null
    ) {
        // Fetch all active, enabled protection rules and contacts from Room database
        val allRules = withContext(Dispatchers.IO) {
            val rules = database.protectionRuleDao().getEnabledRulesSync().toMutableList()
            val existingRulePhones = rules.map { it.normalizedPhoneNumber }.filter { it.isNotBlank() }.toSet()
            val existingRuleNames = rules.map { it.normalizedContactName }.filter { it.isNotBlank() }.toSet()

            val protectedContacts = database.protectedContactDao().getEnabledContactsSync()
            for (c in protectedContacts) {
                val normPhone = c.normalizedPhoneNumber
                val normName = c.normalizedName
                if ((normPhone.isBlank() || normPhone !in existingRulePhones) &&
                    (normName.isBlank() || normName !in existingRuleNames)) {
                    rules.add(
                        ProtectionRule(
                            contactId = c.contactId,
                            contactName = c.displayName,
                            contactPhoneNumber = c.selectedPhoneNumber,
                            disguiseMode = ProtectionRule.MODE_CUSTOM_DECOY,
                            decoyIdentity = "Work",
                            decoyMessage = "New message",
                            isEnabled = c.isEnabled,
                            source = c.source
                        )
                    )
                }
            }
            // Include contacts selected in Triggered/quick-protect as well as the newer
            // protected-contact table. This keeps the listener aligned with the user's
            // selected-contact list instead of silently ignoring those selections.
            val selectedContacts = database.selectedContactDao().getEnabledContactsSync()
            val knownPhones = rules.map { it.normalizedPhoneNumber }.filter { it.isNotBlank() }.toMutableSet()
            val knownNames = rules.map { it.normalizedContactName }.filter { it.isNotBlank() }.toMutableSet()
            for (contact in selectedContacts) {
                val phone = contact.normalizedPhoneNumber
                val name = com.veil.privacy.model.ProtectedContact.normalizeName(contact.displayName)
                val alreadyCovered = (phone.isNotBlank() && phone in knownPhones) ||
                    (name.isNotBlank() && name in knownNames)
                if (!alreadyCovered) {
                    rules.add(
                        ProtectionRule(
                            contactId = contact.contactId,
                            contactName = contact.displayName,
                            contactPhoneNumber = contact.selectedPhoneNumber,
                            disguiseMode = ProtectionRule.MODE_CUSTOM_DECOY,
                            decoyIdentity = "Work",
                            decoyMessage = "New message",
                            isEnabled = contact.isEnabled,
                            source = contact.source
                        )
                    )
                    if (phone.isNotBlank()) knownPhones.add(phone)
                    if (name.isNotBlank()) knownNames.add(name)
                }
            }
            rules
        }

        if (allRules.isEmpty() && !preferences.isProtectAllSenders) {
            emitDiagnostic("RULE", "No protection rules configured. Original alert left untouched.")
            return
        }

        // High-confidence matching evaluation
        var matchedRule = ruleMatchingEngine.evaluateMatch(sbn, parsed, allRules)

        if (matchedRule == null && preferences.isProtectAllSenders) {
            matchedRule = ProtectionRule(
                contactId = "-1",
                contactName = parsed.sender,
                contactPhoneNumber = "",
                decoyIdentity = "VEIL",
                decoyMessage = "Protected message",
                isEnabled = true
            )
            emitDiagnostic("RULE", "Protect All Senders active: created automatic rule")
        }

        if (matchedRule == null) {
            emitDiagnostic("RULE", "Notification did not match a protected contact. Undisguised.")
            return
        }

        emitDiagnostic("RULE", "Matched a protection rule")

        // Call disguise toggle check
        if (parsed.isCall && !matchedRule.isCallDisguiseEnabled) {
            emitDiagnostic("CALL", "Call disguise disabled for this rule. Untouched.")
            return
        }

        // --- MATCH CONFIRMED: APPLY PRIVACY TRANSFORMATION ---
        val targetConversation = matchedRule.contactName.lowercase().trim()

        // Also register live reply action under the matched rule's contactName if different from sender
        if (replyActionPair != null && sbn.key != null && matchedRule.contactName.isNotBlank() &&
            !matchedRule.contactName.equals(parsed.sender, ignoreCase = true)
        ) {
            NotificationReplyRegistry.register(
                contactName = matchedRule.contactName,
                notificationKey = sbn.key,
                action = replyActionPair.first,
                remoteInput = replyActionPair.second,
                sourcePackage = sbn.packageName
            )
        }

        // Stable event identification and deduplication: prevent duplicate notification events
        val isDuplicate = withContext(Dispatchers.IO) {
            // Notification keys identify an Android notification update/repost. Do not
            // deduplicate on message text: two legitimate consecutive messages can have
            // identical content (e.g. "OK") and must both appear in VEIL.
            !parsed.notificationKey.isNullOrBlank() &&
                database.protectedEventDao().hasEventWithNotificationKey(parsed.notificationKey) > 0
        }
        if (isDuplicate) {
            // Never include notification keys, contact names, or message-derived values
            // in diagnostics; they can contain private identity information.
            emitDiagnostic("DEDUP", "Ignored duplicate notification event")
            return
        }

        // 2. Prepare protected event payload with dynamic decoy rotation or manual disguise
        val notifType = parsed.getNotificationType()
        val isDynamic = preferences.isDynamicDisguiseEnabled &&
            (matchedRule.disguiseMode == ProtectionRule.MODE_RANDOM_ROTATION ||
             matchedRule.decoyIdentity.equals("Dynamic", ignoreCase = true) ||
             preferences.decoyPresetTheme == com.veil.privacy.storage.VeilPrefs.THEME_DYNAMIC ||
             (matchedRule.decoyIdentity.isBlank() && matchedRule.decoyMessage.isBlank()))

        val (finalDecoySender, finalDecoyMessage) = when {
            matchedRule.disguiseMode == ProtectionRule.MODE_SILENT ||
            matchedRule.decoyIdentity.equals("VEIL", ignoreCase = true) -> {
                Pair("VEIL", "Notification received")
            }
            !isDynamic -> {
                // If Dynamic mode is disabled, use the user's manually configured disguise
                val manualSender = matchedRule.decoyIdentity.trim().ifBlank {
                    preferences.defaultDecoySender.ifBlank { "Notification" }
                }
                val manualMessage = matchedRule.decoyMessage.trim().ifBlank {
                    preferences.defaultDecoyMessage.ifBlank { "New message received" }
                }
                Pair(manualSender, manualMessage)
            }
            else -> {
                // Dynamic disguise active: rotate decoy identity & message without repetition
                val cleanIdentity = matchedRule.decoyIdentity.uppercase().trim()
                val preferredProfile = when {
                    cleanIdentity.contains("WORK") -> com.veil.privacy.storage.VeilPrefs.THEME_WORK
                    cleanIdentity.contains("CALENDAR") -> com.veil.privacy.storage.VeilPrefs.THEME_CALENDAR
                    cleanIdentity.contains("DELIVERY") -> com.veil.privacy.storage.VeilPrefs.THEME_DELIVERY
                    cleanIdentity.contains("SYSTEM") -> com.veil.privacy.storage.VeilPrefs.THEME_SYSTEM
                    else -> null // Varied rotation across available decoy profiles
                }

                com.veil.privacy.privacy.DecoyGenerator.getInstance().generateDynamicDecoy(
                    type = notifType,
                    preferredProfile = preferredProfile,
                    prefs = preferences
                )
            }
        }

        val protectedEvent = ProtectedEvent(
            timestamp = parsed.timestamp,
            sourcePackage = parsed.packageName,
            realSender = parsed.sender,
            realMessage = parsed.message,
            decoySender = finalDecoySender,
            decoyMessage = finalDecoyMessage,
            isCall = parsed.isCall,
            originalNotificationKey = parsed.notificationKey,
            isOutgoing = false,
            replyStatus = "RECEIVED",
            conversationId = targetConversation
        )

        // 3. Save to local encrypted inbox/vault for private inspection
        val insertedId = withContext(Dispatchers.IO) {
            database.protectedEventDao().insert(protectedEvent)
        }
        val eventWithId = protectedEvent.copy(id = insertedId)

        // Persist first; suppress only this matched notification, never unchecked grouped siblings.
        if (preferences.isHideOriginalEnabled) {
            try {
                sbn.key?.let { cancelNotification(it) }
                emitDiagnostic("SUPPRESS", "Requested suppression of the matched notification")
            } catch (_: SecurityException) {
                emitDiagnostic("SUPPRESS", "Android restricted notification suppression", false)
            } catch (e: Exception) {
                emitDiagnostic("SUPPRESS", "Notification suppression failed (${e.javaClass.simpleName})", false)
            }
        }

        // 4. Update interception counter
        preferences.incrementInterceptionsCount()

        // 5. Immediately post the replacement VEIL notification containing configured privacy disguise
        notificationManager.postRuleDecoyNotification(eventWithId, matchedRule)
        emitDiagnostic("POST", "Replacement alert posted: '$finalDecoySender: $finalDecoyMessage'")
    }

    override suspend fun applyPrivacyTransformation(
        sbn: StatusBarNotification,
        parsed: ParsedNotificationInfo,
        matchedContact: SelectedContact
    ) {
        val ruleEquivalent = ProtectionRule(
            contactId = matchedContact.contactId,
            contactName = matchedContact.displayName,
            contactPhoneNumber = matchedContact.selectedPhoneNumber,
            decoyIdentity = "VEIL",
            decoyMessage = "Protected message"
        )
        processIncomingProtectedNotification(sbn, parsed)
    }
}
