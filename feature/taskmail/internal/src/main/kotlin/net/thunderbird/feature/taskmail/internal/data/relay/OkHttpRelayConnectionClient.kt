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
import net.thunderbird.feature.taskmail.internal.data.relay.protocol.RelayError
import net.thunderbird.feature.taskmail.internal.data.relay.protocol.RelayHello
import net.thunderbird.feature.taskmail.internal.data.relay.protocol.RelayHelloAck
import net.thunderbird.feature.taskmail.internal.data.relay.protocol.RelayPacket
import net.thunderbird.feature.taskmail.internal.data.relay.protocol.RelayPacketAck
import net.thunderbird.feature.taskmail.internal.data.relay.protocol.RelayProtocolJsonCodec
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
private const val CONNECT_TIMEOUT_SECONDS = 15L
private const val PING_INTERVAL_SECONDS = 30L
private const val MILLIS_PER_SECOND = 1000L

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
    private var webSocket: WebSocket? = null
    private var helloAckDeferred: CompletableDeferred<Result<RelayHelloAck>>? = null
    private var packetAckDeferred: CompletableDeferred<Result<RelayPacketAck>>? = null

    override val connectionState: StateFlow<RelayConnectionState> = mutableConnectionState.asStateFlow()
    override val sessionUpdates: SharedFlow<RelaySessionUpdate> = mutableSessionUpdates.asSharedFlow()

    override suspend fun connect(config: RelayTransportConfig): Result<RelayHelloAck> {
        return withContext(ioDispatcher) {
            val normalizedConfig = config.normalized()
            if (!normalizedConfig.isConfigured()) {
                val message = "Relay host, port, and transport token are required."
                mutableConnectionState.value = RelayConnectionState.Failed(message)
                return@withContext Result.failure(IllegalStateException(message))
            }

            disconnectInternal()

            val deferred = CompletableDeferred<Result<RelayHelloAck>>()
            helloAckDeferred = deferred
            mutableConnectionState.value = RelayConnectionState.Connecting(normalizedConfig.relayUrl())

            val request = Request.Builder()
                .url(normalizedConfig.relayUrl())
                .header("Authorization", "Bearer ${normalizedConfig.transportToken}")
                .build()

            webSocket = client.newWebSocket(
                request,
                RelaySocketListener(
                    config = normalizedConfig,
                    deferred = deferred,
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

    override suspend fun sendPacket(packet: RelayPacket): Result<RelayPacketAck> {
        return withContext(ioDispatcher) {
            val activeWebSocket = webSocket
            if (activeWebSocket == null || mutableConnectionState.value !is RelayConnectionState.Connected) {
                logger.warn(TAG) { "Refusing relay packet send because websocket is not connected." }
                return@withContext Result.failure(
                    IllegalStateException("Relay websocket is not connected."),
                )
            }

            if (packetAckDeferred?.isCompleted == false) {
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
                withTimeout(CONNECT_TIMEOUT_SECONDS * MILLIS_PER_SECOND) {
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
    }

    private inner class RelaySocketListener(
        private val config: RelayTransportConfig,
        private val deferred: CompletableDeferred<Result<RelayHelloAck>>,
    ) : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            val hello = RelayHello(
                clientId = CLIENT_ID,
                clientVersion = CLIENT_VERSION,
                transportTokenId = config.tokenFingerprint().orEmpty(),
                sentAt = currentUtcTimestamp(),
            )
            val sent = webSocket.send(codec.encodeHello(hello))
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

        private fun failCurrentOperation(error: Throwable) {
            if (
                mutableConnectionState.value is RelayConnectionState.Failed &&
                helloAckDeferred == null &&
                packetAckDeferred == null
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

            webSocket?.cancel()
        }

        private fun handleServerMessage(message: RelayServerMessage) {
            when (message) {
                is RelayServerMessage.HelloAck -> handleHelloAck(message.message)
                is RelayServerMessage.PacketAck -> handlePacketAck(message.message)
                is RelayServerMessage.SessionUpdate -> handleSessionUpdate(message.message)
                is RelayServerMessage.Error -> handleServerError(message.message)
            }
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

        private fun handleSessionUpdate(message: RelaySessionUpdate) {
            val emitted = mutableSessionUpdates.tryEmit(message)
            if (!emitted) {
                logger.warn(TAG) {
                    "Dropping relay session_update updateId=${message.updateId} because no collector is ready."
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
