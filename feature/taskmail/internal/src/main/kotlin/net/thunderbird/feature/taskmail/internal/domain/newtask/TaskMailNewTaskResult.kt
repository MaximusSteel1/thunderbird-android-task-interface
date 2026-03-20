package net.thunderbird.feature.taskmail.internal.domain.newtask

internal data class TaskMailNewTaskResult(
    val isSuccess: Boolean,
    val errorMessage: String? = null,
) {
    companion object {
        fun success(): TaskMailNewTaskResult = TaskMailNewTaskResult(isSuccess = true)

        fun failure(errorMessage: String? = null): TaskMailNewTaskResult {
            return TaskMailNewTaskResult(
                isSuccess = false,
                errorMessage = errorMessage,
            )
        }
    }
}
