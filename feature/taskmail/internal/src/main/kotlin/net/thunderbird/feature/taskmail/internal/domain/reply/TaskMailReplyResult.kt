package net.thunderbird.feature.taskmail.internal.domain.reply

internal data class TaskMailReplyResult(
    val isSuccess: Boolean,
    val errorMessage: String? = null,
) {
    companion object {
        fun success(): TaskMailReplyResult = TaskMailReplyResult(isSuccess = true)

        fun failure(errorMessage: String? = null): TaskMailReplyResult {
            return TaskMailReplyResult(
                isSuccess = false,
                errorMessage = errorMessage,
            )
        }
    }
}
