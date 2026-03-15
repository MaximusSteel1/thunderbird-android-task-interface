package net.thunderbird.feature.taskmail.internal.ui.detail

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import net.thunderbird.feature.taskmail.internal.domain.parser.TaskQuestionCapsule
import net.thunderbird.feature.taskmail.internal.preview.TaskMailPreviewData

@Composable
@Preview(showBackground = true)
internal fun TaskSessionDetailPreview() {
    val detail = TaskMailPreviewData.sessionDetails.first()

    TaskSessionDetailContent(
        state = TaskSessionDetailContract.State(
            detail = TaskSessionDetailUiState(
                sessionName = detail.sessionName,
                backend = detail.backend.name,
                status = detail.status.name,
                repoPath = detail.repoPath,
                workdir = detail.workdir,
                lastSummary = detail.lastSummary,
                pendingQuestions = detail.pendingQuestions
                    .map(TaskQuestionCapsule::toUiState)
                    .toImmutableList(),
                quickAnswerChoices = detail.pendingQuestions
                    .singleOrNull()
                    ?.choices
                    ?.toImmutableList()
                    ?: persistentListOf(),
                timeline = detail.timeline.map {
                    TaskTimelineItemUi(
                        id = it.id,
                        timestamp = it.timestamp,
                        direction = it.direction.name,
                        statusLabel = it.statusLabel?.name,
                        summary = it.summary,
                        plainText = it.body.plainText,
                    )
                }.toImmutableList(),
            ),
        ),
        onEvent = {},
        onPickAttachments = {},
    )
}

private fun TaskQuestionCapsule.toUiState(): TaskPendingQuestionUi {
    return TaskPendingQuestionUi(
        questionId = questionId,
        questionText = questionText,
        choices = choices.map { choice ->
            TaskPendingQuestionChoiceUi(
                value = choice,
                label = choiceLabels[choice] ?: choice,
            )
        }.toImmutableList(),
        isRequired = required,
    )
}
