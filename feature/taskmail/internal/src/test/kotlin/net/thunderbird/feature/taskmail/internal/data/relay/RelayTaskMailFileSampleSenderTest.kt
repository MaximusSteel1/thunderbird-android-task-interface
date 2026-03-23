package net.thunderbird.feature.taskmail.internal.data.relay

import assertk.assertThat
import assertk.assertions.isEqualTo
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import net.thunderbird.feature.taskmail.internal.data.debug.FileBackedTaskMailFileSampleStore
import net.thunderbird.feature.taskmail.internal.domain.filesample.TaskMailFileSampleRequest
import net.thunderbird.feature.taskmail.internal.domain.filesample.TaskMailFileSampleStatus
import net.thunderbird.feature.taskmail.internal.domain.model.RelayTransportConfig

class RelayTaskMailFileSampleSenderTest {
    private var tempDirectory: File? = null

    @AfterTest
    fun tearDown() {
        tempDirectory?.deleteRecursively()
    }

    @Test
    fun `send should upload download and persist file sample artifacts`() = runTest {
        val storageDirectory = Files.createTempDirectory("taskmail-file-sample-test").toFile()
        tempDirectory = storageDirectory
        val fakeClient = FakeRelayFileSurfaceClient()
        val testSubject = RelayTaskMailFileSampleSender(
            fileSurfaceClient = fakeClient,
            store = FileBackedTaskMailFileSampleStore(
                storageDirectory = storageDirectory,
                logger = RelayFakeLogger(),
            ),
            ioDispatcher = StandardTestDispatcher(testScheduler),
        )

        val result = testSubject.send(
            config = RelayTransportConfig(
                host = "relay.example.org",
                port = 8787,
                useTls = false,
                transportToken = "secret-token",
            ),
            request = TaskMailFileSampleRequest(
                sampleId = "file_sample_test",
                payloadText = "TaskMail file sample text",
            ),
        )

        val sampleDirectory = File(storageDirectory, "file_sample_test")

        assertThat(result.status).isEqualTo(TaskMailFileSampleStatus.Completed)
        assertThat(result.fileId).isEqualTo("file_test")
        assertThat(sampleDirectory.exists()).isEqualTo(true)
        assertThat(File(sampleDirectory, "request_metadata.json").exists()).isEqualTo(true)
        assertThat(
            File(sampleDirectory, "downloaded_taskmail-file-sample-file_sample_test.txt").exists(),
        ).isEqualTo(true)
        assertThat(File(sampleDirectory, "manifest.json").readText().contains("\"status\":\"completed\""))
            .isEqualTo(true)
    }
}

private class FakeRelayFileSurfaceClient : RelayFileSurfaceClient {
    private var lastUploadRequest: RelayFileSurfaceUploadRequest? = null

    override suspend fun upload(
        config: RelayTransportConfig,
        request: RelayFileSurfaceUploadRequest,
    ): RelayFileSurfaceEnvelope {
        lastUploadRequest = request
        return buildEnvelope(request)
    }

    override suspend fun getMetadata(
        config: RelayTransportConfig,
        metadataUrl: String,
    ): RelayFileSurfaceEnvelope {
        return buildEnvelope(requireNotNull(lastUploadRequest))
    }

    override suspend fun downloadContent(
        config: RelayTransportConfig,
        downloadUrl: String,
    ): RelayDownloadedFileContent {
        val request = requireNotNull(lastUploadRequest)
        return RelayDownloadedFileContent(
            bytes = request.bytes,
            contentType = request.metadata.mimeType,
            etag = request.metadata.sha256,
        )
    }

    private fun buildEnvelope(
        request: RelayFileSurfaceUploadRequest,
    ): RelayFileSurfaceEnvelope {
        return RelayFileSurfaceEnvelope(
            schemaVersion = "taskmail-control-artifact-contract-v1",
            fileId = "file_test",
            storedAt = "2026-03-24T12:00:00Z",
            artifact = RelayFileSurfaceArtifactDescriptor(
                artifactId = request.metadata.artifactId,
                fileId = "file_test",
                name = request.metadata.name,
                kind = request.metadata.kind,
                role = request.metadata.role,
                mimeType = request.metadata.mimeType,
                byteSize = request.metadata.byteSize,
                sha256 = request.metadata.sha256,
                metadataUrl = "/v1/files/file_test",
                downloadUrl = "/v1/files/file_test/content",
            ),
        )
    }
}
