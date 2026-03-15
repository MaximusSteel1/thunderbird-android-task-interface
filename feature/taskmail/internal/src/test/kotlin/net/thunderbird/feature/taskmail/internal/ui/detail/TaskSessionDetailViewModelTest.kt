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
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import net.thunderbird.core.testing.coroutines.MainDispatcherHelper
import net.thunderbird.feature.taskmail.internal.data.TaskMailReplyAttachmentResolver
import net.thunderbird.feature.taskmail.internal.data.TaskMailTimelineAttachmentHandler
import net.thunderbird.feature.taskmail.internal.domain.model.TaskMessageAttachment
import net.thunderbird.feature.taskmail.internal.domain.model.TaskReplyAttachment
import net.thunderbird.feature.taskmail.internal.domain.model.TaskSessionDetail
import net.thunderbird.feature.taskmail.internal.domain.model.TaskSessionKey
import net.thunderbird.feature.taskmail.internal.domain.model.TaskWorkspaceSummary
import net.thunderbird.feature.taskmail.internal.domain.parser.TaskQuestionCapsule
import net.thunderbird.feature.taskmail.internal.domain.reply.TaskMailReplyKind
import net.thunderbird.feature.taskmail.internal.domain.reply.TaskMailReplyRequest
import net.thunderbird.feature.taskmail.internal.domain.reply.TaskMailReplyResult
import net.thunderbird.feature.taskmail.internal.domain.reply.TaskMailReplySender
import net.thunderbird.feature.taskmail.internal.domain.repository.TaskMailRepository
import net.thunderbird.feature.taskmail.internal.domain.usecase.GetTaskSessionDetail
import net.thunderbird.feature.taskmail.internal.domain.usecase.SendTaskMailReply
import net.thunderbird.feature.taskmail.internal.preview.TaskMailPreviewData

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
        with(TaskSessionDetailViewModelRobot(this, FakeTaskSessionDetailRepository())) {
            start()
            loadDetail()
            assertLoadedDetail()
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
            assertThat(repository.requestedKeys.size).isEqualTo(2)
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
            assertThat(sender.requests.single().kind).isEqualTo(TaskMailReplyKind.StructuredAnswers)
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
                "Add at least one structured answer before sending.",
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
    fun `refresh clicked should keep detail visible when refresh fails`() = runMviTest {
        val repository = FakeTaskSessionDetailRepository()

        with(TaskSessionDetailViewModelRobot(this, repository)) {
            start()
            loadDetail()
            repository.shouldThrowDetailError = true
            refresh()
            assertThat(viewModelState().detail).isNotNull()
            assertThat(viewModelState().sendError).isEqualTo("Failed to refresh TaskMail session detail.")
            ensureThatAllEventsAreConsumed()
        }
    }
}

private class TaskSessionDetailViewModelRobot(
    private val mviContext: MviContext,
    repository: TaskMailRepository,
    replySender: TaskMailReplySender = FakeTaskMailReplySender(),
    attachmentResolver: TaskMailReplyAttachmentResolver = FakeTaskMailReplyAttachmentResolver(),
    timelineAttachmentHandler: TaskMailTimelineAttachmentHandler = FakeTaskMailTimelineAttachmentHandler(),
) {
    private val viewModel = TaskSessionDetailViewModel(
        repository = repository,
        getTaskSessionDetail = GetTaskSessionDetail(repository),
        sendTaskMailReply = SendTaskMailReply(replySender),
        replyAttachmentResolver = attachmentResolver,
        timelineAttachmentHandler = timelineAttachmentHandler,
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
    private val detail: TaskSessionDetail? = TaskMailPreviewData.sessionDetails.first(),
    var shouldThrowDetailError: Boolean = false,
) : TaskMailRepository {
    var requestedKey: TaskSessionKey? = null
    val requestedKeys = mutableListOf<TaskSessionKey>()

    override suspend fun getTaskWorkspaceSummaries(): List<TaskWorkspaceSummary> = emptyList()

    override suspend fun getTaskSessionDetail(key: TaskSessionKey): TaskSessionDetail? {
        if (shouldThrowDetailError) error("detail error")
        requestedKey = key
        requestedKeys += key
        return detail
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
