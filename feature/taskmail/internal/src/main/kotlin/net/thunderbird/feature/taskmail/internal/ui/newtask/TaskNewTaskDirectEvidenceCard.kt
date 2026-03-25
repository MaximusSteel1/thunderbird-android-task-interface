package net.thunderbird.feature.taskmail.internal.ui.newtask

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
import net.thunderbird.feature.taskmail.internal.domain.model.TaskMailDirectSendEvidence
import net.thunderbird.feature.taskmail.internal.domain.model.TaskMailDirectSwitchGate
import net.thunderbird.feature.taskmail.internal.ui.component.TaskSectionHeader

internal fun LazyListScope.latestDirectEvidenceItem(
    state: TaskNewTaskContract.State,
) {
    val evidence = state.lastDirectSendEvidence ?: return

    item {
        LatestDirectEvidenceCard(
            evidence = evidence,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("TaskNewTaskLatestDirectEvidence"),
        )
    }
}

@Composable
private fun LatestDirectEvidenceCard(
    evidence: TaskMailDirectSendEvidence,
    modifier: Modifier = Modifier,
) {
    CardOutlined(modifier = modifier) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            TaskSectionHeader(
                title = "Latest dispatch result",
                supportingText = "Restored from the most recent TaskMail send attempt for this sender account.",
            )
            TextTitleMedium(text = evidence.outcome.displayLabel())
            TextBodySmall(
                text = "Switch gate: ${evidence.switchGate.displayLabel()}",
                color = MainTheme.colors.onSurfaceVariant,
            )
            DividerHorizontal()
            DirectEvidenceField(
                label = "Bootstrap",
                value = evidence.bootstrapStatus.displayLabel(),
            )
            evidence.requestId?.let { requestId ->
                DirectEvidenceField(
                    label = "Request ID",
                    value = requestId,
                )
            }
            evidence.receiptId?.let { receiptId ->
                DirectEvidenceField(
                    label = "Receipt ID",
                    value = receiptId,
                )
            }
            evidence.transportMessageId?.let { transportMessageId ->
                DirectEvidenceField(
                    label = "Transport message ID",
                    value = transportMessageId,
                )
            }
            evidence.fallbackReason?.let { fallbackReason ->
                DirectEvidenceField(
                    label = "Dispatch detail",
                    value = fallbackReason,
                )
            }
            evidence.errorMessage?.let { errorMessage ->
                DirectEvidenceField(
                    label = "Error",
                    value = errorMessage,
                )
            }
        }
    }
}

@Composable
private fun DirectEvidenceField(
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
