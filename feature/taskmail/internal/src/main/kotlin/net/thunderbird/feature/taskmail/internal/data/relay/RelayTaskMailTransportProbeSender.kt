package net.thunderbird.feature.taskmail.internal.data.relay

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withTimeoutOrNull
import net.thunderbird.feature.taskmail.internal.data.debug.TaskMailTransportProbeEventStore
import net.thunderbird.feature.taskmail.internal.data.debug.TaskMailTransportProbeManifest
import net.thunderbird.feature.taskmail.internal.data.debug.TaskMailTransportProbeManifestInput
import net.thunderbird.feature.taskmail.internal.data.debug.TaskMailTransportProbeRecordedEvent
import net.thunderbird.feature.taskmail.internal.data.debug.buildTransportProbeManifest
import net.thunderbird.feature.taskmail.internal.data.debug.currentTransportProbeTimestamp
import net.thunderbird.feature.taskmail.internal.data.relay.protocol.RelayPacket
import net.thunderbird.feature.taskmail.internal.data.relay.protocol.RelayPacketAck
import net.thunderbird.feature.taskmail.internal.data.relay.protocol.RelayResult
import net.thunderbird.feature.taskmail.internal.domain.model.RelayConnectionState
import net.thunderbird.feature.taskmail.internal.domain.model.RelayTransportConfig
import net.thunderbird.feature.taskmail.internal.domain.transportprobe.TaskMailTransportProbeDispatchResult
import net.thunderbird.feature.taskmail.internal.domain.transportprobe.TaskMailTransportProbeDispatchStatus
import net.thunderbird.feature.taskmail.internal.domain.transportprobe.TaskMailTransportProbeRequest
import net.thunderbird.feature.taskmail.internal.domain.transportprobe.TaskMailTransportProbeSender
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

private const val TRANSPORT_PROBE_SCHEMA_VERSION = "taskmail-transport-probe-payload-v1"
private const val DIRECT_ACTION_TRANSPORT_PROBE = "transport_probe"
private const val ORIGIN_CLIENT = "android_taskmail"
private const val PACKET_ID_PREFIX = "android-taskmail:transport-probe:"
private const val REQUEST_ID_PREFIX = "req_"
private const val DISPATCH_CHANNEL = "taskmail_android_probe"
private const val FALLBACK_POLICY_NONE = "none"
private const val DIRECTION_ANDROID_TO_PC = "android_to_pc"
private const val TRANSPORT_KIND_RELAY_DIRECT = "relay_direct"
private const val RESULT_OBSERVE_WINDOW_MILLIS = 5_000L
private const val MAX_PAYLOAD_TEXT_LENGTH = 1024
private const val NANOS_PER_MILLISECOND = 1_000_000L

@Suppress("TooManyFunctions")
internal class RelayTaskMailTransportProbeSender(
    private val relayConnectionClient: RelayConnectionClient,
    private val eventStore: TaskMailTransportProbeEventStore,
    private val requestIdFactory: () -> String = ::nextRequestId,
    private val wallClockProvider: () -> String = ::currentUtcTimestamp,
    private val monotonicTimeProvider: () -> Long = { System.nanoTime() / NANOS_PER_MILLISECOND },
) : TaskMailTransportProbeSender {

    override suspend fun send(
        config: RelayTransportConfig,
        request: TaskMailTransportProbeRequest,
    ): TaskMailTransportProbeDispatchResult = coroutineScope {
        val context = createDispatchContext(request)
        val connectedBeforeSend = relayConnectionClient.connectionState.value is RelayConnectionState.Connected

        recordEvent(
            context = context,
            eventType = "android_probe_dispatch_started",
            summary = "Preparing relay transport probe.",
        )

        connectIfNeeded(config = config, context = context, connectedBeforeSend = connectedBeforeSend)
            ?.let { return@coroutineScope it }

        val matchingResult = async {
            relayConnectionClient.serverResults
                .filter { result -> result.matchesProbe(requestId = context.requestId, packetId = context.packetId) }
                .firstOrNull()
        }

        try {
            sendProbePacket(context = context, matchingResult = matchingResult)
        } finally {
            matchingResult.cancel()
            if (!connectedBeforeSend) {
                relayConnectionClient.disconnect()
            }
        }
    }

    private fun createDispatchContext(
        request: TaskMailTransportProbeRequest,
    ): ProbeDispatchContext {
        val payloadText = request.payloadText.normalizeProbePayloadText()
        val requestId = requestIdFactory()
        val packetId = "$PACKET_ID_PREFIX$requestId"
        val manifest = buildTransportProbeManifest(
            input = TaskMailTransportProbeManifestInput(
                probeId = request.probeId,
                scenario = request.scenario.wireValue,
                direction = DIRECTION_ANDROID_TO_PC,
                transportKind = TRANSPORT_KIND_RELAY_DIRECT,
                payloadText = payloadText,
                createdAt = wallClockProvider(),
                requestId = requestId,
                packetId = packetId,
            ),
        )

        return ProbeDispatchContext(
            request = request,
            requestId = requestId,
            packetId = packetId,
            payloadText = payloadText,
            artifactDirectoryPath = eventStore.saveManifest(manifest),
            manifest = manifest,
        )
    }

    private suspend fun connectIfNeeded(
        config: RelayTransportConfig,
        context: ProbeDispatchContext,
        connectedBeforeSend: Boolean,
    ): TaskMailTransportProbeDispatchResult? {
        if (connectedBeforeSend) return null

        return relayConnectionClient.connect(config).fold(
            onSuccess = { helloAck ->
                recordEvent(
                    context = context,
                    eventType = "android_probe_relay_connected",
                    summary = "Relay connected: ${helloAck.connectionId}",
                )
                null
            },
            onFailure = { error ->
                recordEvent(
                    context = context,
                    eventType = "android_probe_dispatch_failed",
                    summary = "Relay connection failed before probe dispatch.",
                    errorMessage = error.message,
                )
                context.toDispatchResult(
                    status = TaskMailTransportProbeDispatchStatus.Failed,
                    errorMessage = error.message,
                )
            },
        )
    }

    private suspend fun sendProbePacket(
        context: ProbeDispatchContext,
        matchingResult: Deferred<RelayResult?>,
    ): TaskMailTransportProbeDispatchResult {
        return relayConnectionClient.sendPacket(
            packet = buildPacket(
                request = context.request,
                requestId = context.requestId,
                packetId = context.packetId,
                payloadText = context.payloadText,
            ),
        ).fold(
            onSuccess = { packetAck -> handlePacketAck(context, packetAck, matchingResult) },
            onFailure = { error -> handleSendFailure(context, error) },
        )
    }

    private suspend fun handlePacketAck(
        context: ProbeDispatchContext,
        packetAck: RelayPacketAck,
        matchingResult: Deferred<RelayResult?>,
    ): TaskMailTransportProbeDispatchResult {
        recordEvent(
            context = context,
            eventType = if (packetAck.accepted) {
                "android_relay_probe_submitted"
            } else {
                "android_probe_dispatch_failed"
            },
            summary = if (packetAck.accepted) {
                "Relay packet accepted."
            } else {
                "Relay packet rejected."
            },
            receiptId = packetAck.receiptId,
            errorCode = packetAck.errorCode,
            errorMessage = packetAck.errorMessage,
        )

        if (!packetAck.accepted) {
            return context.toDispatchResult(
                status = TaskMailTransportProbeDispatchStatus.Rejected,
                receiptId = packetAck.receiptId,
                errorCode = packetAck.errorCode,
                errorMessage = packetAck.errorMessage,
            )
        }

        val observedResult = withTimeoutOrNull(RESULT_OBSERVE_WINDOW_MILLIS) {
            matchingResult.await()
        }

        return if (observedResult == null) {
            handleAcceptedWithoutResult(context, packetAck.receiptId)
        } else {
            handleObservedResult(context, packetAck.receiptId, observedResult)
        }
    }

    private suspend fun handleAcceptedWithoutResult(
        context: ProbeDispatchContext,
        receiptId: String,
    ): TaskMailTransportProbeDispatchResult {
        persistManifest(context = context, receiptId = receiptId)
        recordEvent(
            context = context,
            eventType = "android_probe_receive_timeout",
            summary = "No relay result observed within the local wait window.",
            receiptId = receiptId,
        )

        return context.toDispatchResult(
            status = TaskMailTransportProbeDispatchStatus.AcceptedAwaitingResult,
            receiptId = receiptId,
        )
    }

    private suspend fun handleObservedResult(
        context: ProbeDispatchContext,
        receiptId: String,
        relayResult: RelayResult,
    ): TaskMailTransportProbeDispatchResult {
        persistManifest(
            context = context,
            receiptId = receiptId,
            resultId = relayResult.resultId,
        )
        recordEvent(
            context = context,
            eventType = "android_probe_result_observed",
            summary = "Relay result observed.",
            receiptId = receiptId,
            resultId = relayResult.resultId,
        )

        return context.toDispatchResult(
            status = relayResult.toDispatchStatus(),
            receiptId = receiptId,
            resultId = relayResult.resultId,
            resultType = relayResult.resultType,
            resultStatus = relayResult.status,
        )
    }

    private suspend fun handleSendFailure(
        context: ProbeDispatchContext,
        error: Throwable,
    ): TaskMailTransportProbeDispatchResult {
        val relayServerException = error as? RelayServerException
        recordEvent(
            context = context,
            eventType = "android_probe_dispatch_failed",
            summary = "Relay probe send failed before acceptance.",
            errorCode = relayServerException?.code,
            errorMessage = error.message,
        )

        return context.toDispatchResult(
            status = if (relayServerException != null) {
                TaskMailTransportProbeDispatchStatus.Rejected
            } else {
                TaskMailTransportProbeDispatchStatus.Failed
            },
            errorCode = relayServerException?.code,
            errorMessage = error.message,
        )
    }

    private fun persistManifest(
        context: ProbeDispatchContext,
        receiptId: String? = null,
        resultId: String? = null,
    ) {
        eventStore.saveManifest(
            context.manifest.copy(
                receiptId = receiptId,
                resultId = resultId,
            ),
        )
    }

    private fun buildPacket(
        request: TaskMailTransportProbeRequest,
        requestId: String,
        packetId: String,
        payloadText: String,
    ): RelayPacket {
        return RelayPacket(
            packetId = packetId,
            clientTraceId = requestId,
            taskRunPacket = buildJsonObject {
                put("schema_version", TRANSPORT_PROBE_SCHEMA_VERSION)
                put("action", DIRECT_ACTION_TRANSPORT_PROBE)
                put("request_id", requestId)
                put(
                    "origin",
                    buildJsonObject {
                        put("client", ORIGIN_CLIENT)
                        put("probe_id", request.probeId)
                    },
                )
                put(
                    DIRECT_ACTION_TRANSPORT_PROBE,
                    buildJsonObject {
                        put("probe_id", request.probeId)
                        put("scenario", request.scenario.wireValue)
                        put("direction", DIRECTION_ANDROID_TO_PC)
                        put("transport_kind", TRANSPORT_KIND_RELAY_DIRECT)
                        put("payload_text", payloadText)
                        put("timeout_seconds", request.timeoutSeconds)
                    },
                )
            },
            dispatchMetadata = buildJsonObject {
                put("channel", DISPATCH_CHANNEL)
                put("schema_version", TRANSPORT_PROBE_SCHEMA_VERSION)
                put("action", DIRECT_ACTION_TRANSPORT_PROBE)
                put("fallback_policy", FALLBACK_POLICY_NONE)
                put("probe_id", request.probeId)
            },
            sentAt = wallClockProvider(),
        )
    }

    private suspend fun recordEvent(
        context: ProbeDispatchContext,
        eventType: String,
        summary: String,
        receiptId: String? = null,
        resultId: String? = null,
        errorCode: String? = null,
        errorMessage: String? = null,
    ) {
        eventStore.appendEvent(
            TaskMailTransportProbeRecordedEvent(
                probeId = context.request.probeId,
                eventType = eventType,
                actor = "android_client",
                recordedAt = currentTransportProbeTimestamp(),
                clockSource = "android_wall_clock",
                monotonicMs = monotonicTimeProvider(),
                summary = summary,
                requestId = context.requestId,
                packetId = context.packetId,
                receiptId = receiptId,
                resultId = resultId,
                errorCode = errorCode,
                errorMessage = errorMessage,
            ),
        )
    }

    private companion object {
        fun currentUtcTimestamp(): String {
            return SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }.format(Date())
        }

        fun nextRequestId(): String {
            return REQUEST_ID_PREFIX + UUID.randomUUID()
                .toString()
                .replace("-", "")
        }
    }
}

private data class ProbeDispatchContext(
    val request: TaskMailTransportProbeRequest,
    val requestId: String,
    val packetId: String,
    val payloadText: String,
    val artifactDirectoryPath: String,
    val manifest: TaskMailTransportProbeManifest,
)

private fun ProbeDispatchContext.toDispatchResult(
    status: TaskMailTransportProbeDispatchStatus,
    receiptId: String? = null,
    resultId: String? = null,
    resultType: String? = null,
    resultStatus: String? = null,
    errorCode: String? = null,
    errorMessage: String? = null,
): TaskMailTransportProbeDispatchResult {
    return TaskMailTransportProbeDispatchResult(
        probeId = request.probeId,
        status = status,
        requestId = requestId,
        packetId = packetId,
        artifactDirectoryPath = artifactDirectoryPath,
        receiptId = receiptId,
        resultId = resultId,
        resultType = resultType,
        resultStatus = resultStatus,
        errorCode = errorCode,
        errorMessage = errorMessage,
    )
}

private fun String.normalizeProbePayloadText(): String {
    val normalized = replace("\r", " ")
        .replace("\n", " ")
        .trim()

    require(normalized.isNotEmpty()) {
        "Transport probe text is required."
    }
    require(normalized.length <= MAX_PAYLOAD_TEXT_LENGTH) {
        "Transport probe text must be ${MAX_PAYLOAD_TEXT_LENGTH} characters or fewer."
    }

    return normalized
}

private fun RelayResult.matchesProbe(
    requestId: String,
    packetId: String,
): Boolean {
    return this.requestId == requestId || this.packetId == packetId
}

private fun RelayResult.toDispatchStatus(): TaskMailTransportProbeDispatchStatus {
    return when (status) {
        "completed" -> TaskMailTransportProbeDispatchStatus.ResultCompleted
        "failed" -> TaskMailTransportProbeDispatchStatus.ResultFailed
        else -> TaskMailTransportProbeDispatchStatus.AcceptedAwaitingResult
    }
}
