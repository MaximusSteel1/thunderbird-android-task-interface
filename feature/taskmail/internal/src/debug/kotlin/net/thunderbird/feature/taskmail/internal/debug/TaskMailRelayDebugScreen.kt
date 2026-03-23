package net.thunderbird.feature.taskmail.internal.debug

import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import app.k9mail.core.ui.compose.designsystem.atom.button.ButtonFilled
import app.k9mail.core.ui.compose.designsystem.atom.button.ButtonText
import app.k9mail.core.ui.compose.designsystem.atom.text.TextBodyLarge
import app.k9mail.core.ui.compose.designsystem.atom.text.TextBodySmall
import app.k9mail.core.ui.compose.designsystem.atom.textfield.TextFieldOutlined
import app.k9mail.core.ui.compose.designsystem.molecule.input.SwitchInput
import app.k9mail.core.ui.compose.designsystem.organism.TopAppBarWithBackButton
import app.k9mail.core.ui.compose.designsystem.organism.banner.inline.ErrorBannerInlineNotificationCard
import app.k9mail.core.ui.compose.designsystem.organism.banner.inline.WarningBannerInlineNotificationCard
import app.k9mail.core.ui.compose.designsystem.template.Scaffold
import net.thunderbird.core.ui.compose.theme2.MainTheme
import net.thunderbird.core.ui.contract.mvi.observe
import net.thunderbird.feature.taskmail.internal.domain.model.RelayConnectionState
import net.thunderbird.feature.taskmail.internal.ui.relaydebug.TaskMailRelayDebugContract
import net.thunderbird.feature.taskmail.internal.ui.relaydebug.TaskMailRelayDebugViewModel
import org.koin.androidx.compose.koinViewModel

internal fun isTaskMailRelayDebugUri(uri: Uri?): Boolean {
    val segments = uri?.pathSegments ?: return false
    return segments.size == 2 &&
        segments[0] == "debug" &&
        segments[1] == "relay"
}

@Composable
internal fun TaskMailRelayDebugScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: TaskMailRelayDebugContract.ViewModel = koinViewModel<TaskMailRelayDebugViewModel>(),
) {
    val context = LocalContext.current
    val (state, dispatch) = viewModel.observe { effect ->
        when (effect) {
            is TaskMailRelayDebugContract.Effect.ShowMessage -> {
                Toast.makeText(context, effect.message, Toast.LENGTH_LONG).show()
            }
        }
    }

    LaunchedEffect(Unit) {
        dispatch(TaskMailRelayDebugContract.Event.LoadData)
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBarWithBackButton(
                title = "TaskMail relay debug",
                onBackClick = onBack,
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .imePadding()
                .padding(innerPadding)
                .testTag("TaskMailRelayDebugList"),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                TextBodyLarge(
                    text = "Probe the VPS relay connection without changing current TaskMail mail behavior.",
                )
            }

            item {
                SwitchInput(
                    text = "Relay enabled",
                    checked = state.value.relayEnabled,
                    onCheckedChange = {
                        dispatch(TaskMailRelayDebugContract.Event.RelayEnabledChanged(it))
                    },
                    contentPadding = PaddingValues(0.dp),
                )
            }

            item {
                SwitchInput(
                    text = "Project sync debug file logging",
                    checked = state.value.projectSyncDebugFileLoggingEnabled,
                    onCheckedChange = {
                        dispatch(TaskMailRelayDebugContract.Event.ProjectSyncDebugFileLoggingChanged(it))
                    },
                    contentPadding = PaddingValues(0.dp),
                )
            }

            item {
                TextBodySmall(
                    text = "Writes project-sync-debug.log under app external files only when enabled.",
                    color = MainTheme.colors.onSurfaceVariant,
                )
            }

            if (!state.value.relayEnabled) {
                item {
                    WarningBannerInlineNotificationCard(
                        title = "Relay stays disabled for normal TaskMail flows",
                        supportingText =
                        "This screen can still probe health and test hello/hello_ack manually.",
                        actions = {},
                    )
                }
            }

            state.value.healthError?.let { healthError ->
                item {
                    TaskMailRelayDebugErrorBanner(
                        title = "Relay health error",
                        message = healthError,
                        onDismiss = { dispatch(TaskMailRelayDebugContract.Event.DismissErrors) },
                    )
                }
            }

            state.value.actionError?.let { actionError ->
                item {
                    TaskMailRelayDebugErrorBanner(
                        title = "Relay connection error",
                        message = actionError,
                        onDismiss = { dispatch(TaskMailRelayDebugContract.Event.DismissErrors) },
                    )
                }
            }

            item {
                TextFieldOutlined(
                    value = state.value.host,
                    onValueChange = {
                        dispatch(TaskMailRelayDebugContract.Event.HostChanged(it))
                    },
                    label = "Relay host",
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            item {
                TextFieldOutlined(
                    value = state.value.port,
                    onValueChange = {
                        dispatch(TaskMailRelayDebugContract.Event.PortChanged(it))
                    },
                    label = "Relay port",
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            item {
                SwitchInput(
                    text = "Use TLS",
                    checked = state.value.useTls,
                    onCheckedChange = {
                        dispatch(TaskMailRelayDebugContract.Event.UseTlsChanged(it))
                    },
                    contentPadding = PaddingValues(0.dp),
                )
            }

            item {
                TextFieldOutlined(
                    value = state.value.transportToken,
                    onValueChange = {
                        dispatch(TaskMailRelayDebugContract.Event.TransportTokenChanged(it))
                    },
                    label = "Relay transport token",
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            state.value.healthSummary?.let { summary ->
                item {
                    TextBodySmall(
                        text = summary,
                        color = MainTheme.colors.onSurfaceVariant,
                    )
                }
            }

            item {
                TextBodySmall(
                    text = state.value.connectionState.toSummary(),
                    color = MainTheme.colors.onSurfaceVariant,
                )
            }

            item {
                TextFieldOutlined(
                    value = state.value.probePayloadText,
                    onValueChange = {
                        dispatch(TaskMailRelayDebugContract.Event.ProbePayloadTextChanged(it))
                    },
                    label = "Debug text payload",
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            state.value.lastProbeSummary?.let { summary ->
                item {
                    TextBodySmall(
                        text = summary,
                        color = MainTheme.colors.onSurfaceVariant,
                    )
                }
            }

            state.value.lastProbeArtifactPath?.let { artifactPath ->
                item {
                    TextBodySmall(
                        text = "Artifacts: $artifactPath",
                        color = MainTheme.colors.onSurfaceVariant,
                    )
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    ButtonFilled(
                        text = if (state.value.isSaving) "Saving..." else "Save",
                        onClick = { dispatch(TaskMailRelayDebugContract.Event.SaveClicked) },
                        enabled = !state.value.isSaving,
                        modifier = Modifier.weight(1f),
                    )
                    ButtonFilled(
                        text = if (state.value.isProbingHealth) "Checking..." else "Healthz",
                        onClick = { dispatch(TaskMailRelayDebugContract.Event.ProbeHealthClicked) },
                        enabled = !state.value.isProbingHealth,
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            item {
                ButtonFilled(
                    text = if (state.value.isSendingProbe) {
                        "Sending probe..."
                    } else {
                        "Send direct probe"
                    },
                    onClick = { dispatch(TaskMailRelayDebugContract.Event.SendDirectProbeClicked) },
                    enabled = !state.value.isSendingProbe,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            state.value.lastFileSampleSummary?.let { summary ->
                item {
                    TextBodySmall(
                        text = summary,
                        color = MainTheme.colors.onSurfaceVariant,
                    )
                }
            }

            state.value.lastFileSampleArtifactPath?.let { artifactPath ->
                item {
                    TextBodySmall(
                        text = "File sample artifacts: $artifactPath",
                        color = MainTheme.colors.onSurfaceVariant,
                    )
                }
            }

            item {
                ButtonFilled(
                    text = if (state.value.isSendingFileSample) {
                        "Running /v1/files sample..."
                    } else {
                        "Run /v1/files sample"
                    },
                    onClick = { dispatch(TaskMailRelayDebugContract.Event.SendFileSurfaceSampleClicked) },
                    enabled = !state.value.isSendingFileSample,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    ButtonFilled(
                        text = if (state.value.isConnecting) "Connecting..." else "Connect",
                        onClick = { dispatch(TaskMailRelayDebugContract.Event.ConnectClicked) },
                        enabled = !state.value.isConnecting,
                        modifier = Modifier.weight(1f),
                    )
                    ButtonText(
                        text = "Disconnect",
                        onClick = { dispatch(TaskMailRelayDebugContract.Event.DisconnectClicked) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun TaskMailRelayDebugErrorBanner(
    title: String,
    message: String,
    onDismiss: () -> Unit,
) {
    ErrorBannerInlineNotificationCard(
        title = title,
        supportingText = message,
        actions = {
            ButtonText(
                text = "Dismiss",
                onClick = onDismiss,
            )
        },
    )
}

private fun RelayConnectionState.toSummary(): String {
    return when (this) {
        RelayConnectionState.Idle -> "connection=idle"
        is RelayConnectionState.Connecting -> "connection=connecting | url=$relayUrl"
        is RelayConnectionState.Connected -> {
            "connection=connected | connection_id=$connectionId | " +
                "server_time=$serverTime | heartbeat_seconds=$heartbeatSeconds"
        }
        is RelayConnectionState.Failed -> "connection=failed | message=$message"
    }
}
