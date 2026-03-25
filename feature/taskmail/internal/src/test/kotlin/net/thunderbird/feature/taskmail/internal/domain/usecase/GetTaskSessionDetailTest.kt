package net.thunderbird.feature.taskmail.internal.domain.usecase

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import kotlin.test.Test
import kotlinx.coroutines.test.runTest
import net.thunderbird.feature.taskmail.internal.data.fake.InMemoryTaskMailRepository
import net.thunderbird.feature.taskmail.internal.domain.model.TaskSessionKey

class GetTaskSessionDetailTest {

    @Test
    fun `invoke should return session detail when key exists`() = runTest {
        // Arrange
        val repository = InMemoryTaskMailRepository()
        val testSubject = GetTaskSessionDetail(repository)
        val key = TaskSessionKey(
            sessionId = "session_001",
        )

        // Act
        val result = testSubject(key)

        // Assert
        assertThat(result).isNotNull()
        assertThat(result?.sessionName).isEqualTo("Build TaskMail Phase 1")
    }

    @Test
    fun `invoke should return null when key does not exist`() = runTest {
        // Arrange
        val repository = InMemoryTaskMailRepository()
        val testSubject = GetTaskSessionDetail(repository)
        val key = TaskSessionKey(
            sessionId = "missing",
        )

        // Act
        val result = testSubject(key)

        // Assert
        assertThat(result).isNull()
    }
}
