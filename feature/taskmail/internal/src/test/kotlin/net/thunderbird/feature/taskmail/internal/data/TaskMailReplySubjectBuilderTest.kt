package net.thunderbird.feature.taskmail.internal.data

import assertk.assertThat
import assertk.assertions.isEqualTo
import kotlin.test.Test

class TaskMailReplySubjectBuilderTest {

    private val testSubject = TaskMailReplySubjectBuilder()

    @Test
    fun `build should normalize localized reply prefixes and preserve task tokens`() {
        val subject = testSubject.build("AW: [PAUSED][S:session-42] [CX] Analyze floor_shear")

        assertThat(subject).isEqualTo("Re: [PAUSED] [S:session-42] [CX] Analyze floor_shear")
    }

    @Test
    fun `build should keep plain non task subjects reply safe`() {
        val subject = testSubject.build("Weekly planning notes")

        assertThat(subject).isEqualTo("Re: Weekly planning notes")
    }
}
