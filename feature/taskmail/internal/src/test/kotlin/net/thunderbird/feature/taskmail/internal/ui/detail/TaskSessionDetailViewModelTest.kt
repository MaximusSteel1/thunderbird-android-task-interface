package net.thunderbird.feature.taskmail.internal.ui.detail

import app.k9mail.core.ui.compose.testing.mvi.MviContext
import app.k9mail.core.ui.compose.testing.mvi.MviTurbines
import app.k9mail.core.ui.compose.testing.mvi.advanceUntilIdle
import app.k9mail.core.ui.compose.testing.mvi.runMviTest
import app.k9mail.core.ui.compose.testing.mvi.turbinesWithInitialStateCheck
import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import net.thunderbird.core.logging.LogMessage
import net.thunderbird.core.logging.LogTag
import net.thunderbird.core.logging.Logger
import net.thunderbird.core.testing.coroutines.MainDispatcherHelper
import net.thunderbird.feature.taskmail.internal.data.TaskMailForegroundRefreshTickerFactory
import net.thunderbird.feature.taskmail.internal.data.TaskMailReplyAttachmentResolver
import net.thunderbird.feature.taskmail.internal.data.TaskMailSessionProjector
import net.thunderbird.feature.taskmail.internal.data.TaskMailStoreChangeObserver
import net.thunderbird.feature.taskmail.internal.data.TaskMailSyncRequester
import net.thunderbird.feature.taskmail.internal.data.TaskMailTimelineAttachmentHandler
import net.thunderbird.feature.taskmail.internal.data.cache.TaskMailMessageJsonCodec
import net.thunderbird.feature.taskmail.internal.data.controlplane.protocol.ControlPlaneEvent
import net.thunderbird.feature.taskmail.internal.data.controlplane.protocol.ControlPlaneArtifact
import net.thunderbird.feature.taskmail.internal.data.controlplane.protocol.ControlPlaneArtifactManifest
import net.thunderbird.feature.taskmail.internal.data.controlplane.protocol.ControlPlaneDownloadRef
import net.thunderbird.feature.taskmail.internal.data.controlplane.protocol.ControlPlaneExecutionPolicy
import net.thunderbird.feature.taskmail.internal.data.controlplane.protocol.ControlPlaneResult
import net.thunderbird.feature.taskmail.internal.data.controlplane.protocol.ControlPlaneStructuredPayload
import net.thunderbird.feature.taskmail.internal.data.facade.SessionSnapshotRequestException
import net.thunderbird.feature.taskmail.internal.domain.model.MessageSyncState
import net.thunderbird.feature.taskmail.internal.domain.model.RelayBootstrapStatus
import net.thunderbird.feature.taskmail.internal.domain.model.TaskAttachmentActionTarget
import net.thunderbird.feature.taskmail.internal.domain.model.TaskMailBackend
import net.thunderbird.feature.taskmail.internal.domain.model.TaskMailDirectOutcome
import net.thunderbird.feature.taskmail.internal.domain.model.TaskMailDirectSendEvidence
import net.thunderbird.feature.taskmail.internal.domain.model.TaskMailDirectSwitchGate
import net.thunderbird.feature.taskmail.internal.domain.model.TaskMailSessionActionSendRecord
import net.thunderbird.feature.taskmail.internal.domain.model.TaskMailSessionLifecycle
import net.thunderbird.feature.taskmail.internal.domain.model.TaskMailSessionStatus
import net.thunderbird.feature.taskmail.internal.domain.model.TaskMessageAttachment
import net.thunderbird.feature.taskmail.internal.domain.model.TaskMessageBody
import net.thunderbird.feature.taskmail.internal.domain.model.TaskReplyAttachment
import net.thunderbird.feature.taskmail.internal.domain.model.TaskSessionControlPlaneSnapshot
import net.thunderbird.feature.taskmail.internal.domain.model.TaskSessionDetail
import net.thunderbird.feature.taskmail.internal.domain.model.TaskSessionHistorySnapshotAttachment
import net.thunderbird.feature.taskmail.internal.domain.model.TaskSessionHistorySnapshot
import net.thunderbird.feature.taskmail.internal.domain.model.TaskSessionHistorySnapshotHeader
import net.thunderbird.feature.taskmail.internal.domain.model.TaskSessionHistorySnapshotLocator
import net.thunderbird.feature.taskmail.internal.domain.model.TaskSessionHistorySnapshotRound
import net.thunderbird.feature.taskmail.internal.domain.model.TaskSessionHistorySnapshotTimelineItem
import net.thunderbird.feature.taskmail.internal.domain.model.TaskSessionKey
import net.thunderbird.feature.taskmail.internal.domain.model.TaskSessionLatestActionSnapshot
import net.thunderbird.feature.taskmail.internal.domain.model.TaskSessionPendingSubmission
import net.thunderbird.feature.taskmail.internal.domain.model.TaskSessionLiveProcess
import net.thunderbird.feature.taskmail.internal.domain.model.TaskSessionProcessItem
import net.thunderbird.feature.taskmail.internal.domain.model.TaskSessionProcessItemKind
import net.thunderbird.feature.taskmail.internal.domain.model.TaskSessionProjectionDataSource
import net.thunderbird.feature.taskmail.internal.domain.model.TaskSessionProjectionSubscriptionStatus
import net.thunderbird.feature.taskmail.internal.domain.model.TaskSessionProjectionSyncState
import net.thunderbird.feature.taskmail.internal.domain.model.TaskTimelineDirection
import net.thunderbird.feature.taskmail.internal.domain.model.TaskTimelineItem
import net.thunderbird.feature.taskmail.internal.domain.model.UnifiedMessage
import net.thunderbird.feature.taskmail.internal.domain.model.buildRelayArtifactActionTarget
import net.thunderbird.feature.taskmail.internal.domain.newtask.TaskMailNewTaskPermission
import net.thunderbird.feature.taskmail.internal.domain.parser.TaskQuestionCapsule
import net.thunderbird.feature.taskmail.internal.domain.reply.TaskMailReplyRequest
import net.thunderbird.feature.taskmail.internal.domain.reply.TaskMailReplyResult
import net.thunderbird.feature.taskmail.internal.domain.reply.TaskMailReplySender
import net.thunderbird.feature.taskmail.internal.domain.repository.MessageSyncStateRepository
import net.thunderbird.feature.taskmail.internal.domain.repository.TaskMailSessionActionSendRecordRepository
import net.thunderbird.feature.taskmail.internal.domain.repository.TaskSessionDetailRepository
import net.thunderbird.feature.taskmail.internal.domain.repository.TaskSessionHistorySnapshotRepository
import net.thunderbird.feature.taskmail.internal.domain.repository.UnifiedMessageRepository
import net.thunderbird.feature.taskmail.internal.domain.sessionaction.TaskMailDirectSessionActionRequest
import net.thunderbird.feature.taskmail.internal.domain.sessionaction.TaskMailDirectSessionActionResult
import net.thunderbird.feature.taskmail.internal.domain.sessionaction.TaskMailDirectSessionActionSender
import net.thunderbird.feature.taskmail.internal.domain.sessionaction.TaskMailDirectSessionActionTarget
import net.thunderbird.feature.taskmail.internal.domain.sessionaction.TaskMailDirectSessionActionType
import net.thunderbird.feature.taskmail.internal.domain.sessionaction.TaskMailSessionActionAckStatus
import net.thunderbird.feature.taskmail.internal.domain.sessionaction.TaskMailSessionQuestionAnswer
import net.thunderbird.feature.taskmail.internal.domain.sessionaction.TaskMailSessionActionTargetIdentity
import net.thunderbird.feature.taskmail.internal.domain.usecase.GetLatestTaskMailSessionActionSendRecord
import net.thunderbird.feature.taskmail.internal.domain.usecase.GetTaskSessionDetail
import net.thunderbird.feature.taskmail.internal.domain.usecase.GetTaskSessionHistorySnapshot
import net.thunderbird.feature.taskmail.internal.domain.usecase.ObserveTaskMailSessionUpdates
import net.thunderbird.feature.taskmail.internal.domain.usecase.ObserveTaskMailStoreChanges
import net.thunderbird.feature.taskmail.internal.domain.usecase.RecordTaskMailSessionActionSendRecord
import net.thunderbird.feature.taskmail.internal.domain.usecase.RefreshTaskMail
import net.thunderbird.feature.taskmail.internal.domain.usecase.SendTaskMailDirectSessionAction
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
    fun `load detail should prefer cached vps detail without background cache sync`() = runMviTest {
        val cacheSyncGate = CompletableDeferred<Unit>()

        with(
            TaskSessionDetailViewModelRobot(
                mviContext = this,
                repository = FakeTaskSessionDetailRepository(detail = vpsProjectedDetail()),
                syncTaskMailCache = createBlockingDetailSyncTaskMailCache(cacheSyncGate),
            ),
        ) {
            start()
            loadDetail()
            assertLoadedDetail()
            assertThat(viewModelState().isRefreshing).isEqualTo(false)
            assertThat(viewModelState().detail?.lastSummary).isEqualTo("VPS cached summary")
            ensureThatAllEventsAreConsumed()
        }
    }

    @Test
    fun `load detail should mark legacy mail-only session actions unavailable`() = runMviTest {
        with(
            TaskSessionDetailViewModelRobot(
                this,
                FakeTaskSessionDetailRepository(detail = legacyMailOnlyDetail()),
            ),
        ) {
            start()
            loadDetail()
            assertThat(viewModelState().detail?.canReply).isEqualTo(false)
            assertThat(viewModelState().detail?.canQueryStatus).isEqualTo(false)
            assertThat(viewModelState().detail?.replyUnavailableReason).isEqualTo(
                "Session actions are unavailable until this session is backed by VPS session-action data.",
            )
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
    fun `load detail should preserve workspace id in requested key`() = runMviTest {
        val repository = FakeTaskSessionDetailRepository()

        with(TaskSessionDetailViewModelRobot(this, repository)) {
            start()
            loadDetail(workspaceId = "workspace_direct_001")
            assertThat(repository.requestedKey).isEqualTo(
                TaskSessionKey(
                    workspaceId = "workspace_direct_001",
                    sessionId = "session_001",
                ),
            )
            ensureThatAllEventsAreConsumed()
        }
    }

    @Test
    fun `load detail should restore latest direct session-action record for current session target`() = runMviTest {
        val latestRecord = TaskMailSessionActionSendRecord(
            recordedAt = 200L,
            actionType = TaskMailDirectSessionActionRequest.Status(
                target = sampleDirectTarget(),
            ).actionType,
            target = sampleDirectTarget(),
            evidence = TaskMailDirectSendEvidence(
                bootstrapStatus = RelayBootstrapStatus.HelloAck,
                outcome = TaskMailDirectOutcome.DirectAccepted,
                switchGate = TaskMailDirectSwitchGate.KeepDirectDefault,
                requestId = "req_rehydrated",
                receiptId = "receipt-rehydrated",
            ),
        )

        with(
            TaskSessionDetailViewModelRobot(
                this,
                FakeTaskSessionDetailRepository(),
                sessionActionSendRecordRepository = FakeTaskMailSessionActionSendRecordRepository(latestRecord),
            ),
        ) {
            start()
            loadDetail()
            assertThat(viewModelState().latestDirectSessionActionRecord).isEqualTo(latestRecord)
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
    fun `history clicked should expose history sheet state`() = runMviTest {
        with(TaskSessionDetailViewModelRobot(this, FakeTaskSessionDetailRepository())) {
            start()
            openHistory()
            assertThat(viewModelState().isHistoryVisible).isEqualTo(true)
            ensureThatAllEventsAreConsumed()
        }
    }

    @Test
    fun `history dismissed should hide history sheet state`() = runMviTest {
        with(TaskSessionDetailViewModelRobot(this, FakeTaskSessionDetailRepository())) {
            start()
            openHistory()
            dismissHistory()
            assertThat(viewModelState().isHistoryVisible).isEqualTo(false)
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
    fun `session updates snapshot should overlay detail status and summary`() = runMviTest {
        val sessionUpdatesObserver = FakeObserveTaskMailSessionUpdates()

        with(
            TaskSessionDetailViewModelRobot(
                this,
                FakeTaskSessionDetailRepository(detail = vpsProjectedDetail()),
                sessionUpdatesObserver = sessionUpdatesObserver,
            ),
        ) {
            start()
            loadDetail()
            emitSessionUpdateSnapshot(
                sampleSessionUpdateSnapshot(
                    status = TaskMailSessionStatus.Done,
                    lastSummary = "Direct terminal summary",
                ),
            )
            assertThat(viewModelState().detail?.status).isEqualTo(TaskMailSessionStatus.Done.name)
            assertThat(viewModelState().detail?.lastSummary).isEqualTo("Direct terminal summary")
            ensureThatAllEventsAreConsumed()
        }
    }

    @Test
    fun `session updates snapshot should persist vps native detail into repository`() = runMviTest {
        val repository = FakeTaskSessionDetailRepository(detail = vpsProjectedDetail())
        val sessionUpdatesObserver = FakeObserveTaskMailSessionUpdates()

        with(
            TaskSessionDetailViewModelRobot(
                this,
                repository,
                sessionUpdatesObserver = sessionUpdatesObserver,
            ),
        ) {
            start()
            loadDetail()
            emitSessionUpdateSnapshot(
                sampleSessionUpdateSnapshot(
                    status = TaskMailSessionStatus.Done,
                    lastSummary = "Direct terminal summary",
                ),
            )
            assertThat(repository.detail?.status).isEqualTo(TaskMailSessionStatus.Done)
            assertThat(repository.detail?.lastSummary).isEqualTo("Direct terminal summary")
            assertThat(repository.detail?.projectionSyncState?.dataSource).isEqualTo(
                TaskSessionProjectionDataSource.VpsNative,
            )
            assertThat(repository.detail?.projectionSyncState?.subscriptionStatus).isEqualTo(
                TaskSessionProjectionSubscriptionStatus.Active,
            )
            ensureThatAllEventsAreConsumed()
        }
    }

    @Test
    fun `session updates snapshot should surface live output into active run ui state`() = runMviTest {
        val sessionUpdatesObserver = FakeObserveTaskMailSessionUpdates()

        with(
            TaskSessionDetailViewModelRobot(
                this,
                FakeTaskSessionDetailRepository(
                    detail = currentInputLedDetail().copy(
                        timeline = listOf(
                            TaskTimelineItem(
                                id = "timeline_assistant_previous",
                                timestamp = 100L,
                                direction = TaskTimelineDirection.System,
                                summary = "Older assistant output",
                                body = TaskMessageBody(
                                    plainTextFallback = "Older assistant output",
                                ),
                            ),
                        ),
                    ),
                ),
                sessionUpdatesObserver = sessionUpdatesObserver,
            ),
        ) {
            start()
            loadDetail()
            emitSessionUpdateSnapshot(
                sampleSessionUpdateSnapshot(
                    status = TaskMailSessionStatus.Running,
                    liveProcess = TaskSessionLiveProcess(
                        status = "streaming",
                        updatedAt = "2026-03-29T18:31:45Z",
                        items = persistentListOf(
                            TaskSessionProcessItem(
                                itemId = "live_assistant_001",
                                kind = TaskSessionProcessItemKind.Assistant,
                                createdAt = "2026-03-29T18:31:30Z",
                                updatedAt = "2026-03-29T18:31:45Z",
                                status = "streaming",
                                text = "Streaming assistant output.",
                            ),
                        ),
                    ),
                    lastProgressAt = "2026-03-29T18:31:45Z",
                ),
            )

            assertThat(viewModelState().detail?.pageMode).isEqualTo(TaskSessionPageMode.ActiveRun)
            assertThat(viewModelState().detail?.processSection?.visibleItems?.single()?.plainText)
                .isEqualTo("Streaming assistant output.")
            assertThat(viewModelState().detail?.lastProgressAt).isEqualTo("2026-03-29T18:31:45Z")
            ensureThatAllEventsAreConsumed()
        }
    }

    @Test
    fun `session updates snapshot should continue after enriching provisional key with canonical thread id`() = runMviTest {
        val repository = FakeTaskSessionDetailRepository(
            detail = vpsProjectedDetail().copy(
                key = TaskSessionKey(
                    workspaceId = "workspace_001",
                    sessionId = "session_001",
                    threadId = null,
                ),
            ),
        )
        val sessionUpdatesObserver = FakeObserveTaskMailSessionUpdates()

        with(
            TaskSessionDetailViewModelRobot(
                this,
                repository,
                sessionUpdatesObserver = sessionUpdatesObserver,
            ),
        ) {
            start()
            loadDetail()
            emitSessionUpdateSnapshot(
                sampleSessionUpdateSnapshot(
                    status = TaskMailSessionStatus.Running,
                    lastSummary = "Direct running summary",
                ),
            )
            emitSessionUpdateSnapshot(
                sampleSessionUpdateSnapshot(
                    status = TaskMailSessionStatus.Done,
                    lastSummary = "Direct terminal summary",
                ),
            )

            assertThat(viewModelState().detail?.status).isEqualTo(TaskMailSessionStatus.Done.name)
            assertThat(viewModelState().detail?.lastSummary).isEqualTo("Direct terminal summary")
            assertThat(repository.detail?.key?.threadId).isEqualTo("thread_001")
            assertThat(repository.detail?.projectionSyncState?.subscriptionStatus).isEqualTo(
                TaskSessionProjectionSubscriptionStatus.Active,
            )
            ensureThatAllEventsAreConsumed()
        }
    }

    @Test
    fun `session updates snapshot should update quick answers while preserving reply context`() = runMviTest {
        val sessionUpdatesObserver = FakeObserveTaskMailSessionUpdates()

        with(
            TaskSessionDetailViewModelRobot(
                this,
                FakeTaskSessionDetailRepository(detail = vpsProjectedDetail()),
                sessionUpdatesObserver = sessionUpdatesObserver,
            ),
        ) {
            start()
            loadDetail()
            emitSessionUpdateSnapshot(
                sampleSessionUpdateSnapshot(
                    status = TaskMailSessionStatus.WaitingUser,
                    pendingQuestions = listOf(
                        TaskQuestionCapsule(
                            questionSetId = "question_set_001",
                            questionId = "question_001",
                            questionType = "single_choice",
                            questionText = "Should I proceed?",
                            choices = listOf("approve", "decline"),
                            choiceLabels = mapOf(
                                "approve" to "Ship it",
                                "decline" to "Not yet",
                            ),
                        ),
                    ),
                ),
            )
            assertThat(viewModelState().detail?.quickAnswerChoices?.map(TaskPendingQuestionChoiceUi::label)).isEqualTo(
                listOf("Ship it", "Not yet"),
            )
            assertThat(viewModelState().detail?.replyContext).isNotNull()
            ensureThatAllEventsAreConsumed()
        }
    }

    @Test
    fun `session updates snapshot should append terminal timeline item`() = runMviTest {
        val sessionUpdatesObserver = FakeObserveTaskMailSessionUpdates()

        with(
            TaskSessionDetailViewModelRobot(
                this,
                FakeTaskSessionDetailRepository(detail = vpsProjectedDetail()),
                sessionUpdatesObserver = sessionUpdatesObserver,
            ),
        ) {
            start()
            loadDetail()
            emitSessionUpdateSnapshot(
                sampleSessionUpdateSnapshot(
                    status = TaskMailSessionStatus.Running,
                    timelineItems = listOf(
                        snapshotTimelineItem(
                            itemId = "tl_reply_001",
                            businessEventKey = "reply/2026-03-21T22:37:03",
                            text = "Direct reply preview",
                        ),
                    ),
                ),
            )

            assertThat(viewModelState().detail?.timeline?.first()?.id).isEqualTo("snapshot:tl_reply_001")
            assertThat(viewModelState().detail?.timeline?.first()?.plainText).isEqualTo("Direct reply preview")
            ensureThatAllEventsAreConsumed()
        }
    }

    @Test
    fun `session updates snapshot should prefer latest round result text for detail result body`() = runMviTest {
        val sessionUpdatesObserver = FakeObserveTaskMailSessionUpdates()

        with(
            TaskSessionDetailViewModelRobot(
                this,
                FakeTaskSessionDetailRepository(detail = vpsProjectedDetail()),
                sessionUpdatesObserver = sessionUpdatesObserver,
            ),
        ) {
            start()
            loadDetail()
            emitSessionUpdateSnapshot(
                sampleSessionUpdateSnapshot(
                    status = TaskMailSessionStatus.Done,
                    lastSummary = "Short terminal summary",
                    timelineItems = listOf(
                        snapshotTimelineItem(
                            itemId = "tl_terminal_done_001",
                            businessEventKey = "terminal/done/2026-03-29T18:31:00",
                            text = "Short terminal summary",
                            status = "done",
                        ),
                    ),
                    historyRounds = listOf(
                        TaskSessionHistorySnapshotRound(
                            roundId = "round_003",
                            roundNumber = 3,
                            createdAt = "2026-03-29T18:31:00Z",
                            status = "done",
                            speakerLabel = "Codex",
                            resultText = "Full preserved output\n\nChanged files:\n- TaskNewTaskViewModel.kt",
                        ),
                    ),
                ),
            )

            assertThat(viewModelState().detail?.resultBody?.plainText).isEqualTo(
                "Full preserved output\n\nChanged files:\n- TaskNewTaskViewModel.kt",
            )
            ensureThatAllEventsAreConsumed()
        }
    }

    @Test
    fun `matching mail business event should suppress snapshot timeline item after refresh`() = runMviTest {
        val repository = FakeTaskSessionDetailRepository(detail = vpsProjectedDetail())
        val sessionUpdatesObserver = FakeObserveTaskMailSessionUpdates()

        with(
            TaskSessionDetailViewModelRobot(
                this,
                repository,
                sessionUpdatesObserver = sessionUpdatesObserver,
            ),
        ) {
            start()
            loadDetail()
            emitSessionUpdateSnapshot(
                sampleSessionUpdateSnapshot(
                    status = TaskMailSessionStatus.Running,
                    timelineItems = listOf(
                        snapshotTimelineItem(
                            itemId = "tl_reply_001",
                            businessEventKey = "reply/2026-03-21T22:37:03",
                            text = "Direct reply preview",
                        ),
                    ),
                ),
            )
            assertThat(viewModelState().detail?.timeline?.map(TaskTimelineItemUi::id)?.first()).isEqualTo(
                "snapshot:tl_reply_001",
            )

            val originalTimelineIds = repository.detail?.timeline.orEmpty()
                .asReversed()
                .map(TaskTimelineItem::id)
            repository.detail = repository.detail?.copy(
                timeline = repository.detail?.timeline.orEmpty() + listOf(
                    TaskTimelineItem(
                        id = "mail:reply_001",
                        timestamp = 1_200L,
                        direction = TaskTimelineDirection.System,
                        summary = "Durable reply receipt",
                        body = TaskMessageBody(
                            plainTextFallback = "Durable reply receipt",
                        ),
                        businessEventKeys = listOf("reply/2026-03-21T22:37:03"),
                    ),
                ),
            )
            refresh()

            assertThat(viewModelState().detail?.timeline?.map(TaskTimelineItemUi::id)).isEqualTo(
                listOf("mail:reply_001") + originalTimelineIds,
            )
            ensureThatAllEventsAreConsumed()
        }
    }

    @Test
    fun `load detail should log repository render path`() = runMviTest {
        val logger = DetailViewModelFakeLogger()

        with(
            TaskSessionDetailViewModelRobot(
                this,
                FakeTaskSessionDetailRepository(),
                logger = logger,
            ),
        ) {
            start()
            loadDetail()
            val debugLog = logger.debugMessages.joinToString("\n")
            assertThat(debugLog).contains("Rendered repository detail status=")
            assertThat(debugLog).contains("mailTimelineCount=")
            ensureThatAllEventsAreConsumed()
        }
    }

    @Test
    fun `session updates snapshot should log facade snapshot application`() = runMviTest {
        val logger = DetailViewModelFakeLogger()
        val sessionUpdatesObserver = FakeObserveTaskMailSessionUpdates()

        with(
            TaskSessionDetailViewModelRobot(
                this,
                FakeTaskSessionDetailRepository(detail = vpsProjectedDetail()),
                sessionUpdatesObserver = sessionUpdatesObserver,
                logger = logger,
            ),
        ) {
            start()
            loadDetail()
            emitSessionUpdateSnapshot(
                sampleSessionUpdateSnapshot(
                    status = TaskMailSessionStatus.Done,
                    lastSummary = "Direct terminal summary",
                ),
            )

            assertThat(logger.debugMessages.joinToString("\n")).contains(
                "Applying Android session-updates snapshot",
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
    fun `send reply should use attachment continuation when attachments are selected`() = runMviTest {
        val repository = FakeTaskSessionDetailRepository(detail = directReplyCapableDetail())
        val replyAttachment = sampleReplyAttachment()
        val attachmentResolver = FakeTaskMailReplyAttachmentResolver(
            attachmentsToReturn = listOf(replyAttachment),
        )
        val directSender = FakeTaskMailDirectSessionActionSender()

        with(
            TaskSessionDetailViewModelRobot(
                this,
                repository,
                attachmentResolver = attachmentResolver,
                directSessionActionSender = directSender,
            ),
        ) {
            start()
            loadDetail()
            selectAttachments(listOf(replyAttachment.uriString))
            changeDraft("ship it with the report")
            sendReply()
            assertThat(directSender.requests.single()).isEqualTo(
                TaskMailDirectSessionActionRequest.AttachmentContinuation(
                    target = sampleDirectTarget(),
                    replyText = "ship it with the report",
                    attachments = listOf(replyAttachment),
                ),
            )
            assertThat(viewModelState().replyAttachments).isEqualTo(persistentListOf())
            assertThat(viewModelState().sendError).isNull()
            assertShowMessageEffect(
                "[VPS] Reply submitted. Detail will keep following VPS-native session updates.",
            )
            ensureThatAllEventsAreConsumed()
        }
    }

    @Test
    fun `open latest result artifact should delegate to timeline attachment handler`() = runMviTest {
        val timelineAttachmentHandler = FakeTaskMailTimelineAttachmentHandler()

        with(
            TaskSessionDetailViewModelRobot(
                this,
                FakeTaskSessionDetailRepository(detail = detailWithControlPlaneArtifact()),
                timelineAttachmentHandler = timelineAttachmentHandler,
            ),
        ) {
            start()
            loadDetail()
            openTimelineAttachment("artifact-summary")
            assertOpenAttachmentEffect()
            assertThat(timelineAttachmentHandler.openedAttachments.single().attachmentId).isEqualTo("artifact-summary")
            assertThat(
                timelineAttachmentHandler.openedAttachments.single().relayArtifact?.fileId,
            ).isEqualTo("file_summary_01")
            ensureThatAllEventsAreConsumed()
        }
    }

    @Test
    fun `open server history snapshot attachment should delegate to timeline attachment handler`() = runMviTest {
        val timelineAttachmentHandler = FakeTaskMailTimelineAttachmentHandler()
        val historySnapshotRepository = FakeTaskSessionHistorySnapshotRepository(
            result = Result.success(historySnapshotWithRelayAttachment()),
        )

        with(
            TaskSessionDetailViewModelRobot(
                this,
                FakeTaskSessionDetailRepository(detail = legacyMailOnlyDetail()),
                timelineAttachmentHandler = timelineAttachmentHandler,
                historySnapshotRepository = historySnapshotRepository,
            ),
        ) {
            start()
            loadDetail(preferServerHistoryRounds = true)
            openTimelineAttachment("hist_result_task_001_1")
            assertOpenAttachmentEffect()
            assertThat(timelineAttachmentHandler.openedAttachments.single().attachmentId)
                .isEqualTo("hist_result_task_001_1")
            assertThat(timelineAttachmentHandler.openedAttachments.single().relayArtifact?.fileId)
                .isEqualTo("file_history_001")
            ensureThatAllEventsAreConsumed()
        }
    }

    @Test
    fun `send reply should be blocked for legacy mail-only session`() = runMviTest {
        val repository = FakeTaskSessionDetailRepository(detail = legacyMailOnlyDetail())
        val directSender = FakeTaskMailDirectSessionActionSender()

        with(TaskSessionDetailViewModelRobot(this, repository, directSessionActionSender = directSender)) {
            start()
            loadDetail()
            changeDraft("ship it")
            sendReply()
            assertThat(directSender.requests.size).isEqualTo(0)
            assertThat(viewModelState().sendError).isEqualTo(
                "Session actions are unavailable until this session is backed by VPS session-action data.",
            )
            ensureThatAllEventsAreConsumed()
        }
    }

    @Test
    fun `send reply should persist and render control-plane result summary`() = runMviTest {
        val repository = FakeTaskSessionDetailRepository(detail = directReplyCapableDetail())
        val directSender = FakeTaskMailDirectSessionActionSender(
            result = TaskMailDirectSessionActionResult.Accepted(
                actionType = TaskMailDirectSessionActionType.Reply,
                requestId = "req_001",
                receiptId = "cmd_001",
                controlPlaneSnapshot = TaskSessionControlPlaneSnapshot(
                    events = listOf(
                        ControlPlaneEvent(
                            eventId = "evt_001",
                            commandId = "cmd_001",
                            workspaceId = "workspace_001",
                            sessionId = "session_001",
                            runId = "run_001",
                            eventType = "running",
                            payload = buildJsonObject {
                                put("summary", "Applying persisted control-plane snapshot.")
                            },
                        ),
                    ),
                    result = ControlPlaneResult(
                        resultId = "res_001",
                        commandId = "cmd_001",
                        workspaceId = "workspace_001",
                        sessionId = "session_001",
                        runId = "run_001",
                        finalStatus = "done",
                        summary = "Persisted result summary.",
                        effectiveExecution = ControlPlaneExecutionPolicy(
                            backend = "codex",
                            profile = "strong",
                            permission = "highest",
                            backendTransport = "sdk",
                            resolvedModel = "gpt-5-codex",
                        ),
                        structuredPayload = ControlPlaneStructuredPayload(kind = "task_outcome"),
                    ),
                ),
            ),
        )

        with(
            TaskSessionDetailViewModelRobot(
                this,
                repository,
                directSessionActionSender = directSender,
            ),
        ) {
            start()
            loadDetail()
            changeDraft("ship it")
            sendReply()

            assertThat(viewModelState().detail?.resultSummary?.effectiveExecutionSummary)
                .isEqualTo("backend=codex · profile=strong · permission=highest · transport=sdk · model=gpt-5-codex")
            assertThat(repository.detail?.controlPlaneSnapshot?.result?.summary).isEqualTo("Persisted result summary.")
            assertShowMessageEffect("[VPS] Reply submitted. Detail will keep following VPS-native session updates.")
            ensureThatAllEventsAreConsumed()
        }
    }

    @Test
    fun `send reply should clear draft and refresh on direct success`() = runMviTest {
        val repository = FakeTaskSessionDetailRepository(detail = directReplyCapableDetail())
        val directSender = FakeTaskMailDirectSessionActionSender()
        val syncRequester = FakeTaskMailSyncRequester()

        with(
            TaskSessionDetailViewModelRobot(
                this,
                repository,
                syncRequester = syncRequester,
                directSessionActionSender = directSender,
            ),
        ) {
            start()
            loadDetail()
            changeDraft("ship it")
            sendReply()
            assertThat(
                directSender.requests.single(),
            ).isEqualTo(
                TaskMailDirectSessionActionRequest.Reply(
                    target = sampleDirectTarget(),
                    replyText = "ship it",
                ),
            )
            assertThat(viewModelState().draftText).isEqualTo("")
            assertThat(viewModelState().sendError).isEqualTo(null)
            assertThat(repository.requestedKeys.size).isEqualTo(2)
            assertThat(syncRequester.requestedAccountUuids).isEqualTo(emptyList())
            assertShowMessageEffect(
                "[VPS] Reply submitted. Detail will keep following VPS-native session updates.",
            )
            ensureThatAllEventsAreConsumed()
        }
    }

    @Test
    fun `send reply should use direct lane for plain reply when canonical workspace id is available`() = runMviTest {
        val repository = FakeTaskSessionDetailRepository(detail = directReplyCapableDetail())
        val mailSender = FakeTaskMailReplySender()
        val directSender = FakeTaskMailDirectSessionActionSender(
            result = TaskMailDirectSessionActionResult.Accepted(
                actionType = TaskMailDirectSessionActionRequest.Reply(
                    target = sampleDirectTarget(),
                    replyText = "ship it",
                ).actionType,
                requestId = "req_001",
                receiptId = "receipt-1",
                transportMessageId = "transport-1",
            ),
        )

        with(TaskSessionDetailViewModelRobot(this, repository, mailSender, directSessionActionSender = directSender)) {
            start()
            loadDetail()
            changeReplyPermission(TaskMailNewTaskPermission.Highest)
            changeDraft("ship it")
            sendReply()
            assertThat(mailSender.requests.size).isEqualTo(0)
            assertThat(directSender.requests.single()).isEqualTo(
                TaskMailDirectSessionActionRequest.Reply(
                    target = sampleDirectTarget(),
                    replyText = "ship it",
                    permission = TaskMailNewTaskPermission.Highest,
                ),
            )
            assertThat(viewModelState().draftText).isEqualTo("")
            assertThat(viewModelState().latestDirectSessionActionRecord?.actionType).isEqualTo(
                TaskMailDirectSessionActionType.Reply,
            )
            assertThat(viewModelState().latestDirectSessionActionRecord?.target).isEqualTo(sampleDirectTarget())
            assertThat(viewModelState().latestDirectSessionActionRecord?.evidence?.requestId).isEqualTo("req_001")
            assertThat(viewModelState().latestDirectSessionActionRecord?.evidence?.transportMessageId).isEqualTo(
                "transport-1",
            )
            assertThat(repository.detail?.pendingSubmissions).isEqualTo(
                listOf(
                    TaskSessionPendingSubmission(
                        commandId = "receipt-1",
                        requestId = "req_001",
                        actionType = TaskMailDirectSessionActionType.Reply,
                        submittedAt = 1_234L,
                        ackStatus = TaskMailSessionActionAckStatus.Accepted,
                        targetIdentity = TaskMailSessionActionTargetIdentity(
                            workspaceId = "workspace_001",
                            sessionId = "session_001",
                        ),
                    ),
                ),
            )
            assertShowMessageEffect(
                "[VPS] Reply submitted. Detail will keep following VPS-native session updates.",
            )
            ensureThatAllEventsAreConsumed()
        }
    }

    @Test
    fun `send reply should fail when direct lane is temporarily unavailable`() = runMviTest {
        val repository = FakeTaskSessionDetailRepository(detail = directReplyCapableDetail())
        val mailSender = FakeTaskMailReplySender()
        val directSender = FakeTaskMailDirectSessionActionSender(
            result = TaskMailDirectSessionActionResult.FallbackToMail(
                detailMessage = "direct lane is temporarily unavailable",
                requestId = "req_fallback",
                receiptId = "receipt-fallback",
                transportMessageId = "transport-fallback",
            ),
        )

        with(TaskSessionDetailViewModelRobot(this, repository, mailSender, directSessionActionSender = directSender)) {
            start()
            loadDetail()
            changeDraft("ship it")
            sendReply()
            assertThat(directSender.requests.size).isEqualTo(1)
            assertThat(mailSender.requests.size).isEqualTo(0)
            assertThat(viewModelState().draftText).isEqualTo("ship it")
            assertThat(viewModelState().latestDirectSessionActionRecord?.actionType).isEqualTo(
                TaskMailDirectSessionActionRequest.Reply(
                    target = sampleDirectTarget(),
                    replyText = "ship it",
                ).actionType,
            )
            assertThat(viewModelState().latestDirectSessionActionRecord?.evidence?.outcome).isEqualTo(
                TaskMailDirectOutcome.DirectRejected,
            )
            assertThat(viewModelState().latestDirectSessionActionRecord?.evidence?.switchGate).isEqualTo(
                TaskMailDirectSwitchGate.SwitchBlocker,
            )
            assertThat(viewModelState().latestDirectSessionActionRecord?.evidence?.requestId)
                .isEqualTo("req_fallback")
            assertThat(viewModelState().latestDirectSessionActionRecord?.evidence?.receiptId)
                .isEqualTo("receipt-fallback")
            assertThat(viewModelState().latestDirectSessionActionRecord?.evidence?.transportMessageId)
                .isEqualTo("transport-fallback")
            assertThat(viewModelState().latestDirectSessionActionRecord?.evidence?.fallbackReason)
                .isEqualTo("direct lane is temporarily unavailable")
            assertThat(viewModelState().sendError).isEqualTo("direct lane is temporarily unavailable")
            ensureThatAllEventsAreConsumed()
        }
    }

    @Test
    fun `direct detail should recover via session snapshot when observation completes without projection`() = runMviTest {
        val initialDetail = queuedVpsPendingDetail()
        val repository = FakeTaskSessionDetailRepository(detail = initialDetail)
        val sessionUpdatesObserver = FakeObserveTaskMailSessionUpdates().apply {
            completeWithoutSnapshot()
        }
        val historySnapshotRepository = FakeTaskSessionHistorySnapshotRepository(
            result = Result.success(
                TaskSessionHistorySnapshot(
                    snapshotId = "snapshot_queued_001",
                    generatedAt = "2026-03-29T17:52:52Z",
                    sessionHeader = TaskSessionHistorySnapshotHeader(
                        workspaceId = "workspace_001",
                        sessionId = "session_001",
                        threadId = "thread_001",
                        sessionName = "thread_20260329_175239_d73c4a",
                        backend = TaskMailBackend.Codex,
                        repoPath = "E:/projects/android_task_manager",
                        workdir = "feature/taskmail/internal",
                        status = TaskMailSessionStatus.Done,
                        lifecycle = TaskMailSessionLifecycle.Active,
                        lastSummary = "Hi.",
                        timelineItems = persistentListOf(
                            TaskSessionHistorySnapshotTimelineItem(
                                itemId = "tl_terminal_done_001",
                                businessEventKey = "terminal/done/2026-03-29T17:52:52",
                                itemType = "terminal_summary",
                                createdAt = "2026-03-29T17:52:52",
                                status = "done",
                                text = "Hi.",
                            ),
                        ),
                    ),
                ),
            ),
        )

        with(
            TaskSessionDetailViewModelRobot(
                this,
                repository,
                sessionUpdatesObserver = sessionUpdatesObserver,
                historySnapshotRepository = historySnapshotRepository,
            ),
        ) {
            start()
            loadDetail()

            assertThat(viewModelState().detail?.status).isEqualTo(TaskMailSessionStatus.Done.name)
            assertThat(viewModelState().detail?.lastSummary).isEqualTo("Hi.")
            assertThat(viewModelState().detail?.sessionName).isEqualTo("say hi")
            assertThat(viewModelState().detail?.timeline?.first()?.plainText).isEqualTo("Hi.")
            assertThat(repository.detail?.key?.threadId).isEqualTo("thread_001")
            assertThat(repository.detail?.projectionSyncState?.subscriptionStatus).isEqualTo(
                TaskSessionProjectionSubscriptionStatus.Idle,
            )
            assertThat(historySnapshotRepository.requestedLocators.last()).isEqualTo(
                TaskSessionHistorySnapshotLocator(
                    workspaceId = "workspace_001",
                    sessionId = "session_001",
                    threadId = null,
                    repoPath = initialDetail.repoPath,
                    workdir = initialDetail.workdir,
                ),
            )
            ensureThatAllEventsAreConsumed()
        }
    }

    @Test
    fun `direct detail should retry session snapshot recovery when fallback first sees session not found`() = runMviTest {
        val initialDetail = queuedVpsPendingDetail()
        val repository = FakeTaskSessionDetailRepository(detail = initialDetail)
        val sessionUpdatesObserver = FakeObserveTaskMailSessionUpdates().apply {
            completeWithoutSnapshot()
        }
        val historySnapshotRepository = FakeTaskSessionHistorySnapshotRepository(
            scriptedResults = mutableListOf(
                Result.failure(
                    SessionSnapshotRequestException(
                        errorCode = "session_not_found",
                        message = "could not resolve a session for the requested session_id",
                        retryable = true,
                    ),
                ),
                Result.success(
                    TaskSessionHistorySnapshot(
                        snapshotId = "snapshot_queued_retry_001",
                        generatedAt = "2026-03-29T18:30:55Z",
                        sessionHeader = TaskSessionHistorySnapshotHeader(
                            workspaceId = "workspace_001",
                            sessionId = "session_001",
                            threadId = "thread_001",
                            sessionName = "thread_20260329_182702_5ce18c",
                            backend = TaskMailBackend.Codex,
                            repoPath = "E:/projects/android_task_manager",
                            status = TaskMailSessionStatus.Done,
                            lifecycle = TaskMailSessionLifecycle.Active,
                            lastSummary = "hi",
                            timelineItems = persistentListOf(
                                TaskSessionHistorySnapshotTimelineItem(
                                    itemId = "tl_terminal_done_retry_001",
                                    businessEventKey = "terminal/done/2026-03-29T18:27:15",
                                    itemType = "terminal_summary",
                                    createdAt = "2026-03-29T18:27:15",
                                    status = "done",
                                    text = "hi",
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        )

        with(
            TaskSessionDetailViewModelRobot(
                this,
                repository,
                sessionUpdatesObserver = sessionUpdatesObserver,
                historySnapshotRepository = historySnapshotRepository,
                sessionSnapshotRecoveryMaxAttempts = 2,
                sessionSnapshotRecoveryDelayMs = 0L,
            ),
        ) {
            start()
            loadDetail()

            assertThat(viewModelState().detail?.status).isEqualTo(TaskMailSessionStatus.Done.name)
            assertThat(viewModelState().detail?.lastSummary).isEqualTo("hi")
            assertThat(historySnapshotRepository.requestedLocators.size).isEqualTo(2)
            ensureThatAllEventsAreConsumed()
        }
    }

    @Test
    fun `send reply should fail when direct lane reports unresolved recipient`() = runMviTest {
        val repository = FakeTaskSessionDetailRepository(detail = directReplyCapableDetail())
        val mailSender = FakeTaskMailReplySender()
        val directSender = FakeTaskMailDirectSessionActionSender(
            result = TaskMailDirectSessionActionResult.Rejected(
                errorMessage = "could not resolve a durable canonical reply recipient for the requested session action",
                errorCode = "session_recipient_unresolved",
                requestId = "req_rejected",
                receiptId = "receipt-rejected",
            ),
        )

        with(TaskSessionDetailViewModelRobot(this, repository, mailSender, directSessionActionSender = directSender)) {
            start()
            loadDetail()
            changeDraft("ship it")
            sendReply()
            assertThat(directSender.requests.size).isEqualTo(1)
            assertThat(mailSender.requests.size).isEqualTo(0)
            assertThat(viewModelState().draftText).isEqualTo("ship it")
            assertThat(viewModelState().latestDirectSessionActionRecord?.evidence?.switchGate).isEqualTo(
                TaskMailDirectSwitchGate.SwitchBlocker,
            )
            assertThat(viewModelState().latestDirectSessionActionRecord?.evidence?.requestId)
                .isEqualTo("req_rejected")
            assertThat(viewModelState().latestDirectSessionActionRecord?.evidence?.receiptId)
                .isEqualTo("receipt-rejected")
            assertThat(viewModelState().sendError).isEqualTo(
                "could not resolve a durable canonical reply recipient for the requested session action",
            )
            ensureThatAllEventsAreConsumed()
        }
    }

    @Test
    fun `send reply should preserve draft when direct lane hard rejects without mail fallback eligibility`() = runMviTest {
        val repository = FakeTaskSessionDetailRepository(detail = directReplyCapableDetail())
        val mailSender = FakeTaskMailReplySender()
        val directSender = FakeTaskMailDirectSessionActionSender(
            result = TaskMailDirectSessionActionResult.Rejected(
                errorMessage = "session identity mismatch",
                errorCode = "session_identity_mismatch",
                requestId = "req_rejected",
                receiptId = "receipt-rejected",
            ),
        )

        with(TaskSessionDetailViewModelRobot(this, repository, mailSender, directSessionActionSender = directSender)) {
            start()
            loadDetail()
            changeDraft("ship it")
            sendReply()
            assertThat(directSender.requests.size).isEqualTo(1)
            assertThat(mailSender.requests.size).isEqualTo(0)
            assertThat(viewModelState().draftText).isEqualTo("ship it")
            assertThat(viewModelState().latestDirectSessionActionRecord?.evidence?.switchGate).isEqualTo(
                TaskMailDirectSwitchGate.SwitchBlocker,
            )
            assertThat(viewModelState().sendError).isEqualTo("session identity mismatch")
            ensureThatAllEventsAreConsumed()
        }
    }

    @Test
    fun `send reply should allow session id only target when canonical workspace id is unavailable`() = runMviTest {
        val repository = FakeTaskSessionDetailRepository(detail = directReplyCapableDetail())
        val directSender = FakeTaskMailDirectSessionActionSender(
            result = TaskMailDirectSessionActionResult.Accepted(
                actionType = TaskMailDirectSessionActionType.Reply,
                requestId = "req_session_only",
                receiptId = "receipt-session-only",
                targetIdentity = TaskMailSessionActionTargetIdentity(
                    workspaceId = "workspace_001",
                    sessionId = "session_001",
                ),
            ),
        )

        with(TaskSessionDetailViewModelRobot(this, repository, directSessionActionSender = directSender)) {
            start()
            loadDetail(workspaceId = null)
            changeDraft("ship it")
            sendReply()
            assertThat(directSender.requests.single()).isEqualTo(
                TaskMailDirectSessionActionRequest.Reply(
                    target = TaskMailDirectSessionActionTarget(
                        workspaceId = null,
                        sessionId = "session_001",
                    ),
                    replyText = "ship it",
                ),
            )
            assertThat(viewModelState().sendError).isNull()
            assertThat(repository.detail?.pendingSubmissions).isEqualTo(
                listOf(
                    TaskSessionPendingSubmission(
                        commandId = "receipt-session-only",
                        requestId = "req_session_only",
                        actionType = TaskMailDirectSessionActionType.Reply,
                        submittedAt = 1_234L,
                        ackStatus = TaskMailSessionActionAckStatus.Accepted,
                        targetIdentity = TaskMailSessionActionTargetIdentity(
                            workspaceId = "workspace_001",
                            sessionId = "session_001",
                        ),
                    ),
                ),
            )
            assertShowMessageEffect(
                "[VPS] Reply submitted. Detail will keep following VPS-native session updates.",
            )
            ensureThatAllEventsAreConsumed()
        }
    }

    @Test
    fun `send reply should allow structured answers while the session is awaiting user input`() = runMviTest {
        val repository = FakeTaskSessionDetailRepository(detail = multiQuestionDetail())
        val directSender = FakeTaskMailDirectSessionActionSender()
        val structuredDraft = """
            Answers:
            phase2_entry_position: below
            phase2_icon_strings: provide
        """.trimIndent()

        with(TaskSessionDetailViewModelRobot(this, repository, directSessionActionSender = directSender)) {
            start()
            loadDetail()
            changeDraft(structuredDraft)
            sendReply()
            assertThat(directSender.requests.single()).isEqualTo(
                TaskMailDirectSessionActionRequest.Answers(
                    target = sampleDirectTarget(),
                    questionAnswers = listOf(
                        TaskMailSessionQuestionAnswer(
                            questionId = "phase2_entry_position",
                            value = "below",
                        ),
                        TaskMailSessionQuestionAnswer(
                            questionId = "phase2_icon_strings",
                            value = "provide",
                        ),
                    ),
                ),
            )
            assertThat(viewModelState().sendError).isNull()
            assertShowMessageEffect(
                "[VPS] Reply submitted. Detail will keep following VPS-native session updates.",
            )
            ensureThatAllEventsAreConsumed()
        }
    }

    @Test
    fun `send reply should block incomplete structured answer template`() = runMviTest {
        val repository = FakeTaskSessionDetailRepository(detail = multiQuestionDetail())
        val directSender = FakeTaskMailDirectSessionActionSender()

        with(TaskSessionDetailViewModelRobot(this, repository, directSessionActionSender = directSender)) {
            start()
            loadDetail()
            sendReply()
            assertThat(directSender.requests.size).isEqualTo(0)
            assertThat(viewModelState().sendError).isEqualTo(
                "Complete every required answer before sending this structured reply.",
            )
            ensureThatAllEventsAreConsumed()
        }
    }

    @Test
    fun `send reply should block attachment only structured answers until required answers are complete`() = runMviTest {
        val replyAttachment = sampleReplyAttachment()
        val attachmentResolver = FakeTaskMailReplyAttachmentResolver(
            attachmentsToReturn = listOf(replyAttachment),
        )
        val repository = FakeTaskSessionDetailRepository(detail = multiQuestionDetail())
        val directSender = FakeTaskMailDirectSessionActionSender()

        with(
            TaskSessionDetailViewModelRobot(
                this,
                repository,
                attachmentResolver = attachmentResolver,
                directSessionActionSender = directSender,
            ),
        ) {
            start()
            loadDetail()
            selectAttachments(listOf(replyAttachment.uriString))
            sendReply()
            assertThat(directSender.requests.size).isEqualTo(0)
            assertThat(viewModelState().sendError).isEqualTo(
                "Complete every required answer before sending this structured reply.",
            )
            ensureThatAllEventsAreConsumed()
        }
    }

    @Test
    fun `send reply should preserve draft on failure`() = runMviTest {
        val repository = FakeTaskSessionDetailRepository(detail = directReplyCapableDetail())
        val directSender = FakeTaskMailDirectSessionActionSender(
            result = TaskMailDirectSessionActionResult.Rejected(
                errorMessage = "send failed",
            ),
        )

        with(TaskSessionDetailViewModelRobot(this, repository, directSessionActionSender = directSender)) {
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
    fun `send reply should not depend on reply context when direct target is available`() = runMviTest {
        val repository = FakeTaskSessionDetailRepository(
            detail = directReplyCapableDetail().copy(replyContext = null),
        )
        val directSender = FakeTaskMailDirectSessionActionSender()

        with(TaskSessionDetailViewModelRobot(this, repository, directSessionActionSender = directSender)) {
            start()
            loadDetail()
            changeDraft("ship it")
            sendReply()
            assertThat(directSender.requests.size).isEqualTo(1)
            assertThat(viewModelState().sendError).isNull()
            assertShowMessageEffect(
                "[VPS] Reply submitted. Detail will keep following VPS-native session updates.",
            )
            ensureThatAllEventsAreConsumed()
        }
    }

    @Test
    fun `guide clicked should expose inline composer when current round can accept direct reply`() = runMviTest {
        with(TaskSessionDetailViewModelRobot(this, FakeTaskSessionDetailRepository(detail = currentInputLedDetail()))) {
            start()
            loadDetail()
            openGuideComposer()
            assertThat(viewModelState().isGuideComposerVisible).isEqualTo(true)
            ensureThatAllEventsAreConsumed()
        }
    }

    @Test
    fun `guide dismissed should hide inline composer`() = runMviTest {
        with(TaskSessionDetailViewModelRobot(this, FakeTaskSessionDetailRepository(detail = currentInputLedDetail()))) {
            start()
            loadDetail()
            openGuideComposer()
            dismissGuideComposer()
            assertThat(viewModelState().isGuideComposerVisible).isEqualTo(false)
            ensureThatAllEventsAreConsumed()
        }
    }

    @Test
    fun `guide send should use direct lane and hide inline composer on success`() = runMviTest {
        val repository = FakeTaskSessionDetailRepository(detail = currentInputLedDetail())
        val directSender = FakeTaskMailDirectSessionActionSender()

        with(TaskSessionDetailViewModelRobot(this, repository, directSessionActionSender = directSender)) {
            start()
            loadDetail()
            openGuideComposer()
            changeDraft("Keep the current branch, but skip the cleanup for now.")
            sendReply()
            assertThat(directSender.requests.single()).isEqualTo(
                TaskMailDirectSessionActionRequest.Reply(
                    target = sampleDirectTarget(),
                    replyText = "Keep the current branch, but skip the cleanup for now.",
                ),
            )
            assertThat(viewModelState().isGuideComposerVisible).isEqualTo(false)
            assertShowMessageEffect(
                "[VPS] Guide submitted. Detail will keep following VPS-native session updates.",
            )
            ensureThatAllEventsAreConsumed()
        }
    }

    @Test
    fun `stop running should submit kill through session action`() = runMviTest {
        val directSender = FakeTaskMailDirectSessionActionSender()

        with(
            TaskSessionDetailViewModelRobot(
                this,
                repository = FakeTaskSessionDetailRepository(detail = currentInputLedDetail()),
                directSessionActionSender = directSender,
            ),
        ) {
            start()
            loadDetail()
            stopRunning()
            assertThat(directSender.requests.single()).isEqualTo(
                TaskMailDirectSessionActionRequest.Kill(
                    target = sampleDirectTarget(),
                ),
            )
            assertShowMessageEffect(
                "[VPS] /kill submitted. Detail will keep following VPS-native session updates.",
            )
            ensureThatAllEventsAreConsumed()
        }
    }

    @Test
    fun `deactivate should submit end through session action`() = runMviTest {
        val directSender = FakeTaskMailDirectSessionActionSender()

        with(
            TaskSessionDetailViewModelRobot(
                this,
                repository = FakeTaskSessionDetailRepository(detail = directReplyCapableDetail()),
                directSessionActionSender = directSender,
            ),
        ) {
            start()
            loadDetail()
            deactivateSession()
            assertThat(directSender.requests.single()).isEqualTo(
                TaskMailDirectSessionActionRequest.End(
                    target = sampleDirectTarget(),
                ),
            )
            assertShowMessageEffect(
                "[VPS] /end submitted. Detail will keep following VPS-native session updates.",
            )
            ensureThatAllEventsAreConsumed()
        }
    }

    @Test
    fun `send reply should clear matching pending submission when history snapshot reports latest session action command id`() = runMviTest {
        val repository = FakeTaskSessionDetailRepository(detail = directReplyCapableDetail())
        val historySnapshotRepository = FakeTaskSessionHistorySnapshotRepository(
            result = Result.success(
                TaskSessionHistorySnapshot(
                    snapshotId = "snapshot_001",
                    generatedAt = "2026-03-29T10:00:00Z",
                    latestSessionAction = TaskSessionLatestActionSnapshot(
                        commandId = "cmd_001",
                        actionType = "reply",
                        ackStatus = "accepted",
                    ),
                ),
            ),
        )
        val directSender = FakeTaskMailDirectSessionActionSender(
            result = TaskMailDirectSessionActionResult.Accepted(
                actionType = TaskMailDirectSessionActionType.Reply,
                requestId = "req_001",
                receiptId = "cmd_001",
                targetIdentity = TaskMailSessionActionTargetIdentity(
                    workspaceId = "workspace_001",
                    sessionId = "session_001",
                ),
            ),
        )

        with(
            TaskSessionDetailViewModelRobot(
                this,
                repository,
                directSessionActionSender = directSender,
                historySnapshotRepository = historySnapshotRepository,
            ),
        ) {
            start()
            loadDetail()
            changeDraft("ship it")
            sendReply()
            assertThat(repository.detail?.pendingSubmissions).isEqualTo(emptyList())
            val expectedDetail = requireNotNull(repository.detail)
            assertThat(historySnapshotRepository.requestedLocators.last()).isEqualTo(
                TaskSessionHistorySnapshotLocator(
                    workspaceId = "workspace_001",
                    sessionId = "session_001",
                    threadId = "thread_001",
                    repoPath = expectedDetail.repoPath,
                    workdir = expectedDetail.workdir,
                ),
            )
            assertShowMessageEffect(
                "[VPS] Reply submitted. Detail will keep following VPS-native session updates.",
            )
            ensureThatAllEventsAreConsumed()
        }
    }

    @Test
    fun `status query should dispatch direct status`() = runMviTest {
        val repository = FakeTaskSessionDetailRepository(detail = directReplyCapableDetail())
        val directSender = FakeTaskMailDirectSessionActionSender(
            result = TaskMailDirectSessionActionResult.Accepted(
                actionType = TaskMailDirectSessionActionType.Status,
                requestId = "req_010",
                receiptId = "receipt-10",
            ),
        )

        with(TaskSessionDetailViewModelRobot(this, repository, directSessionActionSender = directSender)) {
            start()
            loadDetail()
            sendStatusQuery()
            assertThat(directSender.requests.single()).isEqualTo(
                TaskMailDirectSessionActionRequest.Status(
                    target = sampleDirectTarget(),
                ),
            )
            assertShowMessageEffect(
                "[VPS] /status submitted. Detail will keep following VPS-native session updates.",
            )
            ensureThatAllEventsAreConsumed()
        }
    }

    @Test
    fun `status query should be blocked for legacy mail-only session`() = runMviTest {
        val repository = FakeTaskSessionDetailRepository(detail = legacyMailOnlyDetail())
        val directSender = FakeTaskMailDirectSessionActionSender()

        with(TaskSessionDetailViewModelRobot(this, repository, directSessionActionSender = directSender)) {
            start()
            loadDetail()
            sendStatusQuery()
            assertThat(directSender.requests.size).isEqualTo(0)
            assertThat(viewModelState().sendError).isEqualTo(
                "Session actions are unavailable until this session is backed by VPS session-action data.",
            )
            ensureThatAllEventsAreConsumed()
        }
    }

    @Test
    fun `status query should use direct lane when canonical workspace id is available`() = runMviTest {
        val repository = FakeTaskSessionDetailRepository(detail = directReplyCapableDetail())
        val mailSender = FakeTaskMailReplySender()
        val directSender = FakeTaskMailDirectSessionActionSender(
            result = TaskMailDirectSessionActionResult.Accepted(
                actionType = TaskMailDirectSessionActionRequest.Status(
                    target = sampleDirectTarget(),
                ).actionType,
                requestId = "req_002",
                receiptId = "receipt-2",
                transportMessageId = "transport-2",
            ),
        )

        with(
            TaskSessionDetailViewModelRobot(
                this,
                repository,
                mailSender,
                directSessionActionSender = directSender,
            ),
        ) {
            start()
            loadDetail()
            sendStatusQuery()
            assertThat(mailSender.requests.size).isEqualTo(0)
            assertThat(directSender.requests.single()).isEqualTo(
                TaskMailDirectSessionActionRequest.Status(
                    target = sampleDirectTarget(),
                ),
            )
            assertThat(viewModelState().latestDirectSessionActionRecord?.actionType).isEqualTo(
                TaskMailDirectSessionActionType.Status,
            )
            assertThat(viewModelState().latestDirectSessionActionRecord?.evidence?.requestId).isEqualTo("req_002")
            assertThat(repository.detail?.pendingSubmissions).isEqualTo(
                listOf(
                    TaskSessionPendingSubmission(
                        commandId = "receipt-2",
                        requestId = "req_002",
                        actionType = TaskMailDirectSessionActionType.Status,
                        submittedAt = 1_234L,
                        ackStatus = TaskMailSessionActionAckStatus.Accepted,
                        targetIdentity = TaskMailSessionActionTargetIdentity(
                            workspaceId = "workspace_001",
                            sessionId = "session_001",
                        ),
                    ),
                ),
            )
            assertShowMessageEffect(
                "[VPS] /status submitted. Detail will keep following VPS-native session updates.",
            )
            ensureThatAllEventsAreConsumed()
        }
    }

    @Test
    fun `resume should use direct lane for paused session and update pending submission state`() = runMviTest {
        val repository = FakeTaskSessionDetailRepository(detail = pausedDetail())
        val directSender = FakeTaskMailDirectSessionActionSender(
            result = TaskMailDirectSessionActionResult.Accepted(
                actionType = TaskMailDirectSessionActionRequest.Resume(
                    target = sampleDirectTarget(),
                ).actionType,
                requestId = "req_resume",
                receiptId = "receipt-resume",
                transportMessageId = "transport-resume",
            ),
        )

        with(
            TaskSessionDetailViewModelRobot(
                this,
                repository,
                directSessionActionSender = directSender,
            ),
        ) {
            start()
            loadDetail()
            resumeSession()
            assertThat(directSender.requests.single()).isEqualTo(
                TaskMailDirectSessionActionRequest.Resume(
                    target = sampleDirectTarget(),
                ),
            )
            assertThat(viewModelState().latestDirectSessionActionRecord?.actionType).isEqualTo(
                TaskMailDirectSessionActionType.Resume,
            )
            assertThat(viewModelState().detail?.pendingSubmission?.message).isEqualTo(
                "Resume accepted by the PC.",
            )
            assertThat(repository.detail?.pendingSubmissions).isEqualTo(
                listOf(
                    TaskSessionPendingSubmission(
                        commandId = "receipt-resume",
                        requestId = "req_resume",
                        actionType = TaskMailDirectSessionActionType.Resume,
                        submittedAt = 1_234L,
                        ackStatus = TaskMailSessionActionAckStatus.Accepted,
                        targetIdentity = TaskMailSessionActionTargetIdentity(
                            workspaceId = "workspace_001",
                            sessionId = "session_001",
                        ),
                    ),
                ),
            )
            assertShowMessageEffect(
                "[VPS] Resume submitted. Detail will keep following VPS-native session updates.",
            )
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
    fun `question choice should use direct answer flow while the session is awaiting user input`() = runMviTest {
        val directSender = FakeTaskMailDirectSessionActionSender()

        with(
            TaskSessionDetailViewModelRobot(
                this,
                FakeTaskSessionDetailRepository(detail = singleQuestionDetailWithChoiceLabels()),
                directSessionActionSender = directSender,
            ),
        ) {
            start()
            loadDetail()
            sendChoice("approve")
            assertThat(directSender.requests.single()).isEqualTo(
                TaskMailDirectSessionActionRequest.Reply(
                    target = sampleDirectTarget(),
                    replyText = "approve",
                ),
            )
            assertThat(viewModelState().sendError).isNull()
            assertShowMessageEffect(
                "[VPS] Quick answer submitted. Detail will keep following VPS-native session updates.",
            )
            ensureThatAllEventsAreConsumed()
        }
    }

    @Test
    fun `send reply should be blocked for paused sessions in direct only mode`() = runMviTest {
        val repository = FakeTaskSessionDetailRepository(detail = pausedDetail())

        with(TaskSessionDetailViewModelRobot(this, repository)) {
            start()
            loadDetail()
            changeDraft("Please continue with the cleanup.")
            sendReply()
            assertThat(viewModelState().sendError).isEqualTo(
                "Plain-text reply is unavailable while the session is paused.",
            )
            ensureThatAllEventsAreConsumed()
        }
    }

    @Test
    fun `question choice should be blocked when the session is paused`() = runMviTest {
        val repository = FakeTaskSessionDetailRepository(detail = pausedSingleQuestionDetail())

        with(TaskSessionDetailViewModelRobot(this, repository)) {
            start()
            loadDetail()
            sendChoice("approve")
            assertThat(viewModelState().sendError).isEqualTo(
                "Plain-text reply is unavailable while the session is paused.",
            )
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
        val directSender = FakeTaskMailDirectSessionActionSender(
            result = TaskMailDirectSessionActionResult.Rejected(
                errorMessage = "send failed",
            ),
        )

        with(TaskSessionDetailViewModelRobot(this, FakeTaskSessionDetailRepository(), directSessionActionSender = directSender)) {
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
    fun `refresh clicked should reload vps detail without mail sync`() = runMviTest {
        val repository = FakeTaskSessionDetailRepository(detail = vpsProjectedDetail())
        val syncRequester = FakeTaskMailSyncRequester()

        with(TaskSessionDetailViewModelRobot(this, repository, syncRequester = syncRequester)) {
            start()
            loadDetail()
            repository.detail = repository.detail?.copy(lastSummary = "Reloaded from VPS cache")
            refresh()
            assertThat(syncRequester.requestCount).isEqualTo(0)
            assertThat(viewModelState().detail?.lastSummary).isEqualTo("Reloaded from VPS cache")
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
    private val sessionActionSendRecordRepository: FakeTaskMailSessionActionSendRecordRepository =
        FakeTaskMailSessionActionSendRecordRepository(),
    private val changeObserver: FakeTaskMailStoreChangeObserver = FakeTaskMailStoreChangeObserver(),
    private val foregroundRefreshTickerFactory: FakeTaskMailForegroundRefreshTickerFactory =
        FakeTaskMailForegroundRefreshTickerFactory(),
    syncTaskMailCache: SyncTaskMailCache? = null,
    private val sessionUpdatesObserver: FakeObserveTaskMailSessionUpdates = FakeObserveTaskMailSessionUpdates(),
    historySnapshotRepository: TaskSessionHistorySnapshotRepository = FakeTaskSessionHistorySnapshotRepository(),
    logger: DetailViewModelFakeLogger = DetailViewModelFakeLogger(),
    directSessionActionSender: FakeTaskMailDirectSessionActionSender? = FakeTaskMailDirectSessionActionSender(),
    sessionSnapshotRecoveryMaxAttempts: Int = 18,
    sessionSnapshotRecoveryDelayMs: Long = 10_000L,
    currentTimeProvider: () -> Long = { 1_234L },
) {
    private val getLatestTaskMailSessionActionSendRecord =
        GetLatestTaskMailSessionActionSendRecord(sessionActionSendRecordRepository)
    private val recordTaskMailSessionActionSendRecord =
        RecordTaskMailSessionActionSendRecord(sessionActionSendRecordRepository)
    private val viewModel = TaskSessionDetailViewModel(
        detailRepository = repository,
        getTaskSessionDetail = GetTaskSessionDetail(repository),
        refreshTaskMail = RefreshTaskMail(syncRequester),
        observeTaskMailStoreChanges = ObserveTaskMailStoreChanges(changeObserver),
        foregroundRefreshTickerFactory = foregroundRefreshTickerFactory,
        sendTaskMailDirectSessionAction = directSessionActionSender?.let(::SendTaskMailDirectSessionAction),
        getLatestTaskMailSessionActionSendRecord = getLatestTaskMailSessionActionSendRecord,
        recordTaskMailSessionActionSendRecord = recordTaskMailSessionActionSendRecord,
        replyAttachmentResolver = attachmentResolver,
        timelineAttachmentHandler = timelineAttachmentHandler,
        logger = logger,
        syncTaskMailCache = syncTaskMailCache,
        observeTaskMailSessionUpdates = sessionUpdatesObserver,
        getTaskSessionHistorySnapshot = GetTaskSessionHistorySnapshot(historySnapshotRepository),
        sessionSnapshotRecoveryMaxAttempts = sessionSnapshotRecoveryMaxAttempts,
        sessionSnapshotRecoveryDelayMs = sessionSnapshotRecoveryDelayMs,
        currentTimeProvider = currentTimeProvider,
    )
    private lateinit var turbines: MviTurbines<TaskSessionDetailContract.State, TaskSessionDetailContract.Effect>

    suspend fun start() {
        turbines = mviContext.turbinesWithInitialStateCheck(viewModel, TaskSessionDetailContract.State())
    }

    suspend fun loadDetail(
        workspaceId: String? = "workspace_001",
        sessionId: String = "session_001",
        preferServerHistoryRounds: Boolean = false,
    ) {
        viewModel.event(
            TaskSessionDetailContract.Event.LoadDetail(
                workspaceId = workspaceId,
                sessionId = sessionId,
                preferServerHistoryRounds = preferServerHistoryRounds,
            ),
        )
        mviContext.advanceUntilIdle()
    }

    fun changeDraft(text: String) {
        viewModel.event(TaskSessionDetailContract.Event.DraftChanged(text))
    }

    fun changeReplyPermission(permission: TaskMailNewTaskPermission) {
        viewModel.event(TaskSessionDetailContract.Event.ReplyPermissionChanged(permission))
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

    suspend fun resumeSession() {
        viewModel.event(TaskSessionDetailContract.Event.ResumeClicked)
        mviContext.advanceUntilIdle()
    }

    suspend fun openGuideComposer() {
        viewModel.event(TaskSessionDetailContract.Event.GuideClicked)
        mviContext.advanceUntilIdle()
    }

    fun dismissGuideComposer() {
        viewModel.event(TaskSessionDetailContract.Event.GuideDismissed)
    }

    suspend fun stopRunning() {
        viewModel.event(TaskSessionDetailContract.Event.StopRunningClicked)
        mviContext.advanceUntilIdle()
    }

    suspend fun deactivateSession() {
        viewModel.event(TaskSessionDetailContract.Event.DeactivateClicked)
        mviContext.advanceUntilIdle()
    }

    suspend fun refresh() {
        viewModel.event(TaskSessionDetailContract.Event.RefreshClicked)
        mviContext.advanceUntilIdle()
    }

    fun openHistory() {
        viewModel.event(TaskSessionDetailContract.Event.HistoryClicked)
    }

    fun dismissHistory() {
        viewModel.event(TaskSessionDetailContract.Event.HistoryDismissed)
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

    suspend fun emitSessionUpdateSnapshot(snapshot: TaskSessionHistorySnapshot) {
        sessionUpdatesObserver.emit(snapshot)
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

    suspend fun assertShowMessageEffect(message: String) {
        assertThat(turbines.awaitEffectItem()).isEqualTo(TaskSessionDetailContract.Effect.ShowMessage(message))
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

    override suspend fun replaceAllSessionDetails(details: List<TaskSessionDetail>) {
        detail = details.firstOrNull()
    }

    override suspend fun upsertSessionDetails(details: List<TaskSessionDetail>) {
        val incomingDetail = details.firstOrNull() ?: return
        detail = incomingDetail
    }

    override suspend fun removeSessionDetails(keys: List<TaskSessionKey>) = Unit
}

private class FakeTaskMailSessionActionSendRecordRepository(
    latestRecord: TaskMailSessionActionSendRecord? = null,
) : TaskMailSessionActionSendRecordRepository {
    private var records = listOfNotNull(latestRecord)

    override suspend fun getLatestRecord(
        target: TaskMailDirectSessionActionTarget,
    ): TaskMailSessionActionSendRecord? {
        return records.firstOrNull { record ->
            record.target.sessionId == target.sessionId &&
                (target.workspaceId == null || record.target.workspaceId == target.workspaceId)
        }
    }

    override suspend fun saveRecord(record: TaskMailSessionActionSendRecord) {
        records = (listOf(record) + records)
            .sortedByDescending(TaskMailSessionActionSendRecord::recordedAt)
    }
}

private class FakeTaskSessionHistorySnapshotRepository(
    private val result: Result<TaskSessionHistorySnapshot> =
        Result.failure(IllegalStateException("history snapshot not configured")),
    private val scriptedResults: MutableList<Result<TaskSessionHistorySnapshot>> = mutableListOf(),
) : TaskSessionHistorySnapshotRepository {
    val requestedLocators = mutableListOf<TaskSessionHistorySnapshotLocator>()

    override suspend fun getHistorySnapshot(locator: TaskSessionHistorySnapshotLocator): Result<TaskSessionHistorySnapshot> {
        requestedLocators += locator
        return if (scriptedResults.isNotEmpty()) {
            scriptedResults.removeAt(0)
        } else {
            result
        }
    }
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

private class DetailViewModelFakeLogger : Logger {
    val debugMessages = mutableListOf<String>()

    override fun verbose(tag: LogTag?, throwable: Throwable?, message: () -> LogMessage) = Unit

    override fun debug(tag: LogTag?, throwable: Throwable?, message: () -> LogMessage) {
        debugMessages += message()
    }

    override fun info(tag: LogTag?, throwable: Throwable?, message: () -> LogMessage) = Unit
    override fun warn(tag: LogTag?, throwable: Throwable?, message: () -> LogMessage) = Unit
    override fun error(tag: LogTag?, throwable: Throwable?, message: () -> LogMessage) = Unit
}

private class FakeObserveTaskMailSessionUpdates : ObserveTaskMailSessionUpdates {
    private val snapshots = MutableSharedFlow<TaskSessionHistorySnapshot>(extraBufferCapacity = 1)
    private var flow: Flow<TaskSessionHistorySnapshot> = snapshots

    override fun invoke(detail: TaskSessionDetail): Flow<TaskSessionHistorySnapshot> = flow

    suspend fun emit(snapshot: TaskSessionHistorySnapshot) {
        snapshots.emit(snapshot)
    }

    fun completeWithoutSnapshot() {
        flow = emptyFlow()
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

private class FakeTaskMailDirectSessionActionSender(
    private val result: TaskMailDirectSessionActionResult = TaskMailDirectSessionActionResult.Accepted(
        actionType = TaskMailDirectSessionActionRequest.Reply(
            target = sampleDirectTarget(),
            replyText = "ship it",
        ).actionType,
        requestId = "req_001",
        receiptId = "receipt-1",
        transportMessageId = "transport-1",
    ),
) : TaskMailDirectSessionActionSender {
    val requests = mutableListOf<TaskMailDirectSessionActionRequest>()

    override suspend fun send(request: TaskMailDirectSessionActionRequest): TaskMailDirectSessionActionResult {
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
    val openedAttachments = mutableListOf<TaskAttachmentActionTarget>()
    val savedAttachments = mutableListOf<Pair<TaskAttachmentActionTarget, String>>()

    override suspend fun createOpenIntent(attachment: TaskAttachmentActionTarget): Result<android.content.Intent> {
        openedAttachments += attachment
        return openIntentResult
    }

    override suspend fun saveAttachmentTo(
        attachment: TaskAttachmentActionTarget,
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

private fun directReplyCapableDetail(): TaskSessionDetail {
    return TaskMailPreviewData.sessionDetails.first().copy(
        status = TaskMailSessionStatus.Done,
        question = null,
        pendingQuestions = emptyList(),
        projectionSyncState = TaskSessionProjectionSyncState(
            dataSource = TaskSessionProjectionDataSource.VpsNative,
            lastSequence = 1L,
            lastProjectionUpdatedAt = 456L,
            subscriptionStatus = TaskSessionProjectionSubscriptionStatus.Active,
        ),
    )
}

private fun detailWithControlPlaneArtifact(): TaskSessionDetail {
    return directReplyCapableDetail().copy(
        controlPlaneSnapshot = TaskSessionControlPlaneSnapshot(
            artifactManifest = ControlPlaneArtifactManifest(
                runId = "run_01",
                artifacts = listOf(
                    ControlPlaneArtifact(
                        artifactId = "artifact-summary",
                        name = "summary.md",
                        kind = "file",
                        role = "output",
                        contentType = "text/markdown",
                        size = 512L,
                        downloadRef = ControlPlaneDownloadRef(
                            kind = "vps_file",
                            fileId = "file_summary_01",
                            metadataUrl = "/v1/files/file_summary_01",
                            contentUrl = "/v1/files/file_summary_01/content",
                        ),
                    ),
                ),
            ),
        ),
    )
}

private fun historySnapshotWithRelayAttachment(): TaskSessionHistorySnapshot {
    return TaskSessionHistorySnapshot(
        snapshotId = "snapshot_history_001",
        generatedAt = "2026-03-31T01:40:00Z",
        rounds = persistentListOf(
            TaskSessionHistorySnapshotRound(
                roundId = "hist_round_task_001",
                roundNumber = 1,
                createdAt = "2026-03-31T01:39:00Z",
                status = "done",
                speakerLabel = "Codex",
                inputText = "Summarize the homepage changes.",
                resultText = "Attached the homepage summary artifact.",
                resultAttachments = persistentListOf(
                    TaskSessionHistorySnapshotAttachment(
                        attachmentId = "hist_result_task_001_1",
                        displayName = "homepage-summary.md",
                        contentType = "text/markdown",
                        actionTarget = buildRelayArtifactActionTarget(
                            attachmentId = "hist_result_task_001_1",
                            displayName = "homepage-summary.md",
                            contentType = "text/markdown",
                            kind = "vps_file",
                            fileId = "file_history_001",
                            metadataUrl = "/v1/files/file_history_001",
                            contentUrl = "/v1/files/file_history_001/content",
                        ),
                    ),
                ),
            ),
        ),
    )
}

private fun currentInputLedDetail(): TaskSessionDetail {
    return directReplyCapableDetail().copy(
        status = TaskMailSessionStatus.Running,
    )
}

private fun vpsProjectedDetail(): TaskSessionDetail {
    return directReplyCapableDetail().copy(
        lastSummary = "VPS cached summary",
        projectionSyncState = TaskSessionProjectionSyncState(
            dataSource = TaskSessionProjectionDataSource.VpsNative,
            lastSequence = 9L,
            lastProjectionUpdatedAt = 789L,
            subscriptionStatus = TaskSessionProjectionSubscriptionStatus.Active,
        ),
    )
}

private fun queuedVpsPendingDetail(): TaskSessionDetail {
    return directReplyCapableDetail().copy(
        key = TaskSessionKey(
            workspaceId = "workspace_001",
            sessionId = "session_001",
            threadId = null,
        ),
        sessionName = "say hi",
        status = TaskMailSessionStatus.Queued,
        lastSummary = "[VPS] Task request submitted. Waiting for the first session update.",
        timeline = listOf(
            TaskTimelineItem(
                id = "android-create-session:session_001",
                timestamp = 1_000L,
                direction = TaskTimelineDirection.System,
                summary = "[VPS] Task request submitted. Waiting for the first session update.",
                body = TaskMessageBody(
                    plainTextFallback = "[VPS] Task request submitted. Waiting for the first session update.",
                ),
            ),
        ),
        projectionSyncState = TaskSessionProjectionSyncState(
            dataSource = TaskSessionProjectionDataSource.VpsNative,
            lastSequence = 0L,
            lastProjectionUpdatedAt = 789L,
            subscriptionStatus = TaskSessionProjectionSubscriptionStatus.Idle,
        ),
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

    return directReplyCapableDetail().copy(
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

    return directReplyCapableDetail().copy(
        question = question,
        pendingQuestions = listOf(question),
    )
}

private fun pausedDetail(): TaskSessionDetail {
    return directReplyCapableDetail().copy(
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

private fun legacyMailOnlyDetail(): TaskSessionDetail {
    return TaskMailPreviewData.sessionDetails.first().copy(
        status = TaskMailSessionStatus.Done,
        question = null,
        pendingQuestions = emptyList(),
        projectionSyncState = TaskSessionProjectionSyncState(
            dataSource = TaskSessionProjectionDataSource.MailCompatibilityImport,
            subscriptionStatus = TaskSessionProjectionSubscriptionStatus.Unknown,
        ),
    )
}

private fun sampleDirectTarget(): TaskMailDirectSessionActionTarget {
    return TaskMailDirectSessionActionTarget(
        workspaceId = "workspace_001",
        sessionId = "session_001",
    )
}

private fun sampleSessionUpdateSnapshot(
    status: TaskMailSessionStatus,
    lastSummary: String? = null,
    lastProgressAt: String? = null,
    liveProcess: TaskSessionLiveProcess? = null,
    pendingQuestions: List<TaskQuestionCapsule> = emptyList(),
    timelineItems: List<TaskSessionHistorySnapshotTimelineItem> = emptyList(),
    historyRounds: List<TaskSessionHistorySnapshotRound> = emptyList(),
    threadId: String = "thread_001",
): TaskSessionHistorySnapshot {
    return TaskSessionHistorySnapshot(
        snapshotId = "snapshot_001",
        generatedAt = "2026-03-29T18:31:00Z",
        sessionHeader = TaskSessionHistorySnapshotHeader(
            workspaceId = "workspace_001",
            sessionId = "session_001",
            threadId = threadId,
            sessionName = "Build TaskMail Phase 1",
            backend = TaskMailBackend.Codex,
            repoPath = "E:/projects/android_task_manager",
            workdir = "feature/taskmail/internal",
            status = status,
            lifecycle = TaskMailSessionLifecycle.Active,
            lastSummary = lastSummary,
            lastProgressAt = lastProgressAt,
            liveProcess = liveProcess,
            pendingQuestions = pendingQuestions.toImmutableList(),
            timelineItems = timelineItems.toImmutableList(),
        ),
        rounds = historyRounds.toImmutableList(),
    )
}

private fun snapshotTimelineItem(
    itemId: String,
    businessEventKey: String,
    text: String,
    createdAt: String = "2026-03-29T18:31:00Z",
    itemType: String = "terminal_summary",
    status: String? = "running",
): TaskSessionHistorySnapshotTimelineItem {
    return TaskSessionHistorySnapshotTimelineItem(
        itemId = itemId,
        businessEventKey = businessEventKey,
        itemType = itemType,
        createdAt = createdAt,
        status = status,
        text = text,
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
