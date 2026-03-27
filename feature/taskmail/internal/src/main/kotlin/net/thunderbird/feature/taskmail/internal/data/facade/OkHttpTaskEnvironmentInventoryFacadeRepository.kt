package net.thunderbird.feature.taskmail.internal.data.facade

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
import net.thunderbird.feature.taskmail.internal.domain.model.TaskEnvironmentCapabilities
import net.thunderbird.feature.taskmail.internal.domain.model.TaskEnvironmentInventorySnapshot
import net.thunderbird.feature.taskmail.internal.domain.model.TaskEnvironmentPc
import net.thunderbird.feature.taskmail.internal.domain.model.TaskEnvironmentRouteAdmission
import net.thunderbird.feature.taskmail.internal.domain.model.TaskEnvironmentWorkspace
import net.thunderbird.feature.taskmail.internal.domain.repository.TaskEnvironmentInventoryRepository
import net.thunderbird.feature.taskmail.internal.domain.repository.TaskTransportConfigRepository
import okhttp3.OkHttpClient
import okhttp3.Request

private const val TAG = "TaskEnvironmentInventory"
private const val CONFIG_REQUIRED_MESSAGE = "Relay host, port, and Android app token are required."

internal class OkHttpTaskEnvironmentInventoryFacadeRepository(
    private val transportConfigRepository: TaskTransportConfigRepository,
    private val logger: Logger,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    okHttpClient: OkHttpClient? = null,
    private val json: Json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    },
) : TaskEnvironmentInventoryRepository {
    private val client = okHttpClient ?: OkHttpClient.Builder()
        .callTimeout(CALL_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .build()

    override suspend fun getEnvironmentInventory(): Result<TaskEnvironmentInventorySnapshot> {
        val config = transportConfigRepository.getRelayTransportConfig().normalized()
        if (!config.isAndroidEnvironmentInventoryConfigured()) {
            return Result.failure(IllegalStateException(CONFIG_REQUIRED_MESSAGE))
        }

        return withContext(ioDispatcher) {
            runCatching {
                executeRequest(config)
            }.onFailure { error ->
                logger.error(TAG, error) {
                    "Failed to fetch Android environment inventory."
                }
            }
        }
    }

    private fun executeRequest(config: RelayTransportConfig): TaskEnvironmentInventorySnapshot {
        val request = Request.Builder()
            .url(config.androidEnvironmentInventoryUrl())
            .header("Authorization", "Bearer ${config.androidAppToken}")
            .header("Accept", "application/json")
            .get()
            .build()

        client.newCall(request).execute().use { response ->
            val body = response.body.string()
            if (!response.isSuccessful) {
                val errorPayload = parseErrorPayload(body)
                error(errorPayload.toUserMessage(response.code))
            }

            return json.decodeFromString<EnvironmentInventoryResponsePayload>(body).toDomain()
        }
    }

    private fun parseErrorPayload(body: String): EnvironmentInventoryErrorPayload {
        return runCatching {
            json.decodeFromString<EnvironmentInventoryErrorPayload>(body)
        }.getOrElse {
            EnvironmentInventoryErrorPayload(
                errorMessage = body.trim().takeIf(String::isNotEmpty),
            )
        }
    }

    private fun EnvironmentInventoryResponsePayload.toDomain(): TaskEnvironmentInventorySnapshot {
        return TaskEnvironmentInventorySnapshot(
            snapshotId = snapshotId,
            generatedAt = generatedAt,
            inventoryState = inventoryState,
            refreshAfterSeconds = refreshAfterSeconds,
            pcs = pcs.map { payload -> payload.toDomainPc() },
        )
    }

    private fun EnvironmentInventoryPcPayload.toDomainPc(): TaskEnvironmentPc {
        return TaskEnvironmentPc(
            pcId = pcId,
            displayName = displayName,
            status = status,
            lastSeenAt = lastSeenAt,
            workspaceInventoryState = workspaceInventoryState,
            workspaceCount = workspaceCount,
            pcCapabilities = pcCapabilities.toDomain(),
            routeAdmission = routeAdmission.toDomain(),
            workspaces = workspaces.map { payload -> payload.toDomainWorkspace() },
        )
    }

    private fun EnvironmentInventoryWorkspacePayload.toDomainWorkspace(): TaskEnvironmentWorkspace {
        return TaskEnvironmentWorkspace(
            workspaceId = workspaceId,
            pcId = pcId,
            displayName = displayName,
            repoPath = repoPath,
            workdir = workdir,
            presence = presence,
            lastSnapshotAt = lastSnapshotAt,
            effectiveExecutionCapabilities = effectiveExecutionCapabilities.toDomain(),
            routeAdmission = routeAdmission.toDomain(),
        )
    }

    private fun EnvironmentInventoryCapabilitiesPayload.toDomain(): TaskEnvironmentCapabilities {
        return TaskEnvironmentCapabilities(
            supportedBackends = supportedBackends,
            profileCatalogs = profileCatalogs,
            permissionModes = permissionModes,
            backendTransportModes = backendTransportModes,
        )
    }

    private fun EnvironmentInventoryRouteAdmissionPayload.toDomain(): TaskEnvironmentRouteAdmission {
        return TaskEnvironmentRouteAdmission(
            allowed = allowed,
            reasonCode = reasonCode,
            reason = reason,
        )
    }

    private fun EnvironmentInventoryErrorPayload.toUserMessage(statusCode: Int): String {
        return errorMessage?.trim()
            ?.takeIf(String::isNotEmpty)
            ?: "Environment inventory request failed with HTTP $statusCode."
    }

    private companion object {
        const val CALL_TIMEOUT_SECONDS = 15L
    }
}

@Serializable
private data class EnvironmentInventoryResponsePayload(
    @SerialName("schema_version")
    val schemaVersion: String,
    @SerialName("snapshot_id")
    val snapshotId: String,
    @SerialName("generated_at")
    val generatedAt: String,
    @SerialName("inventory_state")
    val inventoryState: String,
    @SerialName("refresh_after_seconds")
    val refreshAfterSeconds: Int,
    val pcs: List<EnvironmentInventoryPcPayload> = emptyList(),
)

@Serializable
private data class EnvironmentInventoryPcPayload(
    @SerialName("pc_id")
    val pcId: String,
    @SerialName("display_name")
    val displayName: String,
    val status: String,
    @SerialName("last_seen_at")
    val lastSeenAt: String? = null,
    @SerialName("workspace_inventory_state")
    val workspaceInventoryState: String,
    @SerialName("workspace_count")
    val workspaceCount: Int,
    @SerialName("pc_capabilities")
    val pcCapabilities: EnvironmentInventoryCapabilitiesPayload = EnvironmentInventoryCapabilitiesPayload(),
    @SerialName("route_admission")
    val routeAdmission: EnvironmentInventoryRouteAdmissionPayload = EnvironmentInventoryRouteAdmissionPayload(),
    val workspaces: List<EnvironmentInventoryWorkspacePayload> = emptyList(),
)

@Serializable
private data class EnvironmentInventoryWorkspacePayload(
    @SerialName("workspace_id")
    val workspaceId: String,
    @SerialName("pc_id")
    val pcId: String,
    @SerialName("display_name")
    val displayName: String,
    @SerialName("repo_path")
    val repoPath: String,
    val workdir: String? = null,
    val presence: String,
    @SerialName("last_snapshot_at")
    val lastSnapshotAt: String? = null,
    @SerialName("effective_execution_capabilities")
    val effectiveExecutionCapabilities: EnvironmentInventoryCapabilitiesPayload =
        EnvironmentInventoryCapabilitiesPayload(),
    @SerialName("route_admission")
    val routeAdmission: EnvironmentInventoryRouteAdmissionPayload = EnvironmentInventoryRouteAdmissionPayload(),
)

@Serializable
private data class EnvironmentInventoryCapabilitiesPayload(
    @SerialName("supported_backends")
    val supportedBackends: List<String> = emptyList(),
    @SerialName("profile_catalogs")
    val profileCatalogs: Map<String, List<String>> = emptyMap(),
    @SerialName("permission_modes")
    val permissionModes: List<String> = emptyList(),
    @SerialName("backend_transport_modes")
    val backendTransportModes: Map<String, List<String>> = emptyMap(),
)

@Serializable
private data class EnvironmentInventoryRouteAdmissionPayload(
    val allowed: Boolean = false,
    @SerialName("reason_code")
    val reasonCode: String? = null,
    val reason: String? = null,
)

@Serializable
private data class EnvironmentInventoryErrorPayload(
    val status: String? = null,
    @SerialName("error_code")
    val errorCode: String? = null,
    @SerialName("error_message")
    val errorMessage: String? = null,
    val retryable: Boolean? = null,
)
