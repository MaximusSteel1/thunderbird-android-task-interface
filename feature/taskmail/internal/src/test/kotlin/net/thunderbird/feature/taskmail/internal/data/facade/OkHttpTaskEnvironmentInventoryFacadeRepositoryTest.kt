package net.thunderbird.feature.taskmail.internal.data.facade

import assertk.assertThat
import assertk.assertions.isEqualTo
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlinx.coroutines.test.runTest
import net.thunderbird.feature.taskmail.internal.data.relay.RelayFakeLogger
import net.thunderbird.feature.taskmail.internal.domain.model.RelayTransportConfig
import net.thunderbird.feature.taskmail.internal.domain.repository.TaskTransportConfigRepository
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer

class OkHttpTaskEnvironmentInventoryFacadeRepositoryTest {
    private val server = MockWebServer()

    @AfterTest
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `getEnvironmentInventory should fetch Android-facing snapshot with android app token`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """
                    {
                      "schema_version": "taskmail-android-environment-inventory-facade-v1",
                      "snapshot_id": "env_snap_001",
                      "generated_at": "2026-03-27T09:40:00",
                      "inventory_state": "fresh",
                      "refresh_after_seconds": 15,
                      "pcs": [
                        {
                          "pc_id": "pc_home",
                          "display_name": "Home PC",
                          "status": "online",
                          "last_seen_at": "2026-03-27T09:39:58",
                          "workspace_inventory_state": "fresh",
                          "workspace_count": 1,
                          "pc_capabilities": {
                            "supported_backends": ["codex"],
                            "permission_modes": ["default"]
                          },
                          "route_admission": {
                            "allowed": true
                          },
                          "workspaces": [
                            {
                              "workspace_id": "workspace_android_app",
                              "pc_id": "pc_home",
                              "display_name": "Android app",
                              "repo_path": "E:/projects/android_task_manager",
                              "workdir": "feature/taskmail",
                              "presence": "present",
                              "effective_execution_capabilities": {
                                "supported_backends": ["codex"],
                                "permission_modes": ["default"]
                              },
                              "route_admission": {
                                "allowed": true
                              }
                            }
                          ]
                        }
                      ]
                    }
                """.trimIndent(),
            ),
        )
        server.start()
        val testSubject = OkHttpTaskEnvironmentInventoryFacadeRepository(
            transportConfigRepository = FakeEnvironmentInventoryTransportConfigRepository(
                RelayTransportConfig(
                    host = server.hostName,
                    port = server.port,
                    useTls = false,
                    androidAppToken = "android-app-token",
                ),
            ),
            logger = RelayFakeLogger(),
        )

        val result = testSubject.getEnvironmentInventory()

        val request = server.takeRequest()
        assertThat(request.method).isEqualTo("GET")
        assertThat(request.path).isEqualTo("/v1/android/environment-inventory")
        assertThat(request.getHeader("Authorization")).isEqualTo("Bearer android-app-token")
        assertThat(result.getOrThrow().pcs.single().workspaces.single().workspaceId).isEqualTo(
            "workspace_android_app",
        )
    }

    @Test
    fun `getEnvironmentInventory should fail fast when android app token is missing`() = runTest {
        val testSubject = OkHttpTaskEnvironmentInventoryFacadeRepository(
            transportConfigRepository = FakeEnvironmentInventoryTransportConfigRepository(
                RelayTransportConfig(
                    host = "relay.example.org",
                    port = 8787,
                    useTls = false,
                ),
            ),
            logger = RelayFakeLogger(),
        )

        val result = testSubject.getEnvironmentInventory()

        assertThat(result.exceptionOrNull()?.message).isEqualTo(
            "Relay host, port, and Android app token are required.",
        )
    }
}

private class FakeEnvironmentInventoryTransportConfigRepository(
    private var config: RelayTransportConfig,
) : TaskTransportConfigRepository {
    override fun getRelayTransportConfig(): RelayTransportConfig = config

    override fun saveRelayTransportConfig(config: RelayTransportConfig): Boolean {
        this.config = config
        return true
    }
}
