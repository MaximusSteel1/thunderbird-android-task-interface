package net.thunderbird.feature.taskmail.internal.debug

import android.net.Uri
import androidx.compose.runtime.Composable
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import net.thunderbird.feature.taskmail.internal.domain.model.TaskSessionDetail
import net.thunderbird.feature.taskmail.internal.domain.parser.TaskQuestionCapsule
import net.thunderbird.feature.taskmail.internal.preview.TaskMailPreviewData
import net.thunderbird.feature.taskmail.internal.ui.detail.TaskPendingQuestionChoiceUi
import net.thunderbird.feature.taskmail.internal.ui.detail.TaskPendingQuestionUi
import net.thunderbird.feature.taskmail.internal.ui.detail.TaskSessionDetailContent
import net.thunderbird.feature.taskmail.internal.ui.detail.TaskSessionDetailContract
import net.thunderbird.feature.taskmail.internal.ui.detail.TaskSessionDetailUiState
import net.thunderbird.feature.taskmail.internal.ui.detail.TaskTimelineItemUi

private val debugPreviewDetails = mapOf(
    "question" to TaskMailPreviewData.questionSessionDetail,
    "plain-text-status" to TaskMailPreviewData.plainTextStatusSessionDetail,
    "inline-png" to TaskMailPreviewData.richInlinePngSessionDetail,
    "static-svg" to TaskMailPreviewData.richStaticSvgSessionDetail,
    "unmatched-image-fallback" to TaskMailPreviewData.richUnmatchedImageFallbackSessionDetail,
    "external-deliveries" to TaskMailPreviewData.richExternalDeliveriesSessionDetail,
    "attachment-notices" to TaskMailPreviewData.richAttachmentNoticesSessionDetail,
    "failed-long-error" to TaskMailPreviewData.richFailedLongErrorSessionDetail,
)

internal fun resolveTaskMailDebugPreviewDetail(uri: Uri?): TaskSessionDetail? {
    if (uri?.scheme != "app" || uri.host != "taskmail") return null

    val segments = uri.pathSegments
    if (segments.size != 3 || segments[0] != "preview" || segments[1] != "detail") return null

    return debugPreviewDetails[segments[2]]
}

@Composable
internal fun TaskMailDebugPreviewDetailContent(
    detail: TaskSessionDetail,
    onBack: () -> Unit = {},
) {
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
                    ?.toUiState()
                    ?.choices
                    ?: persistentListOf(),
                timeline = detail.timeline.map {
                    TaskTimelineItemUi(
                        id = it.id,
                        timestamp = it.timestamp,
                        direction = it.direction.name,
                        statusLabel = it.statusLabel?.name,
                        summary = it.summary,
                        plainText = it.body.plainText,
                        renderMode = it.body.renderMode,
                        richDocument = it.body.richDocument,
                    )
                }.toImmutableList(),
            ),
        ),
        onEvent = { event ->
            if (event == TaskSessionDetailContract.Event.BackClicked) {
                onBack()
            }
        },
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
