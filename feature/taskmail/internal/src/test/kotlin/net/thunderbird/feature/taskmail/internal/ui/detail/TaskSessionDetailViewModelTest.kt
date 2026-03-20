package net.thunderbird.feature.taskmail.internal.ui.detail

import app.k9mail.core.ui.compose.testing.mvi.MviContext
import app.k9mail.core.ui.compose.testing.mvi.MviTurbines
import app.k9mail.core.ui.compose.testing.mvi.advanceUntilIdle
import app.k9mail.core.ui.compose.testing.mvi.runMviTest
import app.k9mail.core.ui.compose.testing.mvi.turbinesWithInitialStateCheck
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import net.thunderbird.core.testing.coroutines.MainDispatcherHelper
import net.thunderbird.feature.taskmail.internal.data.TaskMailForegroundRefreshTickerFactory
import net.thunderbird.feature.taskmail.internal.data.TaskMailReplyAttachmentResolver
import net.thunderbird.feature.taskmail.internal.data.TaskMailSessionProjector
import net.thunderbird.feature.taskmail.internal.data.TaskMailStoreChangeObserver
import net.thunderbird.feature.taskmail.internal.data.TaskMailSyncRequester
import net.thunderbird.feature.taskmail.internal.data.TaskMailTimelineAttachmentHandler
import net.thunderbird.feature.taskmail.internal.data.cache.TaskMailMessageJsonCodec
import net.thunderbird.feature.taskmail.internal.domain.model.MessageSyncState
import net.thunderbird.feature.taskmail.internal.domain.model.TaskMailSessionStatus
import net.thunderbird.feature.taskmail.internal.domain.model.TaskMessageAttachment
import net.thunderbird.feature.taskmail.internal.domain.model.TaskMessageBody
import net.thunderbird.feature.taskmail.internal.domain.model.TaskReplyAttachment
import net.thunderbird.feature.taskmail.internal.domain.model.TaskSessionDetail
import net.thunderbird.feature.taskmail.internal.domain.model.TaskSessionKey
import net.thunderbird.feature.taskmail.internal.domain.model.TaskTimelineDirection
import net.thunderbird.feature.taskmail.internal.domain.model.TaskTimelineItem
import net.thunderbird.feature.taskmail.internal.domain.model.UnifiedMessage
import net.thunderbird.feature.taskmail.internal.domain.parser.TaskQuestionCapsule
import net.thunderbird.feature.taskmail.internal.domain.reply.TaskMailReplyMode
import net.thunderbird.feature.taskmail.internal.domain.reply.TaskMailReplyRequest
import net.thunderbird.feature.taskmail.internal.domain.reply.TaskMailReplyResult
import net.thunderbird.feature.taskmail.internal.domain.reply.TaskMailReplySender
import net.thunderbird.feature.taskmail.internal.domain.repository.MessageSyncStateRepository
import net.thunderbird.feature.taskmail.internal.domain.repository.TaskSessionDetailRepository
import net.thunderbird.feature.taskmail.internal.domain.repository.UnifiedMessageRepository
import net.thunderbird.feature.taskmail.internal.domain.usecase.GetTaskSessionDetail
import net.thunderbird.feature.taskmail.internal.domain.usecase.ObserveTaskMailStoreChanges
import net.thunderbird.feature.taskmail.internal.domain.usecase.RefreshTaskMail
import net.thunderbird.feature.taskmail.internal.domain.usecase.SendTaskMailReply
import net.thunderbird.feature.taskmail.internal.domain.usecase.SyncTaskMailCache
import net.thunderbird.feature.taskmail.internal.preview.TaskMailPreviewData
import net.thunderbird.feature.taskmail.internal.sync.DEFAULT_MESSAGE_SYNC_SCOPE_KEY
import net.thunderbird.feature.taskmail.internal.sync.DEFAULT_MESSAGE_SYNC_SOURCE
import net.thunderbird.feature.taskmail.internal.sync.MessageSyncCoordinator
import net.thunderbird.feature.taskmail.internal.sync.MessageSyncRequest

@Suppress("LargeClass")
class TaskSessionDetailViewModelTest {

    @OptIn(ExperimentalCoroutinesApi::class)
    private val mainDispatcher = MainDispatcherHelper(UnconfinedTestDispatcher())

    @BeforeTest
    fun setUp() {
        mainDispatcher.setUp()
    }

    @AfterTest
    fun tearDown() {
        mainDispatcher.tearDown()
    }

    @Test
    fun `load detail should emit content state when repository returns detail`() = runMviTest {
        val syncRequester = FakeTaskMailSyncRequester()

        with(TaskSessionDetailViewModelRobot(this, FakeTaskSessionDetailRepository(), syncRequester = syncRequester)) {
            start()
            loadDetail()
            assertLoadedDetail()
            assertThat(syncRequester.requestCount).isEqualTo(0)
            ensureThatAllEventsAreConsumed()
        }
    }

    @Test
    fun `load detail should show cached detail before background cache sync completes`() = runMviTest {
        val cacheSyncGate = CompletableDeferred<Unit>()

        with(
            TaskSessionDetailViewModelRobot(
                mviContext = this,
                repository = FakeTaskSessionDetailRepository(),
                syncTaskMailCache = createBlockingDetailSyncTaskMailCache(cacheSyncGate),
            ),
        ) {
            start()
            loadDetail()
            assertLoadedDetail()
            assertThat(viewModelState().isLoading).isEqualTo(false)
            assertThat(viewModelState().isRefreshing).isEqualTo(true)

            cacheSyncGate.complete(Unit)
            advanceUntilIdle()

            assertLoadedDetail()
            assertThat(viewModelState().isRefreshing).isEqualTo(false)
            ensureThatAllEventsAreConsumed()
        }
    }

    @Test
    fun `load detail should emit error state when repository returns null`() = runMviTest {
        with(TaskSessionDetailViewModelRobot(this, FakeTaskSessionDetailRepository(detail = null))) {
            start()
            loadDetail()
            assertNotFoundError()
            ensureThatAllEventsAreConsumed()
        }
    }

    @Test
    fun `load detail should emit error state when repository throws`() = runMviTest {
        with(TaskSessionDetailViewModelRobot(this, FakeTaskSessionDetailRepository(shouldThrowDetailError = true))) {
            start()
            loadDetail()
            assertLoadError()
            ensureThatAllEventsAreConsumed()
        }
    }

    @Test
    fun `back clicked should emit navigate back effect`() = runMviTest {
        with(TaskSessionDetailViewModelRobot(this, FakeTaskSessionDetailRepository())) {
            start()
            clickBack()
            assertNavigateBackEffect()
            ensureThatAllEventsAreConsumed()
        }
    }

    @Test
    fun `load detail should keep null session id for fallback thread sessions`() = runMviTest {
        val repository = FakeTaskSessionDetailRepository()

        with(TaskSessionDetailViewModelRobot(this, repository)) {
            start()
            loadDetail(sessionId = null, threadId = "thread_321")
            assertThat(repository.requestedKey).isEqualTo(
                TaskSessionKey(
                    sessionId = null,
                    threadId = "thread_321",
                ),
            )
            ensureThatAllEventsAreConsumed()
        }
    }

    @Test
    fun `draft changed should update draft text`() = runMviTest {
        with(TaskSessionDetailViewModelRobot(this, FakeTaskSessionDetailRepository())) {
            start()
            changeDraft("hello")
            assertThat(viewModelState().draftText).isEqualTo("hello")
            ensureThatAllEventsAreConsumed()
        }
    }

    @Test
    fun `load detail should prefill structured answer template for multi question sessions`() = runMviTest {
        with(TaskSessionDetailViewModelRobot(this, FakeTaskSessionDetailRepository(detail = multiQuestionDetail()))) {
            start()
            loadDetail()
            assertThat(viewModelState().detail?.requiresStructuredReply).isEqualTo(true)
            assertThat(viewModelState().draftText).isEqualTo(
                "Answers:\nphase2_entry_position:\nphase2_icon_strings:",
            )
            ensureThatAllEventsAreConsumed()
        }
    }

    @Test
    fun `load detail should map timeline attachment metadata into ui state`() = runMviTest {
        val detailWithAttachment = TaskMailPreviewData.sessionDetails.first().let { detail ->
            detail.copy(
                timeline = detail.timeline.map { item ->
                    item.copy(
                        attachments = listOf(
                            TaskMessageAttachment(
                                id = "content://taskmail/chart-preview",
                                displayName = "result_chart.png",
                                contentType = "image/png",
                                sizeBytes = 2_048L,
                                isInline = true,
                                isImage = true,
                                internalUriString = "content://taskmail/chart-preview",
                                accountUuid = "account_uuid",
                                folderId = 1L,
                                messageServerId = "msg_001",
                                partId = 42L,
                                isContentAvailable = true,
                            ),
                        ),
                    )
                },
            )
        }

        with(TaskSessionDetailViewModelRobot(this, FakeTaskSessionDetailRepository(detail = detailWithAttachment))) {
            start()
            loadDetail()
            val attachment = viewModelState().detail?.timeline?.single()?.attachments?.single()
            assertThat(attachment).isNotNull()
            assertThat(attachment?.displayName).isEqualTo("result_chart.png")
            assertThat(attachment?.isInline).isEqualTo(true)
            assertThat(attachment?.isImage).isEqualTo(true)
            assertThat(attachment?.internalUriString).isEqualTo("content://taskmail/chart-preview")
            ensureThatAllEventsAreConsumed()
        }
    }

    @Test
    fun `load detail should expose newest timeline item first in ui state`() = runMviTest {
        val detailWithChronologicalTimeline = TaskMailPreviewData.sessionDetails.first().copy(
            timeline = listOf(
                TaskTimelineItem(
                    id = "timeline_oldest",
                    timestamp = 100L,
                    direction = TaskTimelineDirection.System,
                    body = TaskMessageBody(
                        plainText = "Oldest timeline item.",
                        markdownCandidate = false,
                    ),
                ),
                TaskTimelineItem(
                    id = "timeline_newest",
                    timestamp = 200L,
                    direction = TaskTimelineDirection.Outgoing,
                    body = TaskMessageBody(
                        plainText = "Newest timeline item.",
                        markdownCandidate = false,
                    ),
                ),
            ),
        )

        with(
            TaskSessionDetailViewModelRobot(
                this,
                FakeTaskSessionDetailRepository(detail = detailWithChronologicalTimeline),
            ),
        ) {
            start()
            loadDetail()
            assertThat(viewModelState().detail?.timeline?.map(TaskTimelineItemUi::id)).isEqualTo(
                listOf("timeline_newest", "timeline_oldest"),
            )
            ensureThatAllEventsAreConsumed()
        }
    }

    @Test
    fun `load detail should expose quick answer labels while preserving canonical choice values`() = runMviTest {
        with(
            TaskSessionDetailViewModelRobot(
                this,
                FakeTaskSessionDetailRepository(detail = singleQuestionDetailWithChoiceLabels()),
            ),
        ) {
            start()
            loadDetail()
            assertThat(viewModelState().detail?.quickAnswerChoices?.map(TaskPendingQuestionChoiceUi::label)).isEqualTo(
                listOf("Ship it", "Not yet"),
            )
            assertThat(viewModelState().detail?.quickAnswerChoices?.map(TaskPendingQuestionChoiceUi::value)).isEqualTo(
                listOf("approve", "decline"),
            )
            ensureThatAllEventsAreConsumed()
        }
    }

    @Test
    fun `attachments selected should update reply attachments`() = runMviTest {
        val replyAttachment = sampleReplyAttachment(
            id = "content://taskmail/chart",
            displayName = "chart.png",
            contentType = "image/png",
        )
        val attachmentResolver = FakeTaskMailReplyAttachmentResolver(
            attachmentsToReturn = listOf(replyAttachment),
        )

        with(
            TaskSessionDetailViewModelRobot(
                this,
                FakeTaskSessionDetailRepository(),
                attachmentResolver = attachmentResolver,
            ),
        ) {
            start()
            loadDetail()
            selectAttachments(listOf("content://taskmail/chart"))
            assertThat(viewModelState().replyAttachments).isEqualTo(persistentListOf(replyAttachment))
            ensureThatAllEventsAreConsumed()
        }
    }

    @Test
    fun `open timeline attachment should emit open attachment effect`() = runMviTest {
        val timelineAttachmentHandler = FakeTaskMailTimelineAttachmentHandler()

        with(
            TaskSessionDetailViewModelRobot(
                this,
                FakeTaskSessionDetailRepository(detail = detailWithTimelineAttachment()),
                timelineAttachmentHandler = timelineAttachmentHandler,
            ),
        ) {
            start()
            loadDetail()
            openTimelineAttachment("content://taskmail/chart-preview")
            assertOpenAttachmentEffect()
            ensureThatAllEventsAreConsumed()
        }
    }

    @Test
    fun `save timeline attachment should emit create document effect`() = runMviTest {
        with(
            TaskSessionDetailViewModelRobot(
                this,
                FakeTaskSessionDetailRepository(detail = detailWithTimelineAttachment()),
            ),
        ) {
            start()
            loadDetail()
            requestSaveForTimelineAttachment("content://taskmail/chart-preview")
            assertCreateAttachmentDocumentEffect(
                attachmentId = "content://taskmail/chart-preview",
                displayName = "result_chart.png",
                mimeType = "image/png",
            )
            ensureThatAllEventsAreConsumed()
        }
    }

    @Test
    fun `save destination selected should delegate to timeline attachment handler`() = runMviTest {
        val timelineAttachmentHandler = FakeTaskMailTimelineAttachmentHandler()

        with(
            TaskSessionDetailViewModelRobot(
                this,
                FakeTaskSessionDetailRepository(detail = detailWithTimelineAttachment()),
                timelineAttachmentHandler = timelineAttachmentHandler,
            ),
        ) {
            start()
            loadDetail()
            saveTimelineAttachment(
                attachmentId = "content://taskmail/chart-preview",
                destinationUriString = "content://documents/chart",
            )
            assertThat(
                timelineAttachmentHandler.savedAttachments.single().second,
            ).isEqualTo("content://documents/chart")
            ensureThatAllEventsAreConsumed()
        }
    }

    @Test
    fun `send reply should allow attachment only free text replies`() = runMviTest {
        val sender = FakeTaskMailReplySender()
        val replyAttachment = sampleReplyAttachment()
        val attachmentResolver = FakeTaskMailReplyAttachmentResolver(
            attachmentsToReturn = listOf(replyAttachment),
        )

        with(TaskSessionDetailViewModelRobot(this, FakeTaskSessionDetailRepository(), sender, attachmentResolver)) {
            start()
            loadDetail()
            selectAttachments(listOf(replyAttachment.uriString))
            sendReply()
            assertThat(sender.requests.single().body).isEqualTo("")
            assertThat(sender.requests.single().attachments).isEqualTo(listOf(replyAttachment))
            assertThat(viewModelState().replyAttachments).isEqualTo(persistentListOf())
            ensureThatAllEventsAreConsumed()
        }
    }

    @Test
    fun `send reply should clear draft and refresh on success`() = runMviTest {
        val repository = FakeTaskSessionDetailRepository()
        val sender = FakeTaskMailReplySender()

        with(TaskSessionDetailViewModelRobot(this, repository, sender)) {
            start()
            loadDetail()
            changeDraft("ship it")
            sendReply()
            assertThat(sender.requests.single().body).isEqualTo("ship it")
            assertThat(viewModelState().draftText).isEqualTo("")
            assertThat(viewModelState().sendError).isEqualTo(null)
            assertThat(repository.requestedKeys.size).isEqualTo(3)
            ensureThatAllEventsAreConsumed()
        }
    }

    @Test
    fun `send reply should send structured answers for multi question sessions`() = runMviTest {
        val sender = FakeTaskMailReplySender()
        val repository = FakeTaskSessionDetailRepository(detail = multiQuestionDetail())
        val structuredDraft = """
            Answers:
            phase2_entry_position: below
            phase2_icon_strings: provide
        """.trimIndent()

        with(TaskSessionDetailViewModelRobot(this, repository, sender)) {
            start()
            loadDetail()
            changeDraft(structuredDraft)
            sendReply()
            assertThat(sender.requests.single().body).isEqualTo(structuredDraft)
            assertThat(sender.requests.single().mode).isEqualTo(TaskMailReplyMode.AnswerMultiQuestion)
            ensureThatAllEventsAreConsumed()
        }
    }

    @Test
    fun `send reply should block empty structured answer template`() = runMviTest {
        val sender = FakeTaskMailReplySender()
        val repository = FakeTaskSessionDetailRepository(detail = multiQuestionDetail())

        with(TaskSessionDetailViewModelRobot(this, repository, sender)) {
            start()
            loadDetail()
            sendReply()
            assertThat(sender.requests.size).isEqualTo(0)
            assertThat(viewModelState().sendError).isEqualTo(
                "Complete all required answers using valid question_id values before sending.",
            )
            ensureThatAllEventsAreConsumed()
        }
    }

    @Test
    fun `send reply should block attachment only structured answers`() = runMviTest {
        val sender = FakeTaskMailReplySender()
        val replyAttachment = sampleReplyAttachment()
        val attachmentResolver = FakeTaskMailReplyAttachmentResolver(
            attachmentsToReturn = listOf(replyAttachment),
        )
        val repository = FakeTaskSessionDetailRepository(detail = multiQuestionDetail())

        with(TaskSessionDetailViewModelRobot(this, repository, sender, attachmentResolver)) {
            start()
            loadDetail()
            selectAttachments(listOf(replyAttachment.uriString))
            sendReply()
            assertThat(sender.requests.size).isEqualTo(0)
            assertThat(viewModelState().sendError).isEqualTo(
                "Complete all required answers using valid question_id values before sending.",
            )
            ensureThatAllEventsAreConsumed()
        }
    }

    @Test
    fun `send reply should preserve draft on failure`() = runMviTest {
        val sender = FakeTaskMailReplySender(
            result = TaskMailReplyResult.failure("send failed"),
        )

        with(TaskSessionDetailViewModelRobot(this, FakeTaskSessionDetailRepository(), sender)) {
            start()
            loadDetail()
            changeDraft("ship it")
            sendReply()
            assertThat(viewModelState().draftText).isEqualTo("ship it")
            assertThat(viewModelState().sendError).isEqualTo("send failed")
            ensureThatAllEventsAreConsumed()
        }
    }

    @Test
    fun `send should be blocked when reply context is unavailable`() = runMviTest {
        val sender = FakeTaskMailReplySender()
        val repository = FakeTaskSessionDetailRepository(
            detail = TaskMailPreviewData.sessionDetails.first().copy(replyContext = null),
        )

        with(TaskSessionDetailViewModelRobot(this, repository, sender)) {
            start()
            loadDetail()
            changeDraft("ship it")
            sendReply()
            assertThat(sender.requests.size).isEqualTo(0)
            assertThat(viewModelState().sendError).isEqualTo("Reply unavailable for this session.")
            ensureThatAllEventsAreConsumed()
        }
    }

    @Test
    fun `status query should dispatch slash status`() = runMviTest {
        val sender = FakeTaskMailReplySender()

        with(TaskSessionDetailViewModelRobot(this, FakeTaskSessionDetailRepository(), sender)) {
            start()
            loadDetail()
            sendStatusQuery()
            assertThat(sender.requests.single().body).isEqualTo("/status")
            ensureThatAllEventsAreConsumed()
        }
    }

    @Test
    fun `status query should be blocked while attachments are selected`() = runMviTest {
        val sender = FakeTaskMailReplySender()
        val replyAttachment = sampleReplyAttachment()
        val attachmentResolver = FakeTaskMailReplyAttachmentResolver(
            attachmentsToReturn = listOf(replyAttachment),
        )

        with(TaskSessionDetailViewModelRobot(this, FakeTaskSessionDetailRepository(), sender, attachmentResolver)) {
            start()
            loadDetail()
            selectAttachments(listOf(replyAttachment.uriString))
            sendStatusQuery()
            assertThat(sender.requests.size).isEqualTo(0)
            assertThat(viewModelState().sendError).isEqualTo("Remove selected attachments before sending /status.")
            ensureThatAllEventsAreConsumed()
        }
    }

    @Test
    fun `question choice should dispatch selected choice`() = runMviTest {
        val sender = FakeTaskMailReplySender()

        with(TaskSessionDetailViewModelRobot(this, FakeTaskSessionDetailRepository(), sender)) {
            start()
            loadDetail()
            sendChoice("yes")
            assertThat(sender.requests.single().body).isEqualTo("yes")
            ensureThatAllEventsAreConsumed()
        }
    }

    @Test
    fun `send reply should prepend slash resume for paused sessions`() = runMviTest {
        val sender = FakeTaskMailReplySender()
        val repository = FakeTaskSessionDetailRepository(detail = pausedDetail())

        with(TaskSessionDetailViewModelRobot(this, repository, sender)) {
            start()
            loadDetail()
            changeDraft("Please continue with the cleanup.")
            sendReply()
            assertThat(sender.requests.single().body).isEqualTo("/resume\nPlease continue with the cleanup.")
            assertThat(sender.requests.single().mode).isEqualTo(TaskMailReplyMode.ResumeSession)
            ensureThatAllEventsAreConsumed()
        }
    }

    @Test
    fun `question choice should prepend slash resume when paused`() = runMviTest {
        val sender = FakeTaskMailReplySender()
        val repository = FakeTaskSessionDetailRepository(detail = pausedSingleQuestionDetail())

        with(TaskSessionDetailViewModelRobot(this, repository, sender)) {
            start()
            loadDetail()
            sendChoice("approve")
            assertThat(sender.requests.single().body).isEqualTo("/resume\napprove")
            assertThat(sender.requests.single().mode).isEqualTo(TaskMailReplyMode.ResumeSession)
            ensureThatAllEventsAreConsumed()
        }
    }

    @Test
    fun `question choice should be blocked for multi question sessions`() = runMviTest {
        val sender = FakeTaskMailReplySender()
        val repository = FakeTaskSessionDetailRepository(detail = multiQuestionDetail())

        with(TaskSessionDetailViewModelRobot(this, repository, sender)) {
            start()
            loadDetail()
            sendChoice("below")
            assertThat(sender.requests.size).isEqualTo(0)
            assertThat(viewModelState().sendError).isEqualTo(
                "Quick answers are only available when exactly one pending question is active.",
            )
            ensureThatAllEventsAreConsumed()
        }
    }

    @Test
    fun `dismiss send error should clear send error`() = runMviTest {
        val sender = FakeTaskMailReplySender(
            result = TaskMailReplyResult.failure("send failed"),
        )

        with(TaskSessionDetailViewModelRobot(this, FakeTaskSessionDetailRepository(), sender)) {
            start()
            loadDetail()
            changeDraft("ship it")
            sendReply()
            dismissSendError()
            assertThat(viewModelState().sendError).isNull()
            ensureThatAllEventsAreConsumed()
        }
    }

    @Test
    fun `local mail change should refresh detail while preserving draft and attachments`() = runMviTest {
        val repository = FakeTaskSessionDetailRepository()
        val changeObserver = FakeTaskMailStoreChangeObserver()
        val replyAttachment = sampleReplyAttachment()
        val attachmentResolver = FakeTaskMailReplyAttachmentResolver(
            attachmentsToReturn = listOf(replyAttachment),
        )

        with(
            TaskSessionDetailViewModelRobot(
                this,
                repository,
                attachmentResolver = attachmentResolver,
                changeObserver = changeObserver,
            ),
        ) {
            start()
            loadDetail()
            changeDraft("Keep this draft")
            selectAttachments(listOf(replyAttachment.uriString))
            repository.detail = repository.detail?.copy(lastSummary = "Updated summary from local change")
            emitLocalChange()
            assertThat(viewModelState().draftText).isEqualTo("Keep this draft")
            assertThat(viewModelState().replyAttachments).isEqualTo(persistentListOf(replyAttachment))
            assertThat(viewModelState().detail?.lastSummary).isEqualTo("Updated summary from local change")
            ensureThatAllEventsAreConsumed()
        }
    }

    @Test
    fun `refresh clicked should trigger sync and reload detail`() = runMviTest {
        val repository = FakeTaskSessionDetailRepository()
        val syncRequester = FakeTaskMailSyncRequester()

        with(TaskSessionDetailViewModelRobot(this, repository, syncRequester = syncRequester)) {
            start()
            loadDetail()
            repository.detail = repository.detail?.copy(lastSummary = "Reloaded from detail refresh")
            refresh()
            assertThat(syncRequester.requestCount).isEqualTo(1)
            assertThat(viewModelState().detail?.lastSummary).isEqualTo("Reloaded from detail refresh")
            assertThat(viewModelState().refreshError).isEqualTo(null)
            ensureThatAllEventsAreConsumed()
        }
    }

    @Test
    fun `refresh clicked should keep detail visible when sync fails`() = runMviTest {
        val repository = FakeTaskSessionDetailRepository()
        val syncRequester = FakeTaskMailSyncRequester(
            result = Result.failure(IllegalStateException("sync error")),
        )

        with(TaskSessionDetailViewModelRobot(this, repository, syncRequester = syncRequester)) {
            start()
            loadDetail()
            refresh()
            assertThat(syncRequester.requestCount).isEqualTo(1)
            assertThat(viewModelState().detail).isNotNull()
            assertThat(viewModelState().refreshError).isEqualTo("sync error")
            ensureThatAllEventsAreConsumed()
        }
    }

    @Test
    fun `refresh clicked should keep detail visible when refresh fails`() = runMviTest {
        val repository = FakeTaskSessionDetailRepository()

        with(TaskSessionDetailViewModelRobot(this, repository)) {
            start()
            loadDetail()
            repository.shouldThrowDetailError = true
            refresh()
            assertThat(viewModelState().detail).isNotNull()
            assertThat(viewModelState().refreshError).isEqualTo("Failed to refresh TaskMail session detail.")
            ensureThatAllEventsAreConsumed()
        }
    }

    @Test
    fun `foreground refresh should sync the current detail account while visible`() = runMviTest {
        val syncRequester = FakeTaskMailSyncRequester()
        val tickerFactory = FakeTaskMailForegroundRefreshTickerFactory()

        with(
            TaskSessionDetailViewModelRobot(
                mviContext = this,
                repository = FakeTaskSessionDetailRepository(),
                syncRequester = syncRequester,
                foregroundRefreshTickerFactory = tickerFactory,
            ),
        ) {
            start()
            loadDetail()
            startForegroundRefresh()
            emitForegroundRefreshTick()
            stopForegroundRefresh()
            emitForegroundRefreshTick()
            assertThat(syncRequester.requestedAccountUuids).isEqualTo(
                listOf("account-1", "account-1"),
            )
            ensureThatAllEventsAreConsumed()
        }
    }

    @Test
    fun `foreground refresh should stay disabled when reply context is unavailable`() = runMviTest {
        val syncRequester = FakeTaskMailSyncRequester()
        val tickerFactory = FakeTaskMailForegroundRefreshTickerFactory()
        val repository = FakeTaskSessionDetailRepository(
            detail = TaskMailPreviewData.sessionDetails.first().copy(replyContext = null),
        )

        with(
            TaskSessionDetailViewModelRobot(
                mviContext = this,
                repository = repository,
                syncRequester = syncRequester,
                foregroundRefreshTickerFactory = tickerFactory,
            ),
        ) {
            start()
            loadDetail()
            startForegroundRefresh()
            emitForegroundRefreshTick()
            assertThat(syncRequester.requestedAccountUuids).isEqualTo(emptyList())
            ensureThatAllEventsAreConsumed()
        }
    }
}

private class TaskSessionDetailViewModelRobot(
    private val mviContext: MviContext,
    repository: TaskSessionDetailRepository,
    replySender: TaskMailReplySender = FakeTaskMailReplySender(),
    attachmentResolver: TaskMailReplyAttachmentResolver = FakeTaskMailReplyAttachmentResolver(),
    timelineAttachmentHandler: TaskMailTimelineAttachmentHandler = FakeTaskMailTimelineAttachmentHandler(),
    syncRequester: FakeTaskMailSyncRequester = FakeTaskMailSyncRequester(),
    private val changeObserver: FakeTaskMailStoreChangeObserver = FakeTaskMailStoreChangeObserver(),
    private val foregroundRefreshTickerFactory: FakeTaskMailForegroundRefreshTickerFactory =
        FakeTaskMailForegroundRefreshTickerFactory(),
    syncTaskMailCache: SyncTaskMailCache? = null,
) {
    private val viewModel = TaskSessionDetailViewModel(
        detailRepository = repository,
        getTaskSessionDetail = GetTaskSessionDetail(repository),
        refreshTaskMail = RefreshTaskMail(syncRequester),
        observeTaskMailStoreChanges = ObserveTaskMailStoreChanges(changeObserver),
        foregroundRefreshTickerFactory = foregroundRefreshTickerFactory,
        sendTaskMailReply = SendTaskMailReply(replySender),
        replyAttachmentResolver = attachmentResolver,
        timelineAttachmentHandler = timelineAttachmentHandler,
        syncTaskMailCache = syncTaskMailCache,
    )
    private lateinit var turbines: MviTurbines<TaskSessionDetailContract.State, TaskSessionDetailContract.Effect>

    suspend fun start() {
        turbines = mviContext.turbinesWithInitialStateCheck(viewModel, TaskSessionDetailContract.State())
    }

    suspend fun loadDetail(
        sessionId: String? = "session_001",
        threadId: String = "thread_001",
    ) {
        viewModel.event(
            TaskSessionDetailContract.Event.LoadDetail(
                sessionId = sessionId,
                threadId = threadId,
            ),
        )
        mviContext.advanceUntilIdle()
    }

    fun changeDraft(text: String) {
        viewModel.event(TaskSessionDetailContract.Event.DraftChanged(text))
    }

    suspend fun selectAttachments(uriStrings: List<String>) {
        viewModel.event(TaskSessionDetailContract.Event.AttachmentsSelected(uriStrings))
        mviContext.advanceUntilIdle()
    }

    suspend fun sendReply() {
        viewModel.event(TaskSessionDetailContract.Event.SendReplyClicked)
        mviContext.advanceUntilIdle()
    }

    suspend fun sendChoice(choice: String) {
        viewModel.event(TaskSessionDetailContract.Event.SendChoiceClicked(choice))
        mviContext.advanceUntilIdle()
    }

    suspend fun sendStatusQuery() {
        viewModel.event(TaskSessionDetailContract.Event.StatusQueryClicked)
        mviContext.advanceUntilIdle()
    }

    suspend fun refresh() {
        viewModel.event(TaskSessionDetailContract.Event.RefreshClicked)
        mviContext.advanceUntilIdle()
    }

    suspend fun emitLocalChange() {
        changeObserver.emitChange()
        mviContext.advanceUntilIdle()
    }

    suspend fun startForegroundRefresh() {
        viewModel.event(TaskSessionDetailContract.Event.ForegroundRefreshStarted)
        mviContext.advanceUntilIdle()
    }

    suspend fun stopForegroundRefresh() {
        viewModel.event(TaskSessionDetailContract.Event.ForegroundRefreshStopped)
        mviContext.advanceUntilIdle()
    }

    suspend fun emitForegroundRefreshTick() {
        foregroundRefreshTickerFactory.emitTick()
        mviContext.advanceUntilIdle()
    }

    suspend fun openTimelineAttachment(attachmentId: String) {
        viewModel.event(TaskSessionDetailContract.Event.OpenTimelineAttachmentClicked(attachmentId))
        mviContext.advanceUntilIdle()
    }

    fun requestSaveForTimelineAttachment(attachmentId: String) {
        viewModel.event(TaskSessionDetailContract.Event.SaveTimelineAttachmentClicked(attachmentId))
    }

    suspend fun saveTimelineAttachment(
        attachmentId: String,
        destinationUriString: String,
    ) {
        viewModel.event(
            TaskSessionDetailContract.Event.AttachmentSaveDestinationSelected(
                attachmentId = attachmentId,
                destinationUriString = destinationUriString,
            ),
        )
        mviContext.advanceUntilIdle()
    }

    fun dismissSendError() {
        viewModel.event(TaskSessionDetailContract.Event.DismissSendError)
    }

    suspend fun assertLoadedDetail() {
        val state = viewModel.state.value
        assertThat(state.isLoading).isEqualTo(false)
        assertThat(state.error).isEqualTo(null)
        assertThat(state.detail?.sessionName).isEqualTo("Build TaskMail Phase 1")
    }

    suspend fun assertNotFoundError() {
        val state = viewModel.state.value
        assertThat(state.isLoading).isEqualTo(false)
        assertThat(state.error).isEqualTo("Task session detail was not found.")
    }

    suspend fun assertLoadError() {
        val state = viewModel.state.value
        assertThat(state.isLoading).isEqualTo(false)
        assertThat(state.error).isEqualTo("Failed to load TaskMail session detail.")
    }

    fun clickBack() {
        viewModel.event(TaskSessionDetailContract.Event.BackClicked)
    }

    suspend fun assertNavigateBackEffect() {
        assertThat(turbines.awaitEffectItem()).isEqualTo(TaskSessionDetailContract.Effect.NavigateBack)
    }

    suspend fun assertOpenAttachmentEffect() {
        val effect = turbines.awaitEffectItem()
        assertThat(effect is TaskSessionDetailContract.Effect.OpenAttachment).isEqualTo(true)
    }

    suspend fun assertCreateAttachmentDocumentEffect(
        attachmentId: String,
        displayName: String,
        mimeType: String,
    ) {
        assertThat(turbines.awaitEffectItem()).isEqualTo(
            TaskSessionDetailContract.Effect.CreateAttachmentDocument(
                attachmentId = attachmentId,
                displayName = displayName,
                mimeType = mimeType,
            ),
        )
    }

    suspend fun ensureThatAllEventsAreConsumed() {
        turbines.stateTurbine.cancelAndIgnoreRemainingEvents()
        turbines.effectTurbine.ensureAllEventsConsumed()
    }

    fun viewModelState(): TaskSessionDetailContract.State = viewModel.state.value
}

private class FakeTaskSessionDetailRepository(
    var detail: TaskSessionDetail? = TaskMailPreviewData.sessionDetails.first(),
    var shouldThrowDetailError: Boolean = false,
) : TaskSessionDetailRepository {
    var requestedKey: TaskSessionKey? = null
    val requestedKeys = mutableListOf<TaskSessionKey>()

    override suspend fun getTaskSessionDetail(key: TaskSessionKey): TaskSessionDetail? {
        if (shouldThrowDetailError) error("detail error")
        requestedKey = key
        requestedKeys += key
        return detail
    }

    override suspend fun getTaskSessionDetails(): List<TaskSessionDetail> = listOfNotNull(detail)

    override suspend fun replaceAllSessionDetails(details: List<TaskSessionDetail>) = Unit

    override suspend fun upsertSessionDetails(details: List<TaskSessionDetail>) = Unit

    override suspend fun removeSessionDetails(keys: List<TaskSessionKey>) = Unit
}

private class FakeTaskMailSyncRequester(
    private val result: Result<Unit> = Result.success(Unit),
) : TaskMailSyncRequester {
    val requestedAccountUuids = mutableListOf<String?>()

    val requestCount: Int
        get() = requestedAccountUuids.size

    override suspend fun requestSync(): Result<Unit> {
        requestedAccountUuids += null
        return result
    }

    override suspend fun requestSync(accountUuid: String?): Result<Unit> {
        requestedAccountUuids += accountUuid
        return result
    }
}

private class FakeTaskMailStoreChangeObserver : TaskMailStoreChangeObserver {
    private val changes = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    override fun changes(): Flow<Unit> = changes

    suspend fun emitChange() {
        changes.emit(Unit)
    }
}

private class FakeTaskMailForegroundRefreshTickerFactory : TaskMailForegroundRefreshTickerFactory {
    private val ticks = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    override fun createTicker(intervalMs: Long): Flow<Unit> = ticks

    suspend fun emitTick() {
        ticks.emit(Unit)
    }
}

private class FakeTaskMailReplySender(
    private val result: TaskMailReplyResult = TaskMailReplyResult.success(),
) : TaskMailReplySender {
    val requests = mutableListOf<TaskMailReplyRequest>()

    override suspend fun send(request: TaskMailReplyRequest): TaskMailReplyResult {
        requests += request
        return result
    }
}

private class FakeTaskMailReplyAttachmentResolver(
    private val attachmentsToReturn: List<TaskReplyAttachment> = emptyList(),
) : TaskMailReplyAttachmentResolver {
    override suspend fun resolveSelectedAttachments(uriStrings: List<String>): List<TaskReplyAttachment> {
        return if (attachmentsToReturn.isNotEmpty()) {
            attachmentsToReturn
        } else {
            uriStrings.map { uriString ->
                sampleReplyAttachment(
                    id = uriString,
                    uriString = uriString,
                    displayName = uriString.substringAfterLast('/').ifBlank { "attachment" },
                )
            }
        }
    }

    override suspend fun buildOutgoingAttachments(
        attachments: List<TaskReplyAttachment>,
    ): Result<List<com.fsck.k9.message.Attachment>> = error("Not used by this test")
}

private class FakeTaskMailTimelineAttachmentHandler(
    private val openIntentResult: Result<android.content.Intent> = Result.success(
        android.content.Intent("taskmail.test.OPEN"),
    ),
    private val saveResult: Result<Unit> = Result.success(Unit),
) : TaskMailTimelineAttachmentHandler {
    val openedAttachments = mutableListOf<TaskMessageAttachment>()
    val savedAttachments = mutableListOf<Pair<TaskMessageAttachment, String>>()

    override suspend fun createOpenIntent(attachment: TaskMessageAttachment): Result<android.content.Intent> {
        openedAttachments += attachment
        return openIntentResult
    }

    override suspend fun saveAttachmentTo(
        attachment: TaskMessageAttachment,
        destinationUriString: String,
    ): Result<Unit> {
        savedAttachments += attachment to destinationUriString
        return saveResult
    }
}

private fun sampleReplyAttachment(
    id: String = "content://taskmail/final-report",
    uriString: String = id,
    displayName: String = "final_report.md",
    contentType: String? = "text/markdown",
): TaskReplyAttachment {
    return TaskReplyAttachment(
        id = id,
        uriString = uriString,
        displayName = displayName,
        contentType = contentType,
        sizeBytes = 4_096L,
        isImage = contentType?.startsWith("image/") == true,
    )
}

private fun detailWithTimelineAttachment(): TaskSessionDetail {
    return TaskMailPreviewData.sessionDetails.first().copy(
        timeline = TaskMailPreviewData.sessionDetails.first().timeline.map { item ->
            item.copy(
                attachments = listOf(
                    TaskMessageAttachment(
                        id = "content://taskmail/chart-preview",
                        displayName = "result_chart.png",
                        contentType = "image/png",
                        sizeBytes = 2_048L,
                        isInline = true,
                        isImage = true,
                        contentId = "chart-preview",
                        internalUriString = "content://taskmail/chart-preview",
                        accountUuid = "account_uuid",
                        folderId = 1L,
                        messageServerId = "msg_001",
                        partId = 42L,
                        isContentAvailable = false,
                    ),
                ),
            )
        },
    )
}

private fun multiQuestionDetail(): TaskSessionDetail {
    val pendingQuestions = listOf(
        TaskQuestionCapsule(
            questionSetId = "phase2_clarifications",
            questionId = "phase2_entry_position",
            questionType = "single_choice",
            questionText = "Where should the Tasks drawer entry be placed?",
            choices = listOf("top", "below", "section"),
            choiceLabels = mapOf(
                "top" to "Account list top",
                "below" to "Account list bottom",
                "section" to "Standalone section",
            ),
        ),
        TaskQuestionCapsule(
            questionSetId = "phase2_clarifications",
            questionId = "phase2_icon_strings",
            questionType = "single_choice",
            questionText = "Who provides icon and string resources?",
            choices = listOf("provide", "reuse", "placeholder"),
            choiceLabels = mapOf(
                "provide" to "You provide",
                "reuse" to "Reuse existing",
                "placeholder" to "Temporary placeholder",
            ),
        ),
    )

    return TaskMailPreviewData.sessionDetails.first().copy(
        question = pendingQuestions.last(),
        pendingQuestions = pendingQuestions,
    )
}

private fun singleQuestionDetailWithChoiceLabels(): TaskSessionDetail {
    val question = TaskQuestionCapsule(
        questionId = "question_001",
        questionText = "Should I proceed with the cleanup?",
        choices = listOf("approve", "decline"),
        choiceLabels = mapOf(
            "approve" to "Ship it",
            "decline" to "Not yet",
        ),
    )

    return TaskMailPreviewData.sessionDetails.first().copy(
        question = question,
        pendingQuestions = listOf(question),
    )
}

private fun pausedDetail(): TaskSessionDetail {
    return TaskMailPreviewData.sessionDetails.first().copy(
        status = TaskMailSessionStatus.Paused,
        pausedFromStatus = TaskMailSessionStatus.Done,
        question = null,
        pendingQuestions = emptyList(),
    )
}

private fun pausedSingleQuestionDetail(): TaskSessionDetail {
    return singleQuestionDetailWithChoiceLabels().copy(
        status = TaskMailSessionStatus.Paused,
        pausedFromStatus = TaskMailSessionStatus.WaitingUser,
    )
}

@OptIn(ExperimentalCoroutinesApi::class)
private fun createBlockingDetailSyncTaskMailCache(
    syncGate: CompletableDeferred<Unit>,
): SyncTaskMailCache {
    return SyncTaskMailCache(
        unifiedMessageRepository = DetailTestUnifiedMessageRepository(),
        messageSyncStateRepository = DetailTestMessageSyncStateRepository(
            MessageSyncState(
                source = DEFAULT_MESSAGE_SYNC_SOURCE,
                scopeKey = DEFAULT_MESSAGE_SYNC_SCOPE_KEY,
                lastCursor = "cursor-1",
                lastSyncAt = 123L,
            ),
        ),
        syncCoordinator = BlockingDetailMessageSyncCoordinator(syncGate),
        taskMailMessageJsonCodec = TaskMailMessageJsonCodec(),
        sessionProjector = TaskMailSessionProjector(),
        taskSessionDetailRepository = DetailTestTaskSessionDetailRepository(),
        ioDispatcher = UnconfinedTestDispatcher(),
    )
}

private class BlockingDetailMessageSyncCoordinator(
    private val syncGate: CompletableDeferred<Unit>,
) : MessageSyncCoordinator {
    override suspend fun sync(request: MessageSyncRequest): Result<Unit> {
        syncGate.await()
        return Result.success(Unit)
    }
}

private class DetailTestUnifiedMessageRepository : UnifiedMessageRepository {
    private val messages = MutableStateFlow<List<UnifiedMessage>>(emptyList())

    override fun observeMessages(taskId: String): Flow<List<UnifiedMessage>> {
        return messages.map { cachedMessages ->
            cachedMessages.filter { message -> message.taskId == taskId }
        }
    }

    override suspend fun getAllMessages(): List<UnifiedMessage> = messages.value

    override suspend fun upsertMessages(messages: List<UnifiedMessage>) {
        this.messages.value = messages
    }

    override suspend fun findBySourceMessageId(
        source: String,
        sourceMessageId: String,
    ): UnifiedMessage? {
        return messages.value.firstOrNull { message ->
            message.source == source && message.sourceMessageId == sourceMessageId
        }
    }
}

private class DetailTestMessageSyncStateRepository(
    private var state: MessageSyncState? = null,
) : MessageSyncStateRepository {
    override suspend fun getState(
        source: String,
        scopeKey: String,
    ): MessageSyncState? {
        return state?.takeIf { currentState ->
            currentState.source == source && currentState.scopeKey == scopeKey
        }
    }

    override suspend fun upsertState(state: MessageSyncState) {
        this.state = state
    }
}

private class DetailTestTaskSessionDetailRepository : TaskSessionDetailRepository {
    override suspend fun getTaskSessionDetail(key: TaskSessionKey): TaskSessionDetail? = null

    override suspend fun getTaskSessionDetails(): List<TaskSessionDetail> = emptyList()

    override suspend fun replaceAllSessionDetails(details: List<TaskSessionDetail>) = Unit

    override suspend fun upsertSessionDetails(details: List<TaskSessionDetail>) = Unit

    override suspend fun removeSessionDetails(keys: List<TaskSessionKey>) = Unit
}
