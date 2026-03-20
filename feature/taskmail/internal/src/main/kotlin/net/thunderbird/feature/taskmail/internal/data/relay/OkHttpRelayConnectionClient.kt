package net.thunderbird.feature.taskmail.internal.data.relay

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import net.thunderbird.core.logging.Logger
import net.thunderbird.feature.taskmail.internal.data.relay.protocol.RelayHello
import net.thunderbird.feature.taskmail.internal.data.relay.protocol.RelayHelloAck
import net.thunderbird.feature.taskmail.internal.data.relay.protocol.RelayProtocolJsonCodec
import net.thunderbird.feature.taskmail.internal.data.relay.protocol.RelayServerMessage
import net.thunderbird.feature.taskmail.internal.domain.model.RelayConnectionState
import net.thunderbird.feature.taskmail.internal.domain.model.RelayTransportConfig
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit

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
    private var webSocket: WebSocket? = null
    private var helloAckDeferred: CompletableDeferred<Result<RelayHelloAck>>? = null

    override val connectionState: StateFlow<RelayConnectionState> = mutableConnectionState.asStateFlow()

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
                failHandshake("Failed to send relay hello.")
            }
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            runCatching {
                codec.decodeServerMessage(text)
            }.onSuccess { message ->
                when (message) {
                    is RelayServerMessage.HelloAck -> {
                        mutableConnectionState.value = RelayConnectionState.Connected(
                            connectionId = message.message.connectionId,
                            serverTime = message.message.serverTime,
                            heartbeatSeconds = message.message.heartbeatSeconds,
                        )
                        if (!deferred.isCompleted) {
                            deferred.complete(Result.success(message.message))
                        }
                    }

                    is RelayServerMessage.Error -> {
                        failHandshake("${message.message.code}: ${message.message.message}")
                    }
                }
            }.onFailure { error ->
                failHandshake(
                    error.message?.takeIf(String::isNotBlank)
                        ?: "Failed to parse relay server message.",
                )
            }
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            logger.error(TAG, t) { "Relay websocket failed" }
            failHandshake(
                t.message?.takeIf(String::isNotBlank)
                    ?: "Relay websocket connection failed.",
            )
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            if (mutableConnectionState.value is RelayConnectionState.Connected) {
                mutableConnectionState.value = RelayConnectionState.Idle
            }
        }

        private fun failHandshake(message: String) {
            mutableConnectionState.value = RelayConnectionState.Failed(message)
            if (!deferred.isCompleted) {
                deferred.complete(Result.failure(IllegalStateException(message)))
            }
            webSocket?.cancel()
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
