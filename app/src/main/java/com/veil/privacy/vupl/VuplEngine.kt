package com.veil.privacy.vupl

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import org.json.JSONObject
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import java.util.zip.CRC32
import android.icu.text.BreakIterator
import java.util.Locale

/**
 * Kotlin port of the supplied VUPL v1 protocol.
 * Dictionary order is significant: collision retries are resolved in source-list order.
 */
class VuplEngine private constructor(
    private val version: Int,
    private val k2c: Map<String, String>,
    private val c2k: Map<String, String>,
    private val aesKey: ByteArray
) {
    data class Decoded(val text: String, val meta: Map<String, String>)

    companion object {
        private const val SEP = "·"
        private val random = SecureRandom()
        private val pool: List<String> = buildList {
            val ranges = arrayOf(
                65..90, 97..122, 48..57, 0x391..0x3A1, 0x3A3..0x3C9,
                0x410..0x44F, 0x531..0x556, 0x5D0..0x5EA, 0x10D0..0x10FA,
                0x13A0..0x13F4, 0x2200..0x22FF, 0x25A0..0x25FF, 0x20A0..0x20BF,
                0x104B0..0x104D3, 0x16800..0x16A38
            )
            ranges.forEach { r -> r.forEach { add(String(Character.toChars(it))) } }
            "!#$%&*+<>?@^_~".forEach { add(it.toString()) }
        }

        fun createPairSecret(): String = ByteArray(32).also(random::nextBytes).joinToString("") { "%02x".format(it) }

        fun openPair(context: Context, secretHex: String): VuplEngine {
            require(secretHex.matches(Regex("(?i)[0-9a-f]{64}"))) { "Pair secret must be 32 bytes (64 hex characters)" }
            val json = context.assets.open("veil_keys_v1.json").bufferedReader(StandardCharsets.UTF_8).use { it.readText() }
            val root = JSONObject(json)
            require(root.optString("protocol") == "VEIL/1") { "Unsupported VUPL dictionary protocol" }
            val version = root.getInt("version")
            val keys = root.getJSONArray("keys")
            require(keys.length() == 157116) { "VUPL dictionary mismatch: expected 157116 ordered entries, got ${keys.length()}" }
            val secret = hexToBytes(secretHex)
            val dictSecret = hmac(secret, "VUPL-DICT".toByteArray()).joinToString("") { "%02x".format(it) }
            val aes = hmac(secret, "VUPL-AES".toByteArray())
            val dictMac = Mac.getInstance("HmacSHA256").apply {
                init(SecretKeySpec(dictSecret.toByteArray(StandardCharsets.UTF_8), "HmacSHA256"))
            }
            val forward = LinkedHashMap<String, String>(keys.length())
            val reverse = HashMap<String, String>(keys.length())
            for (i in 0 until keys.length()) {
                val key = keys.getString(i)
                var attempt = 0
                var code: String
                do { code = generateCode(dictMac, key, attempt++) } while (reverse.containsKey(code))
                forward[key] = code
                reverse[code] = key
            }
            return VuplEngine(version, forward, reverse, aes)
        }

        private fun generateCode(secretHex: String, key: String, attempt: Int): String {
            val h = hmac(secretHex.toByteArray(StandardCharsets.UTF_8), (key + "\u0000" + attempt).toByteArray(StandardCharsets.UTF_8))
            val n = 2 + ((h[0].toInt() and 255) % 3)
            return buildString { repeat(n) { i ->
                val a = h[1 + 2 * i].toInt() and 255
                val b = h[2 + 2 * i].toInt() and 255
                append(pool[((a shl 8) or b) % pool.size])
            } }
        }

        private fun hmac(key: ByteArray, data: ByteArray): ByteArray =
            Mac.getInstance("HmacSHA256").run { init(SecretKeySpec(key, "HmacSHA256")); doFinal(data) }

        private fun hexToBytes(s: String): ByteArray = ByteArray(s.length / 2) { i ->
            s.substring(i * 2, i * 2 + 2).toInt(16).toByte()
        }

        fun crc32(value: String): String {
            val crc = CRC32()
            crc.update(value.toByteArray(StandardCharsets.UTF_8))
            return crc.value.toString(16).padStart(8, '0')
        }
    }

    private fun isLetterOrNumber(cp: Int) =
        Character.isLetterOrDigit(cp) || Character.getType(cp) == Character.NON_SPACING_MARK.toInt() ||
            Character.getType(cp) == Character.COMBINING_SPACING_MARK.toInt()

    private fun isEmojiCluster(s: String): Boolean =
        s.codePoints().anyMatch { cp ->
            cp in 0x1F000..0x1FAFF || cp in 0x2600..0x27BF || cp in 0x1F1E6..0x1F1FF || cp == 0x20E3
        }

    private fun tokenize(text: String): List<String> {
        val out = ArrayList<String>()
        val phrases = k2c.keys.asSequence().filter { it.startsWith("p:") }
            .map { it.substring(2) }.sortedByDescending { it.length }.toList()
        val graphemes = BreakIterator.getCharacterInstance(Locale.ROOT)
        graphemes.setText(text)
        val clusters = HashMap<Int, String>()
        var g = graphemes.first()
        while (true) {
            val next = graphemes.next()
            if (next == BreakIterator.DONE) break
            clusters[g] = text.substring(g, next)
            g = next
        }

        fun pick(cluster: String) {
            val direct = listOf("ws:", "s:", "c:", "e:").firstOrNull { k2c.containsKey(it + cluster) }
            if (direct != null) out.add(direct + cluster) else {
                val cps = cluster.codePoints().toArray()
                cps.forEach { cp ->
                    out.add("x:CP")
                    cp.toString().forEach { out.add("d:$it") }
                    out.add("x:END")
                }
            }
        }

        var i = 0
        while (i < text.length) {
            val prev = if (i > 0) text.codePointBefore(i) else -1
            val prevAln = prev >= 0 && isLetterOrNumber(prev)
            if (!prevAln) {
                val phrase = phrases.firstOrNull { p ->
                    text.startsWith(p, i) && (i + p.length >= text.length || !isLetterOrNumber(text.codePointAt(i + p.length)))
                }
                if (phrase != null) { out.add("p:$phrase"); i += phrase.length; continue }
            }

            val cp = text.codePointAt(i)
            if (Character.isLetter(cp) || Character.getType(cp) == Character.NON_SPACING_MARK.toInt()) {
                var end = i
                while (end < text.length) {
                    val c = text.codePointAt(end)
                    val type = Character.getType(c)
                    if (!(Character.isLetterOrDigit(c) || type == Character.NON_SPACING_MARK.toInt() ||
                                type == Character.COMBINING_SPACING_MARK.toInt() || c == 39 || c == 0x2019)) break
                    end += Character.charCount(c)
                }
                val word = text.substring(i, end)
                if (k2c.containsKey("w:$word")) out.add("w:$word") else {
                    var p = i
                    while (p < end) { val c = text.codePointAt(p); pick(String(Character.toChars(c))); p += Character.charCount(c) }
                }
                i = end
                continue
            }

            val cluster = clusters[i] ?: String(Character.toChars(cp))
            if (isEmojiCluster(cluster) && k2c.containsKey("e:$cluster")) out.add("e:$cluster")
            else if (cluster.length > Character.charCount(cp)) cluster.codePoints().forEach { pick(String(Character.toChars(it))) }
            else {
                val numeric = Regex("-?\\d+(?:\\.\\d+)?").find(text, i)
                if (numeric != null && numeric.range.first == i && !(numeric.value.startsWith("-") && prevAln)) {
                    out.add("x:NUM")
                    numeric.value.forEach { out.add("d:$it") }
                    out.add("x:END")
                    i += numeric.value.length
                    continue
                }
                pick(cluster)
            }
            i += cluster.length
        }
        return out
    }

    private fun scriptTag(text: String): String {
        val names = listOf(
            "Latn" to Character.UnicodeScript.LATIN, "Deva" to Character.UnicodeScript.DEVANAGARI,
            "Telu" to Character.UnicodeScript.TELUGU, "Taml" to Character.UnicodeScript.TAMIL,
            "Arab" to Character.UnicodeScript.ARABIC, "Jpan" to Character.UnicodeScript.HIRAGANA,
            "Hans" to Character.UnicodeScript.HAN, "Kore" to Character.UnicodeScript.HANGUL,
            "Cyrl" to Character.UnicodeScript.CYRILLIC
        )
        val found = names.filter { (name, s) ->
            text.codePoints().anyMatch {
                val actual = Character.UnicodeScript.of(it)
                actual == s || (name == "Jpan" && actual == Character.UnicodeScript.KATAKANA)
            }
        }.map { it.first }
        return found.joinToString("+").ifEmpty { "Zyyy" }
    }

    fun encode(text: String, from: String = "", to: String = "", includeTimestamp: Boolean = false): String {
        val body = tokenize(text).joinToString(SEP) { token -> k2c[token] ?: error("Not in dictionary: $token") }
        val id = ByteArray(4).also(random::nextBytes).joinToString("") { "%02x".format(it) }
        val ts = if (includeTimestamp) System.currentTimeMillis().toString() else ""
        return listOf("VEIL/1", "d=$version", "m=1", "id=$id", "from=$from", "to=$to",
            "script=${scriptTag(text)}", "enc=utf-8", "ts=$ts", "t=$body", "ck=${crc32(body)}", "ic=${crc32(text)}").joinToString("|")
    }

    fun decode(packet: String): Decoded {
        val parts = packet.trim().split("|")
        require(parts.firstOrNull() == "VEIL/1") { "Not a VEIL/1 packet" }
        val fields = linkedMapOf<String, String>()
        parts.drop(1).forEach { field ->
            val at = field.indexOf('=')
            require(at >= 0) { "Malformed field" }
            fields[field.substring(0, at)] = field.substring(at + 1)
        }
        listOf("d", "id", "t", "ck", "ic").forEach { require(fields.containsKey(it)) { "Corrupted: missing $it" } }
        val body = fields.getValue("t")
        require(crc32(body) == fields.getValue("ck")) { "Corrupted: token checksum mismatch" }
        require(fields.getValue("d").toInt() <= version) { "UPDATE_NEEDED: newer dictionary required" }
        val out = StringBuilder()
        var mode = 0
        val cp = StringBuilder()
        if (body.isNotEmpty()) for (code in body.split(SEP)) {
            val token = c2k[code] ?: error("Unknown token")
            val at = token.indexOf(':')
            val ns = token.substring(0, at)
            val value = token.substring(at + 1)
            when (token) {
                "x:NUM" -> { check(mode == 0); mode = 1 }
                "x:CP" -> { check(mode == 0); mode = 2; cp.setLength(0) }
                "x:END" -> {
                    check(mode != 0) { "Stray end marker" }
                    if (mode == 2) {
                        val n = cp.toString().toLongOrNull() ?: error("Bad code point")
                        require(n in 0..0x10FFFF && n !in 0xD800..0xDFFF) { "Bad code point" }
                        out.append(String(Character.toChars(n.toInt())))
                    }
                    mode = 0
                }
                else -> {
                    check((mode != 0) == (ns == "d")) { "Bad number structure" }
                    if (mode == 2) cp.append(value) else out.append(value)
                }
            }
        }
        require(mode == 0 && crc32(out.toString()) == fields.getValue("ic")) { "Integrity check failed" }
        return Decoded(out.toString(), fields)
    }

    fun sendMessage(text: String): String {
        val plain = encode(text).toByteArray(StandardCharsets.UTF_8)
        val iv = ByteArray(12).also(random::nextBytes)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(aesKey, "AES"), GCMParameterSpec(128, iv))
        cipher.updateAAD("VEILX/2".toByteArray(StandardCharsets.UTF_8))
        val encrypted = cipher.doFinal(plain)
        return "VEILX/2|${Base64.encodeToString(iv, Base64.NO_WRAP)}|${Base64.encodeToString(encrypted, Base64.NO_WRAP)}"
    }

    fun receiveMessage(envelope: String): String {
        val fields = envelope.trim().split("|")
        require(fields.size == 3 && fields[0] == "VEILX/2") { "Not a VEILX/2 envelope" }
        val iv = Base64.decode(fields[1], Base64.NO_WRAP)
        require(iv.size == 12) { "Invalid IV" }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(aesKey, "AES"), GCMParameterSpec(128, iv))
        cipher.updateAAD("VEILX/2".toByteArray(StandardCharsets.UTF_8))
        val plain = cipher.doFinal(Base64.decode(fields[2], Base64.NO_WRAP))
        return decode(String(plain, StandardCharsets.UTF_8)).text
    }
}

/** Local-only encrypted pairing-secret storage. The wrapping key never leaves Android Keystore. */
object VuplSecretStore {
    private const val PREFS = "vupl_pairing_secure"
    private const val ALIAS = "veil_vupl_pair_secret_wrap_v1"

    private fun wrappingKey(): java.security.Key {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(ALIAS, null) as? java.security.Key)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setRandomizedEncryptionRequired(true).build())
        return generator.generateKey()
    }

    fun save(context: Context, secretHex: String) {
        require(secretHex.matches(Regex("(?i)[0-9a-f]{64}")))
        val iv = ByteArray(12).also(SecureRandom()::nextBytes)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, wrappingKey(), GCMParameterSpec(128, iv))
        cipher.updateAAD("VEIL-VUPL-PAIR/1".toByteArray())
        val encrypted = cipher.doFinal(secretHex.toByteArray())
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString("iv", Base64.encodeToString(iv, Base64.NO_WRAP))
            .putString("secret", Base64.encodeToString(encrypted, Base64.NO_WRAP)).apply()
    }

    fun load(context: Context): String? {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val ivText = prefs.getString("iv", null) ?: return null
        val secretText = prefs.getString("secret", null) ?: return null
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, wrappingKey(), GCMParameterSpec(128, Base64.decode(ivText, Base64.NO_WRAP)))
        cipher.updateAAD("VEIL-VUPL-PAIR/1".toByteArray())
        return String(cipher.doFinal(Base64.decode(secretText, Base64.NO_WRAP)), StandardCharsets.UTF_8)
    }

    fun clear(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply()
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        if (ks.containsAlias(ALIAS)) ks.deleteEntry(ALIAS)
    }
}
