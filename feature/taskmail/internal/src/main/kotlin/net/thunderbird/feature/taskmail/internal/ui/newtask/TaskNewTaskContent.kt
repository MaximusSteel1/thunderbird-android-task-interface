package net.thunderbird.feature.taskmail.internal.ui.newtask

import android.content.Context
import android.text.format.Formatter
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.k9mail.core.ui.compose.designsystem.atom.card.CardOutlined
import app.k9mail.core.ui.compose.designsystem.atom.button.ButtonFilled
import app.k9mail.core.ui.compose.designsystem.atom.button.ButtonFilledTonal
import app.k9mail.core.ui.compose.designsystem.atom.button.ButtonOutlined
import app.k9mail.core.ui.compose.designsystem.atom.button.ButtonSegmentedSingleChoice
import app.k9mail.core.ui.compose.designsystem.atom.button.ButtonText
import app.k9mail.core.ui.compose.designsystem.atom.text.TextBodyLarge
import app.k9mail.core.ui.compose.designsystem.atom.text.TextBodyMedium
import app.k9mail.core.ui.compose.designsystem.atom.text.TextBodySmall
import app.k9mail.core.ui.compose.designsystem.atom.text.TextHeadlineSmall
import app.k9mail.core.ui.compose.designsystem.atom.text.TextLabelMedium
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
import net.thunderbird.feature.taskmail.internal.domain.model.TaskReplyAttachment
import net.thunderbird.feature.taskmail.internal.domain.newtask.TaskMailNewTaskMode
import net.thunderbird.feature.taskmail.internal.domain.newtask.TaskMailNewTaskPermission
import net.thunderbird.feature.taskmail.internal.ui.component.TaskSectionHeader

private val backendOptions = TaskMailBackend.entries.toImmutableList()
private val modeOptions = TaskMailNewTaskMode.entries.toImmutableList()
private const val EXECUTION_POLICY_SUPPORTING_TEXT =
    "Backend and advanced execution values stay available, but they should not crowd the task input."
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
    onPickAttachments: () -> Unit = {},
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
        if (state.isLoading) {
            NewTaskCenteredMessage(
                title = "Loading task setup",
                message = "Checking the latest route options and local TaskMail metadata.",
                modifier = Modifier.padding(innerPadding),
            )
        } else {
            TaskNewTaskForm(
                state = state,
                onEvent = onEvent,
                onPickAttachments = onPickAttachments,
                modifier = Modifier.padding(innerPadding),
            )
        }
    }
}

@Composable
private fun TaskNewTaskForm(
    state: TaskNewTaskContract.State,
    onEvent: (TaskNewTaskContract.Event) -> Unit,
    onPickAttachments: () -> Unit,
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
        routeTargetItems(
            state = state,
            onEvent = onEvent,
        )

        taskInputItems(
            state = state,
            onEvent = onEvent,
        )

        inputAttachmentItems(
            state = state,
            onEvent = onEvent,
            onPickAttachments = onPickAttachments,
        )

        executionAndDeliveryItems(
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
            title = "Target environment",
            supportingText = "Select the target PC and workspace first. This route target will be bound once when the new session is created.",
        )
    }

    item {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            PcSelector(
                state = state,
                onPcSelected = { selectedPcId ->
                    onEvent(TaskNewTaskContract.Event.PcChanged(selectedPcId ?: ""))
                },
            )
            RouteHint(
                optionsCount = state.pcSelection.pcOptions.size,
                emptyText = "PC inventory is unavailable right now. You can still enter the target ID manually.",
                nonEmptyText = "${state.pcSelection.pcOptions.size} PC option(s) are available from the live environment inventory.",
            )
        }
    }

    item {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            WorkspaceSelector(
                state = state,
                onWorkspaceSelected = { selectedWorkspaceId ->
                    onEvent(TaskNewTaskContract.Event.WorkspaceChanged(selectedWorkspaceId ?: ""))
                },
            )
            RouteHint(
                optionsCount = state.workspaceSelection.workspaceOptions.size,
                emptyText = if (state.pcSelection.selectedPcId.isBlank()) {
                    "Select a PC first, or enter the target workspace ID manually."
                } else {
                    "No routable workspace is currently exposed for the selected PC. Manual entry is still available."
                },
                nonEmptyText = "${state.workspaceSelection.workspaceOptions.size} workspace option(s) are available on the selected PC.",
            )
        }
    }

    repoPathItem(
        state = state,
        onEvent = onEvent,
    )

    item {
        TextInput(
            onTextChange = { onEvent(TaskNewTaskContract.Event.WorkdirChanged(it)) },
            text = state.workspaceSelection.workdir,
            label = "Workdir",
        )
    }
}

private fun LazyListScope.taskInputItems(
    state: TaskNewTaskContract.State,
    onEvent: (TaskNewTaskContract.Event) -> Unit,
) {
    item {
        TaskSectionHeader(
            title = "Task input",
            supportingText = "Describe the work to be done. The title still defaults to the first non-empty line unless you override it.",
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
}

private fun LazyListScope.inputAttachmentItems(
    state: TaskNewTaskContract.State,
    onEvent: (TaskNewTaskContract.Event) -> Unit,
    onPickAttachments: () -> Unit,
) {
    item {
        TaskSectionHeader(
            title = "Input attachments",
            supportingText = if (state.taskInput.attachments.isEmpty()) {
                "Add screenshots, documents, or other files as part of the initial session input."
            } else {
                "Selected files will be submitted as part of the first round input."
            },
        )
    }

    item {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ButtonOutlined(
                text = if (state.taskInput.attachments.isEmpty()) "Add files" else "Add more files",
                onClick = onPickAttachments,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("TaskNewTaskAddAttachmentsButton"),
            )
        }
    }

    state.taskInput.attachments.forEach { attachment ->
        item(key = "input_attachment_${attachment.id}") {
            InputAttachmentRow(
                attachment = attachment,
                onRemoveAttachment = { attachmentId ->
                    onEvent(TaskNewTaskContract.Event.RemoveAttachmentClicked(attachmentId))
                },
            )
        }
    }
}

private fun LazyListScope.executionAndDeliveryItems(
    state: TaskNewTaskContract.State,
    onEvent: (TaskNewTaskContract.Event) -> Unit,
) {
    item {
        TaskSectionHeader(
            title = "Execution and delivery",
            supportingText = EXECUTION_POLICY_SUPPORTING_TEXT,
        )
    }

    item {
        BackendSelector(
            options = state.executionPolicyEditor.availableBackends
                .takeIf { it.isNotEmpty() }
                ?: backendOptions,
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
        TextInput(
            onTextChange = { onEvent(TaskNewTaskContract.Event.ProfileChanged(it)) },
            text = state.executionPolicyEditor.profile,
            label = "Profile",
        )
    }

    item {
        SelectInput(
            options = state.executionPolicyEditor.availablePermissions,
            selectedOption = state.executionPolicyEditor.permission,
            onOptionChange = { onEvent(TaskNewTaskContract.Event.PermissionChanged(it)) },
            optionToStringTransformation = permissionLabel,
            label = "Permission",
        )
    }

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
        TextInput(
            onTextChange = { onEvent(TaskNewTaskContract.Event.AcceptanceChanged(it)) },
            text = state.executionPolicyEditor.acceptanceText,
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
                text = state.workspaceSelection.repoPath,
                label = "Repository context",
                isRequired = state.resolvedRepoBridgePath == null,
                errorMessage = state.validationErrors.repoError,
            )
            RouteHint(
                optionsCount = if (state.selectedWorkspaceOption?.repoPath != null) 1 else 0,
                emptyText = if (state.hasControlPlaneRouteTarget) {
                    "Keep repository context visible for now. The selected route target still depends on it during submit."
                } else {
                    "Pick a workspace target first, or enter repository context manually."
                },
                nonEmptyText = "Repository context is already available from the selected workspace option and can still be overridden here.",
            )
            ButtonFilledTonal(
                text = "Choose repository from project list",
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
    options: List<TaskMailBackend>,
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
                options = options.toImmutableList(),
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
private fun PcSelector(
    state: TaskNewTaskContract.State,
    onPcSelected: (String?) -> Unit,
) {
    if (state.pcSelection.pcOptions.isEmpty()) {
        TextInput(
            onTextChange = { onPcSelected(it) },
            text = state.pcSelection.selectedPcId,
            label = "PC",
            isRequired = true,
            errorMessage = state.validationErrors.pcError,
        )
        return
    }

    val options = buildList<PcSelectionOption> {
        add(PcSelectionOption.Placeholder)
        addAll(state.pcSelection.pcOptions.map(PcSelectionOption::Pc))
    }.toImmutableList()
    val selectedOption = state.pcSelection.pcOptions
        .firstOrNull { option -> option.id == state.pcSelection.selectedPcId }
        ?.let(PcSelectionOption::Pc)
        ?: PcSelectionOption.Placeholder

    InputLayout(
        errorMessage = state.validationErrors.pcError,
        contentPadding = PaddingValues(0.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            TextFieldOutlinedSelect(
                options = options,
                selectedOption = selectedOption,
                onValueChange = { option ->
                    onPcSelected(
                        when (option) {
                            PcSelectionOption.Placeholder -> null
                            is PcSelectionOption.Pc -> option.option.id
                        },
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("TaskNewTaskPcSelector"),
                optionToStringTransformation = { option ->
                    when (option) {
                        PcSelectionOption.Placeholder -> "Select a PC"
                        is PcSelectionOption.Pc -> option.option.title
                    }
                },
                label = "PC",
                hasError = state.validationErrors.pcError != null,
            )
            (selectedOption as? PcSelectionOption.Pc)
                ?.option
                ?.supportingText
                ?.let { supportingText ->
                    TextBodySmall(
                        text = supportingText,
                        color = MainTheme.colors.onSurfaceVariant,
                    )
                }
        }
    }
}

@Composable
private fun WorkspaceSelector(
    state: TaskNewTaskContract.State,
    onWorkspaceSelected: (String?) -> Unit,
) {
    if (state.workspaceSelection.workspaceOptions.isEmpty()) {
        TextInput(
            onTextChange = { onWorkspaceSelected(it) },
            text = state.workspaceSelection.selectedWorkspaceId,
            label = "Workspace",
            isRequired = true,
            errorMessage = state.validationErrors.workspaceError,
        )
        return
    }

    val options = buildList<WorkspaceSelectionOption> {
        add(WorkspaceSelectionOption.Placeholder)
        addAll(state.workspaceSelection.workspaceOptions.map(WorkspaceSelectionOption::Workspace))
    }.toImmutableList()
    val selectedOption = state.workspaceSelection.workspaceOptions
        .firstOrNull { option -> option.id == state.workspaceSelection.selectedWorkspaceId }
        ?.let(WorkspaceSelectionOption::Workspace)
        ?: WorkspaceSelectionOption.Placeholder

    InputLayout(
        errorMessage = state.validationErrors.workspaceError,
        contentPadding = PaddingValues(0.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            TextFieldOutlinedSelect(
                options = options,
                selectedOption = selectedOption,
                onValueChange = { option ->
                    onWorkspaceSelected(
                        when (option) {
                            WorkspaceSelectionOption.Placeholder -> null
                            is WorkspaceSelectionOption.Workspace -> option.option.id
                        },
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("TaskNewTaskWorkspaceSelector"),
                optionToStringTransformation = { option ->
                    when (option) {
                        WorkspaceSelectionOption.Placeholder -> "Select a workspace"
                        is WorkspaceSelectionOption.Workspace -> option.option.title
                    }
                },
                label = "Workspace",
                hasError = state.validationErrors.workspaceError != null,
            )
            (selectedOption as? WorkspaceSelectionOption.Workspace)
                ?.option
                ?.supportingText
                ?.let { supportingText ->
                    TextBodySmall(
                        text = supportingText,
                        color = MainTheme.colors.onSurfaceVariant,
                    )
                }
        }
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

        TaskSectionHeader(
            title = "Create session",
            supportingText = "Sending this form creates a new session on the selected target environment.",
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

@Composable
private fun InputAttachmentRow(
    attachment: TaskReplyAttachment,
    onRemoveAttachment: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val metadata = buildInputAttachmentMetadataText(context, attachment)

    CardOutlined(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            TextLabelMedium(
                text = if (attachment.isImage) "IMG" else "FILE",
                color = MainTheme.colors.onSurfaceVariant,
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                TextBodyMedium(
                    text = attachment.displayName,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                metadata?.let {
                    TextBodySmall(
                        text = it,
                        color = MainTheme.colors.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (attachment.isImage) {
                    TextLabelMedium(
                        text = "Image",
                        color = MainTheme.colors.onSurfaceVariant,
                    )
                }
            }
            ButtonText(
                text = "Remove",
                onClick = { onRemoveAttachment(attachment.id) },
                modifier = Modifier.testTag("TaskNewTaskRemoveAttachment_${attachment.id.hashCode()}"),
            )
        }
    }
}

private fun buildInputAttachmentMetadataText(
    context: Context,
    attachment: TaskReplyAttachment,
): String? {
    val metadata = buildList {
        attachment.contentType?.let(::add)
        attachment.sizeBytes
            ?.takeIf { it >= 0L }
            ?.let { sizeBytes ->
                add(Formatter.formatShortFileSize(context, sizeBytes))
            }
    }

    return metadata.takeIf { it.isNotEmpty() }?.joinToString(separator = " | ")
}

private sealed interface PcSelectionOption {
    data object Placeholder : PcSelectionOption
    data class Pc(val option: TaskNewTaskPcOptionUi) : PcSelectionOption
}

private sealed interface WorkspaceSelectionOption {
    data object Placeholder : WorkspaceSelectionOption
    data class Workspace(val option: TaskNewTaskWorkspaceOptionUi) : WorkspaceSelectionOption
}
