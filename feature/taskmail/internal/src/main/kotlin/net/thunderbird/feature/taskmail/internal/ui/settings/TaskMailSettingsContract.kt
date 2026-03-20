package net.thunderbird.feature.taskmail.internal.ui.settings

import net.thunderbird.core.ui.contract.mvi.UnidirectionalViewModel

internal interface TaskMailSettingsContract {

    interface ViewModel : UnidirectionalViewModel<State, Event, Effect>

    data class State(
        val isLoading: Boolean = false,
        val address: String = "",
        val isUsingBuildDefault: Boolean = false,
        val isSaving: Boolean = false,
        val addressError: String? = null,
        val saveError: String? = null,
    ) {
        val canSave: Boolean
            get() = !isLoading && !isSaving
    }

    sealed interface Event {
        data object LoadData : Event
        data object BackClicked : Event
        data class AddressChanged(val value: String) : Event
        data object ConfirmClicked : Event
        data object DismissSaveError : Event
    }

    sealed interface Effect {
        data object NavigateBack : Effect
        data class ShowMessage(val message: String) : Effect
    }
}
