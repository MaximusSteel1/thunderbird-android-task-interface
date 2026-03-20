package net.thunderbird.feature.taskmail.internal.ui

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import kotlin.test.Test

internal class TaskMailPathDisplayTest {

    @Test
    fun `toDisplayWorkdir should hide repo root dot marker`() {
        assertThat(".".toDisplayWorkdir()).isNull()
        assertThat("./".toDisplayWorkdir()).isNull()
        assertThat(".\\".toDisplayWorkdir()).isNull()
    }

    @Test
    fun `toDisplayWorkdir should keep only the last segment for nested workdir`() {
        assertThat("feature/taskmail".toDisplayWorkdir()).isEqualTo("taskmail")
        assertThat("feature\\taskmail".toDisplayWorkdir()).isEqualTo("taskmail")
    }
}
