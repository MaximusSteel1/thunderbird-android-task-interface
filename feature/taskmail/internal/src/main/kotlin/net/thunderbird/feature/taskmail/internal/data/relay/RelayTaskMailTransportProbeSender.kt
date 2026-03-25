package net.thunderbird.feature.taskmail.internal.data.relay

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import net.thunderbird.feature.taskmail.internal.data.debug.TaskMailTransportProbeEventStore
import net.thunderbird.feature.taskmail.internal.data.debug.TaskMailTransportProbeManifest
import net.thunderbird.feature.taskmail.internal.data.debug.TaskMailTransportProbeManifestInput
import net.thunderbird.feature.taskmail.internal.data.debug.TaskMailTransportProbeRecordedEvent
import net.thunderbird.feature.taskmail.internal.data.debug.buildTransportProbeManifest
import net.thunderbird.feature.taskmail.internal.data.debug.currentTransportProbeTimestamp
import net.thunderbird.feature.taskmail.internal.data.relay.protocol.RelayCommand
import net.thunderbird.feature.taskmail.internal.data.relay.protocol.RelayCommandAck
import net.thunderbird.feature.taskmail.internal.data.relay.protocol.RelayResult
import net.thunderbird.feature.taskmail.internal.domain.model.RelayTransportConfig
import net.thunderbird.feature.taskmail.internal.domain.transportprobe.TaskMailTransportProbeDispatchResult
import net.thunderbird.feature.taskmail.internal.domain.transportprobe.TaskMailTransportProbeDispatchStatus
import net.thunderbird.feature.taskmail.internal.domain.transportprobe.TaskMailTransportProbeRequest
import net.thunderbird.feature.taskmail.internal.domain.transportprobe.TaskMailTransportProbeSender
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

private const val CONTROL_BOOTSTRAP_SCHEMA_VERSION = "taskmail-bootstrap-control-contract-v2"
private const val TRANSPORT_PROBE_SCHEMA_VERSION = "taskmail-transport-probe-payload-v1"
private const val DIRECT_ACTION_TRANSPORT_PROBE = "transport_probe"
private const val CONTROL_PATH = "/control"
private const val PACKET_ID_PREFIX = "android-control:transport-probe:"
private const val REQUEST_ID_PREFIX = "probe_req_"
private const val TRACE_ID_PREFIX = "trace_transport_probe_"
private const val DIRECTION_ANDROID_TO_PC = "android_to_pc"
private const val TRANSPORT_KIND_MAIL = "mail"
private const val RESULT_OBSERVE_WINDOW_MILLIS = 20_000L
private const val MILLIS_PER_SECOND = 1_000L
private const val MAX_PAYLOAD_TEXT_LENGTH = 1024
private const val NANOS_PER_MILLISECOND = 1_000_000L
private val CONTROL_SUPPORTED_PAYLOAD_SCHEMAS = listOf(
    CONTROL_BOOTSTRAP_SCHEMA_VERSION,
    TRANSPORT_PROBE_SCHEMA_VERSION,
)

@Suppress("TooManyFunctions")
internal class RelayTaskMailTransportProbeSender(
    private val relayConnectionClient: RelayConnectionClient,
    private val eventStore: TaskMailTransportProbeEventStore,
    private val requestIdFactory: () -> String = ::nextRequestId,
    private val wallClockProvider: () -> String = ::currentUtcTimestamp,
    private val monotonicTimeProvider: () -> Long = { System.nanoTime() / NANOS_PER_MILLISECOND },
    private val resultObserveWindowMillis: Long = RESULT_OBSERVE_WINDOW_MILLIS,
) : TaskMailTransportProbeSender {

    override suspend fun send(
        config: RelayTransportConfig,
        request: TaskMailTransportProbeRequest,
    ): TaskMailTransportProbeDispatchResult = coroutineScope {
        val context = createDispatchContext(request)
        val controlConfig = config.copy(path = CONTROL_PATH)

        recordEvent(
            context = context,
            eventType = "android_probe_dispatch_started",
            summary = "Preparing relay transport probe.",
        )

        try {
            connectControl(config = controlConfig, context = context)
                ?.let { return@coroutineScope it }

            val matchingEvents = launch {
                relayConnectionClient.serverEvents
                    .filter { event -> event.matchesProbe(requestId = context.requestId, packetId = context.packetId) }
                    .collect { relayEvent ->
                        recordRelayEvent(context = context, relayEvent = relayEvent)
                    }
            }
            val matchingResult = async {
                relayConnectionClient.serverResults
                    .filter { result -> result.matchesProbe(requestId = context.requestId, packetId = context.packetId) }
                    .firstOrNull()
            }

            try {
                sendProbeCommand(context = context, matchingResult = matchingResult)
            } finally {
                matchingEvents.cancel()
                matchingResult.cancel()
            }
        } finally {
            relayConnectionClient.disconnect()
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
                transportKind = TRANSPORT_KIND_MAIL,
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
            traceId = "$TRACE_ID_PREFIX$requestId",
            payloadText = payloadText,
            artifactDirectoryPath = eventStore.saveManifest(manifest),
            manifest = manifest,
        )
    }

    private suspend fun connectControl(
        config: RelayTransportConfig,
        context: ProbeDispatchContext,
    ): TaskMailTransportProbeDispatchResult? {
        return relayConnectionClient.connect(
            config = config,
            supportedPayloadSchemas = CONTROL_SUPPORTED_PAYLOAD_SCHEMAS,
        ).fold(
            onSuccess = { helloAck ->
                if (!helloAck.acceptedPayloadSchemas.contains(TRANSPORT_PROBE_SCHEMA_VERSION)) {
                    recordEvent(
                        context = context,
                        eventType = "android_probe_dispatch_failed",
                        summary = "Relay /control hello_ack did not advertise transport_probe support.",
                        errorCode = "unsupported_payload_schema",
                        errorMessage = "hello_ack.accepted_payload_schemas missing taskmail-transport-probe-payload-v1",
                    )
                    return@fold context.toDispatchResult(
                        status = TaskMailTransportProbeDispatchStatus.Rejected,
                        errorCode = "unsupported_payload_schema",
                        errorMessage = "hello_ack.accepted_payload_schemas missing taskmail-transport-probe-payload-v1",
                    )
                }
                recordEvent(
                    context = context,
                    eventType = "android_probe_relay_connected",
                    summary = "Relay /control connected: ${helloAck.connectionId}",
                )
                null
            },
            onFailure = { error ->
                recordEvent(
                    context = context,
                    eventType = "android_probe_dispatch_failed",
                    summary = "Relay /control connection failed before probe dispatch.",
                    errorMessage = error.message,
                )
                context.toDispatchResult(
                    status = TaskMailTransportProbeDispatchStatus.Failed,
                    errorMessage = error.message,
                )
            },
        )
    }

    private suspend fun sendProbeCommand(
        context: ProbeDispatchContext,
        matchingResult: Deferred<RelayResult?>,
    ): TaskMailTransportProbeDispatchResult {
        return relayConnectionClient.sendCommand(
            command = buildCommand(
                request = context.request,
                requestId = context.requestId,
                packetId = context.packetId,
                traceId = context.traceId,
                payloadText = context.payloadText,
            ),
            ackTimeoutMillis = serverResponseWindowMillis(context.request),
        ).fold(
            onSuccess = { commandAck -> handleCommandAck(context, commandAck, matchingResult) },
            onFailure = { error -> handleSendFailure(context, error) },
        )
    }

    private suspend fun handleCommandAck(
        context: ProbeDispatchContext,
        commandAck: RelayCommandAck,
        matchingResult: Deferred<RelayResult?>,
    ): TaskMailTransportProbeDispatchResult {
        recordEvent(
            context = context,
            eventType = if (commandAck.isAcceptedLike) {
                "android_relay_probe_submitted"
            } else {
                "android_probe_dispatch_failed"
            },
            summary = if (commandAck.isAcceptedLike) {
                "Relay control command accepted."
            } else {
                "Relay control command rejected."
            },
            receiptId = commandAck.receiptId,
            errorCode = commandAck.errorCode,
            errorMessage = commandAck.errorMessage,
        )

        if (!commandAck.isAcceptedLike) {
            return context.toDispatchResult(
                status = TaskMailTransportProbeDispatchStatus.Rejected,
                receiptId = commandAck.receiptId,
                errorCode = commandAck.errorCode,
                errorMessage = commandAck.errorMessage,
            )
        }

        val observedResult = withTimeoutOrNull(serverResponseWindowMillis(context.request)) {
            matchingResult.await()
        }

        return if (observedResult == null) {
            handleAcceptedWithoutResult(context, commandAck.receiptId)
        } else {
            handleObservedResult(context, commandAck.receiptId, observedResult)
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
            relayResultType = relayResult.resultType,
            relayStatus = relayResult.terminalStatus,
        )

        return context.toDispatchResult(
            status = relayResult.toDispatchStatus(),
            receiptId = receiptId,
            resultId = relayResult.resultId,
            resultType = relayResult.resultType,
            resultStatus = relayResult.terminalStatus,
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
            summary = "Relay control command send failed before acceptance.",
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

    private fun buildCommand(
        request: TaskMailTransportProbeRequest,
        requestId: String,
        packetId: String,
        traceId: String,
        payloadText: String,
    ): RelayCommand {
        return RelayCommand(
            requestId = requestId,
            packetId = packetId,
            commandType = DIRECT_ACTION_TRANSPORT_PROBE,
            payloadSchema = TRANSPORT_PROBE_SCHEMA_VERSION,
            trace = buildJsonObject {
                put("trace_id", traceId)
                put("probe_id", request.probeId)
            },
            payload = buildJsonObject {
                put("probe_id", request.probeId)
                put("scenario", request.scenario.wireValue)
                put("direction", DIRECTION_ANDROID_TO_PC)
                put("transport_kind", TRANSPORT_KIND_MAIL)
                put("payload_text", payloadText)
                put("timeout_seconds", request.timeoutSeconds)
            },
            related = buildJsonObject {
                put("ui_surface", "transport_probe_sheet")
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
        relayResultType: String? = null,
        relayStatus: String? = null,
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
                relayResultType = relayResultType,
                relayStatus = relayStatus,
                errorCode = errorCode,
                errorMessage = errorMessage,
            ),
        )
    }

    private suspend fun recordRelayEvent(
        context: ProbeDispatchContext,
        relayEvent: net.thunderbird.feature.taskmail.internal.data.relay.protocol.RelayEvent,
    ) {
        val relayRecordedAt = relayEvent.sentAt?.takeIf(String::isNotBlank)
        eventStore.appendEvent(
            TaskMailTransportProbeRecordedEvent(
                probeId = context.request.probeId,
                eventType = relayEvent.eventType,
                actor = "relay_server",
                recordedAt = relayRecordedAt ?: currentTransportProbeTimestamp(),
                clockSource = if (relayRecordedAt == null) "android_wall_clock" else "relay_wall_clock",
                monotonicMs = if (relayRecordedAt == null) monotonicTimeProvider() else 0L,
                summary = "Relay event observed: ${relayEvent.eventType}",
                requestId = relayEvent.requestId ?: context.requestId,
                packetId = relayEvent.packetId ?: context.packetId,
            ),
        )
    }

    private fun serverResponseWindowMillis(request: TaskMailTransportProbeRequest): Long {
        return request.timeoutSeconds.toLong() * MILLIS_PER_SECOND + resultObserveWindowMillis
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
    val traceId: String,
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

private fun net.thunderbird.feature.taskmail.internal.data.relay.protocol.RelayEvent.matchesProbe(
    requestId: String,
    packetId: String,
): Boolean {
    return this.requestId == requestId || this.packetId == packetId
}

private fun RelayResult.toDispatchStatus(): TaskMailTransportProbeDispatchStatus {
    return when (terminalStatus) {
        "completed" -> TaskMailTransportProbeDispatchStatus.ResultCompleted
        "partial" -> TaskMailTransportProbeDispatchStatus.ResultPartial
        "failed" -> TaskMailTransportProbeDispatchStatus.ResultFailed
        else -> TaskMailTransportProbeDispatchStatus.AcceptedAwaitingResult
    }
}
