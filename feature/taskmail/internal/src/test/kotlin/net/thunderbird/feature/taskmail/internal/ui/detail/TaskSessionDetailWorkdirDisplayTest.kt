package net.thunderbird.feature.taskmail.internal.ui.detail

import android.content.Intent
import assertk.assertThat
import assertk.assertions.isEqualTo
import com.fsck.k9.message.Attachment
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import net.thunderbird.core.testing.coroutines.MainDispatcherHelper
import net.thunderbird.feature.taskmail.internal.data.TaskMailForegroundRefreshTickerFactory
import net.thunderbird.feature.taskmail.internal.data.TaskMailReplyAttachmentResolver
import net.thunderbird.feature.taskmail.internal.data.TaskMailStoreChangeObserver
import net.thunderbird.feature.taskmail.internal.data.TaskMailSyncRequester
import net.thunderbird.feature.taskmail.internal.data.TaskMailTimelineAttachmentHandler
import net.thunderbird.feature.taskmail.internal.domain.model.TaskMessageAttachment
import net.thunderbird.feature.taskmail.internal.domain.model.TaskReplyAttachment
import net.thunderbird.feature.taskmail.internal.domain.model.TaskSessionDetail
import net.thunderbird.feature.taskmail.internal.domain.model.TaskSessionKey
import net.thunderbird.feature.taskmail.internal.domain.reply.TaskMailReplyRequest
import net.thunderbird.feature.taskmail.internal.domain.reply.TaskMailReplyResult
import net.thunderbird.feature.taskmail.internal.domain.reply.TaskMailReplySender
import net.thunderbird.feature.taskmail.internal.domain.repository.TaskSessionDetailRepository
import net.thunderbird.feature.taskmail.internal.domain.usecase.GetTaskSessionDetail
import net.thunderbird.feature.taskmail.internal.domain.usecase.ObserveTaskMailStoreChanges
import net.thunderbird.feature.taskmail.internal.domain.usecase.RefreshTaskMail
import net.thunderbird.feature.taskmail.internal.domain.usecase.SendTaskMailReply
import net.thunderbird.feature.taskmail.internal.preview.TaskMailPreviewData

@OptIn(ExperimentalCoroutinesApi::class)
internal class TaskSessionDetailWorkdirDisplayTest {

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
    fun `load detail should show only the last workdir segment in ui state`() = runTest {
        val repository = SingleDetailRepository(TaskMailPreviewData.sessionDetails.first())
        val testSubject = TaskSessionDetailViewModel(
            detailRepository = repository,
            getTaskSessionDetail = GetTaskSessionDetail(repository),
            refreshTaskMail = RefreshTaskMail(NoOpTaskMailSyncRequester),
            observeTaskMailStoreChanges = ObserveTaskMailStoreChanges(NoOpTaskMailStoreChangeObserver),
            foregroundRefreshTickerFactory = TaskMailForegroundRefreshTickerFactory { emptyFlow() },
            sendTaskMailReply = SendTaskMailReply(NoOpTaskMailReplySender),
            replyAttachmentResolver = NoOpTaskMailReplyAttachmentResolver,
            timelineAttachmentHandler = NoOpTaskMailTimelineAttachmentHandler,
        )

        testSubject.event(
            TaskSessionDetailContract.Event.LoadDetail(
                sessionId = "session_001",
                threadId = "thread_001",
            ),
        )
        advanceUntilIdle()

        assertThat(testSubject.state.value.detail?.workdir).isEqualTo("taskmail")
    }
}

private class SingleDetailRepository(
    private val detail: TaskSessionDetail,
) : TaskSessionDetailRepository {
    override suspend fun getTaskSessionDetail(key: TaskSessionKey): TaskSessionDetail = detail

    override suspend fun getTaskSessionDetails(): List<TaskSessionDetail> = listOf(detail)

    override suspend fun replaceAllSessionDetails(details: List<TaskSessionDetail>) = Unit

    override suspend fun upsertSessionDetails(details: List<TaskSessionDetail>) = Unit

    override suspend fun removeSessionDetails(keys: List<TaskSessionKey>) = Unit
}

private object NoOpTaskMailSyncRequester : TaskMailSyncRequester {
    override suspend fun requestSync(): Result<Unit> = Result.success(Unit)
}

private object NoOpTaskMailStoreChangeObserver : TaskMailStoreChangeObserver {
    override fun changes() = emptyFlow<Unit>()
}

private object NoOpTaskMailReplySender : TaskMailReplySender {
    override suspend fun send(request: TaskMailReplyRequest): TaskMailReplyResult = TaskMailReplyResult.success()
}

private object NoOpTaskMailReplyAttachmentResolver : TaskMailReplyAttachmentResolver {
    override suspend fun resolveSelectedAttachments(uriStrings: List<String>): List<TaskReplyAttachment> = emptyList()

    override suspend fun buildOutgoingAttachments(attachments: List<TaskReplyAttachment>): Result<List<Attachment>> {
        return Result.success(emptyList())
    }
}

private object NoOpTaskMailTimelineAttachmentHandler : TaskMailTimelineAttachmentHandler {
    override suspend fun createOpenIntent(attachment: TaskMessageAttachment): Result<Intent> {
        return Result.failure(IllegalStateException("Not used in this test"))
    }

    override suspend fun saveAttachmentTo(
        attachment: TaskMessageAttachment,
        destinationUriString: String,
    ): Result<Unit> {
        return Result.failure(IllegalStateException("Not used in this test"))
    }
}
