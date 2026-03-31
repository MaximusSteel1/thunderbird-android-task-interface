package net.thunderbird.feature.taskmail.internal.ui.component

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEqualTo
import kotlin.test.Test

internal class TaskCodeLocatorParserTest {

    @Test
    fun `parse should collapse absolute windows locator to short file tail`() {
        val locator = "/E:/projects/mail_based_task_manager/tests/test_pc_control_plane_client.py:803"

        val result = TaskCodeLocatorParser.parse("Failure at $locator")

        assertThat(result.map(TaskCodeLocatorSegment::text)).containsExactly(
            "Failure at ",
            locator,
        )
        val locatorSegment = result[1] as TaskCodeLocatorSegment.Locator
        assertThat(locatorSegment.displayText).isEqualTo("test_pc_control_plane_client.py:803")
    }

    @Test
    fun `parse should prefer markdown label for absolute file reference`() {
        val reference = "[README.md#L34](/E:/projects/mail_based_task_manager/README.md#L34)"

        val result = TaskCodeLocatorParser.parse("Review $reference before retrying.")

        assertThat(result.map(TaskCodeLocatorSegment::text)).containsExactly(
            "Review ",
            reference,
            " before retrying.",
        )
        val referenceSegment = result[1] as TaskCodeLocatorSegment.Locator
        assertThat(referenceSegment.displayText).isEqualTo("README.md#L34")
        assertThat(referenceSegment.rawText).isEqualTo(reference)
    }

    @Test
    fun `parse should preserve multiple code reference formats in order`() {
        val firstReference = "[README.md#L34](/E:/projects/mail_based_task_manager/README.md#L34)"
        val secondReference = "file:///E:/projects/mail_based_task_manager/scripts/codex_sdk_sidecar/dist/index.js:151:22"
        val thirdReference = "E:\\projects\\mail_based_task_manager\\scripts\\codex_sdk_sidecar\\dist\\index.js"

        val result = TaskCodeLocatorParser.parse(
            """
            Review $firstReference
            at async main ($secondReference)
            Command: node $thirdReference
            """.trimIndent(),
        )

        val locatorSegments = result.filterIsInstance<TaskCodeLocatorSegment.Locator>()
        assertThat(locatorSegments.map(TaskCodeLocatorSegment.Locator::displayText)).containsExactly(
            "README.md#L34",
            "index.js:151:22",
            "index.js",
        )
        assertThat(locatorSegments.map(TaskCodeLocatorSegment.Locator::rawText)).containsExactly(
            firstReference,
            secondReference,
            thirdReference,
        )
    }

    @Test
    fun `parse should collapse bare directory-like absolute path to last segment`() {
        val path = "E:\\projects\\mail_based_task_manager"

        val result = TaskCodeLocatorParser.parse("CWD: $path")

        assertThat(result.map(TaskCodeLocatorSegment::text)).containsExactly(
            "CWD: ",
            path,
        )
        val pathSegment = result[1] as TaskCodeLocatorSegment.Locator
        assertThat(pathSegment.displayText).isEqualTo("mail_based_task_manager")
    }

    @Test
    fun `parse should keep plain text unchanged when locator is absent`() {
        val text = "The latest report is attached below."

        val result = TaskCodeLocatorParser.parse(text)

        assertThat(result).containsExactly(TaskCodeLocatorSegment.Plain(text))
    }
}
