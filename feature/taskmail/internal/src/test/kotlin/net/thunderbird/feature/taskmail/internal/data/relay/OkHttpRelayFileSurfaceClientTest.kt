package net.thunderbird.feature.taskmail.internal.data.relay

import assertk.assertThat
import assertk.assertions.isEqualTo
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlinx.coroutines.test.runTest
import net.thunderbird.feature.taskmail.internal.domain.model.RelayTransportConfig
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer

class OkHttpRelayFileSurfaceClientTest {
    private val server = MockWebServer()

    @AfterTest
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `upload metadata and content should use bearer auth and parse file surface responses`() = runTest {
        val sampleContent = "taskmail-file-sample".encodeToByteArray()
        val uploadResponse = """
            {
              "schema_version": "taskmail-control-artifact-contract-v1",
              "file_id": "file_test",
              "stored_at": "2026-03-24T12:00:00Z",
              "artifact": {
                "artifact_id": "artifact_test",
                "file_id": "file_test",
                "name": "taskmail-file-sample-test.txt",
                "kind": "file",
                "role": "attachment",
                "mime_type": "text/plain; charset=utf-8",
                "byte_size": 20,
                "sha256": "sample_sha256",
                "metadata_url": "/v1/files/file_test",
                "download_url": "/v1/files/file_test/content"
              }
            }
        """.trimIndent()

        server.enqueue(MockResponse().setResponseCode(200).setBody(uploadResponse))
        server.enqueue(MockResponse().setResponseCode(200).setBody(uploadResponse))
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(sampleContent.toString(Charsets.UTF_8))
                .addHeader("Content-Type", "text/plain; charset=utf-8")
                .addHeader("ETag", "sample_sha256"),
        )
        server.start()

        val config = RelayTransportConfig(
            host = server.hostName,
            port = server.port,
            useTls = false,
            transportToken = "secret-token",
        )
        val testSubject = OkHttpRelayFileSurfaceClient(
            logger = RelayFakeLogger(),
        )

        val uploadResult = testSubject.upload(
            config = config,
            request = RelayFileSurfaceUploadRequest(
                metadata = RelayFileUploadMetadata(
                    artifactId = "artifact_test",
                    name = "taskmail-file-sample-test.txt",
                    kind = "file",
                    role = "attachment",
                    mimeType = "text/plain; charset=utf-8",
                    byteSize = sampleContent.size.toLong(),
                    sha256 = "sample_sha256",
                    trace = RelayFileUploadTrace(
                        traceId = "trace_file_sample_test",
                        probeId = "file_sample_test",
                    ),
                ),
                bytes = sampleContent,
            ),
        )
        val metadataResult = testSubject.getMetadata(
            config = config,
            metadataUrl = "/v1/files/file_test",
        )
        val downloadResult = testSubject.downloadContent(
            config = config,
            downloadUrl = "/v1/files/file_test/content",
        )

        val uploadRequest = server.takeRequest()
        val uploadBody = uploadRequest.body.readUtf8()
        val metadataRequest = server.takeRequest()
        val downloadRequest = server.takeRequest()

        assertThat(uploadRequest.method).isEqualTo("POST")
        assertThat(uploadRequest.path).isEqualTo("/v1/files")
        assertThat(uploadRequest.getHeader("Authorization")).isEqualTo("Bearer secret-token")
        assertThat(uploadBody.contains("name=\"metadata\"")).isEqualTo(true)
        assertThat(uploadBody.contains("\"artifact_id\":\"artifact_test\"")).isEqualTo(true)
        assertThat(uploadBody.contains("taskmail-file-sample-test.txt")).isEqualTo(true)
        assertThat(metadataRequest.method).isEqualTo("GET")
        assertThat(metadataRequest.path).isEqualTo("/v1/files/file_test")
        assertThat(downloadRequest.method).isEqualTo("GET")
        assertThat(downloadRequest.path).isEqualTo("/v1/files/file_test/content")
        assertThat(uploadResult.fileId).isEqualTo("file_test")
        assertThat(metadataResult.artifact.metadataUrl).isEqualTo("/v1/files/file_test")
        assertThat(downloadResult.bytes.toString(Charsets.UTF_8)).isEqualTo("taskmail-file-sample")
        assertThat(downloadResult.etag).isEqualTo("sample_sha256")
    }
}
