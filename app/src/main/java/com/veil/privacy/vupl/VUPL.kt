package com.veil.privacy.vupl

import android.icu.text.BreakIterator
import android.icu.text.UnicodeSet
import android.util.Base64
import org.json.JSONObject
import java.nio.charset.StandardCharsets
import java.nio.CharBuffer
import java.nio.charset.CodingErrorAction
import java.security.SecureRandom
import java.util.Locale
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import java.util.zip.CRC32

/**
 * VUPL v1 Kotlin port.
 *
 * Compatibility contract:
 * - dictionary key iteration order is exactly the order in veil_keys_v1.json
 * - dict derivation intentionally uses the ASCII hex representation of HMAC(...VUPL-DICT),
 *   because the TypeScript source passes digest().toString("hex") as the next HMAC key.
 * - AES key is the raw 32-byte HMAC(...VUPL-AES) output.
 */
object VUPL {
    private const val SEP = "·"
    private val random = SecureRandom()

    data class KeysFile(val version: Int, val keys: List<String>)
    class Dictionary internal constructor(
        val version: Int,
        internal val keyToCode: LinkedHashMap<String, String>,
        internal val codeToKey: HashMap<String, String>
    ) {
        internal val phrases: List<String> = keyToCode.keys.asSequence().filter { it.startsWith("p:") }
            .map { it.substring(2) }.sortedByDescending { it.length }.toList()
        fun has(key: String): Boolean = keyToCode.containsKey(key)
        val size: Int get() = keyToCode.size
    }
    data class PairState(val dict: Dictionary, internal val aesKey: ByteArray)
    data class Decoded(val text: String, val meta: Map<String, String>)

    private val ranges = arrayOf(
        intArrayOf(65,90), intArrayOf(97,122), intArrayOf(48,57),
        intArrayOf(0x391,0x3A1), intArrayOf(0x3A3,0x3C9),
        intArrayOf(0x410,0x44F), intArrayOf(0x531,0x556),
        intArrayOf(0x5D0,0x5EA), intArrayOf(0x10D0,0x10FA),
        intArrayOf(0x13A0,0x13F4), intArrayOf(0x2200,0x22FF),
        intArrayOf(0x25A0,0x25FF), intArrayOf(0x20A0,0x20BF),
        intArrayOf(0x104B0,0x104D3), intArrayOf(0x16800,0x16A38)
    )
    private val pool: List<String> = buildList {
        for (r in ranges) for (cp in r[0]..r[1]) add(String(Character.toChars(cp)))
        for (c in "!#$%&*+<>?@^_~") add(c.toString())
    }

    private val emojiSet = UnicodeSet("[[:Extended_Pictographic:][:Regional_Indicator:]\\u20E3]").freeze()

    private fun hmac(key: ByteArray, message: ByteArray): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key, "HmacSHA256"))
        return mac.doFinal(message)
    }
    private fun hex(bytes: ByteArray): String = bytes.joinToString("") { "%02x".format(it.toInt() and 0xff) }
    private fun unhex(s: String): ByteArray {
        require(s.length % 2 == 0 && s.matches(Regex("[0-9a-fA-F]+"))) { "Invalid pairing secret hex" }
        return ByteArray(s.length / 2) { i -> s.substring(i * 2, i * 2 + 2).toInt(16).toByte() }
    }

    /** Phone A: generate exactly 32 random bytes, represented as 64 lowercase hex characters. */
    fun createPairSecret(): String = ByteArray(32).also(random::nextBytes).let(::hex)

    fun parseKeysFile(json: String): KeysFile {
        val root = JSONObject(json)
        require(root.optString("protocol") == "VEIL/1") { "Unsupported keys protocol" }
        val arr = root.getJSONArray("keys")
        val keys = ArrayList<String>(arr.length())
        for (i in 0 until arr.length()) keys.add(arr.getString(i))
        require(root.getInt("count") == keys.size) { "Key count mismatch" }
        return KeysFile(root.getInt("version"), keys)
    }

    private fun glyphCode(dictMac: Mac, key: String, attempt: Int): String {
        val digest = dictMac.doFinal((key + "\u0000" + attempt).toByteArray(StandardCharsets.UTF_8))
        val count = 2 + ((digest[0].toInt() and 0xff) % 3)
        val sb = StringBuilder()
        for (i in 0 until count) {
            val hi = digest[1 + 2 * i].toInt() and 0xff
            val lo = digest[2 + 2 * i].toInt() and 0xff
            sb.append(pool[((hi shl 8) or lo) % pool.size])
        }
        return sb.toString()
    }

    fun openPair(keys: KeysFile, pairSecretHex: String): PairState {
        val secret = unhex(pairSecretHex)
        require(secret.size == 32) { "Pairing secret must be exactly 32 bytes" }
        val dictDigest = hmac(secret, "VUPL-DICT".toByteArray(StandardCharsets.UTF_8))
        // IMPORTANT: TypeScript uses digest().toString("hex") and then uses that STRING as HMAC key.
        val dictSecret = hex(dictDigest).toByteArray(StandardCharsets.UTF_8)
        val aes = hmac(secret, "VUPL-AES".toByteArray(StandardCharsets.UTF_8))
        val dictMac = Mac.getInstance("HmacSHA256").apply { init(SecretKeySpec(dictSecret, "HmacSHA256")) }
        val k2c = LinkedHashMap<String, String>(keys.keys.size)
        val c2k = HashMap<String, String>(keys.keys.size)
        for (key in keys.keys) {
            var attempt = 0
            var code: String
            do {
                code = glyphCode(dictMac, key, attempt)
                attempt++
            } while (c2k.containsKey(code))
            k2c[key] = code
            c2k[code] = key
        }
        return PairState(Dictionary(keys.version, k2c, c2k), aes)
    }

    private fun utf8(value: String): ByteArray {
        val encoder = StandardCharsets.UTF_8.newEncoder()
            .onMalformedInput(CodingErrorAction.REPLACE)
            .onUnmappableCharacter(CodingErrorAction.REPLACE)
            .replaceWith(byteArrayOf(0xEF.toByte(), 0xBF.toByte(), 0xBD.toByte()))
        val buffer = encoder.encode(CharBuffer.wrap(value))
        return ByteArray(buffer.remaining()).also { buffer.get(it) }
    }

    fun crc32(value: String): String {
        val crc = CRC32()
        // JavaScript TextEncoder replaces isolated UTF-16 surrogates with U+FFFD.
        crc.update(utf8(value))
        return crc.value.toString(16).padStart(8, '0')
    }

    private fun isLetterOrNumber(cp: Int): Boolean {
        val type = Character.getType(cp)
        return Character.isLetter(cp) ||
            type == Character.DECIMAL_DIGIT_NUMBER.toInt() ||
            type == Character.LETTER_NUMBER.toInt() ||
            type == Character.OTHER_NUMBER.toInt()
    }
    private fun containsEmojiProperty(cluster: String): Boolean {
        var i = 0
        while (i < cluster.length) {
            val cp = cluster.codePointAt(i)
            if (emojiSet.contains(cp)) return true
            i += Character.charCount(cp)
        }
        return false
    }
    private fun isLetterOrMark(cp: Int): Boolean {
        val t = Character.getType(cp)
        return Character.isLetter(cp) || t == Character.NON_SPACING_MARK.toInt() ||
            t == Character.COMBINING_SPACING_MARK.toInt() || t == Character.ENCLOSING_MARK.toInt()
    }
    private fun isMarkOrNumber(cp: Int): Boolean = isLetterOrMark(cp) || isLetterOrNumber(cp)

    private fun nextWordEnd(text: String, start: Int): Int {
        if (start >= text.length) return start
        var cp = text.codePointAt(start)
        if (!isLetterOrMark(cp)) return start
        var i = start + Character.charCount(cp)
        while (i < text.length) {
            cp = text.codePointAt(i)
            if (isMarkOrNumber(cp) || cp == 0x27 || cp == 0x2019) i += Character.charCount(cp)
            else break
        }
        return i
    }
    private fun nextNumberEnd(text: String, start: Int): Int {
        var i = start
        if (i < text.length && text[i] == '-') i++
        val digitsStart = i
        while (i < text.length && text[i] in '0'..'9') i++
        if (i == digitsStart) return start
        if (i < text.length && text[i] == '.') {
            val dot = i++
            val fracStart = i
            while (i < text.length && text[i] in '0'..'9') i++
            if (i == fracStart) i = dot
        }
        return i
    }
    private fun graphemes(text: String): Map<Int, String> {
        val result = HashMap<Int, String>()
        val it = BreakIterator.getCharacterInstance(Locale.ROOT)
        it.setText(text)
        var start = it.first()
        var end = it.next()
        while (end != BreakIterator.DONE) {
            result[start] = text.substring(start, end)
            start = end
            end = it.next()
        }
        return result
    }
    private fun scripts(text: String): String {
        val found = LinkedHashSet<String>()
        var i = 0
        while (i < text.length) {
            val cp = text.codePointAt(i)
            val us = Character.UnicodeScript.of(cp)
            when (us) {
                Character.UnicodeScript.LATIN -> found.add("Latn")
                Character.UnicodeScript.DEVANAGARI -> found.add("Deva")
                Character.UnicodeScript.TELUGU -> found.add("Telu")
                Character.UnicodeScript.TAMIL -> found.add("Taml")
                Character.UnicodeScript.ARABIC -> found.add("Arab")
                Character.UnicodeScript.HIRAGANA, Character.UnicodeScript.KATAKANA -> found.add("Jpan")
                Character.UnicodeScript.HAN -> found.add("Hans")
                Character.UnicodeScript.HANGUL -> found.add("Kore")
                Character.UnicodeScript.CYRILLIC -> found.add("Cyrl")
                else -> Unit
            }
            i += Character.charCount(cp)
        }
        return if (found.isEmpty()) "Zyyy" else found.joinToString("+")
    }

    fun tokenize(text: String, dict: Dictionary): List<String> {
        val out = ArrayList<String>()
        val graphemeAt = graphemes(text)
        val phrases = dict.phrases

        fun pick(ch: String) {
            val key = listOf("ws:", "s:", "c:", "e:").asSequence()
                .map { it + ch }.firstOrNull { dict.has(it) }
            if (key != null) out.add(key)
            else {
                val cp = ch.codePointAt(0)
                out.add("x:CP")
                cp.toString().forEach { out.add("d:$it") }
                out.add("x:END")
            }
        }

        var i = 0
        while (i < text.length) {
            // Match the TypeScript source's t[i - 1] UTF-16 code-unit boundary check.
            val prevUnit = if (i > 0) text[i - 1].code else -1
            val prevIsAln = prevUnit >= 0 && isLetterOrNumber(prevUnit)

            if (!prevIsAln) {
                val phrase = phrases.firstOrNull { p ->
                    text.startsWith(p, i) && run {
                        val end = i + p.length
                        end >= text.length || !isLetterOrNumber(text.codePointAt(end))
                    }
                }
                if (phrase != null) {
                    out.add("p:$phrase")
                    i += phrase.length
                    continue
                }
            }

            val wordEnd = nextWordEnd(text, i)
            if (wordEnd > i) {
                val word = text.substring(i, wordEnd)
                val wk = "w:$word"
                if (dict.has(wk)) out.add(wk) else {
                    var j = i
                    while (j < wordEnd) {
                        val cp = text.codePointAt(j)
                        pick(String(Character.toChars(cp)))
                        j += Character.charCount(cp)
                    }
                }
                i = wordEnd
                continue
            }

            val cluster = graphemeAt[i] ?: String(Character.toChars(text.codePointAt(i)))
            val firstCp = cluster.codePointAt(0)
            if (containsEmojiProperty(cluster)) {
                val ek = "e:$cluster"
                if (dict.has(ek)) out.add(ek)
                else {
                    var j = 0
                    while (j < cluster.length) {
                        val cp = cluster.codePointAt(j)
                        pick(String(Character.toChars(cp)))
                        j += Character.charCount(cp)
                    }
                }
                i += cluster.length
                continue
            }

            val numberEnd = nextNumberEnd(text, i)
            if (numberEnd > i && !(text[i] == '-' && prevIsAln)) {
                out.add("x:NUM")
                text.substring(i, numberEnd).forEach { out.add("d:$it") }
                out.add("x:END")
                i = numberEnd
                continue
            }

            var j = 0
            while (j < cluster.length) {
                val cp = cluster.codePointAt(j)
                pick(String(Character.toChars(cp)))
                j += Character.charCount(cp)
            }
            i += cluster.length
        }
        return out
    }

    fun encode(text: String, dict: Dictionary, from: String = "", to: String = "", includeTimestamp: Boolean = false): String {
        val body = tokenize(text, dict).joinToString(SEP) { token ->
            dict.keyToCode[token] ?: throw IllegalArgumentException("Not in dictionary: $token")
        }
        val id = ByteArray(4).also(random::nextBytes).let(::hex)
        val ts = if (includeTimestamp) System.currentTimeMillis().toString() else ""
        return listOf(
            "VEIL/1", "d=${dict.version}", "m=1", "id=$id", "from=$from", "to=$to",
            "script=${scripts(text)}", "enc=utf-8", "ts=$ts", "t=$body",
            "ck=${crc32(body)}", "ic=${crc32(text)}"
        ).joinToString("|")
    }

    fun decode(packet: String, dict: Dictionary): Decoded {
        val parts = packet.trim().split('|')
        require(parts.firstOrNull() == "VEIL/1") { "Not a VEIL/1 packet" }
        val fields = LinkedHashMap<String, String>()
        for (part in parts.drop(1)) {
            val ix = part.indexOf('=')
            require(ix >= 0) { "Malformed field" }
            fields[part.substring(0, ix)] = part.substring(ix + 1)
        }
        for (required in listOf("d", "id", "t", "ck", "ic"))
            require(fields.containsKey(required)) { "Corrupted: missing $required" }
        require(crc32(fields.getValue("t")) == fields.getValue("ck")) { "Corrupted: token checksum mismatch" }
        val packetVersion = fields.getValue("d").toIntOrNull()
            ?: throw IllegalArgumentException("Invalid dictionary version")
        require(packetVersion <= dict.version) {
            "UPDATE_NEEDED: packet uses key list v$packetVersion, this app has v${dict.version}"
        }

        val out = StringBuilder()
        var mode = 0 // 0 text, 1 number, 2 raw code point
        val cpDigits = StringBuilder()
        val tokens = fields.getValue("t")
        val codes = if (tokens.isEmpty()) emptyList() else tokens.split(SEP)
        for (code in codes) {
            val key = dict.codeToKey[code] ?: throw IllegalArgumentException("Unknown token")
            val colon = key.indexOf(':')
            require(colon >= 0) { "Malformed dictionary key" }
            val ns = key.substring(0, colon)
            val value = key.substring(colon + 1)
            when (key) {
                "x:NUM" -> { require(mode == 0); mode = 1; continue }
                "x:CP" -> { require(mode == 0); mode = 2; cpDigits.setLength(0); continue }
                "x:END" -> {
                    require(mode != 0) { "Stray end marker" }
                    if (mode == 2) {
                        val n = cpDigits.toString().toLongOrNull()
                            ?: throw IllegalArgumentException("Bad code point")
                        require(n in 0..0x10FFFF) { "Bad code point" }
                        out.append(String(Character.toChars(n.toInt())))
                    }
                    mode = 0
                    continue
                }
            }
            require((mode != 0) == (ns == "d")) { "Bad number structure" }
            if (mode == 2) cpDigits.append(value) else out.append(value)
        }
        require(mode == 0 && crc32(out.toString()) == fields.getValue("ic")) {
            "Integrity check failed"
        }
        return Decoded(out.toString(), fields)
    }

    private fun b64(bytes: ByteArray) = Base64.encodeToString(bytes, Base64.NO_WRAP)
    private fun unb64(value: String) = Base64.decode(value, Base64.DEFAULT)

    private fun seal(plain: String, key: ByteArray): String {
        require(key.size == 32) { "AES-256 key must be 32 bytes" }
        val iv = ByteArray(12).also(random::nextBytes)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, iv))
        cipher.updateAAD("VEILX/2".toByteArray(StandardCharsets.UTF_8))
        val ciphertextAndTag = cipher.doFinal(plain.toByteArray(StandardCharsets.UTF_8))
        return listOf("VEILX/2", b64(iv), b64(ciphertextAndTag)).joinToString("|")
    }

    private fun unseal(envelope: String, key: ByteArray): String {
        val parts = envelope.trim().split('|')
        require(parts.size >= 3 && parts[0] == "VEILX/2") { "Not a VEILX/2 envelope" }
        require(key.size == 32) { "AES-256 key must be 32 bytes" }
        try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, unb64(parts[1])))
            cipher.updateAAD("VEILX/2".toByteArray(StandardCharsets.UTF_8))
            return String(cipher.doFinal(unb64(parts[2])), StandardCharsets.UTF_8)
        } catch (e: Exception) {
            throw IllegalArgumentException("Decryption failed: wrong key or tampered data", e)
        }
    }

    fun sendMessage(text: String, pair: PairState): String =
        seal(encode(text, pair.dict), pair.aesKey)

    fun receiveMessage(envelope: String, pair: PairState): String =
        decode(unseal(envelope, pair.aesKey), pair.dict).text
}
