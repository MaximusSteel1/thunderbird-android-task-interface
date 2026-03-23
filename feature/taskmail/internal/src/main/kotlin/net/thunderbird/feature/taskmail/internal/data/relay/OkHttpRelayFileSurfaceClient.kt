package net.thunderbird.feature.taskmail.internal.data.relay

import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import net.thunderbird.core.logging.Logger
import net.thunderbird.feature.taskmail.internal.domain.model.RelayTransportConfig
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

private const val TAG = "OkHttpRelayFileSurface"

internal interface RelayFileSurfaceClient {
    suspend fun upload(
        config: RelayTransportConfig,
        request: RelayFileSurfaceUploadRequest,
    ): RelayFileSurfaceEnvelope

    suspend fun getMetadata(
        config: RelayTransportConfig,
        metadataUrl: String,
    ): RelayFileSurfaceEnvelope

    suspend fun downloadContent(
        config: RelayTransportConfig,
        downloadUrl: String,
    ): RelayDownloadedFileContent
}

@Serializable
internal data class RelayFileSurfaceUploadRequest(
    val metadata: RelayFileUploadMetadata,
    val bytes: ByteArray,
)

@Serializable
internal data class RelayFileUploadMetadata(
    @SerialName("artifact_id")
    val artifactId: String,
    val name: String,
    val kind: String,
    val role: String,
    @SerialName("mime_type")
    val mimeType: String,
    @SerialName("byte_size")
    val byteSize: Long,
    val sha256: String,
    val trace: RelayFileUploadTrace? = null,
)

@Serializable
internal data class RelayFileUploadTrace(
    @SerialName("trace_id")
    val traceId: String,
    @SerialName("probe_id")
    val probeId: String? = null,
)

@Serializable
internal data class RelayFileSurfaceEnvelope(
    @SerialName("schema_version")
    val schemaVersion: String,
    @SerialName("file_id")
    val fileId: String,
    @SerialName("stored_at")
    val storedAt: String? = null,
    val artifact: RelayFileSurfaceArtifactDescriptor,
)

@Serializable
internal data class RelayFileSurfaceArtifactDescriptor(
    @SerialName("artifact_id")
    val artifactId: String,
    @SerialName("file_id")
    val fileId: String,
    val name: String,
    val kind: String,
    val role: String,
    @SerialName("mime_type")
    val mimeType: String,
    @SerialName("byte_size")
    val byteSize: Long,
    val sha256: String,
    @SerialName("metadata_url")
    val metadataUrl: String,
    @SerialName("download_url")
    val downloadUrl: String,
)

internal data class RelayDownloadedFileContent(
    val bytes: ByteArray,
    val contentType: String?,
    val etag: String?,
)

@Serializable
private data class RelayFileSurfaceErrorPayload(
    @SerialName("error_code")
    val errorCode: String? = null,
    @SerialName("error_message")
    val errorMessage: String? = null,
    val retryable: Boolean? = null,
)

internal class RelayFileSurfaceException(
    val statusCode: Int,
    val errorCode: String?,
    override val message: String,
    val retryable: Boolean?,
) : IllegalStateException(message)

internal class OkHttpRelayFileSurfaceClient(
    private val logger: Logger,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    okHttpClient: OkHttpClient? = null,
    private val json: Json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = true
    },
) : RelayFileSurfaceClient {
    private val client = okHttpClient ?: OkHttpClient.Builder()
        .callTimeout(CALL_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .build()

    override suspend fun upload(
        config: RelayTransportConfig,
        request: RelayFileSurfaceUploadRequest,
    ): RelayFileSurfaceEnvelope {
        return withContext(ioDispatcher) {
            val normalizedConfig = config.normalized()
            runCatching {
                val requestBody = MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addFormDataPart(
                        "metadata",
                        null,
                        json.encodeToString(request.metadata).toRequestBody(JSON_MEDIA_TYPE),
                    )
                    .addFormDataPart(
                        "file",
                        request.metadata.name,
                        request.bytes.toRequestBody(
                            request.metadata.mimeType.toMediaTypeOrNull() ?: OCTET_STREAM_MEDIA_TYPE,
                        ),
                    )
                    .build()

                val httpRequest = Request.Builder()
                    .url(normalizedConfig.fileSurfaceUrl())
                    .header("Authorization", "Bearer ${normalizedConfig.transportToken}")
                    .post(requestBody)
                    .build()

                executeJsonRequest(httpRequest)
            }.onFailure { error ->
                logger.error(TAG, error) { "Failed to upload TaskMail file sample." }
            }.getOrThrow()
        }
    }

    override suspend fun getMetadata(
        config: RelayTransportConfig,
        metadataUrl: String,
    ): RelayFileSurfaceEnvelope {
        return withContext(ioDispatcher) {
            val normalizedConfig = config.normalized()
            runCatching {
                val httpRequest = Request.Builder()
                    .url(normalizedConfig.absoluteHttpUrl(metadataUrl))
                    .header("Authorization", "Bearer ${normalizedConfig.transportToken}")
                    .get()
                    .build()

                executeJsonRequest(httpRequest)
            }.onFailure { error ->
                logger.error(TAG, error) { "Failed to read TaskMail file sample metadata." }
            }.getOrThrow()
        }
    }

    override suspend fun downloadContent(
        config: RelayTransportConfig,
        downloadUrl: String,
    ): RelayDownloadedFileContent {
        return withContext(ioDispatcher) {
            val normalizedConfig = config.normalized()
            runCatching {
                val httpRequest = Request.Builder()
                    .url(normalizedConfig.absoluteHttpUrl(downloadUrl))
                    .header("Authorization", "Bearer ${normalizedConfig.transportToken}")
                    .get()
                    .build()

                client.newCall(httpRequest).execute().use { response ->
                    val body = response.body
                    if (!response.isSuccessful) {
                        throw parseError(
                            statusCode = response.code,
                            body = body.string(),
                        )
                    }

                    RelayDownloadedFileContent(
                        bytes = body.bytes(),
                        contentType = response.header("Content-Type"),
                        etag = response.header("ETag"),
                    )
                }
            }.onFailure { error ->
                logger.error(TAG, error) { "Failed to download TaskMail file sample content." }
            }.getOrThrow()
        }
    }

    private fun executeJsonRequest(request: Request): RelayFileSurfaceEnvelope {
        client.newCall(request).execute().use { response ->
            val body = response.body.string()
            if (!response.isSuccessful) {
                throw parseError(
                    statusCode = response.code,
                    body = body,
                )
            }

            return json.decodeFromString(body)
        }
    }

    private fun parseError(
        statusCode: Int,
        body: String,
    ): RelayFileSurfaceException {
        val payload = runCatching {
            json.decodeFromString<RelayFileSurfaceErrorPayload>(body)
        }.getOrNull()

        val message = payload?.errorMessage?.takeIf(String::isNotBlank)
            ?: "Relay file surface request failed with HTTP $statusCode"

        return RelayFileSurfaceException(
            statusCode = statusCode,
            errorCode = payload?.errorCode,
            message = message,
            retryable = payload?.retryable,
        )
    }

    private companion object {
        const val CALL_TIMEOUT_SECONDS = 30L
        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
        val OCTET_STREAM_MEDIA_TYPE = "application/octet-stream".toMediaType()
    }
}
