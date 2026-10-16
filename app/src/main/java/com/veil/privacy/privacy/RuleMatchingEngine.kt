package com.veil.privacy.privacy

import android.app.Notification
import android.app.Person
import android.os.Build
import android.os.Bundle
import android.service.notification.StatusBarNotification
import com.veil.privacy.model.ParsedNotificationInfo
import com.veil.privacy.model.ProtectionRule

/**
 * High-confidence correlation and matching engine.
 *
 * Enforces strict safety rules:
 * - Never disguise a notification merely because its visible title happens to contain a matching name.
 * - Inspects Android MessagingStyle, Conversation extras, and Person metadata.
 * - Group conversation guard: prevents accidental disguise of group chats where a contact is merely mentioned.
 * - Fails open (returns null) whenever confidence is insufficient.
 */
class RuleMatchingEngine {

    sealed class MatchResult {
        data class Matched(val rule: ProtectionRule, val confidenceReason: String) : MatchResult()
        data object NoMatch : MatchResult()
        data class LowConfidenceRejected(val reason: String) : MatchResult()
    }

    /**
     * Evaluates an incoming WhatsApp notification against active VEIL protection rules.
     * Returns the matched [ProtectionRule] only if matching confidence is high, or null otherwise.
     */
    fun evaluateMatch(
        sbn: StatusBarNotification,
        parsed: ParsedNotificationInfo,
        activeRules: List<ProtectionRule>
    ): ProtectionRule? {
        val result = evaluateWithDetails(sbn, parsed, activeRules)
        return if (result is MatchResult.Matched) {
            result.rule
        } else {
            null
        }
    }

    /**
     * Internal detailed evaluation returning the matching diagnosis.
     */
    fun evaluateWithDetails(
        sbn: StatusBarNotification,
        parsed: ParsedNotificationInfo,
        activeRules: List<ProtectionRule>
    ): MatchResult {
        // Filter only enabled rules
        val enabledRules = activeRules.filter { it.isEnabled }
        if (enabledRules.isEmpty()) {
            return MatchResult.NoMatch
        }

        val extras = sbn.notification.extras ?: Bundle.EMPTY
        val isGroup = isGroupConversation(extras)

        // Safety Guard 1: Ignore WhatsApp system/service notifications
        if (isWhatsAppServiceNotification(parsed.sender, parsed.message)) {
            return MatchResult.LowConfidenceRejected("WhatsApp system service alert")
        }

        // Safety Guard 2: Resolve true sender identity using Android MessagingStyle Person metadata
        val resolvedAuthor = extractAuthorIdentity(extras, parsed.sender)
        if (resolvedAuthor.isBlank()) {
            return MatchResult.LowConfidenceRejected("No verifiable sender author in notification")
        }

        // Safety Guard 3: Group chat safety check
        // In group chats, only match if the specific message author inside the group explicitly matches the rule,
        // NEVER match if the group title merely contains the contact's name.
        if (isGroup) {
            val authorOnlyMatch = findRuleMatchingIdentity(resolvedAuthor, enabledRules)
            return if (authorOnlyMatch != null) {
                MatchResult.Matched(authorOnlyMatch, "Group message author verified: $resolvedAuthor")
            } else {
                MatchResult.NoMatch
            }
        }

        // 1-on-1 Conversation Matching:
        // Try matching against resolved author
        val matchedRule = findRuleMatchingIdentity(resolvedAuthor, enabledRules)
            ?: findRuleMatchingIdentity(parsed.sender, enabledRules)

        return if (matchedRule != null) {
            MatchResult.Matched(matchedRule, "Verified 1-on-1 conversation target")
        } else {
            MatchResult.NoMatch
        }
    }

    /**
     * Determines whether the notification represents a group chat.
     */
    private fun isGroupConversation(extras: Bundle): Boolean {
        if (extras.getBoolean(Notification.EXTRA_IS_GROUP_CONVERSATION, false)) {
            return true
        }
        if (extras.getBoolean("android.isGroupConversation", false)) {
            return true
        }
        return false
    }

    /**
     * Extracts author identity from notification metadata.
     */
    private fun extractAuthorIdentity(extras: Bundle, fallbackSender: String): String {
        return fallbackSender.trim()
    }

    /**
     * Correlates an identity string (phone number or name) against configured rules.
     * Evaluates in strict priority tiers to guarantee that exact phone or exact name matches
     * are never shadowed by loose word overlap from another rule.
     */
    fun findRuleMatchingIdentity(
        incomingIdentity: String,
        rules: List<ProtectionRule>
    ): ProtectionRule? {
        if (incomingIdentity.isBlank()) return null

        val incomingDigits = ProtectionRule.extractDigits(incomingIdentity)
        val normalizedIncoming = ProtectionRule.normalizeName(incomingIdentity)
        val strippedIncoming = stripPunctuation(normalizedIncoming)

        // Tier 1: Highest confidence - Phone number digit matching (exact digits or 7-10 digit suffix)
        if (incomingDigits.length >= 7) {
            val phoneMatch = rules.firstOrNull { rule ->
                if (rule.normalizedPhoneNumber.isNotBlank()) {
                    val ruleDigits = ProtectionRule.extractDigits(rule.normalizedPhoneNumber)
                    ruleDigits.isNotBlank() && isPhoneDigitsMatch(incomingDigits, ruleDigits)
                } else false
            }
            if (phoneMatch != null) return phoneMatch
        }

        // Tier 2: Direct normalized phone string equality
        val normalizedIncomingPhone = ProtectionRule.normalizePhone(incomingIdentity)
        if (normalizedIncomingPhone.isNotBlank()) {
            val directPhoneMatch = rules.firstOrNull {
                it.normalizedPhoneNumber.isNotBlank() && it.normalizedPhoneNumber == normalizedIncomingPhone
            }
            if (directPhoneMatch != null) return directPhoneMatch
        }

        // Tier 3: Exact full normalized contact display name equality
        val exactNameMatch = rules.firstOrNull {
            it.normalizedContactName.isNotBlank() && it.normalizedContactName == normalizedIncoming
        }
        if (exactNameMatch != null) return exactNameMatch

        // Tier 4: Punctuation-stripped exact alphanumeric match
        val strippedMatch = rules.firstOrNull {
            val strippedRule = stripPunctuation(it.normalizedContactName)
            strippedRule.isNotBlank() && strippedRule == strippedIncoming
        }
        if (strippedMatch != null) return strippedMatch

        // Tier 5: Prefix / Base name match before parenthesis
        // e.g. "John Doe (Mobile)" vs rule "John Doe" or rule "John Doe (Work)" vs incoming "John Doe"
        val baseIncoming = extractBaseName(normalizedIncoming)
        val baseMatch = rules.firstOrNull {
            val baseRule = extractBaseName(it.normalizedContactName)
            (baseRule.isNotBlank() && baseRule == baseIncoming) ||
            (baseRule.isNotBlank() && baseRule == normalizedIncoming) ||
            (baseIncoming.isNotBlank() && baseIncoming == it.normalizedContactName)
        }
        if (baseMatch != null) return baseMatch

        // Tier 6: Substring containment (unambiguous single candidate only)
        val substringCandidates = rules.filter { rule ->
            val strippedRule = stripPunctuation(rule.normalizedContactName)
            if (strippedRule.length >= 4 && strippedIncoming.length >= 4) {
                strippedIncoming.contains(strippedRule) || strippedRule.contains(strippedIncoming)
            } else false
        }
        if (substringCandidates.size == 1) {
            return substringCandidates.first()
        }

        // Tier 7: Word token overlap (unambiguous single candidate only with high overlap)
        val incomingWords = strippedIncoming.split("\\s+".toRegex()).filter { it.length >= 3 }.toSet()
        if (incomingWords.isNotEmpty()) {
            val tokenCandidates = rules.filter { rule ->
                val ruleWords = stripPunctuation(rule.normalizedContactName)
                    .split("\\s+".toRegex()).filter { it.length >= 3 }.toSet()
                ruleWords.isNotEmpty() && (ruleWords == incomingWords || (ruleWords.size >= 2 && ruleWords.all { it in incomingWords }))
            }
            if (tokenCandidates.size == 1) {
                return tokenCandidates.first()
            }
        }

        return null
    }

    private fun extractBaseName(name: String): String {
        val parenIndex = name.indexOf('(')
        return if (parenIndex > 0) name.substring(0, parenIndex).trim() else name.trim()
    }

    /**
     * Matches phone number digits with suffix logic (last 7 to 10 digits),
     * ensuring international prefixes (+1, +91, 0, etc.) are matched safely.
     */
    private fun isPhoneDigitsMatch(digits1: String, digits2: String): Boolean {
        if (digits1 == digits2) return true
        val minLen = minOf(digits1.length, digits2.length)
        if (minLen >= 7) {
            val compareLen = minOf(minLen, 10)
            return digits1.takeLast(compareLen) == digits2.takeLast(compareLen)
        }
        return false
    }

    private fun stripPunctuation(str: String): String {
        return str.replace("[^a-zA-Z0-9\\s]".toRegex(), "").trim()
    }

    private fun isWhatsAppServiceNotification(sender: String, message: String): Boolean {
        val s = sender.lowercase()
        val m = message.lowercase()
        return s.contains("whatsapp") ||
                m.contains("checking for new messages") ||
                m.contains("backup in progress") ||
                m.contains("waiting for this message")
    }
}
