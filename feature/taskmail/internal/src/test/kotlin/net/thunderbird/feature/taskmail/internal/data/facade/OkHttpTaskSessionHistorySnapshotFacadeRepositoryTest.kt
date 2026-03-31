package net.thunderbird.feature.taskmail.internal.data.facade

import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.isEqualTo
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlinx.coroutines.test.runTest
import net.thunderbird.feature.taskmail.internal.data.relay.RelayFakeLogger
import net.thunderbird.feature.taskmail.internal.domain.model.RelayTransportConfig
import net.thunderbird.feature.taskmail.internal.domain.model.TaskMailSessionStatus
import net.thunderbird.feature.taskmail.internal.domain.model.TaskSessionHistorySnapshotLocator
import net.thunderbird.feature.taskmail.internal.domain.repository.TaskTransportConfigRepository
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer

class OkHttpTaskSessionHistorySnapshotFacadeRepositoryTest {
    private val server = MockWebServer()

    @AfterTest
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `getHistorySnapshot should fetch Android-facing session snapshot with query locator`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """
                    {
                      "locator": {
                        "workspace_id": "workspace_android_app",
                        "session_id": "session_001",
                        "thread_id": "thread_001"
                      },
                      "snapshot_id": "sess_snap_001",
                      "generated_at": "2026-03-27T12:00:00",
                      "session_snapshot": {
                        "session_name": "say hi",
                        "backend": "codex",
                        "repo_path": "E:/projects/android_task_manager",
                        "status": "done",
                        "last_summary": "Hi.",
                        "live_process": {
                          "status": "streaming",
                          "updated_at": "2026-03-27T12:00:30Z",
                          "items": [
                            {
                              "item_id": "live_assistant_001",
                              "kind": "assistant",
                              "created_at": "2026-03-27T12:00:10Z",
                              "updated_at": "2026-03-27T12:00:30Z",
                              "status": "streaming",
                              "text": "Streaming assistant output."
                            }
                          ]
                        },
                        "timeline_items": [
                          {
                            "item_id": "tl_terminal_done_001",
                            "business_event_key": "terminal/done/2026-03-27T12:00:00",
                            "item_type": "terminal_summary",
                            "created_at": "2026-03-27T12:00:00",
                            "status": "done",
                            "text": "Hi."
                          }
                        ],
                        "history_rounds": [
                          {
                            "round_id": "hist_round_task_002",
                            "round_number": 2,
                            "created_at": "2026-03-27T12:00:00",
                            "status": "running",
                            "speaker_label": "Codex",
                            "input": {
                              "text": "Review the latest homepage sketch and keep the task tree.",
                              "attachments": [
                                {
                                  "attachment_id": "hist_input_task_002_1",
                                  "display_name": "homepage-followup.md",
                                  "content_type": "text/markdown",
                                  "size_bytes": 18,
                                  "is_image": false
                                }
                              ]
                            },
                            "process": {
                              "items": [
                                {
                                  "item_id": "hist_process_task_002_running",
                                  "kind": "assistant",
                                  "created_at": "2026-03-27T12:00:00",
                                  "updated_at": "2026-03-27T12:00:05",
                                  "status": "running",
                                  "text": "Still processing the latest homepage follow-up."
                                }
                              ]
                            },
                            "result": {
                              "text": "Still processing the latest homepage follow-up.",
                              "attachments": [
                                {
                                  "attachment_id": "hist_result_task_002_1",
                                  "display_name": "homepage-followup.png",
                                  "content_type": "image/png",
                                  "size_bytes": 512,
                                  "is_image": true,
                                  "download_ref": {
                                    "kind": "vps_file",
                                    "file_id": "file_history_001",
                                    "metadata_url": "/v1/files/file_history_001",
                                    "content_url": "/v1/files/file_history_001/content"
                                  }
                                }
                              ]
                            }
                          }
                        ]
                      }
                    }
                """.trimIndent(),
            ),
        )
        server.start()
        val testSubject = OkHttpTaskSessionHistorySnapshotFacadeRepository(
            transportConfigRepository = FakeSessionSnapshotTransportConfigRepository(
                RelayTransportConfig(
                    host = server.hostName,
                    port = server.port,
                    useTls = false,
                    androidAppToken = "android-app-token",
                ),
            ),
            logger = RelayFakeLogger(),
        )

        val result = testSubject.getHistorySnapshot(
            TaskSessionHistorySnapshotLocator(
                workspaceId = "workspace_android_app",
                sessionId = "session_001",
                repoPath = "E:/projects/android_task_manager",
                workdir = "feature/taskmail",
            ),
        )

        val request = server.takeRequest()
        assertThat(request.method).isEqualTo("GET")
        assertThat(request.requestUrl?.encodedPath).isEqualTo("/v1/android/session-snapshot")
        assertThat(request.getHeader("Authorization")).isEqualTo("Bearer android-app-token")
        assertThat(request.requestUrl?.queryParameter("workspace_id")).isEqualTo("workspace_android_app")
        assertThat(request.requestUrl?.queryParameter("session_id")).isEqualTo("session_001")
        assertThat(request.requestUrl?.queryParameter("repo_path")).isEqualTo("E:/projects/android_task_manager")
        assertThat(request.requestUrl?.queryParameter("workdir")).isEqualTo("feature/taskmail")
        val snapshot = result.getOrThrow()
        assertThat(snapshot.rounds.single().inputAttachments.single().displayName)
            .isEqualTo("homepage-followup.md")
        assertThat(snapshot.rounds.single().resultAttachments.single().actionTarget?.relayArtifact?.fileId)
            .isEqualTo("file_history_001")
        assertThat(snapshot.sessionHeader?.threadId).isEqualTo("thread_001")
        assertThat(snapshot.sessionHeader?.status).isEqualTo(TaskMailSessionStatus.Done)
        assertThat(snapshot.sessionHeader?.lastSummary).isEqualTo("Hi.")
        assertThat(snapshot.sessionHeader?.liveProcess?.items?.single()?.text).isEqualTo("Streaming assistant output.")
        assertThat(snapshot.sessionHeader?.liveProcess?.status).isEqualTo("streaming")
    }

    @Test
    fun `getHistorySnapshot should fail fast when android app token is missing`() = runTest {
        val testSubject = OkHttpTaskSessionHistorySnapshotFacadeRepository(
            transportConfigRepository = FakeSessionSnapshotTransportConfigRepository(
                RelayTransportConfig(
                    host = "relay.example.org",
                    port = 8787,
                    useTls = false,
                ),
            ),
            logger = RelayFakeLogger(),
        )

        val result = testSubject.getHistorySnapshot(
            TaskSessionHistorySnapshotLocator(
                sessionId = "session_001",
                workspaceId = "workspace_android_app",
            ),
        )

        assertThat(result.exceptionOrNull()?.message).isEqualTo(
            "Relay host, port, and Android app token are required.",
        )
    }

    @Test
    fun `getHistorySnapshot should surface facade error payload`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(404).setBody(
                """
                    {
                      "status": "error",
                      "error_code": "session_not_found",
                      "error_message": "Session snapshot is unavailable."
                    }
                """.trimIndent(),
            ),
        )
        server.start()
        val testSubject = OkHttpTaskSessionHistorySnapshotFacadeRepository(
            transportConfigRepository = FakeSessionSnapshotTransportConfigRepository(
                RelayTransportConfig(
                    host = server.hostName,
                    port = server.port,
                    useTls = false,
                    androidAppToken = "android-app-token",
                ),
            ),
            logger = RelayFakeLogger(),
        )

        val result = testSubject.getHistorySnapshot(
            TaskSessionHistorySnapshotLocator(
                workspaceId = "workspace_android_app",
                sessionId = "session_missing",
            ),
        )

        assertThat(result.exceptionOrNull()?.message ?: "").contains("Session snapshot is unavailable.")
    }
}

private class FakeSessionSnapshotTransportConfigRepository(
    private var config: RelayTransportConfig,
) : TaskTransportConfigRepository {
    override fun getRelayTransportConfig(): RelayTransportConfig = config

    override fun saveRelayTransportConfig(config: RelayTransportConfig): Boolean {
        this.config = config
        return true
    }
}
