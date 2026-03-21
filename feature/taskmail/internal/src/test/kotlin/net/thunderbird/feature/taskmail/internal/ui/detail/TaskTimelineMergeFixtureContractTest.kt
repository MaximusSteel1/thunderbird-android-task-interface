package net.thunderbird.feature.taskmail.internal.ui.detail

import assertk.assertThat
import assertk.assertions.isEqualTo
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import kotlin.test.Test
import kotlinx.serialization.json.Json
import net.thunderbird.feature.taskmail.internal.data.direct.TaskMailDirectSessionProjector
import net.thunderbird.feature.taskmail.internal.domain.model.TaskMessageBody
import net.thunderbird.feature.taskmail.internal.domain.model.TaskTimelineDirection
import net.thunderbird.feature.taskmail.internal.domain.model.TaskTimelineItem
import net.thunderbird.feature.taskmail.internal.testdata.Phase3DirectInboundExpectedProjection
import net.thunderbird.feature.taskmail.internal.testdata.Phase3DirectInboundFixturePackageLoader
import net.thunderbird.feature.taskmail.internal.testdata.Phase3DirectInboundMailCompanionItem
import org.junit.Assume.assumeTrue

class TaskTimelineMergeFixtureContractTest {
    private val json = Json { ignoreUnknownKeys = true }
    private val projector = TaskMailDirectSessionProjector()

    @Test
    fun `mergeTimeline should stay aligned with exported phase3 fixture package`() {
        val manifestFile = Phase3DirectInboundFixturePackageLoader.manifestFile()
        assumeTrue("Phase 3 fixture package is not present in the adjacent PC workspace.", manifestFile.exists())

        val manifest = Phase3DirectInboundFixturePackageLoader.loadManifest(json)

        manifest.fixtures.forEach { fixtureEntry ->
            val fixture = Phase3DirectInboundFixturePackageLoader.loadFixture(json, fixtureEntry)
            val projection = projector.project(
                updates = fixture.sessionUpdates,
                mailBusinessEventKeys = fixture.mailCompanion.items.map(
                    Phase3DirectInboundMailCompanionItem::businessEventKey,
                ),
            )
            val mergedTimeline = mergeTimeline(
                mailTimeline = fixture.mailCompanion.items.map(
                    Phase3DirectInboundMailCompanionItem::toMailTimelineItem,
                ),
                directTimeline = projection?.provisionalTimeline,
            )

            assertMergedTimeline(
                actual = mergedTimeline,
                expected = fixture.expectedProjection,
                mailCompanion = fixture.mailCompanion.items,
            )
        }
    }

    private fun assertMergedTimeline(
        actual: List<TaskTimelineItem>,
        expected: Phase3DirectInboundExpectedProjection,
        mailCompanion: List<Phase3DirectInboundMailCompanionItem>,
    ) {
        val expectedMailKeys = mailCompanion.map(Phase3DirectInboundMailCompanionItem::businessEventKey)
        val suppressedDirectKeys = expected.suppressedDirectBusinessEventKeys.toSet()
        val expectedDirectKeys = expected.visibleBusinessEventKeys
            .filterNot { key -> key in suppressedDirectKeys }
        val expectedMergedKeys = (expectedMailKeys + expectedDirectKeys)
            .filter(String::isNotBlank)
            .distinct()
            .sorted()
        val actualMailKeys = actual
            .filter { item -> item.id.startsWith("mail:") }
            .flatMap(TaskTimelineItem::businessEventKeys)
            .filter(String::isNotBlank)
            .sorted()
        val actualDirectKeys = actual
            .filter { item -> item.id.startsWith("direct:") }
            .flatMap(TaskTimelineItem::businessEventKeys)
            .filter(String::isNotBlank)
            .sorted()
        val actualMergedKeys = actual
            .flatMap(TaskTimelineItem::businessEventKeys)
            .filter(String::isNotBlank)
            .sorted()

        assertThat(actualMailKeys).isEqualTo(expectedMailKeys.sorted())
        assertThat(actualDirectKeys).isEqualTo(expectedDirectKeys.sorted())
        assertThat(actualMergedKeys).isEqualTo(expectedMergedKeys)
        assertThat(actualMergedKeys.distinct()).isEqualTo(actualMergedKeys)
    }
}

private fun Phase3DirectInboundMailCompanionItem.toMailTimelineItem(): TaskTimelineItem {
    return TaskTimelineItem(
        id = "mail:$businessEventKey",
        timestamp = arrivedAt.toTimelineTimestamp(),
        direction = TaskTimelineDirection.System,
        summary = summary,
        body = TaskMessageBody(summary.orEmpty(), markdownCandidate = false),
        businessEventKeys = listOf(businessEventKey),
    )
}

private fun String?.toTimelineTimestamp(): Long {
    if (this.isNullOrBlank()) return 0L

    return TIMELINE_TIMESTAMP_PATTERNS
        .firstNotNullOfOrNull { pattern -> parseTimelineTimestamp(pattern) }
        ?: 0L
}

private fun String.parseTimelineTimestamp(pattern: String): Long? {
    return runCatching {
        SimpleDateFormat(pattern, Locale.US).apply {
            isLenient = false
            timeZone = TimeZone.getTimeZone("UTC")
        }.parse(this)?.time
    }.getOrNull()
}

private val TIMELINE_TIMESTAMP_PATTERNS = listOf(
    "yyyy-MM-dd'T'HH:mm:ss",
    "yyyy-MM-dd'T'HH:mm:ss'Z'",
)
