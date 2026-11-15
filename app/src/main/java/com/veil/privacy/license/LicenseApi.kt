package com.veil.privacy.license

import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

data class LicenseResult(val success: Boolean, val message: String, val token: String? = null)

class LicenseApi {
    private fun post(path: String, body: JSONObject, bearer: String? = null): JSONObject {
        check(LicenseConfig.isConfigured()) { "VEIL licensing server URL is not configured in this build." }
        val connection = (URL(LicenseConfig.baseUrl + path).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 12_000
            readTimeout = 12_000
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Accept", "application/json")
            if (bearer != null) setRequestProperty("Authorization", "Bearer $bearer")
        }
        try {
            OutputStreamWriter(connection.outputStream, Charsets.UTF_8).use { it.write(body.toString()) }
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val response = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            val json = if (response.isBlank()) JSONObject() else JSONObject(response)
            if (status !in 200..299) throw IllegalStateException(json.optString("error", "Licensing request failed ($status)."))
            return json
        } finally {
            connection.disconnect()
        }
    }

    fun activate(code: String, identity: DeviceIdentity): LicenseResult {
        return try {
            val publicKey = identity.publicKeyPem()
            val challenge = post("/api/v1/activation/challenge", JSONObject().put("publicKey", publicKey))
            val nonce = challenge.getString("nonce")
            val signature = identity.signNonce(nonce)
            val result = post("/api/v1/activation/activate", JSONObject()
                .put("code", code)
                .put("publicKey", publicKey)
                .put("nonce", nonce)
                .put("signature", signature))
            LicenseResult(true, "VEIL activation successful.", result.getString("accessToken"))
        } catch (error: Exception) {
            LicenseResult(false, error.localizedMessage ?: "Activation failed.")
        }
    }

    fun verify(token: String): LicenseResult {
        return try {
            val result = post("/api/v1/licenses/status", JSONObject(), token)
            LicenseResult(true, "License active until " + result.optString("expiresAt"), result.getString("accessToken"))
        } catch (error: Exception) {
            LicenseResult(false, error.localizedMessage ?: "License verification failed.")
        }
    }
}
