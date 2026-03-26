package net.thunderbird.feature.taskmail.internal.domain.usecase

import kotlinx.coroutines.channels.ProducerScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch
import net.thunderbird.core.logging.Logger
import net.thunderbird.feature.taskmail.internal.data.controlplane.protocol.toControlPlaneEvent
import net.thunderbird.feature.taskmail.internal.data.controlplane.protocol.toControlPlaneResult
import net.thunderbird.feature.taskmail.internal.data.direct.TaskMailDirectSessionProjection
import net.thunderbird.feature.taskmail.internal.data.direct.TaskMailDirectSessionProjector
import net.thunderbird.feature.taskmail.internal.data.relay.RelayBootstrapManager
import net.thunderbird.feature.taskmail.internal.data.relay.RelayConnectionClient
import net.thunderbird.feature.taskmail.internal.data.relay.RelaySessionDetailSubscription
import net.thunderbird.feature.taskmail.internal.data.relay.RelayTaskMailDirectSessionDetailSubscriber
import net.thunderbird.feature.taskmail.internal.data.relay.protocol.RelayEvent
import net.thunderbird.feature.taskmail.internal.data.relay.protocol.RelayResult
import net.thunderbird.feature.taskmail.internal.data.relay.protocol.RelaySessionUpdate
import net.thunderbird.feature.taskmail.internal.domain.model.RelayConnectionState
import net.thunderbird.feature.taskmail.internal.domain.model.TaskSessionControlPlaneSnapshot
import net.thunderbird.feature.taskmail.internal.domain.model.TaskSessionDetail
import net.thunderbird.feature.taskmail.internal.domain.model.merge

private const val TAG = "ObserveTaskMailDirectSessionDetail"
private const val SUBSCRIPTION_REASON_DETAIL_OPEN = "detail_open"
private const val SUBSCRIPTION_REASON_DETAIL_REFRESH = "detail_refresh"

internal fun interface ObserveTaskMailDirectSessionDetail {
    operator fun invoke(detail: TaskSessionDetail): Flow<TaskMailDirectSessionProjection>
}

internal class DefaultObserveTaskMailDirectSessionDetail(
    private val relayBootstrapManager: RelayBootstrapManager,
    private val relayConnectionClient: RelayConnectionClient,
    private val directSessionDetailSubscriber: RelayTaskMailDirectSessionDetailSubscriber,
    private val projector: TaskMailDirectSessionProjector,
    private val logger: Logger,
) : ObserveTaskMailDirectSessionDetail {

    override fun invoke(detail: TaskSessionDetail): Flow<TaskMailDirectSessionProjection> {
        val target = detail.toDirectObservationTarget() ?: return emptyFlow()

        return channelFlow {
            logger.debug(TAG) {
                "Starting direct detail observation " +
                    "hasWorkspaceId=${target.workspaceId != null} " +
                    "hasRepoLocator=${target.repoPath != null && target.workdir != null} " +
                    "hasSessionId=${target.sessionId != null}"
            }
            if (!ensureConnected()) return@channelFlow

            val state = DirectObservationState(
                canonicalWorkspaceId = target.workspaceId,
            )

            suspend fun requestSubscribe(reason: String): Boolean {
                return requestSubscribe(
                    target = target,
                    state = state,
                    reason = reason,
                )
            }

            val collectorJob = launch {
                relayConnectionClient.sessionUpdates.collect { update ->
                    processUpdate(
                        update = update,
                        target = target,
                        state = state,
                        requestSubscribe = ::requestSubscribe,
                    )
                }
            }
            val eventCollectorJob = launch {
                relayConnectionClient.serverEvents.collect { relayEvent ->
                    processEvent(
                        relayEvent = relayEvent,
                        target = target,
                        state = state,
                    )
                }
            }
            val resultCollectorJob = launch {
                relayConnectionClient.serverResults.collect { relayResult ->
                    processResult(
                        relayResult = relayResult,
                        target = target,
                        state = state,
                    )
                }
            }

            if (!requestSubscribe(SUBSCRIPTION_REASON_DETAIL_OPEN)) {
                collectorJob.cancel()
                eventCollectorJob.cancel()
                resultCollectorJob.cancel()
                disconnectSilently()
                return@channelFlow
            }

            awaitClose {
                collectorJob.cancel()
                eventCollectorJob.cancel()
                resultCollectorJob.cancel()
                launch {
                    disconnectSilently()
                }
            }
        }
    }

    private suspend fun requestSubscribe(
        target: DirectObservationTarget,
        state: DirectObservationState,
        reason: String,
    ): Boolean {
        val result = directSessionDetailSubscriber.subscribe(
            RelaySessionDetailSubscription(
                workspaceId = state.canonicalWorkspaceId,
                repoPath = target.repoPath,
                workdir = target.workdir,
                sessionId = target.sessionId,
                threadId = target.threadId,
                lastKnownSequence = state.lastKnownSequence,
                reason = reason,
            ),
        )

        return result.fold(
            onSuccess = { packetAck ->
                if (packetAck.accepted) {
                    logger.debug(TAG) {
                        "Direct detail subscribe accepted reason=$reason " +
                            "lastKnownSequence=${state.lastKnownSequence ?: 0L}"
                    }
                    state.activeSubscriptionId = null
                    true
                } else {
                    logger.warn(TAG) {
                        "Direct detail subscribe rejected code=${packetAck.errorCode.orEmpty()}"
                    }
                    false
                }
            },
            onFailure = { error ->
                logger.warn(TAG, error) { "Direct detail subscribe failed." }
                false
            },
        )
    }

    private suspend fun ProducerScope<TaskMailDirectSessionProjection>.processUpdate(
        update: RelaySessionUpdate,
        target: DirectObservationTarget,
        state: DirectObservationState,
        requestSubscribe: suspend (String) -> Boolean,
    ) {
        when {
            !update.matchesTarget(target, state.activeSubscriptionId) -> Unit
            update.isDuplicateOf(state.lastKnownSequence) -> Unit
            update.requiresGapRefresh(state.lastKnownSequence) -> {
                handleGap(
                    currentSequence = checkNotNull(state.lastKnownSequence),
                    nextSequence = update.sequence,
                    requestSubscribe = requestSubscribe,
                )
            }

            else -> {
                logger.debug(TAG) {
                    "Direct detail relay update type=${update.updateType} " +
                        "sequence=${update.sequence} " +
                        "hasSnapshot=${update.sessionSnapshot != null} " +
                        "hasDelta=${update.sessionDelta != null}"
                }
                emitProjection(update, state)
            }
        }
    }

    private fun ProducerScope<TaskMailDirectSessionProjection>.emitProjection(
        update: RelaySessionUpdate,
        state: DirectObservationState,
    ) {
        state.accept(update)
        projector.project(state.updates)
            ?.let(state::attachControlPlaneSnapshot)
            ?.let { projection ->
            state.updateFrom(projection)
            logger.debug(TAG) {
                "Direct detail emitted projection " +
                    "status=${projection.headerStatus.name} " +
                    "pendingQuestionCount=${projection.pendingQuestions.size} " +
                    "provisionalTimelineCount=${projection.provisionalTimeline.size} " +
                    "hasControlPlane=${projection.controlPlaneSnapshot != null} " +
                    "lastSequence=${projection.lastSequence}"
            }
            trySend(projection)
        }
    }

    private fun ProducerScope<TaskMailDirectSessionProjection>.processEvent(
        relayEvent: RelayEvent,
        target: DirectObservationTarget,
        state: DirectObservationState,
    ) {
        val event = relayEvent.toControlPlaneEvent()
        if (!event.matchesTarget(target = target, canonicalWorkspaceId = state.canonicalWorkspaceId)) return

        logger.debug(TAG) {
            "Direct detail observed relay event " +
                "type=${event.eventType} " +
                "sessionId=${event.sessionId.orEmpty()} " +
                "workspaceId=${event.workspaceId.orEmpty()}"
        }
        state.mergeControlPlaneSnapshot(
            TaskSessionControlPlaneSnapshot(
                events = listOf(event),
            ),
        )
        emitControlPlaneProjection(state)
    }

    private fun ProducerScope<TaskMailDirectSessionProjection>.processResult(
        relayResult: RelayResult,
        target: DirectObservationTarget,
        state: DirectObservationState,
    ) {
        val result = relayResult.toControlPlaneResult()
        if (!result.matchesTarget(target = target, canonicalWorkspaceId = state.canonicalWorkspaceId)) return

        logger.debug(TAG) {
            "Direct detail observed relay result " +
                "status=${result.finalStatus} " +
                "sessionId=${result.sessionId.orEmpty()} " +
                "workspaceId=${result.workspaceId.orEmpty()}"
        }
        state.mergeControlPlaneSnapshot(
            TaskSessionControlPlaneSnapshot(
                result = result,
            ),
        )
        emitControlPlaneProjection(state)
    }

    private fun ProducerScope<TaskMailDirectSessionProjection>.emitControlPlaneProjection(
        state: DirectObservationState,
    ) {
        state.latestProjectionWithControlPlane()?.let { projection ->
            state.updateFrom(projection)
            trySend(projection)
        }
    }

    private suspend fun ProducerScope<TaskMailDirectSessionProjection>.handleGap(
        currentSequence: Long,
        nextSequence: Long,
        requestSubscribe: suspend (String) -> Boolean,
    ) {
        logger.warn(TAG) {
            "Direct detail update gap detected " +
                "currentSequence=$currentSequence nextSequence=$nextSequence"
        }
        if (!requestSubscribe(SUBSCRIPTION_REASON_DETAIL_REFRESH)) {
            close()
        }
    }

    private suspend fun ensureConnected(): Boolean {
        return when (relayConnectionClient.connectionState.value) {
            is RelayConnectionState.Connected -> true
            else -> relayBootstrapManager.loadConfig()
                .normalized()
                .takeIf { it.isConfigured() }
                ?.let { config ->
                    relayBootstrapManager.connect(config).fold(
                        onSuccess = { true },
                        onFailure = { error ->
                            logger.warn(TAG, error) { "Direct detail relay connect failed." }
                            false
                        },
                    )
                }
                ?: false
        }
    }

    private suspend fun disconnectSilently() {
        runCatching { relayBootstrapManager.disconnect() }
    }
}

private data class DirectObservationTarget(
    val workspaceId: String? = null,
    val repoPath: String? = null,
    val workdir: String? = null,
    val sessionId: String? = null,
    val threadId: String? = null,
)

private class DirectObservationState(
    var activeSubscriptionId: String? = null,
    var canonicalWorkspaceId: String? = null,
    var lastKnownSequence: Long? = null,
    val updates: MutableList<RelaySessionUpdate> = mutableListOf(),
    var controlPlaneSnapshot: TaskSessionControlPlaneSnapshot? = null,
    var latestProjection: TaskMailDirectSessionProjection? = null,
) {
    fun accept(update: RelaySessionUpdate) {
        if (activeSubscriptionId == null) {
            activeSubscriptionId = update.subscriptionId
        }
        updates += update
    }

    fun updateFrom(projection: TaskMailDirectSessionProjection) {
        canonicalWorkspaceId = projection.canonicalWorkspaceId
        lastKnownSequence = projection.lastSequence
        latestProjection = projection
    }

    fun mergeControlPlaneSnapshot(snapshot: TaskSessionControlPlaneSnapshot) {
        controlPlaneSnapshot = controlPlaneSnapshot.merge(snapshot)
    }

    fun attachControlPlaneSnapshot(
        projection: TaskMailDirectSessionProjection,
    ): TaskMailDirectSessionProjection {
        return projection.copy(
            controlPlaneSnapshot = projection.controlPlaneSnapshot.merge(controlPlaneSnapshot),
        )
    }

    fun latestProjectionWithControlPlane(): TaskMailDirectSessionProjection? {
        return latestProjection?.let(::attachControlPlaneSnapshot)
    }
}

private fun TaskSessionDetail.toDirectObservationTarget(): DirectObservationTarget? {
    val workspaceId = workspace.workspaceId?.takeIf(String::isNotBlank)
    val repoPath = repoPath.takeIf(String::isNotBlank)
    val workdir = workdir?.takeIf(String::isNotBlank) ?: workspace.workdir?.takeIf(String::isNotBlank)
    val sessionId = key.sessionId?.takeIf(String::isNotBlank) ?: return null
    val threadId = key.threadId?.takeIf(String::isNotBlank)

    val hasWorkspaceLocator = workspaceId != null || (repoPath != null && workdir != null)
    val hasSessionLocator = sessionId.isNotBlank()
    if (!hasWorkspaceLocator || !hasSessionLocator) return null

    return DirectObservationTarget(
        workspaceId = workspaceId,
        repoPath = repoPath,
        workdir = workdir,
        sessionId = sessionId,
        threadId = threadId,
    )
}

private fun RelaySessionUpdate.matchesTarget(
    target: DirectObservationTarget,
    activeSubscriptionId: String?,
): Boolean {
    if (activeSubscriptionId != null && subscriptionId != activeSubscriptionId) {
        return false
    }

    return if (target.sessionId != null) {
        sessionId == target.sessionId
    } else {
        threadId == target.threadId
    }
}

private fun RelaySessionUpdate.isDuplicateOf(lastKnownSequence: Long?): Boolean {
    return lastKnownSequence != null && sequence <= lastKnownSequence
}

private fun RelaySessionUpdate.requiresGapRefresh(lastKnownSequence: Long?): Boolean {
    val isSnapshot = updateType.equals("session_snapshot", ignoreCase = true)
    return lastKnownSequence != null && !isSnapshot && sequence > lastKnownSequence + 1
}

private fun net.thunderbird.feature.taskmail.internal.data.controlplane.protocol.ControlPlaneEvent.matchesTarget(
    target: DirectObservationTarget,
    canonicalWorkspaceId: String?,
): Boolean {
    val expectedSessionId = target.sessionId?.takeIf(String::isNotBlank) ?: return false
    if (sessionId != expectedSessionId) return false

    val expectedWorkspaceId = canonicalWorkspaceId
        ?.takeIf(String::isNotBlank)
        ?: target.workspaceId?.takeIf(String::isNotBlank)

    return workspaceId == null || expectedWorkspaceId == null || workspaceId == expectedWorkspaceId
}

private fun net.thunderbird.feature.taskmail.internal.data.controlplane.protocol.ControlPlaneResult.matchesTarget(
    target: DirectObservationTarget,
    canonicalWorkspaceId: String?,
): Boolean {
    val expectedSessionId = target.sessionId?.takeIf(String::isNotBlank) ?: return false
    if (sessionId != expectedSessionId) return false

    val expectedWorkspaceId = canonicalWorkspaceId
        ?.takeIf(String::isNotBlank)
        ?: target.workspaceId?.takeIf(String::isNotBlank)

    return workspaceId == null || expectedWorkspaceId == null || workspaceId == expectedWorkspaceId
}
