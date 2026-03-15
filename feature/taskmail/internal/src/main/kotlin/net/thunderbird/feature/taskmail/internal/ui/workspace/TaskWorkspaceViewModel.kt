package net.thunderbird.feature.taskmail.internal.ui.workspace

import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import net.thunderbird.core.ui.contract.mvi.BaseViewModel
import net.thunderbird.feature.taskmail.internal.domain.model.TaskSessionSummary
import net.thunderbird.feature.taskmail.internal.domain.model.TaskWorkspaceSummary
import net.thunderbird.feature.taskmail.internal.domain.repository.TaskMailRepository
import net.thunderbird.feature.taskmail.internal.domain.usecase.GetTaskWorkspaceSummaries
import net.thunderbird.feature.taskmail.internal.ui.workspace.TaskWorkspaceContract.Effect
import net.thunderbird.feature.taskmail.internal.ui.workspace.TaskWorkspaceContract.Event
import net.thunderbird.feature.taskmail.internal.ui.workspace.TaskWorkspaceContract.State

internal class TaskWorkspaceViewModel(
    repository: TaskMailRepository,
    private val getTaskWorkspaceSummaries: GetTaskWorkspaceSummaries = GetTaskWorkspaceSummaries(repository),
    initialState: State = State(),
) : BaseViewModel<State, Event, Effect>(initialState),
    TaskWorkspaceContract.ViewModel {

    private var hasLoadedData = false

    override fun event(event: Event) {
        when (event) {
            Event.LoadData -> loadData(force = false)
            Event.RetryClicked -> loadData(force = true)
            is Event.SessionClicked -> {
                emitEffect(
                    Effect.OpenSessionDetail(
                        sessionId = event.sessionId,
                        threadId = event.threadId,
                    ),
                )
            }
        }
    }

    private fun loadData(force: Boolean) {
        if (hasLoadedData && !force) return

        viewModelScope.launch {
            updateState {
                it.copy(
                    isLoading = true,
                    error = null,
                )
            }

            runCatching {
                getTaskWorkspaceSummaries()
            }.onSuccess { workspaces ->
                hasLoadedData = true
                updateState {
                    it.copy(
                        isLoading = false,
                        error = null,
                        workspaces = workspaces.map(TaskWorkspaceSummary::toUiState),
                    )
                }
            }.onFailure {
                updateState {
                    it.copy(
                        isLoading = false,
                        error = "Failed to load TaskMail workspaces.",
                        workspaces = emptyList(),
                    )
                }
            }
        }
    }
}

private fun TaskWorkspaceSummary.toUiState(): TaskWorkspaceItemUi {
    return TaskWorkspaceItemUi(
        title = title,
        subtitle = subtitle,
        sessionCountLabel = "$sessionCount session${if (sessionCount == 1) "" else "s"}",
        sessions = sessions.map(TaskSessionSummary::toUiState),
    )
}

private fun TaskSessionSummary.toUiState(): TaskSessionItemUi {
    return TaskSessionItemUi(
        sessionId = key.sessionId,
        threadId = key.threadId,
        sessionName = sessionName,
        status = status.name,
        backend = backend.name,
        lastSummary = lastSummary,
        pendingQuestion = pendingQuestion,
    )
}
