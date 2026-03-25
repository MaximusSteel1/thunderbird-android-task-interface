package net.thunderbird.feature.taskmail.internal.ui.newtask

import androidx.lifecycle.viewModelScope
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.launch
import net.thunderbird.core.ui.contract.mvi.BaseViewModel
import net.thunderbird.feature.taskmail.internal.domain.model.TaskMailDirectAcceptedEvidence
import net.thunderbird.feature.taskmail.internal.domain.model.TaskMailSenderAccount
import net.thunderbird.feature.taskmail.internal.domain.newtask.TaskMailDirectNewTaskResult
import net.thunderbird.feature.taskmail.internal.domain.newtask.TaskMailNewTaskDraft
import net.thunderbird.feature.taskmail.internal.domain.newtask.TaskMailNewTaskResult
import net.thunderbird.feature.taskmail.internal.domain.usecase.GetLatestTaskMailNewTaskSendRecord
import net.thunderbird.feature.taskmail.internal.domain.usecase.GetTaskMailSenderAccounts
import net.thunderbird.feature.taskmail.internal.domain.usecase.RecordTaskMailNewTaskSendRecord
import net.thunderbird.feature.taskmail.internal.domain.usecase.RunTaskMailDirectOrFallback
import net.thunderbird.feature.taskmail.internal.domain.usecase.SendTaskMailDirectNewTask
import net.thunderbird.feature.taskmail.internal.domain.usecase.SendTaskMailNewTask
import net.thunderbird.feature.taskmail.internal.domain.usecase.TaskMailDirectAttemptResult
import net.thunderbird.feature.taskmail.internal.domain.usecase.TaskMailDirectOrFallbackResult
import net.thunderbird.feature.taskmail.internal.ui.newtask.TaskNewTaskContract.Effect
import net.thunderbird.feature.taskmail.internal.ui.newtask.TaskNewTaskContract.Event
import net.thunderbird.feature.taskmail.internal.ui.newtask.TaskNewTaskContract.State

private const val NO_SENDER_ACCOUNT_MESSAGE =
    "Set up a mailbox account before sending TaskMail requests."
private const val LOAD_SENDER_ACCOUNTS_ERROR =
    "Unable to load mailbox accounts for TaskMail sending."
private const val SENDER_ACCOUNT_REQUIRED_ERROR = "Select the sending account."
private const val BACKEND_REQUIRED_ERROR = "Select a backend."
private const val REPO_REQUIRED_ERROR = "Repo is required."
private const val TASK_REQUIRED_ERROR = "Task details are required."
private const val TITLE_REQUIRED_ERROR = "Title is required."
private const val TIMEOUT_INVALID_ERROR = "Timeout must be a positive integer."
private const val SEND_FAILURE_MESSAGE = "Failed to send TaskMail task request."
private const val SEND_SUCCESS_MESSAGE =
    "[Relay] Task request sent. It will appear after the first TaskMail status mail arrives."
private const val SEND_FALLBACK_SUCCESS_MESSAGE =
    "[Mail fallback] Task request sent. It will appear after the first TaskMail status mail arrives."

@Suppress("TooManyFunctions")
internal class TaskNewTaskViewModel(
    private val getTaskMailSenderAccounts: GetTaskMailSenderAccounts,
    private val getLatestTaskMailNewTaskSendRecord: GetLatestTaskMailNewTaskSendRecord,
    private val recordTaskMailNewTaskSendRecord: RecordTaskMailNewTaskSendRecord,
    private val sendTaskMailDirectNewTask: SendTaskMailDirectNewTask,
    private val sendTaskMailNewTask: SendTaskMailNewTask,
    private val runTaskMailDirectOrFallback: RunTaskMailDirectOrFallback,
    initialState: State = State(),
) : BaseViewModel<State, Event, Effect>(initialState),
    TaskNewTaskContract.ViewModel {

    override fun event(event: Event) {
        when (event) {
            Event.LoadData -> handleOneTimeEvent(event, ::loadSenderAccounts)
            Event.BackClicked -> emitEffect(Effect.NavigateBack)
            Event.ChooseRepoClicked -> emitEffect(Effect.OpenProjectSync)
            Event.SendClicked -> sendTask()
            Event.DismissSendError -> updateState { it.copy(sendError = null) }
            is Event.SenderAccountSelected,
            is Event.PcChanged,
            is Event.WorkspaceChanged,
            is Event.BackendSelected,
            is Event.RepoChanged,
            is Event.TaskChanged,
            is Event.SubjectTitleChanged,
            Event.AdvancedToggleClicked,
            is Event.WorkdirChanged,
            is Event.ModeChanged,
            is Event.TimeoutChanged,
            is Event.PermissionChanged,
            is Event.ProfileChanged,
            is Event.AcceptanceChanged,
            -> handleInputEvent(event)
        }
    }

    private fun handleInputEvent(event: Event) {
        when (event) {
            is Event.SenderAccountSelected,
            is Event.PcChanged,
            is Event.WorkspaceChanged,
            is Event.BackendSelected,
            is Event.RepoChanged,
            is Event.TaskChanged,
            is Event.SubjectTitleChanged,
            -> handleRequiredFieldEvent(event)
            Event.AdvancedToggleClicked,
            is Event.WorkdirChanged,
            is Event.ModeChanged,
            is Event.TimeoutChanged,
            is Event.PermissionChanged,
            is Event.ProfileChanged,
            is Event.AcceptanceChanged,
            -> handleAdvancedFieldEvent(event)
            else -> Unit
        }
    }

    private fun handleRequiredFieldEvent(event: Event) {
        when (event) {
            is Event.SenderAccountSelected -> handleSenderAccountSelected(event.accountUuid)

            is Event.PcChanged -> updateState {
                it.copy(
                    pcId = event.value,
                    sendError = null,
                )
            }

            is Event.WorkspaceChanged -> updateState {
                it.copy(
                    workspaceId = event.value,
                    sendError = null,
                )
            }

            is Event.BackendSelected -> updateState {
                it.copy(
                    selectedBackend = event.backend,
                    backendError = null,
                    sendError = null,
                )
            }

            is Event.RepoChanged -> updateState {
                it.copy(
                    repoPath = event.value,
                    repoError = null,
                    sendError = null,
                )
            }

            is Event.TaskChanged -> updateState { current ->
                val derivedTitle = deriveSubjectTitle(event.value)
                current.copy(
                    taskText = event.value,
                    subjectTitle = if (current.isSubjectTitleEdited) {
                        current.subjectTitle
                    } else {
                        derivedTitle
                    },
                    taskError = null,
                    titleError = if (current.isSubjectTitleEdited) current.titleError else null,
                    sendError = null,
                )
            }

            is Event.SubjectTitleChanged -> updateState {
                it.copy(
                    subjectTitle = event.value,
                    isSubjectTitleEdited = true,
                    titleError = null,
                    sendError = null,
                )
            }

            else -> Unit
        }
    }

    private fun handleAdvancedFieldEvent(event: Event) {
        when (event) {
            Event.AdvancedToggleClicked -> updateState {
                it.copy(
                    isAdvancedExpanded = !it.isAdvancedExpanded,
                    sendError = null,
                )
            }

            is Event.WorkdirChanged -> updateState {
                it.copy(
                    workdir = event.value,
                    sendError = null,
                )
            }

            is Event.ModeChanged -> updateState {
                it.copy(
                    mode = event.mode,
                    sendError = null,
                )
            }

            is Event.TimeoutChanged -> updateState {
                it.copy(
                    timeoutText = event.value,
                    timeoutError = validateTimeoutText(event.value),
                    sendError = null,
                )
            }

            is Event.PermissionChanged -> updateState {
                it.copy(
                    permission = event.permission,
                    sendError = null,
                )
            }

            is Event.ProfileChanged -> updateState {
                it.copy(
                    profile = event.value,
                    sendError = null,
                )
            }

            is Event.AcceptanceChanged -> updateState {
                it.copy(
                    acceptanceText = event.value,
                    sendError = null,
                )
            }

            else -> Unit
        }
    }

    private fun loadSenderAccounts() {
        updateState {
            it.copy(
                isLoading = true,
                senderAccountBlockingError = null,
                senderAccountError = null,
                sendError = null,
                lastDirectSendEvidence = null,
            )
        }

        viewModelScope.launch {
            runCatching {
                getTaskMailSenderAccounts()
            }.onSuccess { accounts ->
                val selectedSenderAccountId = resolveSelectedSenderAccountId(
                    existingSelection = state.value.selectedSenderAccountId,
                    accounts = accounts,
                )
                updateState { current ->
                    current.copy(
                        isLoading = false,
                        senderAccountBlockingError = accounts.blockingErrorOrNull(),
                        senderAccounts = accounts.toImmutableList(),
                        selectedSenderAccountId = selectedSenderAccountId,
                        senderAccountError = null,
                    )
                }
                if (selectedSenderAccountId != null) {
                    loadLatestSendEvidence(selectedSenderAccountId)
                }
            }.onFailure {
                updateState {
                    it.copy(
                        isLoading = false,
                        senderAccountBlockingError = LOAD_SENDER_ACCOUNTS_ERROR,
                        senderAccounts = persistentListOf(),
                        selectedSenderAccountId = null,
                        senderAccountError = null,
                        lastDirectSendEvidence = null,
                    )
                }
            }
        }
    }

    private fun sendTask() {
        val currentState = state.value
        if (!currentState.canSend) return

        val validation = validate(currentState)
        if (!validation.hasNoErrors()) {
            showValidationErrors(validation)
            return
        }

        val draft = validation.buildDraft(currentState)
        if (draft == null) {
            updateState { it.copy(sendError = SEND_FAILURE_MESSAGE) }
        } else {
            updateState {
                it.copy(
                    isSending = true,
                    sendError = null,
                    lastDirectSendEvidence = null,
                    senderAccountError = null,
                    backendError = null,
                    repoError = null,
                    taskError = null,
                    titleError = null,
                    timeoutError = null,
                )
            }
            viewModelScope.launch {
                val sendResult = runTaskMailDirectOrFallback.execute(
                    directSend = {
                        sendTaskMailDirectNewTask(draft).toDirectAttemptResult()
                    },
                    mailFallback = {
                        sendTaskMailNewTask(draft).toMailFallbackResult()
                    },
                )
                runCatching {
                    recordTaskMailNewTaskSendRecord(
                        draft = draft,
                        evidence = sendResult.evidence,
                    )
                }
                handleSendResult(sendResult)
            }
        }
    }

    private suspend fun handleSendResult(
        sendResult: TaskMailDirectOrFallbackResult<TaskMailDirectNewTaskResult.Accepted>,
    ) {
        when (sendResult) {
            is TaskMailDirectOrFallbackResult.DirectAccepted -> {
                updateState {
                    it.copy(
                        isSending = false,
                        sendError = null,
                        lastDirectSendEvidence = sendResult.evidence,
                    )
                }
                emitEffect(Effect.ShowMessage(SEND_SUCCESS_MESSAGE))
                emitEffect(Effect.NavigateBack)
            }

            is TaskMailDirectOrFallbackResult.MailFallbackSucceeded -> {
                updateState {
                    it.copy(
                        isSending = false,
                        sendError = null,
                        lastDirectSendEvidence = sendResult.evidence,
                    )
                }
                emitEffect(Effect.ShowMessage(SEND_FALLBACK_SUCCESS_MESSAGE))
                emitEffect(Effect.NavigateBack)
            }

            is TaskMailDirectOrFallbackResult.MailFallbackFailed -> {
                updateState {
                    it.copy(
                        isSending = false,
                        lastDirectSendEvidence = sendResult.evidence,
                        sendError = sendResult.errorMessage ?: SEND_FAILURE_MESSAGE,
                    )
                }
            }

            is TaskMailDirectOrFallbackResult.DirectRejected -> {
                updateState {
                    it.copy(
                        isSending = false,
                        lastDirectSendEvidence = sendResult.evidence,
                        sendError = sendResult.errorMessage.ifBlank { SEND_FAILURE_MESSAGE },
                    )
                }
            }
        }
    }

    private fun showValidationErrors(validation: ValidationResult) {
        updateState {
            it.copy(
                senderAccountError = validation.senderAccountError,
                backendError = validation.backendError,
                repoError = validation.repoError,
                taskError = validation.taskError,
                titleError = validation.titleError,
                timeoutError = validation.timeoutError,
                sendError = null,
            )
        }
    }

    private fun validate(state: State): ValidationResult {
        val selectedSenderAccountId = state.selectedSenderAccountId
            ?.takeIf { accountUuid ->
                state.senderAccounts.any { it.accountUuid == accountUuid }
            }
        val repoPath = state.repoPath.trim()
        val taskText = state.taskText.trim()
        val subjectTitle = state.subjectTitle.trim()
        val timeoutText = state.timeoutText.trim()
        val timeoutMinutes = timeoutText
            .takeIf(String::isNotEmpty)
            ?.toIntOrNull()
            ?.takeIf { it > 0 }

        return ValidationResult(
            senderAccountError = if (state.requiresSenderAccountSelection && selectedSenderAccountId == null) {
                SENDER_ACCOUNT_REQUIRED_ERROR
            } else {
                null
            },
            backendError = if (state.selectedBackend == null) BACKEND_REQUIRED_ERROR else null,
            repoError = if (repoPath.isEmpty()) REPO_REQUIRED_ERROR else null,
            taskError = if (taskText.isEmpty()) TASK_REQUIRED_ERROR else null,
            titleError = if (subjectTitle.isEmpty()) TITLE_REQUIRED_ERROR else null,
            timeoutError = validateTimeoutText(timeoutText),
            normalizedSenderAccountId = selectedSenderAccountId,
            normalizedRepoPath = repoPath,
            normalizedTaskText = taskText,
            normalizedSubjectTitle = subjectTitle,
            normalizedTimeoutMinutes = timeoutMinutes,
        )
    }

    private fun handleSenderAccountSelected(accountUuid: String?) {
        updateState {
            it.copy(
                selectedSenderAccountId = accountUuid,
                senderAccountError = null,
                sendError = null,
                lastDirectSendEvidence = null,
            )
        }

        if (accountUuid != null) {
            loadLatestSendEvidence(accountUuid)
        }
    }

    private fun loadLatestSendEvidence(senderAccountId: String) {
        viewModelScope.launch {
            val latestEvidence = runCatching {
                getLatestTaskMailNewTaskSendRecord(senderAccountId)?.evidence
            }.getOrNull()

            updateState { current ->
                if (current.selectedSenderAccountId == senderAccountId) {
                    current.copy(lastDirectSendEvidence = latestEvidence)
                } else {
                    current
                }
            }
        }
    }
}

private fun deriveSubjectTitle(taskText: String): String {
    return taskText.lineSequence()
        .map(String::trim)
        .firstOrNull(String::isNotEmpty)
        .orEmpty()
}

private fun List<TaskMailSenderAccount>.blockingErrorOrNull(): String? {
    return if (isEmpty()) {
        NO_SENDER_ACCOUNT_MESSAGE
    } else {
        null
    }
}

private fun TaskMailDirectNewTaskResult.toDirectAttemptResult():
    TaskMailDirectAttemptResult<TaskMailDirectNewTaskResult.Accepted> {
    return when (this) {
        is TaskMailDirectNewTaskResult.Accepted -> {
            TaskMailDirectAttemptResult.Accepted(
                payload = this,
                acceptedEvidence = TaskMailDirectAcceptedEvidence(
                    requestId = requestId,
                    receiptId = receiptId,
                    transportMessageId = transportMessageId,
                ),
            )
        }
        is TaskMailDirectNewTaskResult.FallbackToMail -> TaskMailDirectAttemptResult.FallbackToMail(detailMessage)
        is TaskMailDirectNewTaskResult.Rejected -> TaskMailDirectAttemptResult.Rejected(errorMessage)
    }
}

private fun TaskMailNewTaskResult.toMailFallbackResult(): Result<Unit> {
    return if (isSuccess) {
        Result.success(Unit)
    } else {
        Result.failure(
            IllegalStateException(
                errorMessage ?: SEND_FAILURE_MESSAGE,
            ),
        )
    }
}

private fun validateTimeoutText(value: String): String? {
    val trimmedValue = value.trim()
    if (trimmedValue.isEmpty()) return null

    val parsedValue = trimmedValue.toIntOrNull()
    return if (parsedValue == null || parsedValue <= 0) {
        TIMEOUT_INVALID_ERROR
    } else {
        null
    }
}

private fun resolveSelectedSenderAccountId(
    existingSelection: String?,
    accounts: List<TaskMailSenderAccount>,
): String? {
    return when {
        accounts.isEmpty() -> null
        accounts.size == 1 -> accounts.single().accountUuid
        existingSelection != null && accounts.any { it.accountUuid == existingSelection } -> existingSelection
        else -> null
    }
}

private data class ValidationResult(
    val senderAccountError: String?,
    val backendError: String?,
    val repoError: String?,
    val taskError: String?,
    val titleError: String?,
    val timeoutError: String?,
    val normalizedSenderAccountId: String?,
    val normalizedRepoPath: String,
    val normalizedTaskText: String,
    val normalizedSubjectTitle: String,
    val normalizedTimeoutMinutes: Int?,
) {
    fun hasNoErrors(): Boolean {
        return senderAccountError == null &&
            backendError == null &&
            repoError == null &&
            taskError == null &&
            titleError == null &&
            timeoutError == null
    }

    fun buildDraft(state: State): TaskMailNewTaskDraft? {
        if (!hasNoErrors()) return null
        val senderAccountId = normalizedSenderAccountId
        val backend = state.selectedBackend

        return if (senderAccountId != null && backend != null) {
            TaskMailNewTaskDraft(
                senderAccountId = senderAccountId,
                backend = backend,
                repoPath = normalizedRepoPath,
                taskText = normalizedTaskText,
                subjectTitle = normalizedSubjectTitle,
                workdir = state.workdir.trim().takeIf { it.isNotEmpty() },
                mode = state.mode,
                timeoutMinutes = normalizedTimeoutMinutes,
                permission = state.permission,
                profile = state.profile.trim().takeIf { it.isNotEmpty() },
                acceptanceCriteria = state.acceptanceText
                    .lineSequence()
                    .map(String::trim)
                    .filter(String::isNotEmpty)
                    .toList(),
                pcId = state.pcId.trim().takeIf { it.isNotEmpty() },
                workspaceId = state.workspaceId.trim().takeIf { it.isNotEmpty() },
            )
        } else {
            null
        }
    }
}
