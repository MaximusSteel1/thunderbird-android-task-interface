package net.thunderbird.feature.taskmail.internal.data.relay

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import net.thunderbird.core.logging.Logger
import net.thunderbird.feature.taskmail.internal.data.relay.protocol.RelayCommand
import net.thunderbird.feature.taskmail.internal.data.relay.protocol.RelayCommandAck
import net.thunderbird.feature.taskmail.internal.data.relay.protocol.RelayControlHello
import net.thunderbird.feature.taskmail.internal.data.relay.protocol.RelayError
import net.thunderbird.feature.taskmail.internal.data.relay.protocol.RelayEvent
import net.thunderbird.feature.taskmail.internal.data.relay.protocol.RelayHello
import net.thunderbird.feature.taskmail.internal.data.relay.protocol.RelayHelloAck
import net.thunderbird.feature.taskmail.internal.data.relay.protocol.RelayPacket
import net.thunderbird.feature.taskmail.internal.data.relay.protocol.RelayPacketAck
import net.thunderbird.feature.taskmail.internal.data.relay.protocol.RelayProtocolJsonCodec
import net.thunderbird.feature.taskmail.internal.data.relay.protocol.RelayResult
import net.thunderbird.feature.taskmail.internal.data.relay.protocol.RelayServerMessage
import net.thunderbird.feature.taskmail.internal.data.relay.protocol.RelaySessionUpdate
import net.thunderbird.feature.taskmail.internal.domain.model.RelayConnectionState
import net.thunderbird.feature.taskmail.internal.domain.model.RelayTransportConfig
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener

private const val TAG = "OkHttpRelayConnectionClient"
private const val CLIENT_ID = "android-taskmail"
private const val CLIENT_VERSION = "0.1.0-dev"
private const val CONTROL_CLIENT_ID = "android-control"
private const val CONTROL_CLIENT_VERSION = "0.1.0"
private const val CONTROL_PATH = "/control"
private const val CONNECT_TIMEOUT_SECONDS = 15L
private const val PING_INTERVAL_SECONDS = 30L
private const val MILLIS_PER_SECOND = 1000L

@Suppress("TooManyFunctions")
internal class OkHttpRelayConnectionClient(
    private val codec: RelayProtocolJsonCodec,
    private val logger: Logger,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    okHttpClient: OkHttpClient? = null,
) : RelayConnectionClient {
    private val client = okHttpClient ?: OkHttpClient.Builder()
        .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .pingInterval(PING_INTERVAL_SECONDS, TimeUnit.SECONDS)
        .build()

    private val mutableConnectionState = MutableStateFlow<RelayConnectionState>(RelayConnectionState.Idle)
    private val mutableSessionUpdates = MutableSharedFlow<RelaySessionUpdate>(extraBufferCapacity = 32)
    private val mutableServerEvents = MutableSharedFlow<RelayEvent>(extraBufferCapacity = 32)
    private val mutableServerResults = MutableSharedFlow<RelayResult>(extraBufferCapacity = 32)
    private var webSocket: WebSocket? = null
    private var helloAckDeferred: CompletableDeferred<Result<RelayHelloAck>>? = null
    private var packetAckDeferred: CompletableDeferred<Result<RelayPacketAck>>? = null
    private var commandAckDeferred: CompletableDeferred<Result<RelayCommandAck>>? = null

    override val connectionState: StateFlow<RelayConnectionState> = mutableConnectionState.asStateFlow()
    override val sessionUpdates: SharedFlow<RelaySessionUpdate> = mutableSessionUpdates.asSharedFlow()
    override val serverEvents: SharedFlow<RelayEvent> = mutableServerEvents.asSharedFlow()
    override val serverResults: SharedFlow<RelayResult> = mutableServerResults.asSharedFlow()

    override suspend fun connect(config: RelayTransportConfig): Result<RelayHelloAck> {
        return connectInternal(
            config = config.normalized(),
            helloPayload = { normalizedConfig ->
                codec.encodeHello(
                    RelayHello(
                        clientId = CLIENT_ID,
                        clientVersion = CLIENT_VERSION,
                        transportTokenId = normalizedConfig.tokenFingerprint().orEmpty(),
                        sentAt = currentUtcTimestamp(),
                    ),
                )
            },
        )
    }

    override suspend fun connect(
        config: RelayTransportConfig,
        supportedPayloadSchemas: List<String>,
    ): Result<RelayHelloAck> {
        return connectInternal(
            config = config.copy(path = CONTROL_PATH).normalized(),
            helloPayload = { normalizedConfig ->
                codec.encodeControlHello(
                    RelayControlHello(
                        clientId = CONTROL_CLIENT_ID,
                        clientVersion = CONTROL_CLIENT_VERSION,
                        transportTokenId = normalizedConfig.tokenFingerprint().orEmpty(),
                        supportedPayloadSchemas = supportedPayloadSchemas,
                        sentAt = currentUtcTimestamp(),
                    ),
                )
            },
        )
    }

    private suspend fun connectInternal(
        config: RelayTransportConfig,
        helloPayload: (RelayTransportConfig) -> String,
    ): Result<RelayHelloAck> {
        return withContext(ioDispatcher) {
            if (!config.isConfigured()) {
                val message = "Relay host, port, and transport token are required."
                mutableConnectionState.value = RelayConnectionState.Failed(message)
                return@withContext Result.failure(IllegalStateException(message))
            }

            disconnectInternal()

            val deferred = CompletableDeferred<Result<RelayHelloAck>>()
            helloAckDeferred = deferred
            mutableConnectionState.value = RelayConnectionState.Connecting(config.relayUrl())

            val request = Request.Builder()
                .url(config.relayUrl())
                .header("Authorization", "Bearer ${config.transportToken}")
                .build()

            webSocket = client.newWebSocket(
                request,
                RelaySocketListener(
                    deferred = deferred,
                    helloPayload = helloPayload(config),
                ),
            )

            try {
                withTimeout(CONNECT_TIMEOUT_SECONDS * MILLIS_PER_SECOND) {
                    deferred.await()
                }
            } catch (error: TimeoutCancellationException) {
                disconnectInternal()
                val message = error.message?.takeIf(String::isNotBlank) ?: "Relay connection timed out."
                mutableConnectionState.value = RelayConnectionState.Failed(message)
                Result.failure(IllegalStateException(message, error))
            }
        }
    }

    override suspend fun sendPacket(
        packet: RelayPacket,
        ackTimeoutMillis: Long,
    ): Result<RelayPacketAck> {
        return withContext(ioDispatcher) {
            val activeWebSocket = webSocket
            if (activeWebSocket == null || mutableConnectionState.value !is RelayConnectionState.Connected) {
                logger.warn(TAG) { "Refusing relay packet send because websocket is not connected." }
                return@withContext Result.failure(
                    IllegalStateException("Relay websocket is not connected."),
                )
            }

            if (packetAckDeferred?.isCompleted == false || commandAckDeferred?.isCompleted == false) {
                return@withContext Result.failure(
                    IllegalStateException("Relay packet send already in progress."),
                )
            }

            val deferred = CompletableDeferred<Result<RelayPacketAck>>()
            packetAckDeferred = deferred

            logger.debug(TAG) { "Sending relay packet packetId=${packet.packetId}" }
            if (!activeWebSocket.send(codec.encodePacket(packet))) {
                packetAckDeferred = null
                logger.warn(TAG) { "Relay packet send returned false for packetId=${packet.packetId}" }
                return@withContext Result.failure(
                    IllegalStateException("Failed to send relay packet."),
                )
            }

            try {
                withTimeout(ackTimeoutMillis) {
                    deferred.await()
                }
            } catch (error: TimeoutCancellationException) {
                packetAckDeferred = null
                logger.warn(TAG, error) { "Relay packet acknowledgement timed out for packetId=${packet.packetId}" }
                Result.failure(
                    IllegalStateException(
                        error.message?.takeIf(String::isNotBlank) ?: "Relay packet acknowledgement timed out.",
                        error,
                    ),
                )
            }
        }
    }

    override suspend fun sendCommand(
        command: RelayCommand,
        ackTimeoutMillis: Long,
    ): Result<RelayCommandAck> {
        return withContext(ioDispatcher) {
            val activeWebSocket = webSocket
            if (activeWebSocket == null || mutableConnectionState.value !is RelayConnectionState.Connected) {
                logger.warn(TAG) { "Refusing relay command send because websocket is not connected." }
                return@withContext Result.failure(
                    IllegalStateException("Relay websocket is not connected."),
                )
            }

            if (packetAckDeferred?.isCompleted == false || commandAckDeferred?.isCompleted == false) {
                return@withContext Result.failure(
                    IllegalStateException("Relay command send already in progress."),
                )
            }

            val deferred = CompletableDeferred<Result<RelayCommandAck>>()
            commandAckDeferred = deferred

            logger.debug(TAG) { "Sending relay command packetId=${command.packetId}" }
            if (!activeWebSocket.send(codec.encodeCommand(command))) {
                commandAckDeferred = null
                logger.warn(TAG) { "Relay command send returned false for packetId=${command.packetId}" }
                return@withContext Result.failure(
                    IllegalStateException("Failed to send relay command."),
                )
            }

            try {
                withTimeout(ackTimeoutMillis) {
                    deferred.await()
                }
            } catch (error: TimeoutCancellationException) {
                commandAckDeferred = null
                logger.warn(TAG, error) { "Relay command acknowledgement timed out for packetId=${command.packetId}" }
                Result.failure(
                    IllegalStateException(
                        error.message?.takeIf(String::isNotBlank)
                            ?: "Relay command acknowledgement timed out.",
                        error,
                    ),
                )
            }
        }
    }

    override suspend fun disconnect() {
        withContext(ioDispatcher) {
            disconnectInternal()
            mutableConnectionState.value = RelayConnectionState.Idle
        }
    }

    private fun disconnectInternal() {
        webSocket?.close(CLOSE_CODE_NORMAL, "client_disconnect")
        webSocket = null
        helloAckDeferred?.cancel()
        helloAckDeferred = null
        packetAckDeferred?.cancel()
        packetAckDeferred = null
        commandAckDeferred?.cancel()
        commandAckDeferred = null
    }

    private fun handleHelloAck(message: RelayHelloAck) {
        mutableConnectionState.value = RelayConnectionState.Connected(
            connectionId = message.connectionId,
            serverTime = message.serverTime,
            heartbeatSeconds = message.heartbeatSeconds,
        )
        helloAckDeferred?.let { helloDeferred ->
            if (!helloDeferred.isCompleted) {
                helloDeferred.complete(Result.success(message))
            }
        }
        helloAckDeferred = null
    }

    private fun handlePacketAck(message: RelayPacketAck) {
        if (message.accepted) {
            logger.debug(TAG) {
                "Received relay packet ack for packetId=${message.packetId}"
            }
        } else {
            logger.warn(TAG) {
                "Relay packet ack rejected for packetId=${message.packetId} " +
                    "code=${message.errorCode.orEmpty()} " +
                    "error=${message.errorMessage.orEmpty()}"
            }
        }

        packetAckDeferred?.let { packetDeferred ->
            if (!packetDeferred.isCompleted) {
                packetDeferred.complete(Result.success(message))
            }
        }
        packetAckDeferred = null
    }

    private fun handleCommandAck(message: RelayCommandAck) {
        if (message.isAcceptedLike) {
            logger.debug(TAG) {
                "Received relay command ack for packetId=${message.packetId.orEmpty()}"
            }
        } else {
            logger.warn(TAG) {
                "Relay command ack rejected for packetId=${message.packetId.orEmpty()} " +
                    "code=${message.errorCode.orEmpty()} " +
                    "error=${message.errorMessage.orEmpty()}"
            }
        }

        commandAckDeferred?.let { commandDeferred ->
            if (!commandDeferred.isCompleted) {
                commandDeferred.complete(Result.success(message))
            }
        }
        commandAckDeferred = null
    }

    private fun handleSessionUpdate(message: RelaySessionUpdate) {
        val emitted = mutableSessionUpdates.tryEmit(message)
        if (!emitted) {
            logger.warn(TAG) {
                "Dropping relay session_update updateId=${message.updateId} because no collector is ready."
            }
        }
    }

    private fun handleEvent(message: RelayEvent) {
        val emitted = mutableServerEvents.tryEmit(message)
        if (!emitted) {
            logger.warn(TAG) {
                "Dropping relay event type=${message.eventType} requestId=${message.requestId.orEmpty()} " +
                    "because no collector is ready."
            }
        }
    }

    private fun handleResult(message: RelayResult) {
        val emitted = mutableServerResults.tryEmit(message)
        if (!emitted) {
            logger.warn(TAG) {
                "Dropping relay result type=${message.resultType.orEmpty()} " +
                    "requestId=${message.requestId.orEmpty()} because no collector is ready."
            }
        }
    }

    private fun handleServerError(message: RelayError) {
        logger.warn(TAG) {
            "Relay server error code=${message.code} message=${message.message}"
        }
        failCurrentOperation(
            RelayServerException(
                code = message.code,
                message = message.message,
            ),
        )
    }

    private fun failCurrentOperation(error: Throwable) {
        if (
            mutableConnectionState.value is RelayConnectionState.Failed &&
            helloAckDeferred == null &&
            packetAckDeferred == null &&
            commandAckDeferred == null
        ) {
            return
        }

        val message = error.message?.takeIf(String::isNotBlank) ?: "Relay websocket operation failed."
        mutableConnectionState.value = RelayConnectionState.Failed(message)

        if (helloAckDeferred?.isCompleted == false) {
            helloAckDeferred?.complete(Result.failure(error))
        }
        helloAckDeferred = null

        if (packetAckDeferred?.isCompleted == false) {
            packetAckDeferred?.complete(Result.failure(error))
        }
        packetAckDeferred = null

        if (commandAckDeferred?.isCompleted == false) {
            commandAckDeferred?.complete(Result.failure(error))
        }
        commandAckDeferred = null

        webSocket?.cancel()
    }

    private inner class RelaySocketListener(
        private val deferred: CompletableDeferred<Result<RelayHelloAck>>,
        private val helloPayload: String,
    ) : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            val sent = webSocket.send(helloPayload)
            if (!sent) {
                failCurrentOperation(IllegalStateException("Failed to send relay hello."))
            }
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            runCatching {
                codec.decodeServerMessage(text)
            }.onSuccess(::handleServerMessage).onFailure { error ->
                failCurrentOperation(
                    IllegalStateException(
                        error.message?.takeIf(String::isNotBlank)
                            ?: "Failed to parse relay server message.",
                        error,
                    ),
                )
            }
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            logger.error(TAG, t) { "Relay websocket failed" }
            failCurrentOperation(
                IllegalStateException(
                    t.message?.takeIf(String::isNotBlank)
                        ?: "Relay websocket connection failed.",
                    t,
                ),
            )
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            if (mutableConnectionState.value is RelayConnectionState.Connected) {
                mutableConnectionState.value = RelayConnectionState.Idle
            }
        }

        private fun handleServerMessage(message: RelayServerMessage) {
            when (message) {
                is RelayServerMessage.HelloAck -> {
                    this@OkHttpRelayConnectionClient.handleHelloAck(message.message)
                }
                is RelayServerMessage.PacketAck -> {
                    this@OkHttpRelayConnectionClient.handlePacketAck(message.message)
                }
                is RelayServerMessage.CommandAck -> {
                    this@OkHttpRelayConnectionClient.handleCommandAck(message.message)
                }
                is RelayServerMessage.SessionUpdate -> {
                    this@OkHttpRelayConnectionClient.handleSessionUpdate(message.message)
                }
                is RelayServerMessage.Event -> {
                    this@OkHttpRelayConnectionClient.handleEvent(message.message)
                }
                is RelayServerMessage.Result -> {
                    this@OkHttpRelayConnectionClient.handleResult(message.message)
                }
                is RelayServerMessage.Error -> {
                    this@OkHttpRelayConnectionClient.handleServerError(message.message)
                }
            }
        }
    }

    private companion object {
        const val CLOSE_CODE_NORMAL = 1000

        fun currentUtcTimestamp(): String {
            return SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }.format(Date())
        }
    }
}
