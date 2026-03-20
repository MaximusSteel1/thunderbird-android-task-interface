package net.thunderbird.feature.taskmail.internal.data.relay.protocol

import assertk.assertThat
import assertk.assertions.isEqualTo
import kotlin.test.Test

class RelayProtocolJsonCodecTest {

    private val testSubject = RelayProtocolJsonCodec()

    @Test
    fun `encodeHello should encode canonical hello payload`() {
        val payload = testSubject.encodeHello(
            RelayHello(
                clientId = "android-taskmail",
                clientVersion = "0.1.0-dev",
                transportTokenId = "abc123def456",
                sentAt = "2026-03-20T19:00:00Z",
            ),
        )

        assertThat(payload).isEqualTo(
            "{" +
                "\"message_type\":\"hello\"," +
                "\"client_id\":\"android-taskmail\"," +
                "\"client_version\":\"0.1.0-dev\"," +
                "\"transport_token_id\":\"abc123def456\"," +
                "\"sent_at\":\"2026-03-20T19:00:00Z\"" +
                "}",
        )
    }

    @Test
    fun `decodeServerMessage should decode hello_ack`() {
        val message = testSubject.decodeServerMessage(
            """
            {
              "message_type": "hello_ack",
              "connection_id": "connection-1",
              "server_time": "2026-03-20T19:00:00Z",
              "heartbeat_seconds": 30
            }
            """.trimIndent(),
        )

        assertThat(message).isEqualTo(
            RelayServerMessage.HelloAck(
                RelayHelloAck(
                    messageType = "hello_ack",
                    connectionId = "connection-1",
                    serverTime = "2026-03-20T19:00:00Z",
                    heartbeatSeconds = 30,
                ),
            ),
        )
    }

    @Test
    fun `decodeServerMessage should decode error`() {
        val message = testSubject.decodeServerMessage(
            """
            {
              "message_type": "error",
              "code": "unauthorized",
              "message": "invalid token",
              "sent_at": "2026-03-20T19:00:00Z"
            }
            """.trimIndent(),
        )

        assertThat(message).isEqualTo(
            RelayServerMessage.Error(
                RelayError(
                    messageType = "error",
                    code = "unauthorized",
                    message = "invalid token",
                    sentAt = "2026-03-20T19:00:00Z",
                ),
            ),
        )
    }
}
