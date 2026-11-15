package com.veil.privacy.communication

import java.nio.charset.StandardCharsets
import java.util.Base64
import java.util.Locale

/**
 * Built-in YUPL v1 reversible language codec.
 *
 * Known words map to compact Unicode symbol tokens. Unknown words, whitespace,
 * punctuation, numbers and all other Unicode text use a lossless UTF-8 fallback.
 * This is encoding/obfuscation, NOT cryptographic encryption.
 */
class BuiltinYuplLanguageCodec : VeilLanguageCodec {
    override val isReady: Boolean = true
    override val dictionaryVersion: Int = 1

    private val wordSymbols = linkedMapOf(
        "hello" to "⟡", "hi" to "☾", "yes" to "✓", "no" to "⊗",
        "ok" to "⌁", "okay" to "⌃", "thanks" to "∴", "thank" to "∵",
        "please" to "⟐", "love" to "♡", "friend" to "◇", "good" to "⊙",
        "bad" to "⧫", "meet" to "⌖", "call" to "⌘", "wait" to "⏳",
        "where" to "⌗", "you" to "⋆", "i" to "ι", "we" to "∩",
        "me" to "μ", "are" to "∧", "am" to "α", "the" to "∈",
        "is" to "≡", "not" to "≠", "help" to "✚", "home" to "⌂",
        "come" to "↗", "go" to "↘", "now" to "⏱", "later" to "⌛",
        "tomorrow" to "☼", "today" to "☀", "sorry" to "∿", "safe" to "⛨",
        "send" to "➤", "message" to "✉", "water" to "≈", "food" to "♨",
        "work" to "⚒", "my" to "∽", "your" to "∾", "what" to "⋈",
        "when" to "⋉", "why" to "⋊"
    )
    private val symbolWords = wordSymbols.entries.associate { it.value to it.key }
    private val phrases = listOf(
        "how are you?", "i am fine", "good morning", "good night",
        "thank you", "i love you", "see you soon", "where are you",
        "on my way", "call me", "are you safe?", "i need help",
        "meet me", "take care", "talk later", "what happened?"
    )
    private val phraseIds = phrases.withIndex().associate { it.value to it.index }

    private val segments = Regex("""\p{L}[\p{L}\p{M}'’_-]*|\p{N}+|[^\p{L}\p{M}\p{N}]+""")

    override fun encode(plainText: String): String {
        if (plainText.isEmpty()) return "Y1;"
        val normalized = plainText.lowercase(Locale.ROOT)
        if (plainText == normalized) phraseIds[normalized]?.let { return "Y1;p" + it.toString(36).padStart(2, '0') }
        val tokens = segments.findAll(plainText).map { match -> encodeSegment(match.value) }.toList()
        return "Y1;" + tokens.joinToString(".")
    }

    override fun decode(encodedText: String): String {
        require(encodedText.startsWith("Y1;")) { "Unsupported YUPL language version" }
        val body = encodedText.removePrefix("Y1;")
        if (body.isEmpty()) return ""
        if (body.startsWith("p") && body.length == 3) {
            val id = body.substring(1).toIntOrNull(36) ?: throw IllegalArgumentException("Invalid YUPL phrase token")
            return phrases.getOrNull(id) ?: throw IllegalArgumentException("Unknown YUPL phrase token")
        }
        return body.split('.').joinToString("") { decodeToken(it) }
    }

    private fun encodeSegment(segment: String): String {
        val lower = segment.lowercase(Locale.ROOT)
        val symbol = wordSymbols[lower]
        if (symbol != null) {
            val case = when {
                segment == lower -> "l"
                segment == segment.uppercase(Locale.ROOT) -> "u"
                segment == lower.replaceFirstChar { it.titlecase(Locale.ROOT) } -> "t"
                else -> null
            }
            if (case != null) return symbol + case
        }
        val bytes = segment.toByteArray(StandardCharsets.UTF_8)
        return "x" + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    private fun decodeToken(token: String): String {
        require(token.isNotEmpty()) { "Empty YUPL token" }
        if (token.startsWith("x")) {
            val bytes = Base64.getUrlDecoder().decode(token.substring(1))
            return String(bytes, StandardCharsets.UTF_8)
        }
        if (token.startsWith("p") && token.length == 3) {
            val id = token.substring(1).toIntOrNull(36) ?: throw IllegalArgumentException("Invalid phrase token")
            return phrases.getOrNull(id) ?: throw IllegalArgumentException("Unknown phrase token")
        }
        require(token.length >= 2) { "Malformed YUPL dictionary token" }
        val symbol = token.dropLast(1)
        val case = token.last()
        val word = symbolWords[symbol] ?: throw IllegalArgumentException("Unknown YUPL symbol")
        return when (case) {
            'l' -> word
            'u' -> word.uppercase(Locale.ROOT)
            't' -> word.replaceFirstChar { it.titlecase(Locale.ROOT) }
            else -> throw IllegalArgumentException("Unknown YUPL case marker")
        }
    }
}
