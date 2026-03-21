package net.thunderbird.feature.taskmail.internal.data.relay

import assertk.assertThat
import assertk.assertions.isEqualTo
import kotlin.test.Test
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.test.runTest
import net.thunderbird.feature.taskmail.internal.data.relay.protocol.RelayHelloAck
import net.thunderbird.feature.taskmail.internal.data.relay.protocol.RelayPacket
import net.thunderbird.feature.taskmail.internal.data.relay.protocol.RelayPacketAck
import net.thunderbird.feature.taskmail.internal.data.relay.protocol.RelaySessionUpdate
import net.thunderbird.feature.taskmail.internal.domain.model.RelayBootstrapStatus
import net.thunderbird.feature.taskmail.internal.domain.model.RelayConnectionState
import net.thunderbird.feature.taskmail.internal.domain.model.RelayHealthStatus
import net.thunderbird.feature.taskmail.internal.domain.model.RelayTransportConfig
import net.thunderbird.feature.taskmail.internal.domain.repository.TaskTransportConfigRepository

class DefaultRelayBootstrapManagerTest {

    @Test
    fun `loadConfig should return repository config`() {
        // Arrange
        val fakeConfigRepository = FakeTaskTransportConfigRepository(
            config = relayConfig(host = "relay.example.org"),
        )
        val testSubject = DefaultRelayBootstrapManager(
            transportConfigRepository = fakeConfigRepository,
            relayHealthProbe = FakeRelayHealthProbe(),
            relayConnectionClient = FakeRelayConnectionClient(),
        )

        // Act
        val result = testSubject.loadConfig()

        // Assert
        assertThat(result).isEqualTo(relayConfig(host = "relay.example.org"))
    }

    @Test
    fun `saveConfig should delegate to repository`() {
        // Arrange
        val fakeConfigRepository = FakeTaskTransportConfigRepository()
        val testSubject = DefaultRelayBootstrapManager(
            transportConfigRepository = fakeConfigRepository,
            relayHealthProbe = FakeRelayHealthProbe(),
            relayConnectionClient = FakeRelayConnectionClient(),
        )
        val config = relayConfig(host = "124.223.41.153")

        // Act
        val result = testSubject.saveConfig(config)

        // Assert
        assertThat(result).isEqualTo(true)
        assertThat(fakeConfigRepository.savedConfig).isEqualTo(config)
    }

    @Test
    fun `probeSavedConfig should use repository config`() = runTest {
        // Arrange
        val fakeConfigRepository = FakeTaskTransportConfigRepository(
            config = relayConfig(host = "124.223.41.153"),
        )
        val fakeRelayHealthProbe = FakeRelayHealthProbe(
            result = Result.success(relayHealthStatus(status = "ok")),
        )
        val testSubject = DefaultRelayBootstrapManager(
            transportConfigRepository = fakeConfigRepository,
            relayHealthProbe = fakeRelayHealthProbe,
            relayConnectionClient = FakeRelayConnectionClient(),
        )

        // Act
        val result = testSubject.probeSavedConfig()

        // Assert
        assertThat(result.isSuccess).isEqualTo(true)
        assertThat(fakeRelayHealthProbe.lastConfig).isEqualTo(relayConfig(host = "124.223.41.153"))
    }

    @Test
    fun `connectSavedConfig should use repository config and expose client state`() = runTest {
        // Arrange
        val fakeConnectionClient = FakeRelayConnectionClient(
            connectResult = Result.success(
                RelayHelloAck(
                    messageType = "hello_ack",
                    connectionId = "connection-1",
                    serverTime = "2026-03-21T11:00:00Z",
                    heartbeatSeconds = 30,
                ),
            ),
        )
        val fakeConfigRepository = FakeTaskTransportConfigRepository(
            config = relayConfig(host = "124.223.41.153"),
        )
        val testSubject = DefaultRelayBootstrapManager(
            transportConfigRepository = fakeConfigRepository,
            relayHealthProbe = FakeRelayHealthProbe(),
            relayConnectionClient = fakeConnectionClient,
        )

        // Act
        val result = testSubject.connectSavedConfig()

        // Assert
        assertThat(result.isSuccess).isEqualTo(true)
        assertThat(fakeConnectionClient.lastConnectConfig).isEqualTo(relayConfig(host = "124.223.41.153"))
        assertThat(testSubject.connectionState.value).isEqualTo(
            RelayConnectionState.Connected(
                connectionId = "connection-1",
                serverTime = "2026-03-21T11:00:00Z",
                heartbeatSeconds = 30,
            ),
        )
    }

    @Test
    fun `disconnect should delegate to connection client`() = runTest {
        // Arrange
        val fakeConnectionClient = FakeRelayConnectionClient()
        val testSubject = DefaultRelayBootstrapManager(
            transportConfigRepository = FakeTaskTransportConfigRepository(),
            relayHealthProbe = FakeRelayHealthProbe(),
            relayConnectionClient = fakeConnectionClient,
        )

        // Act
        testSubject.disconnect()

        // Assert
        assertThat(fakeConnectionClient.disconnectCallCount).isEqualTo(1)
    }

    @Test
    fun `bootstrap should return not configured when config is incomplete`() = runTest {
        // Arrange
        val testSubject = DefaultRelayBootstrapManager(
            transportConfigRepository = FakeTaskTransportConfigRepository(),
            relayHealthProbe = FakeRelayHealthProbe(),
            relayConnectionClient = FakeRelayConnectionClient(),
        )

        // Act
        val result = testSubject.bootstrap(
            relayConfig(
                host = "",
            ),
        )

        // Assert
        assertThat(result.status).isEqualTo(RelayBootstrapStatus.NotConfigured)
        assertThat(result.routesToMailFallback).isEqualTo(true)
    }

    @Test
    fun `bootstrap should classify health http failure`() = runTest {
        // Arrange
        val testSubject = DefaultRelayBootstrapManager(
            transportConfigRepository = FakeTaskTransportConfigRepository(),
            relayHealthProbe = FakeRelayHealthProbe(
                result = Result.failure(IllegalStateException("Relay health probe failed with HTTP 503")),
            ),
            relayConnectionClient = FakeRelayConnectionClient(),
        )

        // Act
        val result = testSubject.bootstrap(relayConfig())

        // Assert
        assertThat(result.status).isEqualTo(RelayBootstrapStatus.InvalidHttpResponse)
        assertThat(result.routesToMailFallback).isEqualTo(true)
    }

    @Test
    fun `bootstrap should classify token mismatch relay response after health success`() = runTest {
        // Arrange
        val healthStatus = relayHealthStatus()
        val testSubject = DefaultRelayBootstrapManager(
            transportConfigRepository = FakeTaskTransportConfigRepository(),
            relayHealthProbe = FakeRelayHealthProbe(
                result = Result.success(healthStatus),
            ),
            relayConnectionClient = FakeRelayConnectionClient(
                connectResult = Result.failure(IllegalStateException("unauthorized: transport token mismatch")),
            ),
        )

        // Act
        val result = testSubject.bootstrap(relayConfig())

        // Assert
        assertThat(result.status).isEqualTo(RelayBootstrapStatus.TokenIdMismatch)
        assertThat(result.healthStatus).isEqualTo(healthStatus)
        assertThat(result.routesToMailFallback).isEqualTo(true)
    }

    @Test
    fun `bootstrap should classify unauthorized relay response after health success`() = runTest {
        // Arrange
        val healthStatus = relayHealthStatus()
        val testSubject = DefaultRelayBootstrapManager(
            transportConfigRepository = FakeTaskTransportConfigRepository(),
            relayHealthProbe = FakeRelayHealthProbe(
                result = Result.success(healthStatus),
            ),
            relayConnectionClient = FakeRelayConnectionClient(
                connectResult = Result.failure(IllegalStateException("unauthorized")),
            ),
        )

        // Act
        val result = testSubject.bootstrap(relayConfig())

        // Assert
        assertThat(result.status).isEqualTo(RelayBootstrapStatus.Unauthorized)
        assertThat(result.healthStatus).isEqualTo(healthStatus)
        assertThat(result.routesToMailFallback).isEqualTo(true)
    }

    @Test
    fun `bootstrap should return hello ack when health and connect succeed`() = runTest {
        // Arrange
        val healthStatus = relayHealthStatus()
        val helloAck = RelayHelloAck(
            messageType = "hello_ack",
            connectionId = "connection-1",
            serverTime = "2026-03-21T11:00:00Z",
            heartbeatSeconds = 30,
        )
        val testSubject = DefaultRelayBootstrapManager(
            transportConfigRepository = FakeTaskTransportConfigRepository(),
            relayHealthProbe = FakeRelayHealthProbe(
                result = Result.success(healthStatus),
            ),
            relayConnectionClient = FakeRelayConnectionClient(
                connectResult = Result.success(helloAck),
            ),
        )

        // Act
        val result = testSubject.bootstrap(relayConfig())

        // Assert
        assertThat(result.status).isEqualTo(RelayBootstrapStatus.HelloAck)
        assertThat(result.healthStatus).isEqualTo(healthStatus)
        assertThat(result.helloAck).isEqualTo(helloAck)
        assertThat(result.routesToMailFallback).isEqualTo(false)
    }
}

private class FakeTaskTransportConfigRepository(
    private val config: RelayTransportConfig = relayConfig(),
    private val saveResult: Boolean = true,
) : TaskTransportConfigRepository {
    var savedConfig: RelayTransportConfig? = null

    override fun getRelayTransportConfig(): RelayTransportConfig = config

    override fun saveRelayTransportConfig(config: RelayTransportConfig): Boolean {
        savedConfig = config
        return saveResult
    }
}

private class FakeRelayHealthProbe(
    private val result: Result<RelayHealthStatus> = Result.success(relayHealthStatus()),
) : RelayHealthProbe {
    var lastConfig: RelayTransportConfig? = null

    override suspend fun probe(config: RelayTransportConfig): Result<RelayHealthStatus> {
        lastConfig = config
        return result
    }
}

private class FakeRelayConnectionClient(
    private val connectResult: Result<RelayHelloAck> = Result.success(
        RelayHelloAck(
            messageType = "hello_ack",
            connectionId = "connection-1",
            serverTime = "2026-03-21T11:00:00Z",
            heartbeatSeconds = 30,
        ),
    ),
) : RelayConnectionClient {
    private val mutableConnectionState = MutableStateFlow<RelayConnectionState>(RelayConnectionState.Idle)
    private val mutableSessionUpdates = MutableSharedFlow<RelaySessionUpdate>(extraBufferCapacity = 1)

    var lastConnectConfig: RelayTransportConfig? = null
    var disconnectCallCount: Int = 0

    override val connectionState = mutableConnectionState
    override val sessionUpdates: SharedFlow<RelaySessionUpdate> = mutableSessionUpdates

    override suspend fun connect(config: RelayTransportConfig): Result<RelayHelloAck> {
        lastConnectConfig = config
        connectResult.getOrNull()?.let { helloAck ->
            mutableConnectionState.value = RelayConnectionState.Connected(
                connectionId = helloAck.connectionId,
                serverTime = helloAck.serverTime,
                heartbeatSeconds = helloAck.heartbeatSeconds,
            )
        }
        connectResult.exceptionOrNull()?.let { error ->
            mutableConnectionState.value = RelayConnectionState.Failed(
                error.message ?: "Relay connection failed.",
            )
        }
        return connectResult
    }

    override suspend fun sendPacket(packet: RelayPacket): Result<RelayPacketAck> {
        return Result.failure(IllegalStateException("sendPacket is not used in bootstrap tests"))
    }

    override suspend fun disconnect() {
        disconnectCallCount += 1
        mutableConnectionState.value = RelayConnectionState.Idle
    }
}

private fun relayConfig(
    host: String = "relay.example.org",
    port: Int = 8787,
): RelayTransportConfig {
    return RelayTransportConfig(
        enabled = true,
        host = host,
        port = port,
        useTls = false,
        transportToken = "secret-token",
    )
}

private fun relayHealthStatus(
    status: String = "ok",
): RelayHealthStatus {
    return RelayHealthStatus(
        status = status,
        service = "mail-runner-relay",
        listenHost = "0.0.0.0",
        listenPort = 8787,
        sessionCount = 1,
        packetCount = 0,
        tlsEnabled = false,
        transportTokenId = "abc123def456",
    )
}
