package net.thunderbird.feature.taskmail.internal.ui.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import app.k9mail.core.ui.compose.designsystem.atom.DividerHorizontal
import app.k9mail.core.ui.compose.designsystem.atom.card.CardOutlined
import app.k9mail.core.ui.compose.designsystem.atom.text.TextBodyMedium
import app.k9mail.core.ui.compose.designsystem.atom.text.TextBodySmall
import app.k9mail.core.ui.compose.designsystem.atom.text.TextLabelMedium
import app.k9mail.core.ui.compose.designsystem.atom.text.TextTitleMedium
import net.thunderbird.core.ui.compose.theme2.MainTheme
import net.thunderbird.feature.taskmail.internal.domain.model.RelayBootstrapStatus
import net.thunderbird.feature.taskmail.internal.domain.model.TaskMailDirectOutcome
import net.thunderbird.feature.taskmail.internal.domain.model.TaskMailDirectSwitchGate
import net.thunderbird.feature.taskmail.internal.domain.model.TaskMailSessionActionSendRecord
import net.thunderbird.feature.taskmail.internal.domain.sessionaction.TaskMailDirectSessionActionType
import net.thunderbird.feature.taskmail.internal.ui.component.TaskSectionHeader

internal fun LazyListScope.latestDirectSessionActionItem(
    state: TaskSessionDetailContract.State,
) {
    val record = state.latestDirectSessionActionRecord ?: return

    item {
        TaskSessionDetailDirectEvidenceCard(
            record = record,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("TaskSessionDetailLatestDirectEvidence"),
        )
    }
}

@Composable
private fun TaskSessionDetailDirectEvidenceCard(
    record: TaskMailSessionActionSendRecord,
    modifier: Modifier = Modifier,
) {
    CardOutlined(modifier = modifier) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            TaskSectionHeader(
                title = "Latest direct result",
                supportingText = "Restored from the most recent direct-attempt record for this current session target.",
            )
            TextTitleMedium(text = record.actionType.displayLabel())
            TextBodySmall(
                text = record.evidence.outcome.displayLabel(),
                color = MainTheme.colors.onSurfaceVariant,
            )
            DividerHorizontal()
            DirectSessionEvidenceFields(record = record)
        }
    }
}

@Composable
private fun DirectSessionEvidenceFields(
    record: TaskMailSessionActionSendRecord,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        DirectSessionEvidenceField(
            label = "Target",
            value = "${record.target.workspaceId} / ${record.target.sessionId}",
        )
        DirectSessionEvidenceField(
            label = "Switch gate",
            value = record.evidence.switchGate.displayLabel(),
        )
        DirectSessionEvidenceField(
            label = "Bootstrap",
            value = record.evidence.bootstrapStatus.displayLabel(),
        )
        record.evidence.requestId?.let { requestId ->
            DirectSessionEvidenceField(
                label = "Request ID",
                value = requestId,
            )
        }
        record.evidence.receiptId?.let { receiptId ->
            DirectSessionEvidenceField(
                label = "Receipt ID",
                value = receiptId,
            )
        }
        record.evidence.transportMessageId?.let { transportMessageId ->
            DirectSessionEvidenceField(
                label = "Transport message ID",
                value = transportMessageId,
            )
        }
        record.evidence.fallbackReason?.let { fallbackReason ->
            DirectSessionEvidenceField(
                label = "Fallback reason",
                value = fallbackReason,
            )
        }
        record.evidence.errorMessage?.let { errorMessage ->
            DirectSessionEvidenceField(
                label = "Error",
                value = errorMessage,
            )
        }
    }
}

@Composable
private fun DirectSessionEvidenceField(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        TextLabelMedium(
            text = label,
            color = MainTheme.colors.onSurfaceVariant,
        )
        TextBodyMedium(text = value)
    }
}

private fun TaskMailDirectSessionActionType.displayLabel(): String {
    return when (this) {
        TaskMailDirectSessionActionType.Reply -> "Plain reply"
        TaskMailDirectSessionActionType.Status -> "Status query"
    }
}

private fun TaskMailDirectOutcome.displayLabel(): String {
    return when (this) {
        TaskMailDirectOutcome.DirectAccepted -> "Direct accepted"
        TaskMailDirectOutcome.MailFallbackSucceeded -> "Mail fallback succeeded"
        TaskMailDirectOutcome.MailFallbackFailed -> "Mail fallback failed"
        TaskMailDirectOutcome.DirectRejected -> "Direct rejected"
    }
}

private fun TaskMailDirectSwitchGate.displayLabel(): String {
    return when (this) {
        TaskMailDirectSwitchGate.KeepDirectDefault -> "Keep direct default"
        TaskMailDirectSwitchGate.FallbackRequired -> "Fallback required"
        TaskMailDirectSwitchGate.SwitchBlocker -> "Switch blocker"
    }
}

private fun RelayBootstrapStatus.displayLabel(): String {
    return when (this) {
        RelayBootstrapStatus.HelloAck -> "Hello ack"
        RelayBootstrapStatus.NotConfigured -> "Not configured"
        RelayBootstrapStatus.Unauthorized -> "Unauthorized"
        RelayBootstrapStatus.TokenIdMismatch -> "Token ID mismatch"
        RelayBootstrapStatus.UnexpectedResponse -> "Unexpected response"
        RelayBootstrapStatus.InvalidJson -> "Invalid JSON"
        RelayBootstrapStatus.SchemeMismatch -> "Scheme mismatch"
        RelayBootstrapStatus.TlsFailure -> "TLS failure"
        RelayBootstrapStatus.ConnectFailure -> "Connect failure"
        RelayBootstrapStatus.Timeout -> "Timeout"
        RelayBootstrapStatus.InvalidHttpResponse -> "Invalid HTTP response"
        RelayBootstrapStatus.InvalidHandshake -> "Invalid handshake"
    }
}
