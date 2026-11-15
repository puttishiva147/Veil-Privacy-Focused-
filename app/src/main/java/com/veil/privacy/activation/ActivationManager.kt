package com.veil.privacy.activation

import android.content.Context
import android.provider.Settings
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.veil.privacy.R
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.MessageDigest
import java.security.Signature
import java.util.concurrent.TimeUnit
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.spec.GCMParameterSpec

class ActivationManager(private val context: Context) {
    companion object {
        @Volatile var isLicenseActive: Boolean = false
            private set
    }
    private val app = context.applicationContext
    private val prefs = app.getSharedPreferences("veil_license_vault", Context.MODE_PRIVATE)
    private val base = app.getString(R.string.veil_api_base_url).trim().trimEnd('/')
    private val client = OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS).readTimeout(20, TimeUnit.SECONDS).build()
    private val media = "application/json; charset=utf-8".toMediaType()

    fun websiteUrl() = app.getString(R.string.veil_activation_website)
    fun hasToken() = readToken() != null

    fun refresh(): Boolean {
        isLicenseActive = false
        val token = readToken() ?: return false
        requireBackend()
        val request = Request.Builder().url(base + "/api/v1/licenses/status")
            .header("Authorization", "Bearer " + token).post("{}".toRequestBody(media)).build()
        client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (response.code == 401 || response.code == 403) { clearToken(); return false }
            if (!response.isSuccessful) throw IOException("VEIL licensing service unavailable (HTTP " + response.code + ").")
            val data = JSONObject(body)
            if (!data.optBoolean("active")) { clearToken(); return false }
            data.optString("accessToken").takeIf { it.isNotBlank() }?.let { saveToken(it) }
            isLicenseActive = true
            return true
        }
    }

    fun activate(rawCode: String) {
        requireBackend()
        val code = rawCode.filter { it.isDigit() }
        require(code.matches(Regex("\\d{12}"))) { "Enter the complete 12-digit activation code." }
        val pair = deviceKeyPair()
        val pem = "-----BEGIN PUBLIC KEY-----\n" + Base64.encodeToString(pair.public.encoded, Base64.NO_WRAP) + "\n-----END PUBLIC KEY-----"
        val fingerprint = sha256(app.packageName + ":" + Settings.Secure.getString(app.contentResolver, Settings.Secure.ANDROID_ID).orEmpty())
        val challenge = post("/api/v1/activation/challenge", JSONObject().put("publicKey", pem).put("deviceFingerprintHash", fingerprint))
        val nonce = challenge.getString("nonce")
        val signer = Signature.getInstance("SHA256withRSA")
        signer.initSign(pair.private)
        signer.update(nonce.toByteArray(Charsets.UTF_8))
        val proof = Base64.encodeToString(signer.sign(), Base64.NO_WRAP)
        val result = post("/api/v1/activation/activate", JSONObject().put("code", code).put("publicKey", pem).put("nonce", nonce).put("signature", proof).put("deviceFingerprintHash", fingerprint))
        val token = result.optString("accessToken")
        if (token.isBlank()) throw IOException("Activation server did not issue a session.")
        saveToken(token)
        isLicenseActive = true
    }

    private fun post(path: String, payload: JSONObject): JSONObject {
        val request = Request.Builder().url(base + path).post(payload.toString().toRequestBody(media)).build()
        client.newCall(request).execute().use { response ->
            val raw = response.body?.string().orEmpty()
            val data = try { JSONObject(raw) } catch (_: Exception) { JSONObject() }
            if (!response.isSuccessful) throw IOException(data.optString("error").ifBlank { "Activation failed (HTTP " + response.code + ")." })
            return data
        }
    }

    private fun requireBackend() {
        if (!base.startsWith("https://")) throw IOException("VEIL licensing backend is not configured. Activation is not available yet.")
    }

    private fun deviceKeyPair(): KeyPair {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val alias = "veil_license_device_rsa_v1"
        if (ks.containsAlias(alias)) return KeyPair(ks.getCertificate(alias).publicKey, ks.getKey(alias, null) as java.security.PrivateKey)
        val generator = KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_RSA, "AndroidKeyStore")
        generator.initialize(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_SIGN).setKeySize(2048).setDigests(KeyProperties.DIGEST_SHA256).setUserAuthenticationRequired(false).build())
        return generator.generateKeyPair()
    }

    private fun sha256(value: String) = MessageDigest.getInstance("SHA-256").digest(value.toByteArray()).joinToString("") { "%02x".format(it) }

    private fun saveToken(token: String) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, aesKey())
        val encrypted = cipher.doFinal(token.toByteArray())
        prefs.edit().putString("iv", Base64.encodeToString(cipher.iv, Base64.NO_WRAP)).putString("ciphertext", Base64.encodeToString(encrypted, Base64.NO_WRAP)).apply()
    }

    private fun readToken(): String? {
        return try {
            val iv = prefs.getString("iv", null) ?: return null
            val data = prefs.getString("ciphertext", null) ?: return null
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, aesKey(), GCMParameterSpec(128, Base64.decode(iv, Base64.NO_WRAP)))
            String(cipher.doFinal(Base64.decode(data, Base64.NO_WRAP)))
        } catch (_: Exception) {
            clearToken()
            null
        }
    }

    private fun clearToken() { isLicenseActive = false; prefs.edit().clear().apply() }

    private fun aesKey(): javax.crypto.SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val alias = "veil_license_token_aes_v1"
        (ks.getKey(alias, null) as? javax.crypto.SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT).setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).setKeySize(256).build())
        return generator.generateKey()
    }
}