package net.thunderbird.feature.taskmail.internal.navigation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TaskMailRouteCodecTest {

    @Test
    fun `encodeTaskMailSessionId uses sentinel for missing session id`() {
        val result = encodeTaskMailSessionId(sessionId = null)

        assertEquals(MISSING_TASKMAIL_SESSION_ID, result)
    }

    @Test
    fun `decodeTaskMailSessionId restores null for sentinel`() {
        val result = decodeTaskMailSessionId(MISSING_TASKMAIL_SESSION_ID)

        assertNull(result)
    }

    @Test
    fun `decodeTaskMailSessionId keeps real session id`() {
        val result = decodeTaskMailSessionId("session-123")

        assertEquals("session-123", result)
    }
}
