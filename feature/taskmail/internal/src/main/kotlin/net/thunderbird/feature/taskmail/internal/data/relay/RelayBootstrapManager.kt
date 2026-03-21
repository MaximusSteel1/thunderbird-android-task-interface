package net.thunderbird.feature.taskmail.internal.data.relay

import kotlinx.coroutines.flow.StateFlow
import net.thunderbird.feature.taskmail.internal.data.relay.protocol.RelayHelloAck
import net.thunderbird.feature.taskmail.internal.domain.model.RelayBootstrapResult
import net.thunderbird.feature.taskmail.internal.domain.model.RelayBootstrapStatus
import net.thunderbird.feature.taskmail.internal.domain.model.RelayConnectionState
import net.thunderbird.feature.taskmail.internal.domain.model.RelayHealthStatus
import net.thunderbird.feature.taskmail.internal.domain.model.RelayTransportConfig
import net.thunderbird.feature.taskmail.internal.domain.repository.TaskTransportConfigRepository

internal interface RelayBootstrapManager {
    val connectionState: StateFlow<RelayConnectionState>

    fun loadConfig(): RelayTransportConfig

    fun saveConfig(config: RelayTransportConfig): Boolean

    suspend fun probeHealth(config: RelayTransportConfig): Result<RelayHealthStatus>

    suspend fun connect(config: RelayTransportConfig): Result<RelayHelloAck>

    suspend fun disconnect()

    suspend fun probeSavedConfig(): Result<RelayHealthStatus> = probeHealth(loadConfig())

    suspend fun connectSavedConfig(): Result<RelayHelloAck> = connect(loadConfig())

    suspend fun bootstrap(config: RelayTransportConfig): RelayBootstrapResult

    suspend fun bootstrapSavedConfig(): RelayBootstrapResult = bootstrap(loadConfig())
}

internal class DefaultRelayBootstrapManager(
    private val transportConfigRepository: TaskTransportConfigRepository,
    private val relayHealthProbe: RelayHealthProbe,
    private val relayConnectionClient: RelayConnectionClient,
) : RelayBootstrapManager {
    override val connectionState: StateFlow<RelayConnectionState> = relayConnectionClient.connectionState

    override fun loadConfig(): RelayTransportConfig {
        return transportConfigRepository.getRelayTransportConfig()
    }

    override fun saveConfig(config: RelayTransportConfig): Boolean {
        return transportConfigRepository.saveRelayTransportConfig(config)
    }

    override suspend fun probeHealth(config: RelayTransportConfig): Result<RelayHealthStatus> {
        return relayHealthProbe.probe(config)
    }

    override suspend fun connect(config: RelayTransportConfig): Result<RelayHelloAck> {
        return relayConnectionClient.connect(config)
    }

    override suspend fun disconnect() {
        relayConnectionClient.disconnect()
    }

    override suspend fun bootstrap(config: RelayTransportConfig): RelayBootstrapResult {
        val normalizedConfig = config.normalized()
        return if (!normalizedConfig.isConfigured()) {
            RelayBootstrapResult(
                status = RelayBootstrapStatus.NotConfigured,
                detailMessage = "Relay host, port, and transport token are required.",
            )
        } else {
            relayHealthProbe.probe(normalizedConfig).fold(
                onSuccess = { healthStatus ->
                    relayConnectionClient.connect(normalizedConfig).fold(
                        onSuccess = { helloAck ->
                            RelayBootstrapResult(
                                status = RelayBootstrapStatus.HelloAck,
                                healthStatus = healthStatus,
                                helloAck = helloAck,
                            )
                        },
                        onFailure = { error ->
                            RelayBootstrapResult(
                                status = error.classifyRelayConnectionFailure(normalizedConfig),
                                healthStatus = healthStatus,
                                detailMessage = error.message,
                            )
                        },
                    )
                },
                onFailure = { error ->
                    RelayBootstrapResult(
                        status = error.classifyRelayHealthFailure(normalizedConfig),
                        detailMessage = error.message,
                    )
                },
            )
        }
    }
}
