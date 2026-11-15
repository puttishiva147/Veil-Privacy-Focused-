package com.veil.privacy.licensing
import com.veil.privacy.BuildConfig
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit
object LicenseApi {
 private val client=OkHttpClient.Builder().connectTimeout(12,TimeUnit.SECONDS).readTimeout(15,TimeUnit.SECONDS).writeTimeout(15,TimeUnit.SECONDS).build()
 private val jsonType="application/json; charset=utf-8".toMediaType()
 private fun baseUrl():String{val value=BuildConfig.VEIL_LICENSE_API_BASE_URL.trim().trimEnd('/');require(value.startsWith("https://")&&!value.contains("YOUR-VEIL-WEBSITE-DOMAIN")){"VEIL licensing server is not configured in this build."};return value}
 private fun post(path:String,payload:JSONObject,bearer:String?=null):JSONObject{val builder=Request.Builder().url(baseUrl()+path).post(payload.toString().toRequestBody(jsonType)).header("Accept","application/json");if(!bearer.isNullOrBlank())builder.header("Authorization","Bearer $bearer");client.newCall(builder.build()).execute().use{response->val text=response.body?.string().orEmpty();val body=try{JSONObject(text)}catch(_:Exception){JSONObject()};if(!response.isSuccessful)throw IllegalStateException(body.optString("error").ifBlank{"License server rejected the request (${response.code})."});return body}}
 fun activate(code:String):ActivationResult{val publicKey=LicenseDeviceKey.publicKeyPem();val challenge=post("/activation/challenge",JSONObject().put("publicKey",publicKey));val nonce=challenge.getString("nonce");val signature=LicenseDeviceKey.signNonce(nonce);val response=post("/activation/activate",JSONObject().put("code",code).put("publicKey",publicKey).put("nonce",nonce).put("signature",signature));return ActivationResult(response.getString("accessToken"),response.optLong("expiresInSeconds",900L),code)}
 fun refresh(token:String):ActivationResult{val response=post("/licenses/status",JSONObject(),token);return ActivationResult(response.getString("accessToken"),response.optLong("expiresInSeconds",900L),"")}
 data class ActivationResult(val accessToken:String,val expiresInSeconds:Long,val activationCode:String)
}
