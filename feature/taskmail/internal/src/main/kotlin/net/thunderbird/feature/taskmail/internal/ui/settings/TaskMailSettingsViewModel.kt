package net.thunderbird.feature.taskmail.internal.ui.settings

import androidx.lifecycle.viewModelScope
import com.fsck.k9.EmailAddressValidator
import kotlinx.coroutines.launch
import net.thunderbird.core.ui.contract.mvi.BaseViewModel
import net.thunderbird.feature.taskmail.internal.domain.model.TaskMailBotMailboxSettings
import net.thunderbird.feature.taskmail.internal.domain.usecase.GetTaskMailBotMailboxSettings
import net.thunderbird.feature.taskmail.internal.domain.usecase.SaveTaskMailBotMailboxSettings
import net.thunderbird.feature.taskmail.internal.ui.settings.TaskMailSettingsContract.Effect
import net.thunderbird.feature.taskmail.internal.ui.settings.TaskMailSettingsContract.Event
import net.thunderbird.feature.taskmail.internal.ui.settings.TaskMailSettingsContract.State

private const val LOAD_SETTINGS_ERROR = "Unable to load TaskMail bot mailbox settings."
private const val ADDRESS_REQUIRED_ERROR = "Bot mailbox address is required."
private const val ADDRESS_INVALID_ERROR = "Enter a valid email address."
private const val SAVE_SETTINGS_ERROR = "Unable to save TaskMail bot mailbox settings."
private const val SAVE_SETTINGS_SUCCESS = "TaskMail bot mailbox saved."

internal class TaskMailSettingsViewModel(
    private val getTaskMailBotMailboxSettings: GetTaskMailBotMailboxSettings,
    private val saveTaskMailBotMailboxSettings: SaveTaskMailBotMailboxSettings,
    private val emailAddressValidator: EmailAddressValidator,
    initialState: State = State(),
) : BaseViewModel<State, Event, Effect>(initialState),
    TaskMailSettingsContract.ViewModel {

    override fun event(event: Event) {
        when (event) {
            Event.LoadData -> handleOneTimeEvent(event, ::loadData)
            Event.BackClicked -> emitEffect(Effect.NavigateBack)
            Event.ConfirmClicked -> saveSettings()
            Event.DismissSaveError -> updateState { it.copy(saveError = null) }
            is Event.AddressChanged -> updateState {
                it.copy(
                    address = event.value,
                    addressError = null,
                    saveError = null,
                )
            }
        }
    }

    private fun loadData() {
        updateState {
            it.copy(
                isLoading = true,
                addressError = null,
                saveError = null,
            )
        }

        viewModelScope.launch {
            runCatching {
                getTaskMailBotMailboxSettings()
            }.onSuccess { settings ->
                updateState {
                    it.copy(
                        isLoading = false,
                        address = settings.address.orEmpty(),
                        isUsingBuildDefault = settings.source == TaskMailBotMailboxSettings.Source.BuildDefault,
                    )
                }
            }.onFailure {
                updateState {
                    it.copy(
                        isLoading = false,
                        saveError = LOAD_SETTINGS_ERROR,
                    )
                }
            }
        }
    }

    private fun saveSettings() {
        val address = state.value.address.trim()

        when {
            address.isEmpty() -> {
                updateState {
                    it.copy(
                        addressError = ADDRESS_REQUIRED_ERROR,
                        saveError = null,
                    )
                }
            }

            !emailAddressValidator.isValidAddressOnly(address) -> {
                updateState {
                    it.copy(
                        addressError = ADDRESS_INVALID_ERROR,
                        saveError = null,
                    )
                }
            }

            else -> {
                updateState {
                    it.copy(
                        isSaving = true,
                        address = address,
                        addressError = null,
                        saveError = null,
                    )
                }

                viewModelScope.launch {
                    val isSaved = saveTaskMailBotMailboxSettings(address)

                    updateState {
                        it.copy(
                            isSaving = false,
                            address = address,
                            isUsingBuildDefault = false,
                            saveError = if (isSaved) null else SAVE_SETTINGS_ERROR,
                        )
                    }

                    if (isSaved) {
                        emitEffect(Effect.ShowMessage(SAVE_SETTINGS_SUCCESS))
                        emitEffect(Effect.NavigateBack)
                    }
                }
            }
        }
    }
}
