package net.thunderbird.feature.taskmail.internal.ui.newtask

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.k9mail.core.ui.compose.designsystem.atom.button.ButtonFilled
import app.k9mail.core.ui.compose.designsystem.atom.button.ButtonFilledTonal
import app.k9mail.core.ui.compose.designsystem.atom.button.ButtonSegmentedSingleChoice
import app.k9mail.core.ui.compose.designsystem.atom.button.ButtonText
import app.k9mail.core.ui.compose.designsystem.atom.text.TextBodyLarge
import app.k9mail.core.ui.compose.designsystem.atom.text.TextBodySmall
import app.k9mail.core.ui.compose.designsystem.atom.text.TextHeadlineSmall
import app.k9mail.core.ui.compose.designsystem.atom.text.TextLabelMedium
import app.k9mail.core.ui.compose.designsystem.atom.textfield.TextFieldOutlined
import app.k9mail.core.ui.compose.designsystem.atom.textfield.TextFieldOutlinedSelect
import app.k9mail.core.ui.compose.designsystem.molecule.input.InputLayout
import app.k9mail.core.ui.compose.designsystem.molecule.input.SelectInput
import app.k9mail.core.ui.compose.designsystem.molecule.input.TextInput
import app.k9mail.core.ui.compose.designsystem.organism.TopAppBarWithBackButton
import app.k9mail.core.ui.compose.designsystem.organism.banner.inline.ErrorBannerInlineNotificationCard
import app.k9mail.core.ui.compose.designsystem.template.Scaffold
import kotlinx.collections.immutable.toImmutableList
import net.thunderbird.core.ui.compose.theme2.MainTheme
import net.thunderbird.feature.taskmail.internal.domain.model.TaskMailBackend
import net.thunderbird.feature.taskmail.internal.domain.model.TaskMailSenderAccount
import net.thunderbird.feature.taskmail.internal.domain.newtask.TaskMailNewTaskMode
import net.thunderbird.feature.taskmail.internal.domain.newtask.TaskMailNewTaskPermission
import net.thunderbird.feature.taskmail.internal.ui.component.TaskSectionHeader

private val backendOptions = TaskMailBackend.entries.toImmutableList()
private val modeOptions = TaskMailNewTaskMode.entries.toImmutableList()
private val permissionOptions = TaskMailNewTaskPermission.entries.toImmutableList()
private const val EXECUTION_POLICY_SUPPORTING_TEXT =
    "Backend is part of the new control-plane shape. Advanced values stay optional for now."
private const val SUBMIT_REQUIREMENTS_SUPPORTING_TEXT =
    "PC/workspace is now the required route target. " +
        "Sender identity plus repository context still stay visible while routed workspace inventory is being hydrated."
private val backendLabel: (TaskMailBackend) -> String = { backend ->
    when (backend) {
        TaskMailBackend.OpenCode -> "OpenCode"
        TaskMailBackend.Codex -> "Codex"
    }
}
private val modeLabel: (TaskMailNewTaskMode) -> String = { mode ->
    when (mode) {
        TaskMailNewTaskMode.Modify -> "Modify (default)"
        TaskMailNewTaskMode.AnalysisOnly -> "Analysis only"
    }
}
private val permissionLabel: (TaskMailNewTaskPermission) -> String = { permission ->
    when (permission) {
        TaskMailNewTaskPermission.Default -> "Use backend default"
        TaskMailNewTaskPermission.Highest -> "Highest"
    }
}

@Composable
internal fun TaskNewTaskContent(
    state: TaskNewTaskContract.State,
    onEvent: (TaskNewTaskContract.Event) -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBarWithBackButton(
                title = "New task",
                onBackClick = { onEvent(TaskNewTaskContract.Event.BackClicked) },
            )
        },
    ) { innerPadding ->
        when {
            state.isLoading -> {
                NewTaskCenteredMessage(
                    title = "Loading sender accounts",
                    message = "Checking which mailbox accounts can send this TaskMail request.",
                    modifier = Modifier.padding(innerPadding),
                )
            }

            state.hasBlockingState -> {
                NewTaskCenteredMessage(
                    title = "Cannot send TaskMail yet",
                    message = state.senderAccountBlockingError.orEmpty(),
                    modifier = Modifier.padding(innerPadding),
                )
            }

            else -> {
                TaskNewTaskForm(
                    state = state,
                    onEvent = onEvent,
                    modifier = Modifier.padding(innerPadding),
                )
            }
        }
    }
}

@Composable
private fun TaskNewTaskForm(
    state: TaskNewTaskContract.State,
    onEvent: (TaskNewTaskContract.Event) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .imePadding()
            .testTag("TaskNewTaskFormList"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            TextBodyLarge(
                text = "Create a new TaskMail session on the selected route target. " +
                    "The page now starts from PC and workspace routing. " +
                    "Sender account plus repository context stays visible while the routed workspace bridge is still settling.",
            )
        }

        routeTargetItems(
            state = state,
            onEvent = onEvent,
        )

        taskInputItems(
            state = state,
            onEvent = onEvent,
        )

        executionPolicyItems(
            state = state,
            onEvent = onEvent,
        )

        submitRequirementsItems(
            state = state,
            onEvent = onEvent,
        )

        item {
            TaskNewTaskSendSection(
                state = state,
                onSend = {
                    onEvent(TaskNewTaskContract.Event.SendClicked)
                },
                onDismissSendError = {
                    onEvent(TaskNewTaskContract.Event.DismissSendError)
                },
            )
        }
    }
}

private fun LazyListScope.routeTargetItems(
    state: TaskNewTaskContract.State,
    onEvent: (TaskNewTaskContract.Event) -> Unit,
) {
    item {
        TaskSectionHeader(
            title = "Route target",
            supportingText = "Pick the intended PC and workspace first. Both fields are required for create-session submit.",
        )
    }

    item {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            TextInput(
                onTextChange = {
                    onEvent(TaskNewTaskContract.Event.PcChanged(it))
                },
                text = state.pcSelection.selectedPcId,
                label = "PC ID",
                isRequired = true,
                errorMessage = state.validationErrors.pcError,
            )
            RouteHint(
                optionsCount = state.pcSelection.pcOptions.size,
                emptyText = "PC list is not wired yet. Enter the intended target ID manually for now.",
                nonEmptyText = "${state.pcSelection.pcOptions.size} PC option(s) available.",
            )
        }
    }

    item {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            TextInput(
                onTextChange = {
                    onEvent(TaskNewTaskContract.Event.WorkspaceChanged(it))
                },
                text = state.workspaceSelection.selectedWorkspaceId,
                label = "Workspace ID",
                isRequired = true,
                errorMessage = state.validationErrors.workspaceError,
            )
            RouteHint(
                optionsCount = state.workspaceSelection.workspaceOptions.size,
                emptyText = "Workspace list is not wired yet. Enter the intended workspace ID manually for now.",
                nonEmptyText = "${state.workspaceSelection.workspaceOptions.size} workspace option(s) available.",
            )
        }
    }
}

private fun LazyListScope.taskInputItems(
    state: TaskNewTaskContract.State,
    onEvent: (TaskNewTaskContract.Event) -> Unit,
) {
    item {
        TaskSectionHeader(
            title = "Task input",
            supportingText = "Describe the task first. The mail subject still derives from the first non-empty line unless you override it.",
        )
    }

    item {
        TextInput(
            onTextChange = {
                onEvent(TaskNewTaskContract.Event.TaskChanged(it))
            },
            text = state.taskInput.taskText,
            label = "Task",
            isRequired = true,
            errorMessage = state.validationErrors.taskError,
            isSingleLine = false,
        )
    }

    item {
        TextInput(
            onTextChange = {
                onEvent(TaskNewTaskContract.Event.SubjectTitleChanged(it))
            },
            text = state.taskInput.subjectTitle,
            label = "Title",
            isRequired = true,
            errorMessage = state.validationErrors.titleError,
        )
    }
}

private fun LazyListScope.executionPolicyItems(
    state: TaskNewTaskContract.State,
    onEvent: (TaskNewTaskContract.Event) -> Unit,
) {
    item {
        TaskSectionHeader(
            title = "Execution policy",
            supportingText = EXECUTION_POLICY_SUPPORTING_TEXT,
        )
    }

    item {
        BackendSelector(
            selectedBackend = state.executionPolicyEditor.backend,
            errorMessage = state.validationErrors.backendError,
            onBackendSelected = {
                onEvent(TaskNewTaskContract.Event.BackendSelected(it))
            },
        )
    }

    item {
        ButtonFilledTonal(
            text = if (state.executionPolicyEditor.isExpanded) {
                "Hide advanced options"
            } else {
                "Advanced options"
            },
            onClick = { onEvent(TaskNewTaskContract.Event.AdvancedToggleClicked) },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("TaskNewTaskAdvancedToggle"),
        )
    }

    if (!state.executionPolicyEditor.isExpanded) return

    item {
        SelectInput(
            options = modeOptions,
            selectedOption = state.executionPolicyEditor.mode,
            onOptionChange = { onEvent(TaskNewTaskContract.Event.ModeChanged(it)) },
            optionToStringTransformation = modeLabel,
            label = "Mode",
        )
    }

    item {
        TextInput(
            onTextChange = { onEvent(TaskNewTaskContract.Event.TimeoutChanged(it)) },
            text = state.executionPolicyEditor.timeoutText,
            label = "Timeout (minutes)",
            errorMessage = state.validationErrors.timeoutError,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        )
    }

    item {
        SelectInput(
            options = permissionOptions,
            selectedOption = state.executionPolicyEditor.permission,
            onOptionChange = { onEvent(TaskNewTaskContract.Event.PermissionChanged(it)) },
            optionToStringTransformation = permissionLabel,
            label = "Permission",
        )
    }

    item {
        TextInput(
            onTextChange = { onEvent(TaskNewTaskContract.Event.ProfileChanged(it)) },
            text = state.executionPolicyEditor.profile,
            label = "Profile",
        )
    }

    item {
        TextInput(
            onTextChange = { onEvent(TaskNewTaskContract.Event.AcceptanceChanged(it)) },
            text = state.executionPolicyEditor.acceptanceText,
            label = "Acceptance",
            isSingleLine = false,
        )
    }
}

private fun LazyListScope.submitRequirementsItems(
    state: TaskNewTaskContract.State,
    onEvent: (TaskNewTaskContract.Event) -> Unit,
) {
    item {
        TaskSectionHeader(
            title = "Current submit bridge",
            supportingText = SUBMIT_REQUIREMENTS_SUPPORTING_TEXT,
        )
    }

    senderAccountItems(
        state = state,
        onEvent = onEvent,
    )

    repoPathItem(
        state = state,
        onEvent = onEvent,
    )

    if (!state.executionPolicyEditor.isExpanded) return

    item {
        TextInput(
            onTextChange = { onEvent(TaskNewTaskContract.Event.WorkdirChanged(it)) },
            text = state.workspaceSelection.workdir,
            label = "Workdir",
        )
    }
}

private fun LazyListScope.senderAccountItems(
    state: TaskNewTaskContract.State,
    onEvent: (TaskNewTaskContract.Event) -> Unit,
) {
    when {
        state.senderAccounts.size == 1 -> {
            item {
                InputLayout(contentPadding = PaddingValues(0.dp)) {
                    TextFieldOutlined(
                        value = (state.selectedSenderAccount ?: state.senderAccounts.single()).displayLabel,
                        onValueChange = {},
                        label = "Send from",
                        isReadOnly = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }

        state.requiresSenderAccountSelection -> {
            item {
                SenderAccountSelector(
                    state = state,
                    onAccountSelected = {
                        onEvent(TaskNewTaskContract.Event.SenderAccountSelected(it))
                    },
                )
            }
        }
    }
}

private fun LazyListScope.repoPathItem(
    state: TaskNewTaskContract.State,
    onEvent: (TaskNewTaskContract.Event) -> Unit,
) {
    item {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            TextInput(
                onTextChange = {
                    onEvent(TaskNewTaskContract.Event.RepoChanged(it))
                },
                text = state.workspaceSelection.repoPath,
                label = "Repository bridge",
                isRequired = state.resolvedRepoBridgePath == null,
                errorMessage = state.validationErrors.repoError,
            )
            RouteHint(
                optionsCount = if (state.selectedWorkspaceOption?.repoPath != null) 1 else 0,
                emptyText = if (state.hasControlPlaneRouteTarget) {
                    "Keep repository context for now. The current routed workspace bridge still uses it during submit."
                } else {
                    "Pick a workspace target first, or enter repository context manually."
                },
                nonEmptyText = "Repository context is already available from the selected workspace option and can still be overridden here.",
            )
            ButtonFilledTonal(
                text = "Choose from project list",
                onClick = { onEvent(TaskNewTaskContract.Event.ChooseRepoClicked) },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("TaskNewTaskChooseRepoButton"),
            )
        }
    }
}

@Composable
private fun RouteHint(
    optionsCount: Int,
    emptyText: String,
    nonEmptyText: String,
) {
    TextBodySmall(
        text = if (optionsCount == 0) emptyText else nonEmptyText,
        color = MainTheme.colors.onSurfaceVariant,
    )
}

@Composable
private fun BackendSelector(
    selectedBackend: TaskMailBackend?,
    errorMessage: String?,
    onBackendSelected: (TaskMailBackend) -> Unit,
) {
    InputLayout(
        errorMessage = errorMessage,
        contentPadding = PaddingValues(0.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            TextLabelMedium(text = "Backend")
            ButtonSegmentedSingleChoice(
                onClick = onBackendSelected,
                options = backendOptions,
                optionTitle = backendLabel,
                selectedOption = selectedBackend,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("TaskNewTaskBackendSelector"),
            )
        }
    }
}

@Composable
private fun SenderAccountSelector(
    state: TaskNewTaskContract.State,
    onAccountSelected: (String?) -> Unit,
) {
    val options = buildList<SenderAccountOption> {
        add(SenderAccountOption.Placeholder)
        addAll(state.senderAccounts.map(SenderAccountOption::Account))
    }.toImmutableList()
    val selectedOption = state.selectedSenderAccount
        ?.let(SenderAccountOption::Account)
        ?: SenderAccountOption.Placeholder

    InputLayout(
        errorMessage = state.validationErrors.senderAccountError,
        contentPadding = PaddingValues(0.dp),
    ) {
        TextFieldOutlinedSelect(
            options = options,
            selectedOption = selectedOption,
            onValueChange = { option ->
                onAccountSelected(
                    when (option) {
                        SenderAccountOption.Placeholder -> null
                        is SenderAccountOption.Account -> option.account.accountUuid
                    },
                )
            },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("TaskNewTaskSenderAccountSelector"),
            optionToStringTransformation = { option ->
                when (option) {
                    SenderAccountOption.Placeholder -> "Select an account"
                    is SenderAccountOption.Account -> option.account.displayLabel
                }
            },
            label = "Send from",
            hasError = state.validationErrors.senderAccountError != null,
        )
    }
}

@Composable
private fun TaskNewTaskSendSection(
    state: TaskNewTaskContract.State,
    onSend: () -> Unit,
    onDismissSendError: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        state.submitState.sendError?.let { sendError ->
            ErrorBannerInlineNotificationCard(
                title = "TaskMail send failed",
                supportingText = sendError,
                actions = {
                    ButtonText(
                        text = "Dismiss",
                        onClick = onDismissSendError,
                    )
                },
            )
        }

        TextBodySmall(
            text = "PC/workspace is the route target now. " +
                "Sender identity plus repository context remain visible until routed workspace data is fully hydrated.",
            color = MainTheme.colors.onSurfaceVariant,
        )

        ButtonFilled(
            text = if (state.submitState.isSending) "Sending..." else "Send task",
            onClick = onSend,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("TaskNewTaskSendButton"),
            enabled = state.canSend,
        )
    }
}

@Composable
private fun NewTaskCenteredMessage(
    title: String,
    message: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        TextHeadlineSmall(text = title)
        TextBodyLarge(
            text = message,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

private sealed interface SenderAccountOption {
    data object Placeholder : SenderAccountOption
    data class Account(val account: TaskMailSenderAccount) : SenderAccountOption
}
