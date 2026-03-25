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
private const val ADVANCED_OPTIONS_SUPPORTING_TEXT =
    "Only set these when the default first-task request is not enough."
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
                text = "Create a new TaskMail request. " +
                    "This sends a first message to your configured TaskMail service address.",
            )
        }

        senderAccountItems(
            state = state,
            onEvent = onEvent,
        )

        controlTargetItems(
            state = state,
            onEvent = onEvent,
        )

        requiredItems(
            state = state,
            onEvent = onEvent,
        )

        item {
            ButtonFilledTonal(
                text = if (state.isAdvancedExpanded) {
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

        advancedItems(
            state = state,
            onEvent = onEvent,
        )

        latestDirectEvidenceItem(state)

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

private fun LazyListScope.requiredItems(
    state: TaskNewTaskContract.State,
    onEvent: (TaskNewTaskContract.Event) -> Unit,
) {
    item {
        TaskSectionHeader(
            title = "Required fields",
            supportingText = "Choose a backend, the repo path, and the task you want to send.",
        )
    }

    item {
        BackendSelector(
            selectedBackend = state.selectedBackend,
            errorMessage = state.backendError,
            onBackendSelected = {
                onEvent(TaskNewTaskContract.Event.BackendSelected(it))
            },
        )
    }

    repoPathItem(
        state = state,
        onEvent = onEvent,
    )

    item {
        TextInput(
            onTextChange = {
                onEvent(TaskNewTaskContract.Event.TaskChanged(it))
            },
            text = state.taskText,
            label = "Task",
            isRequired = true,
            errorMessage = state.taskError,
            isSingleLine = false,
        )
    }

    item {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            TaskSectionHeader(
                title = "Title",
                supportingText = "The title becomes the mail subject text after the backend prefix. " +
                    "By default it follows the first non-empty line of Task.",
            )
            TextInput(
                onTextChange = {
                    onEvent(TaskNewTaskContract.Event.SubjectTitleChanged(it))
                },
                text = state.subjectTitle,
                label = "Title",
                isRequired = true,
                errorMessage = state.titleError,
            )
        }
    }
}

private fun LazyListScope.controlTargetItems(
    state: TaskNewTaskContract.State,
    onEvent: (TaskNewTaskContract.Event) -> Unit,
) {
    item {
        TaskSectionHeader(
            title = "Control target",
            supportingText = "Prepare the future VPS-first route target. These stay optional while repo-based sending is still active.",
        )
    }

    item {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            TaskSectionHeader(
                title = "PC ID",
                supportingText = "Target PC for the future control-plane session.",
            )
            TextInput(
                onTextChange = {
                    onEvent(TaskNewTaskContract.Event.PcChanged(it))
                },
                text = state.pcId,
                label = "PC ID",
            )
        }
    }

    item {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            TaskSectionHeader(
                title = "Workspace ID",
                supportingText = "Target workspace on the selected PC.",
            )
            TextInput(
                onTextChange = {
                    onEvent(TaskNewTaskContract.Event.WorkspaceChanged(it))
                },
                text = state.workspaceId,
                label = "Workspace ID",
            )
        }
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

private fun LazyListScope.advancedItems(
    state: TaskNewTaskContract.State,
    onEvent: (TaskNewTaskContract.Event) -> Unit,
) {
    if (!state.isAdvancedExpanded) return

    item { TaskSectionHeader(title = "Advanced options", supportingText = ADVANCED_OPTIONS_SUPPORTING_TEXT) }

    item {
        TextInput(
            onTextChange = { onEvent(TaskNewTaskContract.Event.WorkdirChanged(it)) },
            text = state.workdir,
            label = "Workdir",
        )
    }
    item {
        SelectInput(
            options = modeOptions,
            selectedOption = state.mode,
            onOptionChange = { onEvent(TaskNewTaskContract.Event.ModeChanged(it)) },
            optionToStringTransformation = modeLabel,
            label = "Mode",
        )
    }
    item {
        TextInput(
            onTextChange = { onEvent(TaskNewTaskContract.Event.TimeoutChanged(it)) },
            text = state.timeoutText,
            label = "Timeout (minutes)",
            errorMessage = state.timeoutError,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        )
    }
    item {
        SelectInput(
            options = permissionOptions,
            selectedOption = state.permission,
            onOptionChange = { onEvent(TaskNewTaskContract.Event.PermissionChanged(it)) },
            optionToStringTransformation = permissionLabel,
            label = "Permission",
        )
    }
    item {
        TextInput(
            onTextChange = { onEvent(TaskNewTaskContract.Event.ProfileChanged(it)) },
            text = state.profile,
            label = "Profile",
        )
    }
    item {
        TextInput(
            onTextChange = { onEvent(TaskNewTaskContract.Event.AcceptanceChanged(it)) },
            text = state.acceptanceText,
            label = "Acceptance",
            isSingleLine = false,
        )
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
                text = state.repoPath,
                label = "Repo",
                isRequired = true,
                errorMessage = state.repoError,
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
        errorMessage = state.senderAccountError,
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
            hasError = state.senderAccountError != null,
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
        state.sendError?.let { sendError ->
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
            text = "TaskMail requests are sent from this account to your configured TaskMail service address.",
            color = MainTheme.colors.onSurfaceVariant,
        )

        ButtonFilled(
            text = if (state.isSending) "Sending..." else "Send task",
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
