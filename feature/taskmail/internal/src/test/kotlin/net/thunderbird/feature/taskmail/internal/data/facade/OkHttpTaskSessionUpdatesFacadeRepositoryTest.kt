package net.thunderbird.feature.taskmail.internal.data.facade

import java.util.concurrent.TimeUnit
import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.isEqualTo
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import net.thunderbird.feature.taskmail.internal.data.relay.RelayFakeLogger
import net.thunderbird.feature.taskmail.internal.domain.model.RelayTransportConfig
import net.thunderbird.feature.taskmail.internal.domain.model.TaskMailSessionStatus
import net.thunderbird.feature.taskmail.internal.domain.model.TaskSessionHistorySnapshotLocator
import net.thunderbird.feature.taskmail.internal.domain.repository.TaskTransportConfigRepository
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer

class OkHttpTaskSessionUpdatesFacadeRepositoryTest {
    private val server = MockWebServer()
    private val clients = mutableListOf<OkHttpClient>()

    @AfterTest
    fun tearDown() {
        clients.forEach { client ->
            client.dispatcher.executorService.shutdownNow()
            client.connectionPool.evictAll()
        }
        server.shutdown()
    }

    @Test
    fun `observeSessionUpdates should connect to android session updates websocket and emit snapshot`() = runTest {
        server.enqueue(
            MockResponse().withWebSocketUpgrade(
                object : WebSocketListener() {
                    override fun onOpen(webSocket: WebSocket, response: Response) {
                        webSocket.send(
                            """
                            {
                              "schema_version": "taskmail-android-session-updates-v1",
                              "subscription_id": "android-session-updates:20260329_183100:abc123",
                              "message_type": "session_snapshot",
                              "sent_at": "2026-03-29T18:31:00Z",
                              "payload": {
                                "locator": {
                                  "workspace_id": "workspace_android_app",
                                  "session_id": "session_001",
                                  "thread_id": "thread_001"
                                },
                                "snapshot_id": "sess_snap_001",
                                "generated_at": "2026-03-29T18:31:00Z",
                                "session_snapshot": {
                                  "session_name": "say hi",
                                  "backend": "codex",
                                  "repo_path": "E:/projects/android_task_manager",
                                  "status": "done",
                                  "last_summary": "Hi.",
                                  "live_process": {
                                    "status": "streaming",
                                    "updated_at": "2026-03-29T18:31:30Z",
                                    "items": [
                                      {
                                        "item_id": "live_assistant_001",
                                        "kind": "assistant",
                                        "created_at": "2026-03-29T18:31:10Z",
                                        "updated_at": "2026-03-29T18:31:30Z",
                                        "status": "streaming",
                                        "text": "Streaming assistant output."
                                      }
                                    ]
                                  },
                                  "timeline_items": [],
                                  "history_rounds": []
                                }
                              }
                            }
                            """.trimIndent(),
                        )
                        webSocket.close(1000, "snapshot_sent")
                    }
                },
            ),
        )
        server.start()
        val okHttpClient = OkHttpClient.Builder().build()
        clients += okHttpClient
        val testSubject = OkHttpTaskSessionUpdatesFacadeRepository(
            transportConfigRepository = FakeSessionUpdatesTransportConfigRepository(
                RelayTransportConfig(
                    host = server.hostName,
                    port = server.port,
                    useTls = false,
                    androidAppToken = "android-app-token",
                ),
            ),
            logger = RelayFakeLogger(),
            okHttpClient = okHttpClient,
        )

        val snapshot = async(start = CoroutineStart.UNDISPATCHED) {
            withContext(Dispatchers.Default.limitedParallelism(1)) {
                withTimeout(5_000) {
                    testSubject.observeSessionUpdates(
                        TaskSessionHistorySnapshotLocator(
                            workspaceId = "workspace_android_app",
                            sessionId = "session_001",
                            repoPath = "E:/projects/android_task_manager",
                            workdir = "feature/taskmail",
                        ),
                    ).first()
                }
            }
        }

        val request = requireNotNull(server.takeRequest(5, TimeUnit.SECONDS))
        assertThat(request.requestUrl?.encodedPath).isEqualTo("/v1/android/session-updates")
        assertThat(request.getHeader("Authorization")).isEqualTo("Bearer android-app-token")
        assertThat(request.requestUrl?.queryParameter("workspace_id")).isEqualTo("workspace_android_app")
        assertThat(request.requestUrl?.queryParameter("session_id")).isEqualTo("session_001")
        assertThat(request.requestUrl?.queryParameter("repo_path")).isEqualTo("E:/projects/android_task_manager")
        assertThat(request.requestUrl?.queryParameter("workdir")).isEqualTo("feature/taskmail")
        val receivedSnapshot = snapshot.await()
        assertThat(receivedSnapshot.sessionHeader?.status).isEqualTo(TaskMailSessionStatus.Done)
        assertThat(receivedSnapshot.sessionHeader?.lastSummary).isEqualTo("Hi.")
        assertThat(receivedSnapshot.sessionHeader?.liveProcess?.items?.single()?.text).isEqualTo("Streaming assistant output.")
        assertThat(receivedSnapshot.sessionHeader?.liveProcess?.updatedAt).isEqualTo("2026-03-29T18:31:30Z")
    }

    @Test
    fun `observeSessionUpdates should surface facade error envelope`() = runTest {
        server.enqueue(
            MockResponse().withWebSocketUpgrade(
                object : WebSocketListener() {
                    override fun onOpen(webSocket: WebSocket, response: Response) {
                        webSocket.send(
                            """
                            {
                              "schema_version": "taskmail-android-session-updates-v1",
                              "subscription_id": "android-session-updates:20260329_183101:def456",
                              "message_type": "error",
                              "sent_at": "2026-03-29T18:31:01Z",
                              "payload": {
                                "status": "error",
                                "error_code": "session_not_found",
                                "error_message": "Session snapshot is unavailable.",
                                "retryable": true
                              }
                            }
                            """.trimIndent(),
                        )
                        webSocket.close(1008, "session_not_found")
                    }
                },
            ),
        )
        server.start()
        val okHttpClient = OkHttpClient.Builder().build()
        clients += okHttpClient
        val testSubject = OkHttpTaskSessionUpdatesFacadeRepository(
            transportConfigRepository = FakeSessionUpdatesTransportConfigRepository(
                RelayTransportConfig(
                    host = server.hostName,
                    port = server.port,
                    useTls = false,
                    androidAppToken = "android-app-token",
                ),
            ),
            logger = RelayFakeLogger(),
            okHttpClient = okHttpClient,
        )

        val result = runCatching {
            withContext(Dispatchers.Default.limitedParallelism(1)) {
                withTimeout(5_000) {
                    testSubject.observeSessionUpdates(
                        TaskSessionHistorySnapshotLocator(
                            workspaceId = "workspace_android_app",
                            sessionId = "session_missing",
                        ),
                    ).first()
                }
            }
        }

        assertThat(result.exceptionOrNull()?.message ?: "").contains("Session snapshot is unavailable.")
    }
}

private class FakeSessionUpdatesTransportConfigRepository(
    private var config: RelayTransportConfig,
) : TaskTransportConfigRepository {
    override fun getRelayTransportConfig(): RelayTransportConfig = config

    override fun saveRelayTransportConfig(config: RelayTransportConfig): Boolean {
        this.config = config
        return true
    }
}
