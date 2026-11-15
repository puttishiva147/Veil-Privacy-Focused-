package com.veil.privacy.communication

/**
 * Adapter contract for the VEIL-owned universal language dictionary.
 * The dictionary file will provide the concrete implementation later.
 */
interface VeilLanguageCodec {
    val isReady: Boolean
    val dictionaryVersion: Int
    fun encode(plainText: String): String
    fun decode(encodedText: String): String
}

/** Safe placeholder until the official VEIL dictionary file is supplied and loaded. */
class DictionaryPendingCodec : VeilLanguageCodec {
    override val isReady: Boolean = false
    override val dictionaryVersion: Int = 0

    override fun encode(plainText: String): String =
        throw IllegalStateException("VEIL language dictionary has not been installed")

    override fun decode(encodedText: String): String =
        throw IllegalStateException("VEIL language dictionary has not been installed")
}
