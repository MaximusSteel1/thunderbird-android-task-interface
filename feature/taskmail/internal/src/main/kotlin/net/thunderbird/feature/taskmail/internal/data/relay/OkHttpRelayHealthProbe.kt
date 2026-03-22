package net.thunderbird.feature.taskmail.internal.data.relay

import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import net.thunderbird.core.logging.Logger
import net.thunderbird.feature.taskmail.internal.domain.model.RelayHealthStatus
import net.thunderbird.feature.taskmail.internal.domain.model.RelayTransportConfig
import okhttp3.OkHttpClient
import okhttp3.Request

private const val TAG = "OkHttpRelayHealthProbe"

internal class OkHttpRelayHealthProbe(
    private val logger: Logger,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    okHttpClient: OkHttpClient? = null,
    private val json: Json = Json { ignoreUnknownKeys = true },
) : RelayHealthProbe {
    private val client = okHttpClient ?: OkHttpClient.Builder()
        .callTimeout(CALL_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .build()

    override suspend fun probe(config: RelayTransportConfig): Result<RelayHealthStatus> {
        return withContext(ioDispatcher) {
            runCatching {
                val request = Request.Builder()
                    .url(config.normalized().healthUrl())
                    .get()
                    .build()

                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        error("Relay health probe failed with HTTP ${response.code}")
                    }

                    val body = response.body.string()
                    parseHealthStatus(body)
                }
            }.onFailure { error ->
                logger.error(TAG, error) { "Failed to probe relay health" }
            }
        }
    }

    private fun parseHealthStatus(payload: String): RelayHealthStatus {
        val root = json.parseToJsonElement(payload).jsonObject
        val listen = root["listen"]?.jsonObject
        val auth = root["auth"]?.jsonObject

        return RelayHealthStatus(
            status = root["status"]?.jsonPrimitive?.content.orEmpty(),
            service = root["service"]?.jsonPrimitive?.content,
            listenHost = listen?.get("host")?.jsonPrimitive?.content,
            listenPort = listen?.get("port")?.jsonPrimitive?.intOrNull,
            sessionCount = root["session_count"]?.jsonPrimitive?.intOrNull,
            packetCount = root["packet_count"]?.jsonPrimitive?.intOrNull,
            tlsEnabled = root["tls_enabled"]?.jsonPrimitive?.booleanOrNull,
            transportTokenId = auth?.get("transport_token_id")?.jsonPrimitive?.content,
        )
    }

    private companion object {
        const val CALL_TIMEOUT_SECONDS = 15L
    }
}
