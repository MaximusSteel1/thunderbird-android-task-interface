package net.thunderbird.feature.taskmail.internal.domain.usecase

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive
import net.thunderbird.core.logging.Logger
import net.thunderbird.feature.taskmail.internal.data.facade.SessionSnapshotRequestException
import net.thunderbird.feature.taskmail.internal.domain.model.TaskSessionDetail
import net.thunderbird.feature.taskmail.internal.domain.model.TaskSessionHistorySnapshot
import net.thunderbird.feature.taskmail.internal.domain.model.TaskSessionHistorySnapshotLocator
import net.thunderbird.feature.taskmail.internal.domain.repository.TaskSessionUpdatesRepository

private const val TAG = "ObserveTaskMailSessionUpdates"
private const val SESSION_NOT_FOUND_ERROR_CODE = "session_not_found"
private const val DEFAULT_RECONNECT_DELAY_MS = 2_000L

internal fun interface ObserveTaskMailSessionUpdates {
    operator fun invoke(detail: TaskSessionDetail): Flow<TaskSessionHistorySnapshot>
}

internal class DefaultObserveTaskMailSessionUpdates(
    private val repository: TaskSessionUpdatesRepository,
    private val logger: Logger,
    private val reconnectDelayMillis: Long = DEFAULT_RECONNECT_DELAY_MS,
) : ObserveTaskMailSessionUpdates {

    override fun invoke(detail: TaskSessionDetail): Flow<TaskSessionHistorySnapshot> {
        val locator = detail.toSessionUpdatesLocator() ?: return emptyFlow()

        return flow {
            while (currentCoroutineContext().isActive) {
                try {
                    emitAll(repository.observeSessionUpdates(locator))

                    if (!currentCoroutineContext().isActive) {
                        return@flow
                    }

                    logger.debug(TAG) {
                        "Android session-updates websocket completed. Reconnecting detail observer."
                    }
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Throwable) {
                    if (!error.shouldRetrySessionUpdates()) {
                        logger.warn(TAG, error) {
                            "Android session-updates observation failed with non-retryable error."
                        }
                        throw error
                    }

                    logger.warn(TAG, error) {
                        "Android session-updates observation failed. Reconnecting detail observer."
                    }
                }

                if (reconnectDelayMillis > 0 && currentCoroutineContext().isActive) {
                    delay(reconnectDelayMillis)
                }
            }
        }
    }
}

private fun TaskSessionDetail.toSessionUpdatesLocator(): TaskSessionHistorySnapshotLocator? {
    val sessionId = key.sessionId?.trim()?.takeIf(String::isNotBlank) ?: return null
    return TaskSessionHistorySnapshotLocator(
        workspaceId = key.workspaceId?.trim()?.takeIf(String::isNotBlank)
            ?: workspace.workspaceId?.trim()?.takeIf(String::isNotBlank),
        sessionId = sessionId,
        threadId = key.threadId?.trim()?.takeIf(String::isNotBlank),
        repoPath = repoPath.trim().takeIf(String::isNotBlank),
        workdir = workdir?.trim()?.takeIf(String::isNotBlank)
            ?: workspace.workdir?.trim()?.takeIf(String::isNotBlank),
    )
}

private fun Throwable.shouldRetrySessionUpdates(): Boolean {
    if (this is SessionSnapshotRequestException) {
        if (errorCode.equals(SESSION_NOT_FOUND_ERROR_CODE, ignoreCase = true)) {
            return true
        }

        if (!retryable) {
            return false
        }
    }

    return message?.contains("Android app token are required", ignoreCase = true) != true
}
