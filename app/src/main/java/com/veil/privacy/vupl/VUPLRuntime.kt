package com.veil.privacy.vupl

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Process-local cache of the one locally paired VUPL peer. No network or account is used. */
object VUPLRuntime {
    const val DEFAULT_PAIR_ID = "primary"
    @Volatile private var cachedSecret: String? = null
    @Volatile private var cachedPair: VUPL.PairState? = null

    fun clearCache() {
        synchronized(this) {
            cachedSecret = null
            cachedPair = null
        }
    }

    fun pairState(context: Context): VUPL.PairState? {
        val secret = PairSecretStore(context).get(DEFAULT_PAIR_ID) ?: return null
        cachedPair?.let { if (cachedSecret == secret) return it }
        synchronized(this) {
            cachedPair?.let { if (cachedSecret == secret) return it }
            val json = context.applicationContext.assets.open("veil_keys_v1.json")
                .bufferedReader(Charsets.UTF_8).use { it.readText() }
            val keys = VUPL.parseKeysFile(json)
            return VUPL.openPair(keys, secret).also {
                cachedSecret = secret
                cachedPair = it
            }
        }
    }

    suspend fun encodeOutgoing(context: Context, plaintext: String): String = withContext(Dispatchers.IO) {
        if (plaintext.isEmpty()) return@withContext plaintext
        val pair = pairState(context) ?: return@withContext plaintext
        VUPL.sendMessage(plaintext, pair)
    }

    suspend fun decodeIncoming(context: Context, incoming: String): String = withContext(Dispatchers.IO) {
        if (!incoming.startsWith("VEILX/2|")) return@withContext incoming
        val pair = pairState(context) ?: return@withContext "[Encrypted VUPL message — pair this device to decrypt]"
        try {
            VUPL.receiveMessage(incoming, pair)
        } catch (_: Exception) {
            "[Encrypted VUPL message — decryption failed]"
        }
    }
}
