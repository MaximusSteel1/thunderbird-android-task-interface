package net.thunderbird.feature.taskmail.internal.ui.projectsync

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import app.k9mail.core.ui.compose.designsystem.atom.button.ButtonFilled
import app.k9mail.core.ui.compose.designsystem.atom.button.ButtonFilledTonal
import app.k9mail.core.ui.compose.designsystem.atom.button.ButtonText
import app.k9mail.core.ui.compose.designsystem.atom.text.TextBodyLarge
import app.k9mail.core.ui.compose.designsystem.atom.text.TextBodySmall
import app.k9mail.core.ui.compose.designsystem.atom.text.TextHeadlineSmall
import app.k9mail.core.ui.compose.designsystem.atom.text.TextLabelMedium
import app.k9mail.core.ui.compose.designsystem.atom.textfield.TextFieldOutlined
import app.k9mail.core.ui.compose.designsystem.atom.textfield.TextFieldOutlinedSelect
import app.k9mail.core.ui.compose.designsystem.molecule.input.InputLayout
import app.k9mail.core.ui.compose.designsystem.organism.TopAppBarWithBackButton
import app.k9mail.core.ui.compose.designsystem.organism.banner.inline.ErrorBannerInlineNotificationCard
import app.k9mail.core.ui.compose.designsystem.organism.banner.inline.WarningBannerInlineNotificationCard
import app.k9mail.core.ui.compose.designsystem.template.Scaffold
import kotlinx.collections.immutable.toImmutableList
import net.thunderbird.core.ui.compose.theme2.MainTheme
import net.thunderbird.feature.taskmail.internal.domain.model.TaskMailProjectSyncResult
import net.thunderbird.feature.taskmail.internal.domain.model.TaskMailProjectSyncRoot
import net.thunderbird.feature.taskmail.internal.domain.model.TaskMailSenderAccount
import net.thunderbird.feature.taskmail.internal.ui.component.TaskSectionHeader

@Composable
internal fun TaskProjectSyncContent(
    state: TaskProjectSyncContract.State,
    onEvent: (TaskProjectSyncContract.Event) -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBarWithBackButton(
                title = "Project list",
                onBackClick = { onEvent(TaskProjectSyncContract.Event.BackClicked) },
            )
        },
    ) { innerPadding ->
        when {
            state.isLoading -> {
                ProjectSyncCenteredMessage(
                    title = "Loading sender accounts",
                    message = "Checking which mailbox accounts can request the TaskMail project list.",
                    modifier = Modifier.padding(innerPadding),
                )
            }

            state.hasBlockingState -> {
                ProjectSyncCenteredMessage(
                    title = "Cannot load projects yet",
                    message = state.senderAccountBlockingError.orEmpty(),
                    modifier = Modifier.padding(innerPadding),
                )
            }

            else -> {
                ProjectSyncBody(
                    state = state,
                    onEvent = onEvent,
                    modifier = Modifier.padding(innerPadding),
                )
            }
        }
    }
}

@Composable
private fun ProjectSyncBody(
    state: TaskProjectSyncContract.State,
    onEvent: (TaskProjectSyncContract.Event) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("TaskProjectSyncList"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            TextBodyLarge(text = PROJECT_SYNC_INTRO_TEXT)
        }

        senderAccountItems(
            state = state,
            onEvent = onEvent,
        )

        syncActionItems(
            state = state,
            onEvent = onEvent,
        )

        resultItems(
            state = state,
            onEvent = onEvent,
        )
    }
}

private fun LazyListScope.syncActionItems(
    state: TaskProjectSyncContract.State,
    onEvent: (TaskProjectSyncContract.Event) -> Unit,
) {
    item {
        ButtonFilled(
            text = if (state.isSyncing) "Syncing project list..." else "Sync project list",
            onClick = { onEvent(TaskProjectSyncContract.Event.SyncRequested) },
            enabled = state.canRequestSync,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("TaskProjectSyncSyncButton"),
        )
    }

    state.syncError?.let { syncError ->
        item {
            ErrorBannerInlineNotificationCard(
                title = "Project sync failed",
                supportingText = syncError,
                actions = {
                    ButtonText(
                        text = "Dismiss",
                        onClick = { onEvent(TaskProjectSyncContract.Event.DismissSyncError) },
                    )
                },
            )
        }
    }

    state.resultError?.let { resultError ->
        item {
            WarningBannerInlineNotificationCard(
                title = "Unable to read the latest project list",
                supportingText = resultError,
                actions = {},
            )
        }
    }
}

private fun LazyListScope.resultItems(
    state: TaskProjectSyncContract.State,
    onEvent: (TaskProjectSyncContract.Event) -> Unit,
) {
    when {
        state.isLoadingResult -> {
            item {
                TaskSectionHeader(
                    title = "Loading latest project list",
                    supportingText = "Reading the newest [SYNC] reply from local mail.",
                )
            }
        }

        state.latestResult == null -> {
            item {
                TaskSectionHeader(
                    title = "No synced project list yet",
                    supportingText = "Send a [SYNC] request to fetch the current allowlisted project folders.",
                )
            }
        }

        else -> {
            latestResultItems(
                result = state.latestResult,
                onEvent = onEvent,
            )
        }
    }
}

private fun LazyListScope.senderAccountItems(
    state: TaskProjectSyncContract.State,
    onEvent: (TaskProjectSyncContract.Event) -> Unit,
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
                        onEvent(TaskProjectSyncContract.Event.SenderAccountSelected(it))
                    },
                )
            }
        }
    }
}

private fun LazyListScope.latestResultItems(
    result: TaskMailProjectSyncResult,
    onEvent: (TaskProjectSyncContract.Event) -> Unit,
) {
    item {
        TaskSectionHeader(
            title = "Latest project list",
            supportingText = result.scannedAt?.let { scannedAt ->
                "Scanned at $scannedAt"
            } ?: "Choose a repo path and send it back to the new-task form.",
        )
    }

    result.roots.forEach { root ->
        item {
            ProjectSyncRootCard(
                root = root,
                onUseRepo = { repoPath ->
                    onEvent(TaskProjectSyncContract.Event.UseRepoClicked(repoPath))
                },
            )
        }
    }
}

@Composable
private fun SenderAccountSelector(
    state: TaskProjectSyncContract.State,
    onAccountSelected: (String?) -> Unit,
) {
    val options = buildList<ProjectSyncSenderAccountOption> {
        add(ProjectSyncSenderAccountOption.Placeholder)
        addAll(state.senderAccounts.map(ProjectSyncSenderAccountOption::Account))
    }.toImmutableList()
    val selectedOption = state.selectedSenderAccount
        ?.let(ProjectSyncSenderAccountOption::Account)
        ?: ProjectSyncSenderAccountOption.Placeholder

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
                        ProjectSyncSenderAccountOption.Placeholder -> null
                        is ProjectSyncSenderAccountOption.Account -> option.account.accountUuid
                    },
                )
            },
            modifier = Modifier.fillMaxWidth(),
            optionToStringTransformation = { option ->
                when (option) {
                    ProjectSyncSenderAccountOption.Placeholder -> "Select an account"
                    is ProjectSyncSenderAccountOption.Account -> option.account.displayLabel
                }
            },
            label = "Send from",
            hasError = state.senderAccountError != null,
        )
    }
}

@Composable
private fun ProjectSyncRootCard(
    root: TaskMailProjectSyncRoot,
    onUseRepo: (String) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        TaskSectionHeader(
            title = root.rootPath,
            supportingText = root.supportingText(),
        )

        if (root.projects.isEmpty()) {
            TextBodySmall(
                text = "No selectable project folders were listed for this root.",
                color = MainTheme.colors.onSurfaceVariant,
            )
        } else {
            root.projects.forEach { project ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    TextLabelMedium(text = project.displayName)
                    TextBodySmall(
                        text = project.repoPath,
                        color = MainTheme.colors.onSurfaceVariant,
                    )
                    ButtonFilledTonal(
                        text = "Use this repo",
                        onClick = { onUseRepo(project.repoPath) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("TaskProjectSyncUseRepoButton"),
                    )
                }
            }
        }
    }
}

@Composable
private fun ProjectSyncCenteredMessage(
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

private fun TaskMailProjectSyncRoot.supportingText(): String {
    return if (isAvailable) {
        folderCount?.let { folderCount ->
            "$folderCount folder${if (folderCount == 1) "" else "s"} available"
        } ?: "Available"
    } else {
        unavailableReason ?: "Unavailable"
    }
}

private sealed interface ProjectSyncSenderAccountOption {
    data object Placeholder : ProjectSyncSenderAccountOption
    data class Account(val account: TaskMailSenderAccount) : ProjectSyncSenderAccountOption
}

private const val PROJECT_SYNC_INTRO_TEXT =
    "Fetch the latest allowlisted project folders, then choose a Repo path " +
        "for the new TaskMail request."
