package io.github.hamzacoder53.wavelet.account

import io.github.hamzacoder53.wavelet.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException

/** Google refused the saved sign-in (expired or revoked); the user has to sign in again. */
class SignInExpiredException(message: String) : IOException(message)

/**
 * Google's OAuth 2.0 device flow ("TVs and limited-input devices"): the watch shows a short code,
 * the user approves it at google.com/device on any phone or computer.
 */
class DeviceCodeAuth(private val http: OkHttpClient = OkHttpClient()) {
    data class DeviceCode(
        val deviceCode: String,
        val userCode: String,
        val verificationUrl: String,
        val intervalSeconds: Int,
        val expiresInSeconds: Int,
    )

    data class Tokens(val accessToken: String, val refreshToken: String?, val expiresInSeconds: Int)

    suspend fun requestCode(): DeviceCode {
        val json =
            post(
                "https://oauth2.googleapis.com/device/code",
                "client_id" to BuildConfig.OAUTH_CLIENT_ID,
                "scope" to SCOPE,
            )
        return DeviceCode(
            deviceCode = json.getString("device_code"),
            userCode = json.getString("user_code"),
            verificationUrl = json.optString("verification_url", "https://www.google.com/device"),
            intervalSeconds = json.optInt("interval", 5),
            expiresInSeconds = json.optInt("expires_in", 1800),
        )
    }

    /** Polls until the user approves (returns tokens), denies or the code expires (throws). */
    suspend fun awaitApproval(code: DeviceCode): Tokens {
        var interval = code.intervalSeconds
        val deadline = System.currentTimeMillis() + code.expiresInSeconds * 1000L
        while (System.currentTimeMillis() < deadline) {
            delay(interval * 1000L)
            val json =
                postAllowingErrors(
                    "https://oauth2.googleapis.com/token",
                    "client_id" to BuildConfig.OAUTH_CLIENT_ID,
                    "client_secret" to BuildConfig.OAUTH_CLIENT_SECRET,
                    "device_code" to code.deviceCode,
                    "grant_type" to "urn:ietf:params:oauth:grant-type:device_code",
                )
            when (json.optString("error")) {
                "" -> return json.toTokens()
                "authorization_pending" -> Unit
                "slow_down" -> interval += 5
                else -> throw IOException(json.optString("error_description", json.optString("error")))
            }
        }
        throw IOException("Code expired")
    }

    suspend fun refresh(refreshToken: String): Tokens =
        post(
            "https://oauth2.googleapis.com/token",
            "client_id" to BuildConfig.OAUTH_CLIENT_ID,
            "client_secret" to BuildConfig.OAUTH_CLIENT_SECRET,
            "refresh_token" to refreshToken,
            "grant_type" to "refresh_token",
        ).toTokens()

    private fun JSONObject.toTokens() =
        Tokens(
            accessToken = getString("access_token"),
            refreshToken = optString("refresh_token").takeIf { it.isNotEmpty() },
            expiresInSeconds = optInt("expires_in", 3600),
        )

    private suspend fun post(url: String, vararg fields: Pair<String, String>): JSONObject {
        val json = postAllowingErrors(url, *fields)
        if (json.has("error")) {
            val error = json.optString("error")
            if (error == "invalid_grant") throw SignInExpiredException(json.optString("error_description", error))
            throw IOException(json.optString("error_description", error))
        }
        return json
    }

    private suspend fun postAllowingErrors(url: String, vararg fields: Pair<String, String>): JSONObject =
        withContext(Dispatchers.IO) {
            val body = FormBody.Builder().apply { fields.forEach { (k, v) -> add(k, v) } }.build()
            http.newCall(Request.Builder().url(url).post(body).build()).execute().use { response ->
                JSONObject(response.body?.string().orEmpty().ifEmpty { "{}" })
            }
        }

    companion object {
        /** Read-only: liked videos and playlists. */
        const val SCOPE = "https://www.googleapis.com/auth/youtube.readonly"
    }
}
