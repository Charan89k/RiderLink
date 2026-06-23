package com.example.riderlink.audio

import android.util.Base64
import java.nio.charset.StandardCharsets
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

object TokenGenerator {

    fun generateToken(
        apiKey: String,
        apiSecret: String,
        roomName: String,
        identity: String,
        name: String = identity,
        expirationSeconds: Long = 86400 // 24 hours
    ): String {
        val headerJson = "{\"alg\":\"HS256\",\"typ\":\"JWT\"}"
        
        val exp = (System.currentTimeMillis() / 1000) + expirationSeconds
        val payloadJson = """
            {
              "exp": $exp,
              "iss": "$apiKey",
              "sub": "$identity",
              "name": "$name",
              "video": {
                "roomJoin": true,
                "room": "$roomName"
              }
            }
        """.trimIndent().replace(Regex("\\s+"), "") // Compact JSON representation
        
        val headerBase64 = encodeUrl(headerJson.toByteArray(StandardCharsets.UTF_8))
        val payloadBase64 = encodeUrl(payloadJson.toByteArray(StandardCharsets.UTF_8))
        
        val signatureInput = "$headerBase64.$payloadBase64"
        val signatureBytes = hmacSha256(signatureInput.toByteArray(StandardCharsets.UTF_8), apiSecret.toByteArray(StandardCharsets.UTF_8))
        val signatureBase64 = encodeUrl(signatureBytes)
        
        return "$signatureInput.$signatureBase64"
    }

    private fun encodeUrl(data: ByteArray): String {
        return Base64.encodeToString(data, Base64.NO_WRAP or Base64.NO_PADDING or Base64.URL_SAFE).trim()
    }

    private fun hmacSha256(data: ByteArray, key: ByteArray): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        val secretKey = SecretKeySpec(key, "HmacSHA256")
        mac.init(secretKey)
        return mac.doFinal(data)
    }
}
