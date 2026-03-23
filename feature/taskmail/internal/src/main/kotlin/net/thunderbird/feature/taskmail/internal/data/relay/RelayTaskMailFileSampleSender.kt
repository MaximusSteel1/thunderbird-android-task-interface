package net.thunderbird.feature.taskmail.internal.data.relay

import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import net.thunderbird.feature.taskmail.internal.data.debug.TaskMailFileSampleManifest
import net.thunderbird.feature.taskmail.internal.data.debug.TaskMailFileSampleStore
import net.thunderbird.feature.taskmail.internal.data.debug.currentTaskMailFileSampleTimestamp
import net.thunderbird.feature.taskmail.internal.domain.filesample.TaskMailFileSampleRequest
import net.thunderbird.feature.taskmail.internal.domain.filesample.TaskMailFileSampleResult
import net.thunderbird.feature.taskmail.internal.domain.filesample.TaskMailFileSampleSender
import net.thunderbird.feature.taskmail.internal.domain.filesample.TaskMailFileSampleStatus
import net.thunderbird.feature.taskmail.internal.domain.model.RelayTransportConfig

private const val FILE_SAMPLE_MIME_TYPE = "text/plain; charset=utf-8"
private const val FILE_SAMPLE_KIND = "file"
private const val FILE_SAMPLE_ROLE = "attachment"
private const val REQUEST_METADATA_FILE_NAME = "request_metadata.json"
private const val UPLOAD_RESPONSE_FILE_NAME = "upload_response.json"
private const val DOWNLOAD_METADATA_FILE_NAME = "download_metadata.json"

internal class RelayTaskMailFileSampleSender(
    private val fileSurfaceClient: RelayFileSurfaceClient,
    private val store: TaskMailFileSampleStore,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val json: Json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = true
        prettyPrint = true
    },
) : TaskMailFileSampleSender {
    override suspend fun send(
        config: RelayTransportConfig,
        request: TaskMailFileSampleRequest,
    ): TaskMailFileSampleResult {
        return withContext(ioDispatcher) {
            val createdAt = currentTaskMailFileSampleTimestamp()
            val traceId = "trace_file_sample_${request.sampleId}"
            val artifactId = "artifact_${request.sampleId}"
            val fileName = "taskmail-file-sample-${request.sampleId}.txt"
            val sourceContent = buildSourceContent(
                sampleId = request.sampleId,
                createdAt = createdAt,
                payloadText = request.payloadText,
            )
            val sourceSha256 = sourceContent.sha256Hex()
            val uploadMetadata = RelayFileUploadMetadata(
                artifactId = artifactId,
                name = fileName,
                kind = FILE_SAMPLE_KIND,
                role = FILE_SAMPLE_ROLE,
                mimeType = FILE_SAMPLE_MIME_TYPE,
                byteSize = sourceContent.size.toLong(),
                sha256 = sourceSha256,
                trace = RelayFileUploadTrace(
                    traceId = traceId,
                    probeId = request.sampleId,
                ),
            )
            val initialManifest = TaskMailFileSampleManifest(
                sampleId = request.sampleId,
                createdAt = createdAt,
                traceId = traceId,
                artifactId = artifactId,
                fileName = fileName,
                mimeType = FILE_SAMPLE_MIME_TYPE,
                byteSize = sourceContent.size.toLong(),
                expectedSha256 = sourceSha256,
                status = "running",
            )
            val artifactDirectoryPath = store.saveManifest(initialManifest)
            store.writeText(
                sampleId = request.sampleId,
                fileName = REQUEST_METADATA_FILE_NAME,
                content = json.encodeToString(uploadMetadata),
            )
            store.writeBytes(
                sampleId = request.sampleId,
                fileName = fileName,
                content = sourceContent,
            )

            var uploadResponse: RelayFileSurfaceEnvelope? = null
            var metadataResponse: RelayFileSurfaceEnvelope? = null
            var downloadedContent: RelayDownloadedFileContent? = null

            runCatching {
                uploadResponse = fileSurfaceClient.upload(
                    config = config,
                    request = RelayFileSurfaceUploadRequest(
                        metadata = uploadMetadata,
                        bytes = sourceContent,
                    ),
                )
                store.writeText(
                    sampleId = request.sampleId,
                    fileName = UPLOAD_RESPONSE_FILE_NAME,
                    content = json.encodeToString(requireNotNull(uploadResponse)),
                )
                requireNotNull(uploadResponse).requireConsistency(
                    expectedFileId = null,
                    expectedByteSize = sourceContent.size.toLong(),
                    expectedSha256 = sourceSha256,
                )

                metadataResponse = fileSurfaceClient.getMetadata(
                    config = config,
                    metadataUrl = requireNotNull(uploadResponse).artifact.metadataUrl,
                )
                store.writeText(
                    sampleId = request.sampleId,
                    fileName = DOWNLOAD_METADATA_FILE_NAME,
                    content = json.encodeToString(requireNotNull(metadataResponse)),
                )
                requireNotNull(metadataResponse).requireConsistency(
                    expectedFileId = requireNotNull(uploadResponse).fileId,
                    expectedByteSize = sourceContent.size.toLong(),
                    expectedSha256 = sourceSha256,
                )

                downloadedContent = fileSurfaceClient.downloadContent(
                    config = config,
                    downloadUrl = requireNotNull(uploadResponse).artifact.downloadUrl,
                )
                store.writeBytes(
                    sampleId = request.sampleId,
                    fileName = "downloaded_$fileName",
                    content = requireNotNull(downloadedContent).bytes,
                )

                val observedSha256 = requireNotNull(downloadedContent).bytes.sha256Hex()
                if (observedSha256 != sourceSha256) {
                    error(
                        "Downloaded bytes sha256 mismatch: expected=$sourceSha256 observed=$observedSha256",
                    )
                }

                val acceptedUpload = requireNotNull(uploadResponse)
                val fetchedMetadata = requireNotNull(metadataResponse)

                store.saveManifest(
                    initialManifest.copy(
                        observedSha256 = observedSha256,
                        fileId = acceptedUpload.fileId,
                        storedAt = acceptedUpload.storedAt ?: fetchedMetadata.storedAt,
                        metadataUrl = acceptedUpload.artifact.metadataUrl,
                        downloadUrl = acceptedUpload.artifact.downloadUrl,
                        status = "completed",
                    ),
                )

                TaskMailFileSampleResult(
                    sampleId = request.sampleId,
                    status = TaskMailFileSampleStatus.Completed,
                    artifactDirectoryPath = artifactDirectoryPath,
                    fileId = acceptedUpload.fileId,
                    metadataUrl = acceptedUpload.artifact.metadataUrl,
                    downloadUrl = acceptedUpload.artifact.downloadUrl,
                    byteSize = sourceContent.size.toLong(),
                    expectedSha256 = sourceSha256,
                    observedSha256 = observedSha256,
                )
            }.getOrElse { error ->
                val observedSha256 = downloadedContent?.bytes?.sha256Hex()
                val relayError = error as? RelayFileSurfaceException
                store.saveManifest(
                    initialManifest.copy(
                        observedSha256 = observedSha256,
                        fileId = uploadResponse?.fileId ?: metadataResponse?.fileId,
                        storedAt = uploadResponse?.storedAt ?: metadataResponse?.storedAt,
                        metadataUrl = uploadResponse?.artifact?.metadataUrl ?: metadataResponse?.artifact?.metadataUrl,
                        downloadUrl = uploadResponse?.artifact?.downloadUrl ?: metadataResponse?.artifact?.downloadUrl,
                        status = "failed",
                        errorCode = relayError?.errorCode ?: "file_sample_failed",
                        errorMessage = error.message ?: "TaskMail file sample failed.",
                    ),
                )

                TaskMailFileSampleResult(
                    sampleId = request.sampleId,
                    status = TaskMailFileSampleStatus.Failed,
                    artifactDirectoryPath = artifactDirectoryPath,
                    fileId = uploadResponse?.fileId ?: metadataResponse?.fileId,
                    metadataUrl = uploadResponse?.artifact?.metadataUrl ?: metadataResponse?.artifact?.metadataUrl,
                    downloadUrl = uploadResponse?.artifact?.downloadUrl ?: metadataResponse?.artifact?.downloadUrl,
                    byteSize = sourceContent.size.toLong(),
                    expectedSha256 = sourceSha256,
                    observedSha256 = observedSha256,
                    errorCode = relayError?.errorCode ?: "file_sample_failed",
                    errorMessage = error.message ?: "TaskMail file sample failed.",
                )
            }
        }
    }

    private fun buildSourceContent(
        sampleId: String,
        createdAt: String,
        payloadText: String,
    ): ByteArray {
        return buildString {
            appendLine("taskmail_file_sample_id=$sampleId")
            appendLine("created_at=$createdAt")
            appendLine("payload_text=$payloadText")
        }.toByteArray(StandardCharsets.UTF_8)
    }
}

private fun RelayFileSurfaceEnvelope.requireConsistency(
    expectedFileId: String?,
    expectedByteSize: Long,
    expectedSha256: String,
) {
    check(fileId == artifact.fileId) {
        "file surface metadata mismatch: envelope file_id=$fileId artifact file_id=${artifact.fileId}"
    }
    expectedFileId?.let { requiredFileId ->
        check(fileId == requiredFileId) {
            "file surface replay mismatch: expected file_id=$requiredFileId actual file_id=$fileId"
        }
    }
    check(artifact.byteSize == expectedByteSize) {
        "file surface byte_size mismatch: expected=$expectedByteSize actual=${artifact.byteSize}"
    }
    check(artifact.sha256 == expectedSha256) {
        "file surface sha256 mismatch: expected=$expectedSha256 actual=${artifact.sha256}"
    }
    check(artifact.metadataUrl.isNotBlank()) {
        "file surface metadata_url is blank"
    }
    check(artifact.downloadUrl.isNotBlank()) {
        "file surface download_url is blank"
    }
}

private fun ByteArray.sha256Hex(): String {
    val digest = MessageDigest.getInstance("SHA-256").digest(this)
    return digest.joinToString(separator = "") { byte -> "%02x".format(byte) }
}
