package net.thunderbird.feature.taskmail.internal.domain.model

internal data class RelayHealthStatus(
    val status: String,
    val service: String?,
    val listenHost: String?,
    val listenPort: Int?,
    val sessionCount: Int?,
    val packetCount: Int?,
    val tlsEnabled: Boolean?,
    val transportTokenId: String?,
)
