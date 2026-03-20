package net.thunderbird.feature.taskmail.internal.data

import assertk.assertThat
import assertk.assertions.isEqualTo
import kotlin.test.Test
import net.thunderbird.feature.taskmail.internal.domain.model.TaskMessageAttachment

class TaskMessageAttachmentDisplayPolicyTest {

    @Test
    fun `shouldDisplayInTaskTimeline should hide multipart container parts`() {
        // Arrange
        val attachment = TaskMessageAttachment(
            id = "attachment-1",
            displayName = "noname",
            contentType = "multipart/alternative",
        )

        // Act
        val result = attachment.shouldDisplayInTaskTimeline()

        // Assert
        assertThat(result).isEqualTo(false)
    }

    @Test
    fun `shouldDisplayInTaskTimeline should keep real file attachments`() {
        // Arrange
        val attachment = TaskMessageAttachment(
            id = "attachment-2",
            displayName = "result.md",
            contentType = "text/markdown",
        )

        // Act
        val result = attachment.shouldDisplayInTaskTimeline()

        // Assert
        assertThat(result).isEqualTo(true)
    }

    @Test
    fun `deduplicateForTaskTimeline should merge duplicate attachments by part id`() {
        // Arrange
        val attachments = listOf(
            TaskMessageAttachment(
                id = "attachment-fallback",
                displayName = "result_chart.png",
                contentType = "image/png",
                sizeBytes = 2_048L,
                isImage = true,
                partId = 42L,
            ),
            TaskMessageAttachment(
                id = "content://taskmail/result-chart",
                displayName = "result_chart.png",
                contentType = "image/png",
                sizeBytes = 2_048L,
                isInline = true,
                isImage = true,
                internalUriString = "content://taskmail/result-chart",
                partId = 42L,
                isContentAvailable = true,
            ),
        )

        // Act
        val result = attachments.deduplicateForTaskTimeline()

        // Assert
        assertThat(result.size).isEqualTo(1)
        assertThat(result.single()).isEqualTo(
            TaskMessageAttachment(
                id = "content://taskmail/result-chart",
                displayName = "result_chart.png",
                contentType = "image/png",
                sizeBytes = 2_048L,
                isInline = true,
                isImage = true,
                internalUriString = "content://taskmail/result-chart",
                partId = 42L,
                isContentAvailable = true,
            ),
        )
    }

    @Test
    fun `deduplicateForTaskTimeline should merge duplicate attachments by fallback signature`() {
        // Arrange
        val attachments = listOf(
            TaskMessageAttachment(
                id = "attachment-1",
                displayName = "result_chart.svg",
                contentType = "image/svg",
                sizeBytes = 512L,
                isInline = true,
                isImage = true,
                contentId = "chart-preview",
            ),
            TaskMessageAttachment(
                id = "attachment-2",
                displayName = "result_chart.svg",
                contentType = "image/svg",
                sizeBytes = 512L,
                isImage = true,
                contentId = "chart-preview",
                isContentAvailable = true,
            ),
        )

        // Act
        val result = attachments.deduplicateForTaskTimeline()

        // Assert
        assertThat(result.size).isEqualTo(1)
        assertThat(result.single().isInline).isEqualTo(true)
        assertThat(result.single().isContentAvailable).isEqualTo(true)
        assertThat(result.single().contentId).isEqualTo("chart-preview")
    }
}
