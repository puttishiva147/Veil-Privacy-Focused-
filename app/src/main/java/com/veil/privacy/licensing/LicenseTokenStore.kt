package com.veil.privacy.licensing
import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
/** Encrypts bearer token and activation code with an Android Keystore AES key. */
object LicenseTokenStore {
 private const val PREFS="veil_license_secure_store";private const val KEY_ALIAS="com.veil.privacy.license.storage.v1";private const val TOKEN="encrypted_token";private const val CODE="encrypted_activation_code";private const val EXPIRY="token_expiry"
 private fun secretKey():SecretKey{val store=KeyStore.getInstance("AndroidKeyStore").apply{load(null)};val existing=store.getKey(KEY_ALIAS,null) as? SecretKey;if(existing!=null)return existing
  val generator=KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore");generator.init(KeyGenParameterSpec.Builder(KEY_ALIAS,KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT).setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).setKeySize(256).setRandomizedEncryptionRequired(true).build());return generator.generateKey()
 }
 private fun encrypt(value:String):String{val cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.ENCRYPT_MODE,secretKey());val encrypted=cipher.doFinal(value.toByteArray(Charsets.UTF_8));return Base64.encodeToString(cipher.iv,Base64.NO_WRAP)+"."+Base64.encodeToString(encrypted,Base64.NO_WRAP)}
 private fun decrypt(value:String):String? {
  return try {
   val parts=value.split(".")
   if(parts.size!=2) null else {
    val iv=Base64.decode(parts[0],Base64.NO_WRAP);val encrypted=Base64.decode(parts[1],Base64.NO_WRAP)
    val cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.DECRYPT_MODE,secretKey(),GCMParameterSpec(128,iv))
    String(cipher.doFinal(encrypted),Charsets.UTF_8)
   }
  } catch(_:Exception) { null }
 }
 fun save(context:Context,token:String,activationCode:String,expiresInSeconds:Long){context.getSharedPreferences(PREFS,Context.MODE_PRIVATE).edit().putString(TOKEN,encrypt(token)).putString(CODE,encrypt(activationCode)).putLong(EXPIRY,System.currentTimeMillis()+expiresInSeconds*1000L).apply()}
 fun token(context:Context):String?{val prefs=context.getSharedPreferences(PREFS,Context.MODE_PRIVATE);if(prefs.getLong(EXPIRY,0L)<=System.currentTimeMillis())return null;return prefs.getString(TOKEN,null)?.let(::decrypt)}
 fun activationCode(context:Context):String?=context.getSharedPreferences(PREFS,Context.MODE_PRIVATE).getString(CODE,null)?.let(::decrypt)
 fun hasLocallyValidSession(context:Context):Boolean=token(context)!=null
 fun clear(context:Context){context.getSharedPreferences(PREFS,Context.MODE_PRIVATE).edit().clear().apply()}
}
