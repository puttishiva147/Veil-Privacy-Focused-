package com.veil.privacy.licensing
import android.content.Context
import android.provider.Settings
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.MessageDigest
import java.security.Signature
/** Device identity uses a non-exportable RSA key plus a hashed app-scoped Android ID for trial abuse prevention. */
object LicenseDeviceKey {
 private const val KEY_ALIAS="com.veil.privacy.license.device.v1"
 private fun keyStore():KeyStore=KeyStore.getInstance("AndroidKeyStore").apply{load(null)}
 private fun ensureKey(){val store=keyStore();if(store.containsAlias(KEY_ALIAS))return
  val generator=KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_RSA,"AndroidKeyStore")
  generator.initialize(KeyGenParameterSpec.Builder(KEY_ALIAS,KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY).setKeySize(2048).setDigests(KeyProperties.DIGEST_SHA256).setSignaturePaddings(KeyProperties.SIGNATURE_PADDING_RSA_PKCS1).setUserAuthenticationRequired(false).build())
  generator.generateKeyPair()
 }
 fun publicKeyPem():String{ensureKey();val bytes=keyStore().getCertificate(KEY_ALIAS).publicKey.encoded;val encoded=Base64.encodeToString(bytes,Base64.NO_WRAP);return "-----BEGIN PUBLIC KEY-----\n$encoded\n-----END PUBLIC KEY-----"}
 fun signNonce(nonce:String):String{ensureKey();val privateKey=keyStore().getKey(KEY_ALIAS,null) as java.security.PrivateKey;val signer=Signature.getInstance("SHA256withRSA");signer.initSign(privateKey);signer.update(nonce.toByteArray(Charsets.UTF_8));return Base64.encodeToString(signer.sign(),Base64.NO_WRAP)}
 fun fingerprintHash(context:Context):String{
  val androidId=Settings.Secure.getString(context.contentResolver,Settings.Secure.ANDROID_ID)?.takeIf{it.isNotBlank()}?:throw IllegalStateException("Android device fingerprint is unavailable.")
  return fingerprintHashFromAndroidId(androidId)
 }
 fun fingerprintHashFromAndroidId(androidId:String):String {
  require(androidId.isNotBlank()){"Android device fingerprint is unavailable."}
  val digest=MessageDigest.getInstance("SHA-256").digest(androidId.toByteArray(Charsets.UTF_8))
  return digest.joinToString(""){byte->"%02x".format(byte.toInt() and 0xff)}
 }
}
