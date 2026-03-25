package net.thunderbird.feature.taskmail.internal.data.direct

import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import net.thunderbird.feature.taskmail.internal.data.relay.protocol.RelayQuestion
import net.thunderbird.feature.taskmail.internal.data.relay.protocol.RelayQuestionState
import net.thunderbird.feature.taskmail.internal.data.relay.protocol.RelaySessionUpdate
import net.thunderbird.feature.taskmail.internal.data.relay.protocol.RelayStateTransition
import net.thunderbird.feature.taskmail.internal.data.relay.protocol.RelayTimelineItem
import net.thunderbird.feature.taskmail.internal.domain.model.TaskMailBackend
import net.thunderbird.feature.taskmail.internal.domain.model.TaskSessionControlPlaneSnapshot
import net.thunderbird.feature.taskmail.internal.domain.model.TaskMailSessionLifecycle
import net.thunderbird.feature.taskmail.internal.domain.model.TaskMailSessionStatus
import net.thunderbird.feature.taskmail.internal.domain.model.TaskMailStatusLabel
import net.thunderbird.feature.taskmail.internal.domain.model.TaskMessageBody
import net.thunderbird.feature.taskmail.internal.domain.model.TaskTimelineDirection
import net.thunderbird.feature.taskmail.internal.domain.model.TaskTimelineItem
import net.thunderbird.feature.taskmail.internal.domain.parser.TaskQuestionCapsule

internal class TaskMailDirectSessionProjector {
    fun project(
        updates: List<RelaySessionUpdate>,
        mailBusinessEventKeys: List<String> = emptyList(),
    ): TaskMailDirectSessionProjection? {
        val firstSnapshot = updates.firstOrNull { update ->
            update.updateType.equals(UPDATE_TYPE_SNAPSHOT, ignoreCase = true) && update.sessionSnapshot != null
        } ?: return null

        val state = MutableProjectionState(firstSnapshot)
        updates.dropWhile { it != firstSnapshot }.drop(1).forEach(state::apply)
        state.applyMailBusinessEventKeys(mailBusinessEventKeys)
        return state.toProjection()
    }
}

internal data class TaskMailDirectSessionProjection(
    val canonicalWorkspaceId: String,
    val canonicalSessionId: String,
    val canonicalThreadId: String,
    val taskId: String,
    val sessionName: String,
    val backend: TaskMailBackend,
    val headerStatus: TaskMailSessionStatus,
    val headerLifecycle: TaskMailSessionLifecycle? = null,
    val repoPath: String,
    val workdir: String? = null,
    val lastSummary: String? = null,
    val pausedFromStatus: TaskMailSessionStatus? = null,
    val lastActiveAt: String? = null,
    val lastProgressAt: String? = null,
    val pendingQuestions: List<TaskQuestionCapsule> = emptyList(),
    val provisionalTimeline: List<TaskTimelineItem> = emptyList(),
    val visibleBusinessEventKeys: List<String> = emptyList(),
    val suppressedDirectBusinessEventKeys: List<String> = emptyList(),
    val lastSequence: Long,
    val controlPlaneSnapshot: TaskSessionControlPlaneSnapshot? = null,
) {
    val questionSetId: String?
        get() = pendingQuestions
            .mapNotNull(TaskQuestionCapsule::questionSetId)
            .distinct()
            .singleOrNull()

    val pendingQuestionIds: List<String>
        get() = pendingQuestions.map(TaskQuestionCapsule::questionId)

    val quickAnswerChoices: List<TaskMailDirectChoiceProjection>
        get() {
            val question = pendingQuestions.singleOrNull() ?: return emptyList()
            return question.choices.map { choice ->
                TaskMailDirectChoiceProjection(
                    value = choice,
                    label = question.choiceLabels[choice] ?: choice,
                )
            }
        }
}

internal data class TaskMailDirectChoiceProjection(
    val value: String,
    val label: String,
)

private class MutableProjectionState(firstSnapshot: RelaySessionUpdate) {
    var canonicalWorkspaceId: String = firstSnapshot.workspaceId
    var canonicalSessionId: String = firstSnapshot.sessionId
    var canonicalThreadId: String = firstSnapshot.threadId
    var taskId: String = firstSnapshot.taskId
    var sessionName: String = ""
    var backend: TaskMailBackend = TaskMailBackend.Codex
    var headerStatus: TaskMailSessionStatus = TaskMailSessionStatus.Unknown
    var headerLifecycle: TaskMailSessionLifecycle? = null
    var repoPath: String = ""
    var workdir: String? = null
    var lastSummary: String? = null
    var pausedFromStatus: TaskMailSessionStatus? = null
    var lastActiveAt: String? = null
    var lastProgressAt: String? = null
    var pendingQuestions: List<TaskQuestionCapsule> = emptyList()
    var lastSequence: Long = 0L

    private val visibleBusinessEventKeys = mutableListOf<String>()
    private val directVisibleBusinessEventKeys = linkedSetOf<String>()
    private val directVisibleItemIds = linkedSetOf<String>()
    private val mailVisibleBusinessEventKeys = linkedSetOf<String>()
    private val suppressedDirectBusinessEventKeys = linkedSetOf<String>()
    private val visibleDirectTimelineItems = linkedMapOf<String, TaskTimelineItem>()

    init {
        applySnapshot(firstSnapshot)
    }

    fun apply(update: RelaySessionUpdate) {
        if (update.sequence < lastSequence) return

        when {
            update.updateType.equals(UPDATE_TYPE_SNAPSHOT, ignoreCase = true) -> applySnapshot(update)
            update.updateType.equals(UPDATE_TYPE_DELTA, ignoreCase = true) -> applyDelta(update)
        }
    }

    fun applyMailBusinessEventKeys(keys: List<String>) {
        keys.forEach { key ->
            if (key.isBlank()) return@forEach

            mailVisibleBusinessEventKeys += key
            if (directVisibleBusinessEventKeys.remove(key)) {
                suppressedDirectBusinessEventKeys += key
                visibleDirectTimelineItems.remove(key)
            }
            appendVisibleKey(key)
        }
    }

    fun toProjection(): TaskMailDirectSessionProjection {
        return TaskMailDirectSessionProjection(
            canonicalWorkspaceId = canonicalWorkspaceId,
            canonicalSessionId = canonicalSessionId,
            canonicalThreadId = canonicalThreadId,
            taskId = taskId,
            sessionName = sessionName,
            backend = backend,
            headerStatus = headerStatus,
            headerLifecycle = headerLifecycle,
            repoPath = repoPath,
            workdir = workdir,
            lastSummary = lastSummary,
            pausedFromStatus = pausedFromStatus,
            lastActiveAt = lastActiveAt,
            lastProgressAt = lastProgressAt,
            pendingQuestions = pendingQuestions,
            provisionalTimeline = visibleDirectTimelineItems.values
                .sortedWith(compareBy(TaskTimelineItem::timestamp, TaskTimelineItem::id)),
            visibleBusinessEventKeys = visibleBusinessEventKeys.toList(),
            suppressedDirectBusinessEventKeys = suppressedDirectBusinessEventKeys.toList(),
            lastSequence = lastSequence,
        )
    }

    private fun applySnapshot(update: RelaySessionUpdate) {
        val snapshot = update.sessionSnapshot ?: return

        canonicalWorkspaceId = update.workspaceId
        canonicalSessionId = update.sessionId
        canonicalThreadId = update.threadId
        taskId = update.taskId
        sessionName = snapshot.sessionName
        backend = TaskMailBackend.fromWireValue(snapshot.backend) ?: TaskMailBackend.Codex
        headerStatus = TaskMailSessionStatus.fromWireValue(snapshot.status) ?: TaskMailSessionStatus.Unknown
        headerLifecycle = TaskMailSessionLifecycle.fromWireValue(snapshot.lifecycle)
        repoPath = snapshot.repoPath
        workdir = snapshot.workdir
        lastSummary = snapshot.lastSummary
        pausedFromStatus = TaskMailSessionStatus.fromWireValue(snapshot.pausedFromStatus)
        lastActiveAt = snapshot.lastActiveAt
        lastProgressAt = snapshot.lastProgressAt
        pendingQuestions = snapshot.questionState.toQuestionCapsules()
        lastSequence = update.sequence

        visibleBusinessEventKeys.retainAll(mailVisibleBusinessEventKeys)
        directVisibleBusinessEventKeys.clear()
        directVisibleItemIds.clear()
        visibleDirectTimelineItems.clear()
        appendDirectTimelineItems(
            items = snapshot.timelineItems,
            suppressOnGap = false,
        )
    }

    private fun applyDelta(update: RelaySessionUpdate) {
        val delta = update.sessionDelta ?: return
        if (update.sequence <= lastSequence) return

        if (update.sequence != lastSequence + 1) {
            appendDirectTimelineItems(
                items = delta.timelineItems,
                suppressOnGap = true,
            )
        } else {
            when {
                delta.deltaType.equals(DELTA_TYPE_STATE_TRANSITION, ignoreCase = true) -> {
                    applyStateTransition(delta.stateTransition)
                }

                delta.deltaType.equals(DELTA_TYPE_TIMELINE_APPEND, ignoreCase = true) -> {
                    appendDirectTimelineItems(
                        items = delta.timelineItems,
                        suppressOnGap = false,
                    )
                }
            }

            lastSequence = update.sequence
        }
    }

    private fun applyStateTransition(transition: RelayStateTransition?) {
        transition ?: return

        headerStatus = TaskMailSessionStatus.fromWireValue(transition.status) ?: headerStatus
        headerLifecycle = TaskMailSessionLifecycle.fromWireValue(transition.lifecycle) ?: headerLifecycle
        lastSummary = transition.lastSummary
        pausedFromStatus = TaskMailSessionStatus.fromWireValue(transition.pausedFromStatus)
        lastActiveAt = transition.lastActiveAt
        lastProgressAt = transition.lastProgressAt
        pendingQuestions = transition.questionState.toQuestionCapsules()
    }

    private fun appendDirectTimelineItems(
        items: List<RelayTimelineItem>,
        suppressOnGap: Boolean,
    ) {
        items.forEach { item ->
            if (item.itemId.isBlank()) return@forEach
            if (!directVisibleItemIds.add(item.itemId)) return@forEach

            val key = item.businessEventKey.takeIf(String::isNotBlank) ?: return@forEach
            if (suppressOnGap) {
                suppressedDirectBusinessEventKeys += key
                directVisibleItemIds.remove(item.itemId)
                directVisibleBusinessEventKeys.remove(key)
                visibleDirectTimelineItems.remove(key)
                return@forEach
            }

            if (key in mailVisibleBusinessEventKeys || key in suppressedDirectBusinessEventKeys) {
                directVisibleBusinessEventKeys.remove(key)
                visibleDirectTimelineItems.remove(key)
                appendVisibleKey(key)
                return@forEach
            }

            directVisibleBusinessEventKeys += key
            visibleDirectTimelineItems[key] = item.toTaskTimelineItem()
            appendVisibleKey(key)
        }
    }

    private fun appendVisibleKey(key: String) {
        if (key !in visibleBusinessEventKeys) {
            visibleBusinessEventKeys += key
        }
    }
}

private fun RelayTimelineItem.toTaskTimelineItem(): TaskTimelineItem {
    val plainText = text
        ?.trim()
        ?.takeIf(String::isNotBlank)
        ?: fallbackPlainText()

    return TaskTimelineItem(
        id = "direct:$itemId",
        timestamp = createdAt.toTimelineTimestamp(),
        direction = TaskTimelineDirection.System,
        statusLabel = toTimelineStatusLabel(),
        summary = plainText.takeIf(String::isNotBlank),
        body = TaskMessageBody(plainTextFallback = plainText),
        businessEventKeys = listOf(businessEventKey),
    )
}

private fun RelayTimelineItem.toTimelineStatusLabel(): TaskMailStatusLabel? {
    return when (itemType.lowercase()) {
        "question_prompt" -> TaskMailStatusLabel.Question
        "paused_hint" -> TaskMailStatusLabel.Paused
        "terminal_summary",
        "status_transition",
        -> TaskMailSessionStatus.fromWireValue(status)?.toTimelineStatusLabel()

        else -> null
    }
}

private fun RelayTimelineItem.fallbackPlainText(): String {
    return when (itemType.lowercase()) {
        "question_prompt" -> "Question pending."
        "paused_hint" -> "Session is paused."
        "terminal_summary",
        "status_transition",
        -> status
            ?.let(TaskMailSessionStatus::fromWireValue)
            ?.let { taskStatus -> "Status: ${taskStatus.name}" }
            .orEmpty()

        else -> ""
    }
}

private fun TaskMailSessionStatus.toTimelineStatusLabel(): TaskMailStatusLabel? {
    return when (this) {
        TaskMailSessionStatus.Queued -> TaskMailStatusLabel.Accepted
        TaskMailSessionStatus.Running -> TaskMailStatusLabel.Running
        TaskMailSessionStatus.WaitingUser -> TaskMailStatusLabel.Question
        TaskMailSessionStatus.Paused -> TaskMailStatusLabel.Paused
        TaskMailSessionStatus.Done -> TaskMailStatusLabel.Done
        TaskMailSessionStatus.Failed -> TaskMailStatusLabel.Failed
        TaskMailSessionStatus.Killed -> TaskMailStatusLabel.Killed
        TaskMailSessionStatus.Unknown -> TaskMailStatusLabel.Status
    }
}

private fun String.toTimelineTimestamp(): Long {
    return TIMELINE_TIMESTAMP_PATTERNS
        .firstNotNullOfOrNull { pattern -> parseUtcTimestamp(pattern) }
        ?: 0L
}

private fun String.parseUtcTimestamp(pattern: String): Long? {
    return runCatching {
        SimpleDateFormat(pattern, Locale.US).apply {
            isLenient = false
            timeZone = TimeZone.getTimeZone("UTC")
        }.parse(this)?.time
    }.getOrNull()
}

private fun RelayQuestionState?.toQuestionCapsules(): List<TaskQuestionCapsule> {
    return this?.questions
        ?.map { question -> question.toTaskQuestionCapsule(questionSetId) }
        .orEmpty()
}

private fun RelayQuestion.toTaskQuestionCapsule(questionSetId: String?): TaskQuestionCapsule {
    return TaskQuestionCapsule(
        questionId = questionId,
        questionText = questionText,
        choices = choices,
        questionSetId = questionSetId,
        questionType = questionType,
        required = required,
        choiceLabels = choiceLabels,
    )
}

private const val UPDATE_TYPE_SNAPSHOT = "session_snapshot"
private const val UPDATE_TYPE_DELTA = "session_delta"
private const val DELTA_TYPE_STATE_TRANSITION = "state_transition"
private const val DELTA_TYPE_TIMELINE_APPEND = "timeline_append"
private val TIMELINE_TIMESTAMP_PATTERNS = listOf(
    "yyyy-MM-dd'T'HH:mm:ss",
    "yyyy-MM-dd'T'HH:mm:ss'Z'",
)
