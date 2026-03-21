package net.thunderbird.feature.taskmail.internal.data.direct

import assertk.assertThat
import assertk.assertions.isEqualTo
import kotlin.test.Test
import net.thunderbird.feature.taskmail.internal.data.relay.protocol.RelayQuestion
import net.thunderbird.feature.taskmail.internal.data.relay.protocol.RelayQuestionState
import net.thunderbird.feature.taskmail.internal.data.relay.protocol.RelaySessionDelta
import net.thunderbird.feature.taskmail.internal.data.relay.protocol.RelaySessionSnapshot
import net.thunderbird.feature.taskmail.internal.data.relay.protocol.RelaySessionUpdate
import net.thunderbird.feature.taskmail.internal.data.relay.protocol.RelayStateTransition
import net.thunderbird.feature.taskmail.internal.data.relay.protocol.RelayTimelineItem
import net.thunderbird.feature.taskmail.internal.domain.model.TaskMailSessionLifecycle
import net.thunderbird.feature.taskmail.internal.domain.model.TaskMailSessionStatus

class TaskMailDirectSessionProjectorTest {
    private val testSubject = TaskMailDirectSessionProjector()

    @Test
    @Suppress("LongMethod")
    fun `project should map state transition into canonical single-question quick answers`() {
        val result = testSubject.project(
            updates = listOf(
                snapshotUpdate(
                    sequence = 15,
                    status = "running",
                    lastSummary = "Running.",
                ),
                RelaySessionUpdate(
                    schemaVersion = SCHEMA_VERSION,
                    subscriptionId = "sub_fx015",
                    workspaceId = WORKSPACE_ID,
                    sessionId = SESSION_ID,
                    threadId = THREAD_ID,
                    taskId = TASK_ID,
                    updateId = "sessupd:session_001:16",
                    sequence = 16,
                    sentAt = "2026-03-21T22:44:10",
                    updateType = "session_delta",
                    sessionDelta = RelaySessionDelta(
                        deltaType = "state_transition",
                        stateTransition = RelayStateTransition(
                            status = "awaiting_user_input",
                            lifecycle = "active",
                            lastSummary = "Need one answer before continuing.",
                            lastActiveAt = "2026-03-21T22:44:10",
                            lastProgressAt = "2026-03-21T22:44:10",
                            questionState = RelayQuestionState(
                                questionSetId = "qset_branch_choice",
                                questionCount = 1,
                                questions = listOf(
                                    RelayQuestion(
                                        questionId = "q_branch",
                                        questionText = "Which branch should I use?",
                                        questionType = "single_choice",
                                        required = true,
                                        choices = listOf("main", "release"),
                                        choiceLabels = mapOf(
                                            "main" to "Main branch",
                                            "release" to "Release branch",
                                        ),
                                    ),
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        )

        assertThat(result?.headerStatus).isEqualTo(TaskMailSessionStatus.WaitingUser)
        assertThat(result?.headerLifecycle).isEqualTo(TaskMailSessionLifecycle.Active)
        assertThat(result?.lastSummary).isEqualTo("Need one answer before continuing.")
        assertThat(result?.questionSetId).isEqualTo("qset_branch_choice")
        assertThat(result?.pendingQuestionIds).isEqualTo(listOf("q_branch"))
        assertThat(result?.quickAnswerChoices).isEqualTo(
            listOf(
                TaskMailDirectChoiceProjection("main", "Main branch"),
                TaskMailDirectChoiceProjection("release", "Release branch"),
            ),
        )
    }

    @Test
    fun `project should suppress direct terminal summary while keeping visible business event for durable mail`() {
        val result = testSubject.project(
            updates = listOf(
                snapshotUpdate(
                    sequence = 19,
                    status = "done",
                    lastSummary = "Completed successfully.",
                    timelineItems = listOf(
                        RelayTimelineItem(
                            itemId = "tl_terminal_017",
                            businessEventKey = "terminal/done/2026-03-21T22:46:03",
                            itemType = "terminal_summary",
                            createdAt = "2026-03-21T22:46:03",
                            status = "done",
                            text = "Completed successfully.",
                        ),
                    ),
                ),
            ),
            mailBusinessEventKeys = listOf("terminal/done/2026-03-21T22:46:03"),
        )

        assertThat(result?.visibleBusinessEventKeys).isEqualTo(
            listOf("terminal/done/2026-03-21T22:46:03"),
        )
        assertThat(result?.suppressedDirectBusinessEventKeys).isEqualTo(
            listOf("terminal/done/2026-03-21T22:46:03"),
        )
        assertThat(result?.provisionalTimeline).isEqualTo(emptyList())
    }

    @Test
    @Suppress("LongMethod")
    fun `project should suppress gapped delta and replace it with fresh snapshot`() {
        val result = testSubject.project(
            updates = listOf(
                snapshotUpdate(
                    sequence = 30,
                    status = "running",
                    lastSummary = "Running before gap.",
                    subscriptionId = "sub_fx020a",
                ),
                RelaySessionUpdate(
                    schemaVersion = SCHEMA_VERSION,
                    subscriptionId = "sub_fx020a",
                    workspaceId = WORKSPACE_ID,
                    sessionId = SESSION_ID,
                    threadId = THREAD_ID,
                    taskId = TASK_ID,
                    updateId = "sessupd:session_001:32",
                    sequence = 32,
                    sentAt = "2026-03-21T22:49:10",
                    updateType = "session_delta",
                    sessionDelta = RelaySessionDelta(
                        deltaType = "timeline_append",
                        timelineItems = listOf(
                            RelayTimelineItem(
                                itemId = "tl_reply_gap_020",
                                businessEventKey = "reply/2026-03-21T22:49:10",
                                itemType = "assistant_reply_preview",
                                createdAt = "2026-03-21T22:49:10",
                                text = "This delta should be treated as gapped because sequence 31 is missing.",
                            ),
                        ),
                    ),
                ),
                snapshotUpdate(
                    sequence = 40,
                    status = "running",
                    lastSummary = "Recovered after resubscribe.",
                    subscriptionId = "sub_fx020b",
                    timelineItems = listOf(
                        RelayTimelineItem(
                            itemId = "tl_reply_020b",
                            businessEventKey = "reply/2026-03-21T22:49:20",
                            itemType = "assistant_reply_preview",
                            createdAt = "2026-03-21T22:49:20",
                            text = "Recovered after resubscribe.",
                        ),
                    ),
                ),
            ),
        )

        assertThat(result?.lastSummary).isEqualTo("Recovered after resubscribe.")
        assertThat(result?.visibleBusinessEventKeys).isEqualTo(
            listOf("reply/2026-03-21T22:49:20"),
        )
        assertThat(result?.suppressedDirectBusinessEventKeys).isEqualTo(
            listOf("reply/2026-03-21T22:49:10"),
        )
        assertThat(result?.lastSequence).isEqualTo(40L)
        assertThat(result?.provisionalTimeline?.map { item -> item.businessEventKeys.single() }).isEqualTo(
            listOf("reply/2026-03-21T22:49:20"),
        )
    }

    @Test
    fun `project should expose provisional reply preview timeline item`() {
        val result = testSubject.project(
            updates = listOf(
                snapshotUpdate(
                    sequence = 8,
                    status = "running",
                    lastSummary = "Inspecting the detail merge path.",
                    timelineItems = listOf(
                        RelayTimelineItem(
                            itemId = "tl_reply_008",
                            businessEventKey = "reply/2026-03-21T22:37:03",
                            itemType = "assistant_reply_preview",
                            createdAt = "2026-03-21T22:37:03",
                            text = "Inspecting the detail merge path.",
                        ),
                    ),
                ),
            ),
        )

        assertThat(result?.provisionalTimeline?.single()?.businessEventKeys).isEqualTo(
            listOf("reply/2026-03-21T22:37:03"),
        )
        assertThat(result?.provisionalTimeline?.single()?.body?.plainText).isEqualTo(
            "Inspecting the detail merge path.",
        )
    }
}

private fun snapshotUpdate(
    sequence: Long,
    status: String,
    lastSummary: String,
    subscriptionId: String = "sub_fx_base",
    timelineItems: List<RelayTimelineItem> = emptyList(),
): RelaySessionUpdate {
    return RelaySessionUpdate(
        schemaVersion = SCHEMA_VERSION,
        subscriptionId = subscriptionId,
        workspaceId = WORKSPACE_ID,
        sessionId = SESSION_ID,
        threadId = THREAD_ID,
        taskId = TASK_ID,
        updateId = "sessupd:session_001:$sequence",
        sequence = sequence,
        sentAt = "2026-03-21T22:38:03",
        updateType = "session_snapshot",
        sessionSnapshot = RelaySessionSnapshot(
            sessionName = "Phase 3 detail bridge",
            backend = "codex",
            repoPath = "E:\\projects\\android_task_manager",
            workdir = "feature/taskmail/internal",
            status = status,
            lifecycle = "active",
            lastSummary = lastSummary,
            lastActiveAt = "2026-03-21T22:38:03",
            lastProgressAt = "2026-03-21T22:38:03",
            questionState = null,
            timelineItems = timelineItems,
        ),
    )
}

private const val SCHEMA_VERSION = "phase3-direct-inbound-wire-v1"
private const val WORKSPACE_ID = "workspace_a13f92d1c0ef"
private const val SESSION_ID = "session_001"
private const val THREAD_ID = "thread_001"
private const val TASK_ID = "task_001"
