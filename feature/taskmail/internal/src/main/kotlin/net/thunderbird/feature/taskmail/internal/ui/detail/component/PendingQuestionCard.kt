package net.thunderbird.feature.taskmail.internal.ui.detail.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.k9mail.core.ui.compose.designsystem.atom.card.CardElevated
import app.k9mail.core.ui.compose.designsystem.atom.text.TextBodyMedium
import app.k9mail.core.ui.compose.designsystem.atom.text.TextLabelMedium
import app.k9mail.core.ui.compose.designsystem.atom.text.TextTitleMedium
import kotlinx.collections.immutable.ImmutableList
import net.thunderbird.feature.taskmail.internal.ui.component.TaskSectionHeader
import net.thunderbird.feature.taskmail.internal.ui.component.TaskStatusBadge
import net.thunderbird.feature.taskmail.internal.ui.detail.TaskPendingQuestionChoiceUi
import net.thunderbird.feature.taskmail.internal.ui.detail.TaskPendingQuestionUi

@Composable
internal fun PendingQuestionCard(
    questions: ImmutableList<TaskPendingQuestionUi>,
    modifier: Modifier = Modifier,
) {
    CardElevated(modifier = modifier) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            TaskSectionHeader(
                title = if (questions.size > 1) "Pending questions" else "Pending question",
                supportingText = questionSupportingText(questions.size),
            )
            questions.forEach { question ->
                PendingQuestionItem(question = question)
            }
        }
    }
}

@Composable
private fun PendingQuestionItem(question: TaskPendingQuestionUi) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        TextTitleMedium(text = question.questionText)
        TextLabelMedium(
            text = buildString {
                append(if (question.isRequired) "Required" else "Optional")
                append(" · ID: ")
                append(question.questionId)
            },
        )
        if (question.choices.isNotEmpty()) {
            PendingQuestionChoices(choices = question.choices)
        } else {
            TextBodyMedium(text = "Reply with your answer in the composer.")
        }
    }
}

@Composable
private fun PendingQuestionChoices(
    choices: ImmutableList<TaskPendingQuestionChoiceUi>,
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        choices.forEach { choice ->
            TaskStatusBadge(text = choice.label)
        }
    }

    val labeledChoices = choices.filter { it.label != it.value }
    if (labeledChoices.isNotEmpty()) {
        TextBodyMedium(
            text = labeledChoices.joinToString(
                separator = " | ",
                prefix = "Accepted values: ",
            ) { choice ->
                "${choice.label} = ${choice.value}"
            },
        )
    }
}

private fun questionSupportingText(questionCount: Int): String {
    return if (questionCount > 1) {
        "This session is waiting for structured answers. " +
            "Use the matching question_id values in the reply composer."
    } else {
        "This session is waiting for user input."
    }
}
