package net.thunderbird.feature.taskmail.internal.ui.workspace

import net.thunderbird.core.ui.contract.mvi.UnidirectionalViewModel

internal interface TaskWorkspaceContract {

    interface ViewModel : UnidirectionalViewModel<State, Event, Effect>

    data class State(
        val isLoading: Boolean = false,
        val isRefreshing: Boolean = false,
        val error: String? = null,
        val refreshError: String? = null,
        val attentionSessions: List<TaskSessionItemUi> = emptyList(),
        val activeSessions: List<TaskSessionItemUi> = emptyList(),
        val recentSessions: List<TaskSessionItemUi> = emptyList(),
        val workspaceSummaries: List<TaskWorkspaceItemUi> = emptyList(),
        val pcTreeNodes: List<TaskWorkspacePcNodeUi> = emptyList(),
    ) {
        val hasContent: Boolean
            get() = attentionSessions.isNotEmpty() ||
                activeSessions.isNotEmpty() ||
                recentSessions.isNotEmpty() ||
                workspaceSummaries.isNotEmpty() ||
                pcTreeNodes.isNotEmpty()

        val isEmpty: Boolean
            get() = !isLoading &&
                error == null &&
                !hasContent
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
            val workspaceId: String?,
            val sessionId: String,
        ) : Event
    }

    sealed interface Effect {
        data object OpenProjectSync : Effect
        data object OpenNewTask : Effect
        data class OpenSessionDetail(
            val workspaceId: String?,
            val sessionId: String,
        ) : Effect
    }
}
