package net.thunderbird.feature.taskmail.internal.ui.newtask

import androidx.lifecycle.viewModelScope
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.launch
import net.thunderbird.core.ui.contract.mvi.BaseViewModel
import net.thunderbird.feature.taskmail.internal.data.controlplane.protocol.ControlPlaneExecutionPolicy
import net.thunderbird.feature.taskmail.internal.domain.model.TaskMailDirectAcceptedEvidence
import net.thunderbird.feature.taskmail.internal.domain.model.TaskMailSenderAccount
import net.thunderbird.feature.taskmail.internal.domain.newtask.TaskMailDirectNewTaskResult
import net.thunderbird.feature.taskmail.internal.domain.newtask.TaskMailNewTaskDraft
import net.thunderbird.feature.taskmail.internal.domain.newtask.TaskMailNewTaskPermission
import net.thunderbird.feature.taskmail.internal.domain.usecase.GetLatestTaskMailNewTaskSendRecord
import net.thunderbird.feature.taskmail.internal.domain.usecase.GetTaskMailSenderAccounts
import net.thunderbird.feature.taskmail.internal.domain.usecase.RecordTaskMailNewTaskSendRecord
import net.thunderbird.feature.taskmail.internal.domain.usecase.RunTaskMailDirectDispatch
import net.thunderbird.feature.taskmail.internal.domain.usecase.SendTaskMailDirectNewTask
import net.thunderbird.feature.taskmail.internal.domain.usecase.TaskMailDirectAttemptResult
import net.thunderbird.feature.taskmail.internal.domain.usecase.TaskMailDirectDispatchResult
import net.thunderbird.feature.taskmail.internal.ui.newtask.TaskNewTaskContract.Effect
import net.thunderbird.feature.taskmail.internal.ui.newtask.TaskNewTaskContract.Event
import net.thunderbird.feature.taskmail.internal.ui.newtask.TaskNewTaskContract.State

private const val NO_SENDER_ACCOUNT_MESSAGE =
    "Set up a mailbox account before sending TaskMail requests."
private const val LOAD_SENDER_ACCOUNTS_ERROR =
    "Unable to load mailbox accounts for TaskMail sending."
private const val SENDER_ACCOUNT_REQUIRED_ERROR = "Select the sending account."
private const val BACKEND_REQUIRED_ERROR = "Select a backend."
private const val REPO_REQUIRED_ERROR = "Repository bridge is required."
private const val TASK_REQUIRED_ERROR = "Task details are required."
private const val TITLE_REQUIRED_ERROR = "Title is required."
private const val TIMEOUT_INVALID_ERROR = "Timeout must be a positive integer."
private const val SEND_FAILURE_MESSAGE = "Failed to send TaskMail task request."
private const val SEND_SUCCESS_MESSAGE =
    "[Relay] Task request sent. It will appear after the first TaskMail update arrives."

@Suppress("TooManyFunctions")
internal class TaskNewTaskViewModel(
    private val getTaskMailSenderAccounts: GetTaskMailSenderAccounts,
    private val getLatestTaskMailNewTaskSendRecord: GetLatestTaskMailNewTaskSendRecord,
    private val recordTaskMailNewTaskSendRecord: RecordTaskMailNewTaskSendRecord,
    private val sendTaskMailDirectNewTask: SendTaskMailDirectNewTask,
    private val runTaskMailDirectDispatch: RunTaskMailDirectDispatch,
    initialState: State = State(),
) : BaseViewModel<State, Event, Effect>(initialState),
    TaskNewTaskContract.ViewModel {

    override fun event(event: Event) {
        when (event) {
            Event.LoadData -> handleOneTimeEvent(event, ::loadSenderAccounts)
            Event.BackClicked -> emitEffect(Effect.NavigateBack)
            Event.ChooseRepoClicked -> emitEffect(Effect.OpenProjectSync)
            Event.SendClicked -> sendTask()
            Event.DismissSendError -> updateState {
                it.copy(
                    submitState = it.submitState.copy(sendError = null),
                )
            }

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
                    pcSelection = it.pcSelection.copy(selectedPcId = event.value),
                    submitState = it.submitState.copy(sendError = null),
                )
            }

            is Event.WorkspaceChanged -> updateState {
                val selectedOption = it.workspaceSelection.workspaceOptions
                    .firstOrNull { option -> option.id == event.value }
                it.copy(
                    workspaceSelection = it.workspaceSelection.copy(
                        selectedWorkspaceId = event.value,
                        repoPath = selectedOption?.repoPath
                            ?.takeIf { optionRepoPath ->
                                it.workspaceSelection.repoPath.isBlank()
                            }
                            ?: it.workspaceSelection.repoPath,
                        workdir = selectedOption?.workdir
                            ?.takeIf { optionWorkdir ->
                                it.workspaceSelection.workdir.isBlank()
                            }
                            ?: it.workspaceSelection.workdir,
                    ),
                    validationErrors = it.validationErrors.copy(repoError = null),
                    submitState = it.submitState.copy(sendError = null),
                )
            }

            is Event.BackendSelected -> updateState {
                it.copy(
                    executionPolicyEditor = it.executionPolicyEditor.copy(backend = event.backend),
                    validationErrors = it.validationErrors.copy(backendError = null),
                    submitState = it.submitState.copy(sendError = null),
                )
            }

            is Event.RepoChanged -> updateState {
                it.copy(
                    workspaceSelection = it.workspaceSelection.copy(repoPath = event.value),
                    validationErrors = it.validationErrors.copy(repoError = null),
                    submitState = it.submitState.copy(sendError = null),
                )
            }

            is Event.TaskChanged -> updateState { current ->
                val derivedTitle = deriveSubjectTitle(event.value)
                current.copy(
                    taskInput = current.taskInput.copy(
                        taskText = event.value,
                        subjectTitle = if (current.taskInput.isSubjectTitleEdited) {
                            current.taskInput.subjectTitle
                        } else {
                            derivedTitle
                        },
                    ),
                    validationErrors = current.validationErrors.copy(
                        taskError = null,
                        titleError = if (current.taskInput.isSubjectTitleEdited) {
                            current.validationErrors.titleError
                        } else {
                            null
                        },
                    ),
                    submitState = current.submitState.copy(sendError = null),
                )
            }

            is Event.SubjectTitleChanged -> updateState {
                it.copy(
                    taskInput = it.taskInput.copy(
                        subjectTitle = event.value,
                        isSubjectTitleEdited = true,
                    ),
                    validationErrors = it.validationErrors.copy(titleError = null),
                    submitState = it.submitState.copy(sendError = null),
                )
            }

            else -> Unit
        }
    }

    private fun handleAdvancedFieldEvent(event: Event) {
        when (event) {
            Event.AdvancedToggleClicked -> updateState {
                it.copy(
                    executionPolicyEditor = it.executionPolicyEditor.copy(
                        isExpanded = !it.executionPolicyEditor.isExpanded,
                    ),
                    submitState = it.submitState.copy(sendError = null),
                )
            }

            is Event.WorkdirChanged -> updateState {
                it.copy(
                    workspaceSelection = it.workspaceSelection.copy(workdir = event.value),
                    submitState = it.submitState.copy(sendError = null),
                )
            }

            is Event.ModeChanged -> updateState {
                it.copy(
                    executionPolicyEditor = it.executionPolicyEditor.copy(mode = event.mode),
                    submitState = it.submitState.copy(sendError = null),
                )
            }

            is Event.TimeoutChanged -> updateState {
                it.copy(
                    executionPolicyEditor = it.executionPolicyEditor.copy(timeoutText = event.value),
                    validationErrors = it.validationErrors.copy(
                        timeoutError = validateTimeoutText(event.value),
                    ),
                    submitState = it.submitState.copy(sendError = null),
                )
            }

            is Event.PermissionChanged -> updateState {
                it.copy(
                    executionPolicyEditor = it.executionPolicyEditor.copy(permission = event.permission),
                    submitState = it.submitState.copy(sendError = null),
                )
            }

            is Event.ProfileChanged -> updateState {
                it.copy(
                    executionPolicyEditor = it.executionPolicyEditor.copy(profile = event.value),
                    submitState = it.submitState.copy(sendError = null),
                )
            }

            is Event.AcceptanceChanged -> updateState {
                it.copy(
                    executionPolicyEditor = it.executionPolicyEditor.copy(acceptanceText = event.value),
                    submitState = it.submitState.copy(sendError = null),
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
                validationErrors = it.validationErrors.copy(senderAccountError = null),
                submitState = it.submitState.copy(
                    isSending = false,
                    sendError = null,
                ),
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
                        validationErrors = current.validationErrors.copy(senderAccountError = null),
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
                        validationErrors = it.validationErrors.copy(senderAccountError = null),
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
            updateState {
                it.copy(
                    submitState = it.submitState.copy(sendError = SEND_FAILURE_MESSAGE),
                )
            }
            return
        }

        updateState {
            it.copy(
                submitState = it.submitState.copy(
                    isSending = true,
                    sendError = null,
                ),
                lastDirectSendEvidence = null,
                validationErrors = TaskNewTaskValidationErrors(),
            )
        }

        viewModelScope.launch {
            val sendResult = runTaskMailDirectDispatch.execute(
                directSend = {
                    sendTaskMailDirectNewTask(draft).toDirectAttemptResult()
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

    private suspend fun handleSendResult(
        sendResult: TaskMailDirectDispatchResult<TaskMailDirectNewTaskResult.Accepted>,
    ) {
        when (sendResult) {
            is TaskMailDirectDispatchResult.DirectAccepted -> {
                updateState {
                    it.copy(
                        submitState = it.submitState.copy(
                            isSending = false,
                            sendError = null,
                        ),
                        lastDirectSendEvidence = sendResult.evidence,
                    )
                }
                emitEffect(Effect.ShowMessage(SEND_SUCCESS_MESSAGE))
                emitEffect(Effect.NavigateBack)
            }

            is TaskMailDirectDispatchResult.DirectRejected -> {
                updateState {
                    it.copy(
                        submitState = it.submitState.copy(
                            isSending = false,
                            sendError = sendResult.errorMessage.ifBlank { SEND_FAILURE_MESSAGE },
                        ),
                        lastDirectSendEvidence = sendResult.evidence,
                    )
                }
            }
        }
    }

    private fun showValidationErrors(validation: ValidationResult) {
        updateState {
            it.copy(
                validationErrors = TaskNewTaskValidationErrors(
                    senderAccountError = validation.senderAccountError,
                    backendError = validation.backendError,
                    repoError = validation.repoError,
                    taskError = validation.taskError,
                    titleError = validation.titleError,
                    timeoutError = validation.timeoutError,
                ),
                submitState = it.submitState.copy(sendError = null),
            )
        }
    }

    private fun validate(state: State): ValidationResult {
        val selectedSenderAccountId = state.selectedSenderAccountId
            ?.takeIf { accountUuid ->
                state.senderAccounts.any { it.accountUuid == accountUuid }
            }
        val repoPath = state.resolvedRepoBridgePath.orEmpty().trim()
        val taskText = state.taskInput.taskText.trim()
        val subjectTitle = state.taskInput.subjectTitle.trim()
        val timeoutText = state.executionPolicyEditor.timeoutText.trim()
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
            backendError = if (state.executionPolicyEditor.backend == null) BACKEND_REQUIRED_ERROR else null,
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
                validationErrors = it.validationErrors.copy(senderAccountError = null),
                submitState = it.submitState.copy(sendError = null),
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
        val backend = state.executionPolicyEditor.backend

        return if (senderAccountId != null && backend != null) {
            TaskMailNewTaskDraft(
                senderAccountId = senderAccountId,
                backend = backend,
                repoPath = normalizedRepoPath,
                taskText = normalizedTaskText,
                subjectTitle = normalizedSubjectTitle,
                workdir = state.resolvedWorkdirBridge,
                mode = state.executionPolicyEditor.mode,
                timeoutMinutes = normalizedTimeoutMinutes,
                permission = state.executionPolicyEditor.permission,
                profile = state.executionPolicyEditor.profile.trim().takeIf { it.isNotEmpty() },
                acceptanceCriteria = state.executionPolicyEditor.acceptanceText
                    .lineSequence()
                    .map(String::trim)
                    .filter(String::isNotEmpty)
                    .toList(),
                pcId = state.pcSelection.selectedPcId.trim().takeIf { it.isNotEmpty() },
                workspaceId = state.workspaceSelection.selectedWorkspaceId.trim().takeIf { it.isNotEmpty() },
                executionPolicy = state.executionPolicyEditor.toControlPlaneExecutionPolicy(),
            )
        } else {
            null
        }
    }
}

private fun TaskNewTaskExecutionPolicyUiState.toControlPlaneExecutionPolicy(): ControlPlaneExecutionPolicy {
    return ControlPlaneExecutionPolicy(
        backend = backend?.wireValue,
        profile = profile.trim().takeIf { it.isNotEmpty() },
        permission = when (permission) {
            TaskMailNewTaskPermission.Default -> "default"
            TaskMailNewTaskPermission.Highest -> "highest"
        },
        backendTransport = backendTransport.trim().takeIf { it.isNotEmpty() },
    )
}
