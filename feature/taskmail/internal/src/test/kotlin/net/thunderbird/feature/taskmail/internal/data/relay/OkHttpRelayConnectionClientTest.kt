package net.thunderbird.feature.taskmail.internal.data.relay

import assertk.assertThat
import assertk.assertions.isEqualTo
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlinx.coroutines.test.runTest
import net.thunderbird.core.logging.LogTag
import net.thunderbird.core.logging.Logger
import net.thunderbird.feature.taskmail.internal.domain.model.RelayConnectionState
import net.thunderbird.feature.taskmail.internal.domain.model.RelayTransportConfig
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer

class OkHttpRelayConnectionClientTest {
    private val server = MockWebServer()

    @AfterTest
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `connect should send hello with bearer auth and update connected state`() = runTest {
        var capturedHello: String? = null
        val config = RelayTransportConfig(
            host = server.hostName,
            port = server.port,
            useTls = false,
            transportToken = "secret-token",
        )
        server.enqueue(
            MockResponse().withWebSocketUpgrade(
                object : WebSocketListener() {
                    override fun onMessage(webSocket: WebSocket, text: String) {
                        capturedHello = text
                        webSocket.send(
                            """
                            {
                              "message_type": "hello_ack",
                              "connection_id": "connection-1",
                              "server_time": "2026-03-20T19:00:00Z",
                              "heartbeat_seconds": 30
                            }
                            """.trimIndent(),
                        )
                    }
                },
            ),
        )
        server.start()
        val testSubject = OkHttpRelayConnectionClient(
            codec = net.thunderbird.feature.taskmail.internal.data.relay.protocol.RelayProtocolJsonCodec(),
            logger = RelayFakeLogger(),
        )

        val result = testSubject.connect(config)

        val request = server.takeRequest()
        assertThat(request.getHeader("Authorization")).isEqualTo("Bearer secret-token")
        assertThat(result.isSuccess).isEqualTo(true)
        assertThat((testSubject.connectionState.value as RelayConnectionState.Connected).connectionId)
            .isEqualTo("connection-1")
        assertThat(capturedHello?.contains("\"message_type\":\"hello\"")).isEqualTo(true)
        assertThat(
            capturedHello?.contains("\"transport_token_id\":\"${config.tokenFingerprint()}\""),
        ).isEqualTo(true)
    }
}

internal class RelayFakeLogger : Logger {
    override fun verbose(tag: LogTag?, throwable: Throwable?, message: () -> String) = Unit
    override fun debug(tag: LogTag?, throwable: Throwable?, message: () -> String) = Unit
    override fun info(tag: LogTag?, throwable: Throwable?, message: () -> String) = Unit
    override fun warn(tag: LogTag?, throwable: Throwable?, message: () -> String) = Unit
    override fun error(tag: LogTag?, throwable: Throwable?, message: () -> String) = Unit
}
