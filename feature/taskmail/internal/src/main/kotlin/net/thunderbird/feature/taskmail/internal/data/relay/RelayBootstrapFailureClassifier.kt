package net.thunderbird.feature.taskmail.internal.data.relay

import net.thunderbird.feature.taskmail.internal.domain.model.RelayBootstrapStatus
import net.thunderbird.feature.taskmail.internal.domain.model.RelayTransportConfig

internal fun Throwable.classifyRelayHealthFailure(config: RelayTransportConfig): RelayBootstrapStatus {
    val message = message.orEmpty()
    return classifyRelayFailure(
        defaultStatus = RelayBootstrapStatus.ConnectFailure,
        rules = listOf(
            RelayBootstrapStatus.InvalidHttpResponse to { message.containsAnyPattern(httpFailurePatterns) },
            RelayBootstrapStatus.Timeout to { message.containsAnyPattern(timeoutPatterns) },
            RelayBootstrapStatus.SchemeMismatch to { message.containsAnyPattern(schemeMismatchPatterns) },
            RelayBootstrapStatus.TlsFailure to { config.useTls && message.containsAnyPattern(tlsFailurePatterns) },
        ),
    )
}

internal fun Throwable.classifyRelayConnectionFailure(config: RelayTransportConfig): RelayBootstrapStatus {
    val message = message.orEmpty()
    return classifyRelayFailure(
        defaultStatus = RelayBootstrapStatus.ConnectFailure,
        rules = listOf(
            RelayBootstrapStatus.NotConfigured to { message.containsAnyPattern(configurationPatterns) },
            RelayBootstrapStatus.TokenIdMismatch to { message.containsAnyPattern(tokenMismatchPatterns) },
            RelayBootstrapStatus.Unauthorized to { message.containsAnyPattern(unauthorizedPatterns) },
            RelayBootstrapStatus.UnexpectedResponse to { message.containsAnyPattern(unexpectedResponsePatterns) },
            RelayBootstrapStatus.InvalidJson to { message.containsAnyPattern(invalidJsonPatterns) },
            RelayBootstrapStatus.Timeout to { message.containsAnyPattern(timeoutPatterns) },
            RelayBootstrapStatus.SchemeMismatch to { message.containsAnyPattern(schemeMismatchPatterns) },
            RelayBootstrapStatus.TlsFailure to { config.useTls && message.containsAnyPattern(tlsFailurePatterns) },
            RelayBootstrapStatus.InvalidHandshake to { message.containsAnyPattern(invalidHandshakePatterns) },
        ),
    )
}

private fun classifyRelayFailure(
    defaultStatus: RelayBootstrapStatus,
    rules: List<Pair<RelayBootstrapStatus, () -> Boolean>>,
): RelayBootstrapStatus {
    return rules.firstOrNull { (_, matches) -> matches() }?.first ?: defaultStatus
}

private fun String.containsAnyPattern(patterns: List<String>): Boolean {
    return patterns.any { pattern -> contains(pattern, ignoreCase = true) }
}

private val httpFailurePatterns = listOf("HTTP")
private val timeoutPatterns = listOf("timeout")
private val schemeMismatchPatterns = listOf(
    "unexpected end of stream",
    "wrong version number",
    "not an SSL/TLS record",
)
private val tlsFailurePatterns = listOf(
    "certificate",
    "trust anchor",
    "handshake",
    "hostname",
)
private val configurationPatterns = listOf("required")
private val tokenMismatchPatterns = listOf(
    "token_id_mismatch",
    "transport token mismatch",
)
private val unauthorizedPatterns = listOf("unauthorized")
private val unexpectedResponsePatterns = listOf("unexpected_response")
private val invalidJsonPatterns = listOf(
    "invalid json",
    "failed to parse relay server message",
)
private val invalidHandshakePatterns = listOf(
    "expected http 101",
    "invalid handshake",
)
