package com.veil.privacy.licensing

import android.content.Context
import android.provider.Settings
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.squareup.moshi.Json
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.MessageDigest
import java.security.Signature
import java.security.spec.RSAKeyGenParameterSpec
import java.security.interfaces.RSAPublicKey
import java.util.concurrent.TimeUnit
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * VEIL's customer-app licensing contract.
 *
 * The API origin must be supplied by the release build configuration. Never put
 * payment credentials, admin credentials, or server signing secrets in this app.
 */
interface VeilLicenseApi {
    @POST("api/v1/activation/challenge")
    suspend fun createChallenge(@Body request: ActivationChallengeRequest): ActivationChallengeData

    @POST("api/v1/activation/activate")
    suspend fun activate(@Body request: ActivationRequest): ActivationData

    @POST("api/v1/licenses/status")
    suspend fun status(@Header("Authorization") bearer: String): LicenseStatusData
}

data class ActivationChallengeRequest(
    @Json(name = "publicKey") val publicKey: String,
    @Json(name = "deviceFingerprintHash") val deviceFingerprintHash: String
)

data class ActivationChallengeData(
    @Json(name = "nonce") val nonce: String,
    @Json(name = "expiresInSeconds") val expiresInSeconds: Long
)

data class ActivationRequest(
    @Json(name = "code") val code: String,
    @Json(name = "publicKey") val publicKey: String,
    @Json(name = "nonce") val nonce: String,
    @Json(name = "signature") val signature: String,
    @Json(name = "deviceFingerprintHash") val deviceFingerprintHash: String
)

data class ActivationData(
    @Json(name = "accessToken") val accessToken: String,
    @Json(name = "tokenType") val tokenType: String,
    @Json(name = "expiresInSeconds") val expiresInSeconds: Long,
    @Json(name = "licenseType") val licenseType: String,
    @Json(name = "expiresAt") val expiresAt: String,
    @Json(name = "referralCode") val referralCode: String?
)

data class LicenseStatusData(
    @Json(name = "active") val active: Boolean,
    @Json(name = "accessToken") val accessToken: String,
    @Json(name = "tokenType") val tokenType: String,
    @Json(name = "expiresInSeconds") val expiresInSeconds: Long,
    @Json(name = "licenseType") val licenseType: String,
    @Json(name = "expiresAt") val expiresAt: String,
    @Json(name = "referralCode") val referralCode: String?
)

class VeilDeviceIdentity(private val context: Context) {
    private val alias = "veil_license_device_rsa_v1"
    private val keyStoreType = "AndroidKeyStore"

    @Synchronized
    fun publicKeyPem(): String {
        val keyStore = KeyStore.getInstance(keyStoreType).apply { load(null) }
        if (!keyStore.containsAlias(alias)) {
            val generator = KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_RSA, keyStoreType)
            generator.initialize(
                KeyGenParameterSpec.Builder(
                    alias,
                    KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY
                )
                    .setKeySize(2048)
                    .setDigests(KeyProperties.DIGEST_SHA256)
                    .setSignaturePaddings(KeyProperties.SIGNATURE_PADDING_RSA_PKCS1)
                    .setUserAuthenticationRequired(false)
                    .build()
            )
            generator.generateKeyPair()
        }
        val publicKey = keyStore.getCertificate(alias).publicKey.encoded
        val base64 = Base64.encodeToString(publicKey, Base64.NO_WRAP)
        return "-----BEGIN PUBLIC KEY-----\\n$base64\\n-----END PUBLIC KEY-----"
    }

    fun signNonce(nonce: String): String {
        val keyStore = KeyStore.getInstance(keyStoreType).apply { load(null) }
        val privateKey = keyStore.getKey(alias, null) as? java.security.PrivateKey
            ?: throw IllegalStateException("VEIL device key is missing")
        val signer = Signature.getInstance("SHA256withRSA")
        signer.initSign(privateKey)
        signer.update(nonce.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(signer.sign(), Base64.NO_WRAP)
    }

    fun fingerprintHash(): String {
        val androidId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
            ?: throw IllegalStateException("Android device identifier is unavailable")
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(androidId.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }
}

/** Stores only the short-lived server token, encrypted with an Android Keystore AES key. */
class VeilLicenseTokenStore(context: Context) {
    private val prefs = context.getSharedPreferences("veil_license_secure", Context.MODE_PRIVATE)
    private val alias = "veil_license_token_aes_v1"
    private val keyStoreType = "AndroidKeyStore"

    private fun secretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(keyStoreType).apply { load(null) }
        (keyStore.getKey(alias, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, keyStoreType)
        generator.init(
            KeyGenParameterSpec.Builder(
                alias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build()
        )
        return generator.generateKey()
    }

    fun save(token: String) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val encrypted = cipher.doFinal(token.toByteArray(Charsets.UTF_8))
        prefs.edit()
            .putString("iv", Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
            .putString("token", Base64.encodeToString(encrypted, Base64.NO_WRAP))
            .apply()
    }

    fun read(): String? {
        val iv = prefs.getString("iv", null) ?: return null
        val token = prefs.getString("token", null) ?: return null
        return try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(
                Cipher.DECRYPT_MODE,
                secretKey(),
                GCMParameterSpec(128, Base64.decode(iv, Base64.NO_WRAP))
            )
            String(cipher.doFinal(Base64.decode(token, Base64.NO_WRAP)), Charsets.UTF_8)
        } catch (_: Exception) {
            clear()
            null
        }
    }

    fun clear() {
        prefs.edit().clear().apply()
    }
}

class VeilLicenseRepository(
    private val api: VeilLicenseApi,
    private val identity: VeilDeviceIdentity,
    private val tokenStore: VeilLicenseTokenStore
) {
    suspend fun activate(codeInput: String): ActivationData {
        val code = codeInput.filter(Char::isDigit)
        require(code.length == 12) { "Enter the 12-digit VEIL activation code." }
        val publicKey = identity.publicKeyPem()
        val fingerprint = identity.fingerprintHash()
        val challenge = api.createChallenge(
            ActivationChallengeRequest(publicKey, fingerprint)
        )
        require(challenge.expiresInSeconds > 0) { "Activation challenge expired. Try again." }
        val result = api.activate(
            ActivationRequest(
                code = code,
                publicKey = publicKey,
                nonce = challenge.nonce,
                signature = identity.signNonce(challenge.nonce),
                deviceFingerprintHash = fingerprint
            )
        )
        tokenStore.save(result.accessToken)
        return result
    }

    suspend fun refreshStatus(): LicenseStatusData {
        val token = tokenStore.read() ?: throw IllegalStateException("VEIL activation is required.")
        val result = api.status("Bearer $token")
        if (!result.active) {
            tokenStore.clear()
            throw IllegalStateException("VEIL license is inactive.")
        }
        tokenStore.save(result.accessToken)
        return result
    }

    fun clearActivation() = tokenStore.clear()
}

object VeilLicenseApiFactory {
    fun create(baseUrl: String): VeilLicenseApi {
        require(baseUrl.startsWith("https://")) { "VEIL licensing API must use HTTPS." }
        require(baseUrl.endsWith("/")) { "VEIL API base URL must end with /." }
        val logger = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.NONE // Never log activation codes or bearer tokens.
        }
        val client = OkHttpClient.Builder()
            .addInterceptor(logger)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .build()
        val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(VeilLicenseApi::class.java)
    }
}
