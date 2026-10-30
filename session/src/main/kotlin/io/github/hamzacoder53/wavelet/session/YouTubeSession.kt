package io.github.hamzacoder53.wavelet.session

import org.json.JSONObject

/** A signed-in YouTube Music web session, as Metrolist stores it after its WebView login. */
data class YouTubeSession(
    val cookie: String,
    val visitorData: String,
    val dataSyncId: String,
    val authUser: String,
) {
    fun toBytes(): ByteArray =
        JSONObject()
            .put("cookie", cookie)
            .put("visitorData", visitorData)
            .put("dataSyncId", dataSyncId)
            .put("authUser", authUser)
            .toString()
            .toByteArray()

    /** Never log the cookie; it is the user's YouTube login. */
    override fun toString(): String = "YouTubeSession(authUser=$authUser)"

    companion object {
        fun fromBytes(bytes: ByteArray): YouTubeSession? =
            runCatching {
                val json = JSONObject(String(bytes))
                YouTubeSession(
                    cookie = json.getString("cookie"),
                    visitorData = json.getString("visitorData"),
                    dataSyncId = json.optString("dataSyncId"),
                    authUser = json.optString("authUser", "0"),
                ).takeIf { "SAPISID" in it.cookie && it.visitorData.isNotBlank() }
            }.getOrNull()
    }
}

/** Wear Data Layer protocol between the phone companion and the watch app. */
object SessionTransfer {
    /** Phone → watch: [YouTubeSession.toBytes]. */
    const val PATH_SESSION = "/wavelet/session"

    /** Watch → phone: UTF-8 account name (may be empty) once the session is applied. */
    const val PATH_SESSION_OK = "/wavelet/session/ok"

    /** Watch → phone: UTF-8 error message if the session couldn't be used. */
    const val PATH_SESSION_FAILED = "/wavelet/session/failed"

    /** Declared by the watch app (res/values/wear.xml). */
    const val CAPABILITY_WATCH = "wavelet_watch"

    /** Declared by the phone companion (res/values/wear.xml). */
    const val CAPABILITY_PHONE = "wavelet_phone"

    /** Deep link the watch opens on the phone to start sign-in there. */
    const val SIGN_IN_URI = "wavelet://signin"
}
