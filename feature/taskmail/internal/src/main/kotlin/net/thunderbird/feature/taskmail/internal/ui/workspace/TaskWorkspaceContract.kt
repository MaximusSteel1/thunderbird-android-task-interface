package net.thunderbird.feature.taskmail.internal.ui.workspace

import net.thunderbird.core.ui.contract.mvi.UnidirectionalViewModel

internal interface TaskWorkspaceContract {

    interface ViewModel : UnidirectionalViewModel<State, Event, Effect>

    data class State(
        val isLoading: Boolean = false,
        val error: String? = null,
        val workspaces: List<TaskWorkspaceItemUi> = emptyList(),
    ) {
        val isEmpty: Boolean = !isLoading && error == null && workspaces.isEmpty()
    }

    sealed interface Event {
        data object LoadData : Event
        data object RetryClicked : Event
        data class SessionClicked(
            val sessionId: String?,
            val threadId: String,
        ) : Event
    }

    sealed interface Effect {
        data class OpenSessionDetail(
            val sessionId: String?,
            val threadId: String,
        ) : Effect
    }
}
