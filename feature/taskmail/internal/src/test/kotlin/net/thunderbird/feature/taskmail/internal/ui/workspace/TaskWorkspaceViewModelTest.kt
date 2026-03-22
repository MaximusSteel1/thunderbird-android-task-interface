package net.thunderbird.feature.taskmail.internal.ui.workspace

import app.k9mail.core.ui.compose.testing.mvi.MviContext
import app.k9mail.core.ui.compose.testing.mvi.MviTurbines
import app.k9mail.core.ui.compose.testing.mvi.advanceUntilIdle
import app.k9mail.core.ui.compose.testing.mvi.runMviTest
import app.k9mail.core.ui.compose.testing.mvi.turbinesWithInitialStateCheck
import assertk.assertThat
import assertk.assertions.hasSize
import assertk.assertions.isEqualTo
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import net.thunderbird.core.testing.coroutines.MainDispatcherHelper
import net.thunderbird.feature.taskmail.internal.data.TaskMailForegroundRefreshTickerFactory
import net.thunderbird.feature.taskmail.internal.data.TaskMailSenderAccountSource
import net.thunderbird.feature.taskmail.internal.data.TaskMailSessionProjector
import net.thunderbird.feature.taskmail.internal.data.TaskMailStoreChangeObserver
import net.thunderbird.feature.taskmail.internal.data.TaskMailSyncRequester
import net.thunderbird.feature.taskmail.internal.data.cache.TaskMailMessageJsonCodec
import net.thunderbird.feature.taskmail.internal.domain.model.MessageSyncState
import net.thunderbird.feature.taskmail.internal.domain.model.TaskMailSenderAccount
import net.thunderbird.feature.taskmail.internal.domain.model.TaskSessionDetail
import net.thunderbird.feature.taskmail.internal.domain.model.TaskSessionKey
import net.thunderbird.feature.taskmail.internal.domain.model.TaskWorkspaceSummary
import net.thunderbird.feature.taskmail.internal.domain.model.UnifiedMessage
import net.thunderbird.feature.taskmail.internal.domain.repository.MessageSyncStateRepository
import net.thunderbird.feature.taskmail.internal.domain.repository.TaskMailRepository
import net.thunderbird.feature.taskmail.internal.domain.repository.TaskSessionDetailRepository
import net.thunderbird.feature.taskmail.internal.domain.repository.UnifiedMessageRepository
import net.thunderbird.feature.taskmail.internal.domain.usecase.GetTaskMailSenderAccounts
import net.thunderbird.feature.taskmail.internal.domain.usecase.GetTaskWorkspaceSummaries
import net.thunderbird.feature.taskmail.internal.domain.usecase.ObserveTaskMailStoreChanges
import net.thunderbird.feature.taskmail.internal.domain.usecase.RefreshTaskMail
import net.thunderbird.feature.taskmail.internal.domain.usecase.SyncTaskMailCache
import net.thunderbird.feature.taskmail.internal.preview.TaskMailPreviewData
import net.thunderbird.feature.taskmail.internal.sync.DEFAULT_MESSAGE_SYNC_SCOPE_KEY
import net.thunderbird.feature.taskmail.internal.sync.DEFAULT_MESSAGE_SYNC_SOURCE
import net.thunderbird.feature.taskmail.internal.sync.MessageSyncCoordinator
import net.thunderbird.feature.taskmail.internal.sync.MessageSyncRequest

class TaskWorkspaceViewModelTest {

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
    fun `load data should emit content state when repository returns data`() = runMviTest {
        val syncRequester = FakeTaskMailSyncRequester()

        with(TaskWorkspaceViewModelRobot(this, FakeTaskMailRepository(), syncRequester = syncRequester)) {
            start()
            loadData()
            assertLoadedContent()
            assertThat(syncRequester.requestCount).isEqualTo(0)
            ensureThatAllEventsAreConsumed()
        }
    }

    @Test
    fun `load data should emit empty state when repository returns empty list`() = runMviTest {
        with(TaskWorkspaceViewModelRobot(this, FakeTaskMailRepository(workspaces = emptyList()))) {
            start()
            loadData()
            assertEmptyState()
            ensureThatAllEventsAreConsumed()
        }
    }

    @Test
    fun `load data should show only the last workdir segment in workspace subtitle`() = runMviTest {
        with(TaskWorkspaceViewModelRobot(this, FakeTaskMailRepository())) {
            start()
            loadData()
            assertThat(viewModelState().workspaces.single().subtitle).isEqualTo("taskmail")
            ensureThatAllEventsAreConsumed()
        }
    }

    @Test
    fun `load data should emit error state when repository throws`() = runMviTest {
        with(TaskWorkspaceViewModelRobot(this, FakeTaskMailRepository(shouldThrowWorkspaceError = true))) {
            start()
            loadData()
            assertErrorState()
            ensureThatAllEventsAreConsumed()
        }
    }

    @Test
    fun `session clicked should emit open detail effect`() = runMviTest {
        with(TaskWorkspaceViewModelRobot(this, FakeTaskMailRepository())) {
            start()
            clickSession()
            assertOpenDetailEffect()
            ensureThatAllEventsAreConsumed()
        }
    }

    @Test
    fun `project list clicked should emit open project sync effect`() = runMviTest {
        with(TaskWorkspaceViewModelRobot(this, FakeTaskMailRepository())) {
            start()
            clickProjectList()
            assertOpenProjectSyncEffect()
            ensureThatAllEventsAreConsumed()
        }
    }

    @Test
    fun `new task clicked should emit open new task effect`() = runMviTest {
        with(TaskWorkspaceViewModelRobot(this, FakeTaskMailRepository())) {
            start()
            clickNewTask()
            assertOpenNewTaskEffect()
            ensureThatAllEventsAreConsumed()
        }
    }

    @Test
    fun `refresh requested should trigger sync and reload workspace data`() = runMviTest {
        val repository = FakeTaskMailRepository()
        val syncRequester = FakeTaskMailSyncRequester()

        with(TaskWorkspaceViewModelRobot(this, repository, syncRequester = syncRequester)) {
            start()
            loadData()
            repository.workspaces = emptyList()
            refreshRequested()
            assertThat(syncRequester.requestCount).isEqualTo(1)
            assertThat(viewModelState().workspaces).hasSize(0)
            assertThat(viewModelState().refreshError).isEqualTo(null)
            ensureThatAllEventsAreConsumed()
        }
    }

    @Test
    fun `refresh failure should keep stale content and expose refresh error`() = runMviTest {
        val repository = FakeTaskMailRepository()
        val syncRequester = FakeTaskMailSyncRequester(
            result = Result.failure(IllegalStateException("sync error")),
        )

        with(TaskWorkspaceViewModelRobot(this, repository, syncRequester = syncRequester)) {
            start()
            loadData()
            refreshRequested()
            assertLoadedContent()
            assertThat(viewModelState().refreshError).isEqualTo("sync error")
            ensureThatAllEventsAreConsumed()
        }
    }

    @Test
    fun `load data should show cached workspaces before background cache sync completes`() = runMviTest {
        val cacheSyncGate = CompletableDeferred<Unit>()

        with(
            TaskWorkspaceViewModelRobot(
                mviContext = this,
                repository = FakeTaskMailRepository(),
                syncTaskMailCache = createBlockingSyncTaskMailCache(cacheSyncGate),
            ),
        ) {
            start()
            loadData()
            assertLoadedContent()
            assertThat(viewModelState().isLoading).isEqualTo(false)
            assertThat(viewModelState().isRefreshing).isEqualTo(true)

            cacheSyncGate.complete(Unit)
            advanceUntilIdle()

            assertLoadedContent()
            assertThat(viewModelState().isRefreshing).isEqualTo(false)
            ensureThatAllEventsAreConsumed()
        }
    }

    @Test
    fun `local mail change should reload workspaces after initial load`() = runMviTest {
        val repository = FakeTaskMailRepository()
        val changeObserver = FakeTaskMailStoreChangeObserver()

        with(TaskWorkspaceViewModelRobot(this, repository, changeObserver = changeObserver)) {
            start()
            loadData()
            repository.workspaces = emptyList()
            emitLocalChange()
            assertThat(viewModelState().workspaces).hasSize(0)
            ensureThatAllEventsAreConsumed()
        }
    }

    @Test
    fun `foreground refresh should sync the single resolved sender account while visible`() = runMviTest {
        val syncRequester = FakeTaskMailSyncRequester()
        val tickerFactory = FakeTaskMailForegroundRefreshTickerFactory()

        with(
            TaskWorkspaceViewModelRobot(
                mviContext = this,
                repository = FakeTaskMailRepository(),
                syncRequester = syncRequester,
                foregroundRefreshTickerFactory = tickerFactory,
            ),
        ) {
            start()
            loadData()
            startForegroundRefresh()
            emitForegroundRefreshTick()
            stopForegroundRefresh()
            emitForegroundRefreshTick()
            assertThat(syncRequester.requestedAccountUuids).isEqualTo(
                listOf("account_001", "account_001"),
            )
            ensureThatAllEventsAreConsumed()
        }
    }

    @Test
    fun `foreground refresh should reload current workspace summaries when returning visible`() = runMviTest {
        val repository = FakeTaskMailRepository()
        val syncRequester = FakeTaskMailSyncRequester()

        with(
            TaskWorkspaceViewModelRobot(
                mviContext = this,
                repository = repository,
                syncRequester = syncRequester,
            ),
        ) {
            start()
            loadData()
            repository.workspaces = emptyList()

            startForegroundRefresh()

            assertThat(viewModelState().workspaces).hasSize(0)
            assertThat(syncRequester.requestedAccountUuids).isEqualTo(listOf("account_001"))
            ensureThatAllEventsAreConsumed()
        }
    }

    @Test
    fun `foreground refresh should stay disabled when sender account resolution is ambiguous`() = runMviTest {
        val syncRequester = FakeTaskMailSyncRequester()
        val tickerFactory = FakeTaskMailForegroundRefreshTickerFactory()

        with(
            TaskWorkspaceViewModelRobot(
                mviContext = this,
                repository = FakeTaskMailRepository(),
                syncRequester = syncRequester,
                senderAccounts = listOf(
                    sampleSenderAccount(accountUuid = "account_001"),
                    sampleSenderAccount(accountUuid = "account_002"),
                ),
                foregroundRefreshTickerFactory = tickerFactory,
            ),
        ) {
            start()
            loadData()
            startForegroundRefresh()
            emitForegroundRefreshTick()
            assertThat(syncRequester.requestedAccountUuids).isEqualTo(emptyList())
            ensureThatAllEventsAreConsumed()
        }
    }
}

private class TaskWorkspaceViewModelRobot(
    private val mviContext: MviContext,
    repository: TaskMailRepository,
    syncRequester: FakeTaskMailSyncRequester = FakeTaskMailSyncRequester(),
    senderAccounts: List<TaskMailSenderAccount> = listOf(sampleSenderAccount()),
    private val changeObserver: FakeTaskMailStoreChangeObserver = FakeTaskMailStoreChangeObserver(),
    private val foregroundRefreshTickerFactory: FakeTaskMailForegroundRefreshTickerFactory =
        FakeTaskMailForegroundRefreshTickerFactory(),
    syncTaskMailCache: SyncTaskMailCache? = null,
) {
    private val viewModel = TaskWorkspaceViewModel(
        repository = repository,
        getTaskWorkspaceSummaries = GetTaskWorkspaceSummaries(repository),
        getTaskMailSenderAccounts = GetTaskMailSenderAccounts(FakeTaskMailSenderAccountSource(senderAccounts)),
        refreshTaskMail = RefreshTaskMail(syncRequester),
        observeTaskMailStoreChanges = ObserveTaskMailStoreChanges(changeObserver),
        foregroundRefreshTickerFactory = foregroundRefreshTickerFactory,
        syncTaskMailCache = syncTaskMailCache,
    )
    private lateinit var turbines: MviTurbines<TaskWorkspaceContract.State, TaskWorkspaceContract.Effect>

    suspend fun start() {
        turbines = mviContext.turbinesWithInitialStateCheck(viewModel, TaskWorkspaceContract.State())
    }

    suspend fun loadData() {
        viewModel.event(TaskWorkspaceContract.Event.LoadData)
        mviContext.advanceUntilIdle()
    }

    suspend fun refreshRequested() {
        viewModel.event(TaskWorkspaceContract.Event.RefreshRequested)
        mviContext.advanceUntilIdle()
    }

    suspend fun emitLocalChange() {
        changeObserver.emitChange()
        mviContext.advanceUntilIdle()
    }

    suspend fun startForegroundRefresh() {
        viewModel.event(TaskWorkspaceContract.Event.ForegroundRefreshStarted)
        mviContext.advanceUntilIdle()
    }

    suspend fun stopForegroundRefresh() {
        viewModel.event(TaskWorkspaceContract.Event.ForegroundRefreshStopped)
        mviContext.advanceUntilIdle()
    }

    suspend fun emitForegroundRefreshTick() {
        foregroundRefreshTickerFactory.emitTick()
        mviContext.advanceUntilIdle()
    }

    suspend fun assertLoadedContent() {
        val state = viewModel.state.value
        assertThat(state.isLoading).isEqualTo(false)
        assertThat(state.error).isEqualTo(null)
        assertThat(state.workspaces).hasSize(1)
        assertThat(state.workspaces.first().sessions).hasSize(1)
        assertThat(state.workspaces.first().sessions.first().workspaceId).isEqualTo("workspace_001")
    }

    suspend fun assertEmptyState() {
        val state = viewModel.state.value
        assertThat(state.isLoading).isEqualTo(false)
        assertThat(state.isEmpty).isEqualTo(true)
    }

    suspend fun assertErrorState() {
        val state = viewModel.state.value
        assertThat(state.isLoading).isEqualTo(false)
        assertThat(state.error).isEqualTo("Failed to load TaskMail workspaces.")
    }

    fun clickSession() {
        viewModel.event(
            TaskWorkspaceContract.Event.SessionClicked(
                workspaceId = "workspace_001",
                sessionId = "session_001",
                threadId = "thread_001",
            ),
        )
    }

    fun clickProjectList() {
        viewModel.event(TaskWorkspaceContract.Event.ProjectListClicked)
    }

    fun clickNewTask() {
        viewModel.event(TaskWorkspaceContract.Event.NewTaskClicked)
    }

    suspend fun assertOpenDetailEffect() {
        assertThat(turbines.awaitEffectItem()).isEqualTo(
            TaskWorkspaceContract.Effect.OpenSessionDetail(
                workspaceId = "workspace_001",
                sessionId = "session_001",
                threadId = "thread_001",
            ),
        )
    }

    suspend fun assertOpenProjectSyncEffect() {
        assertThat(turbines.awaitEffectItem()).isEqualTo(TaskWorkspaceContract.Effect.OpenProjectSync)
    }

    suspend fun assertOpenNewTaskEffect() {
        assertThat(turbines.awaitEffectItem()).isEqualTo(TaskWorkspaceContract.Effect.OpenNewTask)
    }

    suspend fun ensureThatAllEventsAreConsumed() {
        turbines.stateTurbine.cancelAndIgnoreRemainingEvents()
        turbines.effectTurbine.ensureAllEventsConsumed()
    }

    fun viewModelState(): TaskWorkspaceContract.State = viewModel.state.value
}

private class FakeTaskMailRepository(
    var workspaces: List<TaskWorkspaceSummary> = TaskMailPreviewData.workspaceSummaries,
    private val shouldThrowWorkspaceError: Boolean = false,
) : TaskMailRepository {
    override suspend fun getTaskWorkspaceSummaries(): List<TaskWorkspaceSummary> {
        if (shouldThrowWorkspaceError) error("workspace error")
        return workspaces
    }

    override suspend fun getTaskSessionDetail(key: TaskSessionKey): TaskSessionDetail? = null
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

private class FakeTaskMailSenderAccountSource(
    private val accounts: List<TaskMailSenderAccount>,
) : TaskMailSenderAccountSource {
    override fun getSenderAccounts(): List<TaskMailSenderAccount> = accounts

    override fun getAccount(accountUuid: String) = error("Not used by this test")
}

private class FakeTaskMailForegroundRefreshTickerFactory : TaskMailForegroundRefreshTickerFactory {
    private val ticks = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    override fun createTicker(intervalMs: Long): Flow<Unit> = ticks

    suspend fun emitTick() {
        ticks.emit(Unit)
    }
}

private fun sampleSenderAccount(accountUuid: String = "account_001"): TaskMailSenderAccount {
    return TaskMailSenderAccount(
        accountUuid = accountUuid,
        displayName = "TaskMail User",
        emailAddress = "taskmail@example.com",
    )
}

@OptIn(ExperimentalCoroutinesApi::class)
private fun createBlockingSyncTaskMailCache(syncGate: CompletableDeferred<Unit>): SyncTaskMailCache {
    return SyncTaskMailCache(
        unifiedMessageRepository = WorkspaceTestUnifiedMessageRepository(),
        messageSyncStateRepository = WorkspaceTestMessageSyncStateRepository(
            MessageSyncState(
                source = DEFAULT_MESSAGE_SYNC_SOURCE,
                scopeKey = DEFAULT_MESSAGE_SYNC_SCOPE_KEY,
                lastCursor = "cursor-1",
                lastSyncAt = 123L,
            ),
        ),
        syncCoordinator = BlockingMessageSyncCoordinator(syncGate),
        taskMailMessageJsonCodec = TaskMailMessageJsonCodec(),
        sessionProjector = TaskMailSessionProjector(),
        taskSessionDetailRepository = WorkspaceTestTaskSessionDetailRepository(),
        ioDispatcher = UnconfinedTestDispatcher(),
    )
}

private class BlockingMessageSyncCoordinator(
    private val syncGate: CompletableDeferred<Unit>,
) : MessageSyncCoordinator {
    override suspend fun sync(request: MessageSyncRequest): Result<Unit> {
        syncGate.await()
        return Result.success(Unit)
    }
}

private class WorkspaceTestUnifiedMessageRepository : UnifiedMessageRepository {
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

private class WorkspaceTestMessageSyncStateRepository(
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

private class WorkspaceTestTaskSessionDetailRepository : TaskSessionDetailRepository {
    override suspend fun getTaskSessionDetail(key: TaskSessionKey): TaskSessionDetail? = null

    override suspend fun getTaskSessionDetails(): List<TaskSessionDetail> = emptyList()

    override suspend fun replaceAllSessionDetails(details: List<TaskSessionDetail>) = Unit

    override suspend fun upsertSessionDetails(details: List<TaskSessionDetail>) = Unit

    override suspend fun removeSessionDetails(keys: List<TaskSessionKey>) = Unit
}
