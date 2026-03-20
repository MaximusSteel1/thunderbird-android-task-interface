package net.thunderbird.feature.taskmail.internal.domain.newtask

internal class TaskMailNewTaskBodySerializer {

    fun serialize(draft: TaskMailNewTaskDraft): String {
        val lines = buildList {
            add("Repo: ${draft.repoPath.trim()}")
            draft.workdir
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
                ?.let { add("Workdir: $it") }
            draft.timeoutMinutes?.let { add("Timeout: $it") }
            if (draft.mode != TaskMailNewTaskMode.Modify) {
                add("Mode: ${draft.mode.wireValue}")
            }
            draft.profile
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
                ?.let { add("Profile: $it") }
            draft.permission.wireValue?.let { add("Permission: $it") }
            add("")
            add("Task:")
            add(draft.taskText.trimEnd())

            val acceptanceItems = draft.acceptanceCriteria
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .map { it.removePrefix("- ").trim() }
                .filter { it.isNotEmpty() }

            if (acceptanceItems.isNotEmpty()) {
                add("")
                add("Acceptance:")
                acceptanceItems.forEach { item ->
                    add("- $item")
                }
            }
        }

        return lines.joinToString(separator = "\n")
    }
}
