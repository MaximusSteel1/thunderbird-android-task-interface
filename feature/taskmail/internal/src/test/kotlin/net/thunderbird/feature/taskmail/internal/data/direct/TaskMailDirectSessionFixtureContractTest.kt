package net.thunderbird.feature.taskmail.internal.data.direct

import assertk.assertThat
import assertk.assertions.isEqualTo
import kotlin.test.Test
import kotlinx.serialization.json.Json
import net.thunderbird.feature.taskmail.internal.domain.model.TaskMailSessionLifecycle
import net.thunderbird.feature.taskmail.internal.domain.model.TaskMailSessionStatus
import net.thunderbird.feature.taskmail.internal.testdata.Phase3DirectInboundChoiceProjection
import net.thunderbird.feature.taskmail.internal.testdata.Phase3DirectInboundExpectedProjection
import net.thunderbird.feature.taskmail.internal.testdata.Phase3DirectInboundFixturePackageLoader
import net.thunderbird.feature.taskmail.internal.testdata.Phase3DirectInboundMailCompanionItem
import org.junit.Assume.assumeTrue

class TaskMailDirectSessionFixtureContractTest {
    private val json = Json { ignoreUnknownKeys = true }
    private val testSubject = TaskMailDirectSessionProjector()

    @Test
    fun `project should stay aligned with exported phase3 fixture package`() {
        val manifestFile = Phase3DirectInboundFixturePackageLoader.manifestFile()
        assumeTrue("Phase 3 fixture package is not present in the adjacent PC workspace.", manifestFile.exists())
        val fixtureRoot = requireNotNull(manifestFile.parentFile)

        val manifest = Phase3DirectInboundFixturePackageLoader.loadManifest(json)

        manifest.fixtures.forEach { fixtureEntry ->
            val fixtureFile = fixtureRoot.resolve(fixtureEntry.file)
            assumeTrue("Missing fixture file: ${fixtureFile.absolutePath}", fixtureFile.exists())

            val fixture = Phase3DirectInboundFixturePackageLoader.loadFixture(json, fixtureEntry)
            val projection = testSubject.project(
                updates = fixture.sessionUpdates,
                mailBusinessEventKeys = fixture.mailCompanion.items.map(
                    Phase3DirectInboundMailCompanionItem::businessEventKey,
                ),
            )

            assertProjection(
                actual = projection?.toContractProjection(),
                expected = fixture.expectedProjection,
            )
        }
    }

    private fun assertProjection(
        actual: ContractProjection?,
        expected: Phase3DirectInboundExpectedProjection,
    ) {
        val normalizedActual = actual ?: ContractProjection(
            canonicalWorkspaceId = null,
            canonicalSessionId = null,
            canonicalThreadId = null,
            headerStatus = "mail_only",
            headerLifecycle = null,
            lastSummary = null,
            questionSetId = null,
            pendingQuestionIds = emptyList(),
            quickAnswerChoices = emptyList(),
            visibleBusinessEventKeys = emptyList(),
            suppressedDirectBusinessEventKeys = emptyList(),
        )

        assertThat(normalizedActual).isEqualTo(
            ContractProjection(
                canonicalWorkspaceId = expected.canonicalWorkspaceId,
                canonicalSessionId = expected.canonicalSessionId,
                canonicalThreadId = expected.canonicalThreadId,
                headerStatus = expected.headerStatus,
                headerLifecycle = expected.headerLifecycle,
                lastSummary = expected.lastSummary,
                questionSetId = expected.questionSetId,
                pendingQuestionIds = expected.pendingQuestionIds,
                quickAnswerChoices = expected.quickAnswerChoices,
                visibleBusinessEventKeys = expected.visibleBusinessEventKeys,
                suppressedDirectBusinessEventKeys = expected.suppressedDirectBusinessEventKeys,
            ),
        )
    }
}

private fun TaskMailDirectSessionProjection.toContractProjection(): ContractProjection {
    return ContractProjection(
        canonicalWorkspaceId = canonicalWorkspaceId,
        canonicalSessionId = canonicalSessionId,
        canonicalThreadId = canonicalThreadId,
        headerStatus = headerStatus.toWireValue(),
        headerLifecycle = headerLifecycle?.toWireValue(),
        lastSummary = lastSummary,
        questionSetId = questionSetId,
        pendingQuestionIds = pendingQuestionIds,
        quickAnswerChoices = quickAnswerChoices.map { choice ->
            Phase3DirectInboundChoiceProjection(
                value = choice.value,
                label = choice.label,
            )
        },
        visibleBusinessEventKeys = visibleBusinessEventKeys,
        suppressedDirectBusinessEventKeys = suppressedDirectBusinessEventKeys,
    )
}

private fun TaskMailSessionStatus.toWireValue(): String {
    return when (this) {
        TaskMailSessionStatus.Queued -> "queued"
        TaskMailSessionStatus.Running -> "running"
        TaskMailSessionStatus.WaitingUser -> "awaiting_user_input"
        TaskMailSessionStatus.Paused -> "paused"
        TaskMailSessionStatus.Done -> "done"
        TaskMailSessionStatus.Failed -> "failed"
        TaskMailSessionStatus.Killed -> "killed"
        TaskMailSessionStatus.Unknown -> "unknown"
    }
}

private fun TaskMailSessionLifecycle.toWireValue(): String {
    return when (this) {
        TaskMailSessionLifecycle.Active -> "active"
        TaskMailSessionLifecycle.Ended -> "ended"
        TaskMailSessionLifecycle.Unknown -> "unknown"
    }
}

private data class ContractProjection(
    val canonicalWorkspaceId: String?,
    val canonicalSessionId: String?,
    val canonicalThreadId: String?,
    val headerStatus: String,
    val headerLifecycle: String?,
    val lastSummary: String?,
    val questionSetId: String?,
    val pendingQuestionIds: List<String>,
    val quickAnswerChoices: List<Phase3DirectInboundChoiceProjection>,
    val visibleBusinessEventKeys: List<String>,
    val suppressedDirectBusinessEventKeys: List<String>,
)
