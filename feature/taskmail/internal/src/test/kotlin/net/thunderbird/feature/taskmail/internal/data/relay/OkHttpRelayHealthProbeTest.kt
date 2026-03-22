package net.thunderbird.feature.taskmail.internal.data.relay

import assertk.assertThat
import assertk.assertions.isEqualTo
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlinx.coroutines.test.runTest
import net.thunderbird.feature.taskmail.internal.domain.model.RelayTransportConfig
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer

class OkHttpRelayHealthProbeTest {
    private val server = MockWebServer()

    @AfterTest
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `probe should parse relay health response`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """
                {
                  "status": "ok",
                  "service": "mail-runner-relay",
                  "listen": {"host": "127.0.0.1", "port": 8787},
                  "session_count": 2,
                  "packet_count": 5,
                  "tls_enabled": false,
                  "auth": {"transport_token_id": "abc123def456"}
                }
                """.trimIndent(),
            ),
        )
        server.start()
        val testSubject = OkHttpRelayHealthProbe(logger = RelayFakeLogger())

        val result = testSubject.probe(
            RelayTransportConfig(
                host = server.hostName,
                port = server.port,
                useTls = false,
                transportToken = "secret-token",
            ),
        )

        assertThat(result.isSuccess).isEqualTo(true)
        assertThat(result.getOrThrow().transportTokenId).isEqualTo("abc123def456")
        assertThat(result.getOrThrow().packetCount).isEqualTo(5)
    }
}
