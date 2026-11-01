package io.github.hamzacoder53.wavelet.session

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PoTokenMessagesTest {
    @Test
    fun requestRoundTrips() {
        val request = PoTokenRequest("1", "dQw4w9WgXcQ", "visitor")
        assertEquals(request, PoTokenRequest.fromBytes(request.toBytes()))
    }

    @Test
    fun replyRoundTrips() {
        val reply = PoTokenReply("1", "player", "streaming")
        assertEquals(reply, PoTokenReply.fromBytes(reply.toBytes()))
    }

    @Test
    fun failedReplyHasNoTokens() {
        val reply = PoTokenReply.fromBytes(PoTokenReply("2", null, null).toBytes())
        assertEquals("2", reply?.id)
        assertNull(reply?.playerToken)
        assertNull(reply?.streamingToken)
    }

    @Test
    fun garbageIsRejected() {
        assertNull(PoTokenRequest.fromBytes("nope".toByteArray()))
        assertNull(PoTokenReply.fromBytes(ByteArray(0)))
    }
}
