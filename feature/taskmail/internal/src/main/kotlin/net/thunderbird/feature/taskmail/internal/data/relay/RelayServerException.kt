package net.thunderbird.feature.taskmail.internal.data.relay

internal class RelayServerException(
    val code: String,
    override val message: String,
) : IllegalStateException("$code: $message")
