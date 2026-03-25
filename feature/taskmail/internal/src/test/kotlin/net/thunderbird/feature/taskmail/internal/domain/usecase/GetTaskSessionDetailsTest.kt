package net.thunderbird.feature.taskmail.internal.domain.usecase

import assertk.assertThat
import assertk.assertions.hasSize
import assertk.assertions.isEqualTo
import kotlin.test.Test
import kotlinx.coroutines.test.runTest
import net.thunderbird.feature.taskmail.internal.data.fake.InMemoryTaskMailRepository

class GetTaskSessionDetailsTest {

    @Test
    fun `invoke should return session details from repository`() = runTest {
        // Arrange
        val repository = InMemoryTaskMailRepository()
        val testSubject = GetTaskSessionDetails(repository)

        // Act
        val result = testSubject()

        // Assert
        assertThat(result).hasSize(8)
        assertThat(result.first().sessionName).isEqualTo("Build TaskMail Phase 1")
    }
}
