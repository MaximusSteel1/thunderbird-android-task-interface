package net.thunderbird.feature.taskmail.internal.domain.parser

internal data class TaskQuestionCapsule(
    val questionId: String,
    val questionText: String,
    val choices: List<String>,
    val questionSetId: String? = null,
    val questionType: String? = null,
    val required: Boolean = true,
    val choiceLabels: Map<String, String> = emptyMap(),
)
