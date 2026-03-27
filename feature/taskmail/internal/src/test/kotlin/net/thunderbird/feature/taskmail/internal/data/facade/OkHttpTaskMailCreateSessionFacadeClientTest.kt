package net.thunderbird.feature.taskmail.internal.data.facade

import com.fsck.k9.message.Attachment
import assertk.assertThat
import assertk.assertions.isEqualTo
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import net.thunderbird.feature.taskmail.internal.data.TaskMailReplyAttachmentResolver
import net.thunderbird.feature.taskmail.internal.data.controlplane.protocol.ControlPlaneExecutionPolicy
import net.thunderbird.feature.taskmail.internal.data.relay.RelayFakeLogger
import net.thunderbird.feature.taskmail.internal.domain.model.RelayTransportConfig
import net.thunderbird.feature.taskmail.internal.domain.model.TaskMailBackend
import net.thunderbird.feature.taskmail.internal.domain.model.TaskReplyAttachment
import net.thunderbird.feature.taskmail.internal.domain.newtask.TaskMailCreateSessionAckStatus
import net.thunderbird.feature.taskmail.internal.domain.newtask.TaskMailCreateSessionBinding
import net.thunderbird.feature.taskmail.internal.domain.newtask.TaskMailCreateSessionResult
import net.thunderbird.feature.taskmail.internal.domain.newtask.TaskMailCreateSessionSubmitAck
import net.thunderbird.feature.taskmail.internal.domain.newtask.TaskMailNewTaskDraft
import net.thunderbird.feature.taskmail.internal.domain.newtask.TaskMailNewTaskMode
import net.thunderbird.feature.taskmail.internal.domain.newtask.TaskMailNewTaskPermission
import net.thunderbird.feature.taskmail.internal.domain.repository.TaskTransportConfigRepository
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer

class OkHttpTaskMailCreateSessionFacadeClientTest {
    private val server = MockWebServer()

    @AfterTest
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `createSession should post facade payload with android app token and parse submitted result`() =
        runTest {
            server.enqueue(
                MockResponse().setResponseCode(200).setBody(
                    """
                        {
                          "schema_version": "taskmail-android-create-session-facade-v1",
                          "status": "accepted",
                          "command_id": "cmd_001",
                          "submit_ack": {
                            "ack_status": "accepted"
                          },
                          "session_binding": {
                            "session_id": "sess_001",
                            "pc_id": "pc_home",
                            "workspace_id": "workspace_android_app"
                          }
                        }
                    """.trimIndent(),
                ),
            )
            server.start()
            val config = RelayTransportConfig(
                host = server.hostName,
                port = server.port,
                useTls = false,
                androidAppToken = "android-app-token",
            )
            val testSubject = OkHttpTaskMailCreateSessionFacadeClient(
                transportConfigRepository = FakeTaskTransportConfigRepository(config),
                logger = RelayFakeLogger(),
                replyAttachmentResolver = FakeTaskMailReplyAttachmentResolver(),
            )

            val result = testSubject.createSession(
                draft = testDraft(),
            )

            val request = server.takeRequest()
            val requestBody = Json.parseToJsonElement(request.body.readUtf8()).jsonObject

            assertThat(request.method).isEqualTo("POST")
            assertThat(request.path).isEqualTo("/v1/android/create-session")
            assertThat(request.getHeader("Authorization")).isEqualTo("Bearer android-app-token")
            assertThat(requestBody["pc_id"]?.jsonPrimitive?.content).isEqualTo("pc_home")
            assertThat(requestBody["workspace_id"]?.jsonPrimitive?.content).isEqualTo("workspace_android_app")
            assertThat(requestBody["prompt"]?.jsonPrimitive?.content).isEqualTo("Audit the create-session path")
            assertThat(
                requestBody["execution_policy"]
                    ?.jsonObject
                    ?.get("backend")
                    ?.jsonPrimitive
                    ?.content,
            ).isEqualTo("codex")
            assertThat(
                requestBody["acceptance"]
                    ?.jsonArray
                    ?.map { it.jsonPrimitive.content },
            ).isEqualTo(listOf("List only blockers.", "Do not touch unrelated files."))
            assertThat(result).isEqualTo(
                TaskMailCreateSessionResult.Submitted(
                    commandId = "cmd_001",
                    submitAck = TaskMailCreateSessionSubmitAck(
                        ackStatus = TaskMailCreateSessionAckStatus.Accepted,
                    ),
                    sessionBinding = TaskMailCreateSessionBinding(
                        sessionId = "sess_001",
                        pcId = "pc_home",
                        workspaceId = "workspace_android_app",
                    ),
                ),
            )
        }

    @Test
    fun `createSession should map rejected facade response to rejected result`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """
                    {
                      "schema_version": "taskmail-android-create-session-facade-v1",
                      "status": "rejected",
                      "command_id": "cmd_002",
                      "submit_ack": {
                        "ack_status": "rejected",
                        "reason": "workspace_unavailable",
                        "error_code": "workspace_unavailable"
                      }
                    }
                """.trimIndent(),
            ),
        )
        server.start()
        val config = RelayTransportConfig(
            host = server.hostName,
            port = server.port,
            useTls = false,
            androidAppToken = "android-app-token",
        )
        val testSubject = OkHttpTaskMailCreateSessionFacadeClient(
            transportConfigRepository = FakeTaskTransportConfigRepository(config),
            logger = RelayFakeLogger(),
            replyAttachmentResolver = FakeTaskMailReplyAttachmentResolver(),
        )

        val result = testSubject.createSession(testDraft())

        assertThat(result).isEqualTo(
            TaskMailCreateSessionResult.Rejected(
                commandId = "cmd_002",
                submitAck = TaskMailCreateSessionSubmitAck(
                    ackStatus = TaskMailCreateSessionAckStatus.Rejected,
                    reason = "workspace_unavailable",
                    errorCode = "workspace_unavailable",
                ),
                errorMessage = "workspace_unavailable",
            ),
        )
    }

    @Test
    fun `createSession should fail fast when android app token is missing`() = runTest {
        val testSubject = OkHttpTaskMailCreateSessionFacadeClient(
            transportConfigRepository = FakeTaskTransportConfigRepository(
                RelayTransportConfig(
                    host = "relay.example.org",
                    port = 8787,
                    useTls = false,
                ),
            ),
            logger = RelayFakeLogger(),
            replyAttachmentResolver = FakeTaskMailReplyAttachmentResolver(),
        )

        val result = testSubject.createSession(testDraft())

        assertThat(result).isEqualTo(
            TaskMailCreateSessionResult.Failed(
                errorMessage = "Relay host, port, and Android app token are required.",
            ),
        )
    }

    @Test
    fun `createSession should include input attachments when draft provides them`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """
                    {
                      "schema_version": "taskmail-android-create-session-facade-v1",
                      "status": "accepted",
                      "command_id": "cmd_attachments",
                      "submit_ack": {
                        "ack_status": "accepted"
                      },
                      "session_binding": {
                        "session_id": "sess_attachments",
                        "pc_id": "pc_home",
                        "workspace_id": "workspace_android_app"
                      }
                    }
                """.trimIndent(),
            ),
        )
        server.start()
        val config = RelayTransportConfig(
            host = server.hostName,
            port = server.port,
            useTls = false,
            androidAppToken = "android-app-token",
        )
        val preparedFile = kotlin.io.path.createTempFile(
            prefix = "taskmail_create_session_",
            suffix = ".png",
        ).toFile()
        preparedFile.writeText("image-binary", Charsets.UTF_8)
        val testSubject = OkHttpTaskMailCreateSessionFacadeClient(
            transportConfigRepository = FakeTaskTransportConfigRepository(config),
            logger = RelayFakeLogger(),
            replyAttachmentResolver = FakeTaskMailReplyAttachmentResolver(
                outgoingAttachments = listOf(
                    FakePreparedAttachment(
                        fileName = preparedFile.absolutePath,
                        contentType = "image/png",
                        name = "wireframe.png",
                        size = preparedFile.length(),
                    ),
                ),
            ),
        )

        testSubject.createSession(
            draft = testDraft(
                attachments = listOf(
                    TaskReplyAttachment(
                        id = "content://taskmail/wireframe",
                        uriString = "content://taskmail/wireframe",
                        displayName = "wireframe.png",
                        contentType = "image/png",
                        sizeBytes = preparedFile.length(),
                        isImage = true,
                    ),
                ),
            ),
        )

        val request = server.takeRequest()
        val requestBody = Json.parseToJsonElement(request.body.readUtf8()).jsonObject
        val attachments = requestBody["attachments"]?.jsonArray
        val attachment = attachments?.single()?.jsonObject

        assertThat(attachments?.size).isEqualTo(1)
        assertThat(attachment?.get("name")?.jsonPrimitive?.content)
            .isEqualTo("wireframe.png")
        assertThat(attachment?.get("content_type")?.jsonPrimitive?.content)
            .isEqualTo("image/png")
        assertThat(attachment?.get("size_bytes")?.jsonPrimitive?.content?.toLong())
            .isEqualTo(preparedFile.length())
        assertThat(
            attachment?.get("content_bytes_b64")?.jsonPrimitive?.content
                .isNullOrBlank(),
        ).isEqualTo(false)

        preparedFile.delete()
    }
}

private fun testDraft(
    attachments: List<TaskReplyAttachment> = emptyList(),
): TaskMailNewTaskDraft {
    return TaskMailNewTaskDraft(
        senderAccountId = "account_primary",
        backend = TaskMailBackend.Codex,
        repoPath = "E:/projects/android_task_manager",
        taskText = "Audit the create-session path",
        subjectTitle = "Audit the create-session path",
        workdir = "feature/taskmail",
        mode = TaskMailNewTaskMode.Modify,
        timeoutMinutes = 15,
        permission = TaskMailNewTaskPermission.Default,
        profile = "gpt-5.4",
        acceptanceCriteria = listOf(
            "List only blockers.",
            "Do not touch unrelated files.",
        ),
        attachments = attachments,
        pcId = "pc_home",
        workspaceId = "workspace_android_app",
        executionPolicy = ControlPlaneExecutionPolicy(
            backend = "codex",
            profile = "gpt-5.4",
            permission = "default",
            backendTransport = "vps",
        ),
    )
}

private class FakeTaskMailReplyAttachmentResolver(
    private val outgoingAttachments: List<Attachment> = emptyList(),
) : TaskMailReplyAttachmentResolver {
    override suspend fun resolveSelectedAttachments(uriStrings: List<String>): List<TaskReplyAttachment> = emptyList()

    override suspend fun buildOutgoingAttachments(
        attachments: List<TaskReplyAttachment>,
    ): Result<List<Attachment>> {
        return Result.success(outgoingAttachments)
    }
}

private data class FakePreparedAttachment(
    override val fileName: String?,
    override val contentType: String?,
    override val name: String?,
    override val size: Long?,
    override val state: Attachment.LoadingState = Attachment.LoadingState.COMPLETE,
    override val isInternalAttachment: Boolean = false,
) : Attachment

private class FakeTaskTransportConfigRepository(
    private var config: RelayTransportConfig,
) : TaskTransportConfigRepository {
    override fun getRelayTransportConfig(): RelayTransportConfig = config

    override fun saveRelayTransportConfig(config: RelayTransportConfig): Boolean {
        this.config = config
        return true
    }
}
