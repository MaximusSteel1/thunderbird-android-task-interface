package net.thunderbird.feature.taskmail.internal.testdata

import java.io.File
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import net.thunderbird.feature.taskmail.internal.data.relay.protocol.RelaySessionUpdate

internal object Phase3DirectInboundFixturePackageLoader {
    fun manifestFile(): File = File(resolveFixtureRoot(), "manifest.json")

    fun loadManifest(json: Json): Phase3DirectInboundFixtureManifest {
        return json.decodeFromString(manifestFile().readText())
    }

    fun loadFixture(
        json: Json,
        entry: Phase3DirectInboundFixtureManifestEntry,
    ): Phase3DirectInboundFixturePackage {
        return json.decodeFromString(File(resolveFixtureRoot(), entry.file).readText())
    }

    private fun resolveFixtureRoot(): File {
        return FIXTURE_ROOT_CANDIDATES.firstOrNull(File::exists)
            ?: FIXTURE_ROOT_CANDIDATES.first()
    }
}

@Serializable
internal data class Phase3DirectInboundFixtureManifest(
    val fixtures: List<Phase3DirectInboundFixtureManifestEntry> = emptyList(),
)

@Serializable
internal data class Phase3DirectInboundFixtureManifestEntry(
    @SerialName("fixture_id")
    val fixtureId: String,
    val file: String,
)

@Serializable
internal data class Phase3DirectInboundFixturePackage(
    @SerialName("fixture_meta")
    val fixtureMeta: Phase3DirectInboundFixtureMeta,
    @SerialName("session_updates")
    val sessionUpdates: List<RelaySessionUpdate> = emptyList(),
    @SerialName("mail_companion")
    val mailCompanion: Phase3DirectInboundMailCompanion = Phase3DirectInboundMailCompanion(),
    @SerialName("expected_projection")
    val expectedProjection: Phase3DirectInboundExpectedProjection,
)

@Serializable
internal data class Phase3DirectInboundFixtureMeta(
    @SerialName("fixture_id")
    val fixtureId: String,
)

@Serializable
internal data class Phase3DirectInboundMailCompanion(
    val items: List<Phase3DirectInboundMailCompanionItem> = emptyList(),
)

@Serializable
internal data class Phase3DirectInboundMailCompanionItem(
    @SerialName("business_event_key")
    val businessEventKey: String,
    val summary: String? = null,
    val status: String? = null,
    @SerialName("arrived_at")
    val arrivedAt: String? = null,
)

@Serializable
internal data class Phase3DirectInboundExpectedProjection(
    @SerialName("canonical_workspace_id")
    val canonicalWorkspaceId: String? = null,
    @SerialName("canonical_session_id")
    val canonicalSessionId: String? = null,
    @SerialName("canonical_thread_id")
    val canonicalThreadId: String? = null,
    @SerialName("header_status")
    val headerStatus: String,
    @SerialName("header_lifecycle")
    val headerLifecycle: String? = null,
    @SerialName("last_summary")
    val lastSummary: String? = null,
    @SerialName("question_set_id")
    val questionSetId: String? = null,
    @SerialName("pending_question_ids")
    val pendingQuestionIds: List<String> = emptyList(),
    @SerialName("quick_answer_choices")
    val quickAnswerChoices: List<Phase3DirectInboundChoiceProjection> = emptyList(),
    @SerialName("visible_business_event_keys")
    val visibleBusinessEventKeys: List<String> = emptyList(),
    @SerialName("suppressed_direct_business_event_keys")
    val suppressedDirectBusinessEventKeys: List<String> = emptyList(),
)

@Serializable
internal data class Phase3DirectInboundChoiceProjection(
    val value: String,
    val label: String,
)

private val FIXTURE_ROOT_CANDIDATES = listOf(
    File("E:\\projects\\mail_based_task_manager\\docs\\plans\\fixtures\\phase3_direct_inbound_v1"),
    File("E:\\projects\\mail_based_task_manager\\taskMail_PC\\docs\\plans\\fixtures\\phase3_direct_inbound_v1"),
)
