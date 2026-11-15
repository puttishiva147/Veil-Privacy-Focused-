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


/**
 * Process-wide codec registry shared by chat UI and NotificationListenerService.
 * Install the dictionary-backed codec once its file format and validation are finalized.
 */
object VeilLanguageCodecProvider {
    @Volatile
    private var activeCodec: VeilLanguageCodec = DictionaryPendingCodec()

    fun get(): VeilLanguageCodec = activeCodec

    fun install(codec: VeilLanguageCodec) {
        require(codec.isReady) { "Cannot install a codec that is not ready" }
        require(codec.dictionaryVersion >= 1) { "Dictionary version must be positive" }
        activeCodec = codec
    }

    fun resetToPending() {
        activeCodec = DictionaryPendingCodec()
    }
}
