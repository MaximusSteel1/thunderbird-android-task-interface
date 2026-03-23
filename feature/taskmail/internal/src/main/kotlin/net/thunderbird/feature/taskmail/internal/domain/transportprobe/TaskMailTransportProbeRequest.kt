package net.thunderbird.feature.taskmail.internal.domain.transportprobe

internal enum class TaskMailTransportProbeScenario(
    val wireValue: String,
) {
    AndroidDirectPingToVpsToPc("android_direct_ping_to_vps_to_pc"),
}

internal data class TaskMailTransportProbeRequest(
    val probeId: String,
    val scenario: TaskMailTransportProbeScenario = TaskMailTransportProbeScenario.AndroidDirectPingToVpsToPc,
    val payloadText: String,
    val timeoutSeconds: Int = DEFAULT_TIMEOUT_SECONDS,
) {
    companion object {
        const val DEFAULT_TIMEOUT_SECONDS = 120
    }
}
