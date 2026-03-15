package net.thunderbird.feature.taskmail.internal.domain.usecase

import assertk.assertThat
import assertk.assertions.hasSize
import assertk.assertions.isEqualTo
import kotlin.test.Test
import kotlinx.coroutines.test.runTest
import net.thunderbird.feature.taskmail.internal.data.fake.InMemoryTaskMailRepository

class GetTaskWorkspaceSummariesTest {

    @Test
    fun `invoke should return workspace summaries from repository`() = runTest {
        // Arrange
        val repository = InMemoryTaskMailRepository()
        val testSubject = GetTaskWorkspaceSummaries(repository)

        // Act
        val result = testSubject()

        // Assert
        assertThat(result).hasSize(1)
        assertThat(result.first().title).isEqualTo("android_task_manager")
    }
}
