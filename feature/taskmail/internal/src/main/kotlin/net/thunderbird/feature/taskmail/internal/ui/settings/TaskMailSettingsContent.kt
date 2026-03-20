package net.thunderbird.feature.taskmail.internal.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import app.k9mail.core.ui.compose.designsystem.atom.button.ButtonFilled
import app.k9mail.core.ui.compose.designsystem.atom.button.ButtonText
import app.k9mail.core.ui.compose.designsystem.atom.text.TextBodyLarge
import app.k9mail.core.ui.compose.designsystem.atom.text.TextBodySmall
import app.k9mail.core.ui.compose.designsystem.atom.text.TextHeadlineSmall
import app.k9mail.core.ui.compose.designsystem.molecule.input.EmailAddressInput
import app.k9mail.core.ui.compose.designsystem.organism.TopAppBarWithBackButton
import app.k9mail.core.ui.compose.designsystem.organism.banner.inline.ErrorBannerInlineNotificationCard
import app.k9mail.core.ui.compose.designsystem.organism.banner.inline.WarningBannerInlineNotificationCard
import app.k9mail.core.ui.compose.designsystem.template.Scaffold
import net.thunderbird.core.ui.compose.theme2.MainTheme

@Composable
internal fun TaskMailSettingsContent(
    state: TaskMailSettingsContract.State,
    onEvent: (TaskMailSettingsContract.Event) -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBarWithBackButton(
                title = "TaskMail bot mailbox",
                onBackClick = { onEvent(TaskMailSettingsContract.Event.BackClicked) },
            )
        },
    ) { innerPadding ->
        if (state.isLoading) {
            TaskMailSettingsCenteredMessage(
                title = "Loading TaskMail settings",
                message = "Reading the mailbox address used for TaskMail requests.",
                modifier = Modifier.padding(innerPadding),
            )
        } else {
            TaskMailSettingsForm(
                state = state,
                onEvent = onEvent,
                modifier = Modifier.padding(innerPadding),
            )
        }
    }
}

@Composable
private fun TaskMailSettingsForm(
    state: TaskMailSettingsContract.State,
    onEvent: (TaskMailSettingsContract.Event) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .imePadding()
            .testTag("TaskMailSettingsList"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            TextBodyLarge(
                text = "TaskMail first-task and reply mail use this address as the destination mailbox.",
            )
        }

        if (state.isUsingBuildDefault) {
            item {
                TaskMailBuildDefaultBanner()
            }
        }

        state.saveError?.let { saveError ->
            item {
                TaskMailSettingsErrorBanner(
                    saveError = saveError,
                    onDismiss = { onEvent(TaskMailSettingsContract.Event.DismissSaveError) },
                )
            }
        }

        item {
            EmailAddressInput(
                onEmailAddressChange = {
                    onEvent(TaskMailSettingsContract.Event.AddressChanged(it))
                },
                emailAddress = state.address,
                errorMessage = state.addressError,
                contentPadding = PaddingValues(0.dp),
            )
        }

        item {
            TextBodySmall(
                text = "Enter one mailbox address. TaskMail sends to this address instead of " +
                    "using generic reply-recipient heuristics.",
                color = MainTheme.colors.onSurfaceVariant,
            )
        }

        item {
            ButtonFilled(
                text = if (state.isSaving) "Saving..." else "Confirm",
                onClick = { onEvent(TaskMailSettingsContract.Event.ConfirmClicked) },
                enabled = state.canSave,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("TaskMailSettingsConfirmButton"),
            )
        }
    }
}

@Composable
private fun TaskMailBuildDefaultBanner() {
    WarningBannerInlineNotificationCard(
        title = "Using build default",
        supportingText =
        "This address currently comes from the build default. Confirm to save it in app settings.",
        actions = {},
    )
}

@Composable
private fun TaskMailSettingsErrorBanner(
    saveError: String,
    onDismiss: () -> Unit,
) {
    ErrorBannerInlineNotificationCard(
        title = "TaskMail settings error",
        supportingText = saveError,
        actions = {
            ButtonText(
                text = "Dismiss",
                onClick = onDismiss,
            )
        },
    )
}

@Composable
private fun TaskMailSettingsCenteredMessage(
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
