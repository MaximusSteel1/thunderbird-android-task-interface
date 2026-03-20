package net.thunderbird.feature.taskmail.internal.ui.workspace

import net.thunderbird.core.ui.contract.mvi.UnidirectionalViewModel

internal interface TaskWorkspaceContract {

    interface ViewModel : UnidirectionalViewModel<State, Event, Effect>

    data class State(
        val isLoading: Boolean = false,
        val isRefreshing: Boolean = false,
        val error: String? = null,
        val refreshError: String? = null,
        val workspaces: List<TaskWorkspaceItemUi> = emptyList(),
    ) {
        val isEmpty: Boolean = !isLoading && error == null && workspaces.isEmpty()
    }

    sealed interface Event {
        data object LoadData : Event
        data object RefreshRequested : Event
        data object ForegroundRefreshStarted : Event
        data object ForegroundRefreshStopped : Event
        data object RetryClicked : Event
        data object ProjectListClicked : Event
        data object NewTaskClicked : Event
        data class SessionClicked(
            val sessionId: String?,
            val threadId: String,
        ) : Event
    }

    sealed interface Effect {
        data object OpenProjectSync : Effect
        data object OpenNewTask : Effect
        data class OpenSessionDetail(
            val sessionId: String?,
            val threadId: String,
        ) : Effect
    }
}
