package io.github.hamzacoder53.wavelet.session

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class YouTubeSessionTest {
    private val session =
        YouTubeSession(
            cookie = "SID=fake; SAPISID=fake-sapisid; HSID=fake",
            visitorData = "fake-visitor",
            dataSyncId = "fake-sync",
            authUser = "1",
        )

    @Test
    fun roundTripsThroughBytes() {
        assertEquals(session, YouTubeSession.fromBytes(session.toBytes()))
    }

    @Test
    fun rejectsSessionsWithoutLoginCookieOrVisitorData() {
        assertNull(YouTubeSession.fromBytes(session.copy(cookie = "SID=only").toBytes()))
        assertNull(YouTubeSession.fromBytes(session.copy(visitorData = "").toBytes()))
        assertNull(YouTubeSession.fromBytes("not json".toByteArray()))
    }

    @Test
    fun toStringNeverContainsTheCookie() {
        assertFalse("SAPISID" in session.toString())
    }
}
