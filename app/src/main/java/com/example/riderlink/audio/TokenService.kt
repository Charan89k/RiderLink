package com.example.riderlink.audio

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** A LiveKit access token plus the server it is valid for. */
data class IntercomCredentials(
    val token: String,
    val serverUrl: String
)

/**
 * Fetches LiveKit access tokens from the RiderLink token server.
 *
 * The app deliberately cannot mint its own tokens: signing requires the LiveKit
 * API secret, and anything shipped inside the APK is readable by anyone holding
 * the APK. Tokens come back scoped to a single room and expire within the hour.
 */
object TokenService {

    private const val CONNECT_TIMEOUT_MS = 10_000
    private const val READ_TIMEOUT_MS = 15_000

    suspend fun fetchCredentials(
        tokenServerUrl: String,
        roomCode: String,
        identity: String
    ): IntercomCredentials = withContext(Dispatchers.IO) {
        val endpoint = URL("${tokenServerUrl.trimEnd('/')}/token")
        val requestBody = JSONObject()
            .put("room", roomCode)
            .put("identity", identity)
            .put("name", identity)
            .toString()

        val connection = (endpoint.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Accept", "application/json")
        }

        try {
            connection.outputStream.use { it.write(requestBody.toByteArray(Charsets.UTF_8)) }

            val status = connection.responseCode
            if (status !in 200..299) {
                val detail = connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
                throw IOException("Token server returned $status${detail.describeFailure()}")
            }

            val payload = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
            val token = payload.optString("token")
            val serverUrl = payload.optString("url")
            if (token.isEmpty() || serverUrl.isEmpty()) {
                throw IOException("Token server response was missing 'token' or 'url'")
            }

            IntercomCredentials(token = token, serverUrl = serverUrl)
        } finally {
            connection.disconnect()
        }
    }

    /** Surfaces the server's `error` field, if it sent one, without dumping raw HTML. */
    private fun String.describeFailure(): String {
        if (isBlank()) return ""
        val error = runCatching { JSONObject(this).optString("error") }.getOrNull()
        return if (error.isNullOrEmpty()) "" else " ($error)"
    }
}
