package net.thunderbird.feature.taskmail.internal.data.facade

import assertk.assertThat
import assertk.assertions.isEqualTo
import com.fsck.k9.message.Attachment
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import net.thunderbird.feature.taskmail.internal.data.TaskMailReplyAttachmentResolver
import net.thunderbird.feature.taskmail.internal.data.controlplane.protocol.ControlPlaneCommandAck
import net.thunderbird.feature.taskmail.internal.data.relay.RelayFakeLogger
import net.thunderbird.feature.taskmail.internal.domain.model.RelayTransportConfig
import net.thunderbird.feature.taskmail.internal.domain.model.TaskReplyAttachment
import net.thunderbird.feature.taskmail.internal.domain.model.TaskSessionControlPlaneSnapshot
import net.thunderbird.feature.taskmail.internal.domain.repository.TaskTransportConfigRepository
import net.thunderbird.feature.taskmail.internal.domain.sessionaction.TaskMailDirectSessionActionRequest
import net.thunderbird.feature.taskmail.internal.domain.sessionaction.TaskMailDirectSessionActionResult
import net.thunderbird.feature.taskmail.internal.domain.sessionaction.TaskMailDirectSessionActionTarget
import net.thunderbird.feature.taskmail.internal.domain.sessionaction.TaskMailSessionActionAckStatus
import net.thunderbird.feature.taskmail.internal.domain.sessionaction.TaskMailSessionQuestionAnswer
import net.thunderbird.feature.taskmail.internal.domain.sessionaction.TaskMailSessionActionTargetIdentity
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer

class OkHttpTaskMailSessionActionFacadeSenderTest {
    private val server = MockWebServer()

    @AfterTest
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `send should post reply payload with android app token and parse accepted result`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """
                    {
                      "schema_version": "taskmail-android-session-action-facade-v1",
                      "status": "accepted",
                      "command_id": "cmd_001",
                      "submit_ack": {
                        "ack_status": "accepted",
                        "queue_position": 0
                      },
                      "target_session_identity": {
                        "pc_id": "pc_home",
                        "workspace_id": "workspace_android_app",
                        "session_id": "session_001",
                        "thread_id": "thread_001"
                      }
                    }
                """.trimIndent(),
            ),
        )
        server.start()
        val request = TaskMailDirectSessionActionRequest.Reply(
            target = sampleTarget(),
            replyText = "Please continue.",
        )
        val testSubject = createTestSubject(requestId = "req_test_001")

        val result = testSubject.send(request)

        val recordedRequest = server.takeRequest()
        val requestBody = Json.parseToJsonElement(recordedRequest.body.readUtf8()).jsonObject

        assertThat(recordedRequest.method).isEqualTo("POST")
        assertThat(recordedRequest.path).isEqualTo("/v1/android/session-action")
        assertThat(recordedRequest.getHeader("Authorization")).isEqualTo("Bearer android-app-token")
        assertThat(requestBody["request_id"]?.jsonPrimitive?.content).isEqualTo("req_test_001")
        assertThat(requestBody["action"]?.jsonPrimitive?.content).isEqualTo("reply")
        assertThat(requestBody["target"]?.jsonObject?.get("workspace_id")?.jsonPrimitive?.content)
            .isEqualTo("workspace_android_app")
        assertThat(requestBody["target"]?.jsonObject?.get("session_id")?.jsonPrimitive?.content)
            .isEqualTo("session_001")
        assertThat(requestBody["reply"]?.jsonObject?.get("reply_text")?.jsonPrimitive?.content)
            .isEqualTo("Please continue.")
        assertThat(result).isEqualTo(
            TaskMailDirectSessionActionResult.Accepted(
                actionType = request.actionType,
                requestId = "req_test_001",
                receiptId = "cmd_001",
                ackStatus = TaskMailSessionActionAckStatus.Accepted,
                targetIdentity = TaskMailSessionActionTargetIdentity(
                    pcId = "pc_home",
                    workspaceId = "workspace_android_app",
                    sessionId = "session_001",
                    threadId = "thread_001",
                ),
                controlPlaneSnapshot = TaskSessionControlPlaneSnapshot(
                    commandAck = ControlPlaneCommandAck(
                        commandId = "cmd_001",
                        ackStatus = "accepted",
                        queuePosition = 0,
                    ),
                ),
            ),
        )
    }

    @Test
    fun `send should allow session id only status target and parse accepted but queued result`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """
                    {
                      "schema_version": "taskmail-android-session-action-facade-v1",
                      "status": "accepted_but_queued",
                      "command_id": "cmd_010",
                      "submit_ack": {
                        "ack_status": "accepted_but_queued",
                        "queue_position": 3
                      },
                      "target_session_identity": {
                        "pc_id": "pc_home",
                        "workspace_id": "workspace_android_app",
                        "session_id": "session_001"
                      }
                    }
                """.trimIndent(),
            ),
        )
        server.start()
        val request = TaskMailDirectSessionActionRequest.Status(
            target = sampleTarget().copy(workspaceId = null, threadId = null),
        )
        val testSubject = createTestSubject(requestId = "req_test_010")

        val result = testSubject.send(request)

        val requestBody = Json.parseToJsonElement(server.takeRequest().body.readUtf8()).jsonObject
        val targetPayload = requestBody["target"]?.jsonObject ?: error("Missing target payload")

        assertThat(requestBody["action"]?.jsonPrimitive?.content).isEqualTo("status")
        assertThat(targetPayload.containsKey("workspace_id")).isEqualTo(false)
        assertThat(targetPayload["session_id"]?.jsonPrimitive?.content).isEqualTo("session_001")
        assertThat(requestBody["status"]?.jsonObject?.isEmpty()).isEqualTo(true)
        assertThat(result).isEqualTo(
            TaskMailDirectSessionActionResult.Accepted(
                actionType = request.actionType,
                requestId = "req_test_010",
                receiptId = "cmd_010",
                ackStatus = TaskMailSessionActionAckStatus.AcceptedButQueued,
                targetIdentity = TaskMailSessionActionTargetIdentity(
                    pcId = "pc_home",
                    workspaceId = "workspace_android_app",
                    sessionId = "session_001",
                ),
                controlPlaneSnapshot = TaskSessionControlPlaneSnapshot(
                    commandAck = ControlPlaneCommandAck(
                        commandId = "cmd_010",
                        ackStatus = "accepted_but_queued",
                        queuePosition = 3,
                    ),
                ),
            ),
        )
    }

    @Test
    fun `send should serialize canonical answers payload`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """
                    {
                      "schema_version": "taskmail-android-session-action-facade-v1",
                      "status": "accepted",
                      "command_id": "cmd_020",
                      "submit_ack": {
                        "ack_status": "accepted"
                      },
                      "target_session_identity": {
                        "workspace_id": "workspace_android_app",
                        "session_id": "session_001"
                      }
                    }
                """.trimIndent(),
            ),
        )
        server.start()
        val request = TaskMailDirectSessionActionRequest.Answers(
            target = sampleTarget(),
            questionAnswers = listOf(
                TaskMailSessionQuestionAnswer(
                    questionId = "phase2_entry_position",
                    value = "below",
                ),
                TaskMailSessionQuestionAnswer(
                    questionId = "phase2_icon_strings",
                    value = "provide",
                ),
            ),
        )
        val testSubject = createTestSubject(requestId = "req_test_020")

        testSubject.send(request)

        val requestBody = Json.parseToJsonElement(server.takeRequest().body.readUtf8()).jsonObject
        val questionAnswers = requestBody["answers"]
            ?.jsonObject
            ?.get("question_answers")
            ?.jsonArray
            ?: error("Missing answers payload")

        assertThat(requestBody["action"]?.jsonPrimitive?.content).isEqualTo("answers")
        assertThat(questionAnswers.size).isEqualTo(2)
        assertThat(questionAnswers[0].jsonObject["question_id"]?.jsonPrimitive?.content)
            .isEqualTo("phase2_entry_position")
        assertThat(questionAnswers[0].jsonObject["value"]?.jsonPrimitive?.content)
            .isEqualTo("below")
        assertThat(questionAnswers[1].jsonObject["question_id"]?.jsonPrimitive?.content)
            .isEqualTo("phase2_icon_strings")
        assertThat(questionAnswers[1].jsonObject["value"]?.jsonPrimitive?.content)
            .isEqualTo("provide")
    }

    @Test
    fun `send should serialize attachment continuation payload`() = runTest {
        val requestAttachment = TaskReplyAttachment(
            id = "content://taskmail/final-report",
            uriString = "content://taskmail/final-report",
            displayName = "final_report.md",
            contentType = "text/markdown",
            sizeBytes = 17L,
        )
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """
                    {
                      "schema_version": "taskmail-android-session-action-facade-v1",
                      "status": "accepted",
                      "command_id": "cmd_030",
                      "submit_ack": {
                        "ack_status": "accepted"
                      },
                      "target_session_identity": {
                        "workspace_id": "workspace_android_app",
                        "session_id": "session_001"
                      }
                    }
                """.trimIndent(),
            ),
        )
        server.start()
        val testSubject = createTestSubject(
            requestId = "req_test_030",
            attachmentResolver = SessionActionFakeReplyAttachmentResolver(
                preparedAttachments = listOf(
                    FakeAttachment(
                        fileName = createTempAttachmentFile("final_report.md", "final report body"),
                        contentType = "text/markdown",
                        name = "final_report.md",
                        size = 17L,
                    ),
                ),
            ),
        )

        testSubject.send(
            TaskMailDirectSessionActionRequest.AttachmentContinuation(
                target = sampleTarget(),
                replyText = "Please review the attached file.",
                attachments = listOf(requestAttachment),
            ),
        )

        val requestBody = Json.parseToJsonElement(server.takeRequest().body.readUtf8()).jsonObject
        val attachmentContinuation = requestBody["attachment_continuation"]?.jsonObject
            ?: error("Missing attachment_continuation payload")
        val attachmentPayload = attachmentContinuation["attachments"]?.jsonArray?.single()?.jsonObject
            ?: error("Missing attachment payload")

        assertThat(requestBody["action"]?.jsonPrimitive?.content).isEqualTo("attachment_continuation")
        assertThat(attachmentContinuation["reply_text"]?.jsonPrimitive?.content)
            .isEqualTo("Please review the attached file.")
        assertThat(attachmentPayload["name"]?.jsonPrimitive?.content).isEqualTo("final_report.md")
        assertThat(attachmentPayload["content_type"]?.jsonPrimitive?.content).isEqualTo("text/markdown")
        assertThat(attachmentPayload["size_bytes"]?.jsonPrimitive?.content?.toLong()).isEqualTo(17L)
        assertThat(attachmentPayload["content_bytes_b64"]?.jsonPrimitive?.content)
            .isEqualTo("ZmluYWwgcmVwb3J0IGJvZHk=")
    }

    @Test
    fun `send should map rejected facade response to rejected result`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """
                    {
                      "schema_version": "taskmail-android-session-action-facade-v1",
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
        val testSubject = createTestSubject(requestId = "req_test_002")

        val result = testSubject.send(
            TaskMailDirectSessionActionRequest.Status(
                target = sampleTarget(),
            ),
        )

        assertThat(result).isEqualTo(
            TaskMailDirectSessionActionResult.Rejected(
                errorMessage = "workspace_unavailable",
                requestId = "req_test_002",
                receiptId = "cmd_002",
            ),
        )
    }

    @Test
    fun `send should fail fast when android app token is missing`() = runTest {
        val testSubject = OkHttpTaskMailSessionActionFacadeSender(
            transportConfigRepository = SessionActionFakeTaskTransportConfigRepository(
                RelayTransportConfig(
                    host = "relay.example.org",
                    port = 8787,
                    useTls = false,
                ),
            ),
            logger = RelayFakeLogger(),
            replyAttachmentResolver = SessionActionFakeReplyAttachmentResolver(),
        )

        val result = testSubject.send(
            TaskMailDirectSessionActionRequest.Status(
                target = sampleTarget(),
            ),
        )

        assertThat(result).isEqualTo(
            TaskMailDirectSessionActionResult.Rejected(
                errorMessage = "Relay host, port, and Android app token are required.",
            ),
        )
    }

    private fun createTestSubject(
        requestId: String,
        attachmentResolver: TaskMailReplyAttachmentResolver = SessionActionFakeReplyAttachmentResolver(),
    ): OkHttpTaskMailSessionActionFacadeSender {
        val config = RelayTransportConfig(
            host = server.hostName,
            port = server.port,
            useTls = false,
            androidAppToken = "android-app-token",
        )
        return OkHttpTaskMailSessionActionFacadeSender(
            transportConfigRepository = SessionActionFakeTaskTransportConfigRepository(config),
            logger = RelayFakeLogger(),
            replyAttachmentResolver = attachmentResolver,
            requestIdFactory = { requestId },
        )
    }
}

private fun sampleTarget(): TaskMailDirectSessionActionTarget {
    return TaskMailDirectSessionActionTarget(
        workspaceId = "workspace_android_app",
        sessionId = "session_001",
        threadId = "thread_001",
    )
}

private class SessionActionFakeTaskTransportConfigRepository(
    private var config: RelayTransportConfig,
) : TaskTransportConfigRepository {
    override fun getRelayTransportConfig(): RelayTransportConfig = config

    override fun saveRelayTransportConfig(config: RelayTransportConfig): Boolean {
        this.config = config
        return true
    }
}

private class SessionActionFakeReplyAttachmentResolver(
    private val preparedAttachments: List<Attachment> = emptyList(),
) : TaskMailReplyAttachmentResolver {
    override suspend fun resolveSelectedAttachments(uriStrings: List<String>): List<TaskReplyAttachment> = emptyList()

    override suspend fun buildOutgoingAttachments(
        attachments: List<TaskReplyAttachment>,
    ): Result<List<Attachment>> = Result.success(preparedAttachments)
}

private data class FakeAttachment(
    override val fileName: String?,
    override val contentType: String?,
    override val name: String?,
    override val size: Long?,
    override val state: Attachment.LoadingState = Attachment.LoadingState.COMPLETE,
    override val isInternalAttachment: Boolean = false,
) : Attachment

private fun createTempAttachmentFile(
    name: String,
    content: String,
): String {
    val tempFile = kotlin.io.path.createTempFile(name.substringBeforeLast('.'), ".tmp").toFile()
    tempFile.writeText(content)
    tempFile.deleteOnExit()
    return tempFile.absolutePath
}
