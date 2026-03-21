package net.thunderbird.feature.taskmail.internal.domain.model

import net.thunderbird.feature.taskmail.internal.data.relay.protocol.RelayHelloAck

internal data class RelayBootstrapResult(
    val status: RelayBootstrapStatus,
    val healthStatus: RelayHealthStatus? = null,
    val helloAck: RelayHelloAck? = null,
    val detailMessage: String? = null,
) {
    val isSuccess: Boolean
        get() = status == RelayBootstrapStatus.HelloAck

    val routesToMailFallback: Boolean
        get() = !isSuccess
}
