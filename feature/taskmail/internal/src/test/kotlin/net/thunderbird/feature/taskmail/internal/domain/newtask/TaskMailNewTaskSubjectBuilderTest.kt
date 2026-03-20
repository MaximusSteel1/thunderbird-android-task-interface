package net.thunderbird.feature.taskmail.internal.domain.newtask

import assertk.assertThat
import assertk.assertions.isEqualTo
import kotlin.test.Test
import net.thunderbird.feature.taskmail.internal.domain.model.TaskMailBackend

class TaskMailNewTaskSubjectBuilderTest {

    private val testSubject = TaskMailNewTaskSubjectBuilder()

    @Test
    fun `build should prefix subject title for opencode`() {
        val subject = testSubject.build(
            backend = TaskMailBackend.OpenCode,
            subjectTitle = "Audit screen flow",
        )

        assertThat(subject).isEqualTo("[OC] Audit screen flow")
    }

    @Test
    fun `build should trim subject title`() {
        val subject = testSubject.build(
            backend = TaskMailBackend.Codex,
            subjectTitle = "  Audit screen flow  ",
        )

        assertThat(subject).isEqualTo("[CX] Audit screen flow")
    }
}
