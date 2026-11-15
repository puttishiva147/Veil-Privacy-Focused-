package com.veil.privacy.license

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import java.security.KeyFactory

/** One RSA signing identity per VEIL installation. The private key never leaves Android Keystore. */
class DeviceIdentity {
    private val alias = "com.veil.privacy.license.device.v1"

    private fun keyPair(): KeyPair {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val privateKey = store.getKey(alias, null) as? java.security.PrivateKey
        val certificate = store.getCertificate(alias)
        if (privateKey != null && certificate != null) return KeyPair(certificate.publicKey, privateKey)
        val generator = KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_RSA, "AndroidKeyStore")
        generator.initialize(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY)
            .setDigests(KeyProperties.DIGEST_SHA256)
            .setSignaturePaddings(KeyProperties.SIGNATURE_PADDING_RSA_PKCS1)
            .setKeySize(2048)
            .build())
        return generator.generateKeyPair()
    }

    fun publicKeyPem(): String {
        val encoded = Base64.encodeToString(keyPair().public.encoded, Base64.NO_WRAP)
        return "-----BEGIN PUBLIC KEY-----\n" + encoded.chunked(64).joinToString("\n") + "\n-----END PUBLIC KEY-----"
    }

    fun signNonce(nonce: String): String {
        val signer = Signature.getInstance("SHA256withRSA")
        signer.initSign(keyPair().private)
        signer.update(nonce.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(signer.sign(), Base64.NO_WRAP)
    }
}
