package net.thunderbird.feature.taskmail.internal.ui.projectsync

import app.k9mail.core.ui.compose.testing.mvi.MviContext
import app.k9mail.core.ui.compose.testing.mvi.MviTurbines
import app.k9mail.core.ui.compose.testing.mvi.advanceUntilIdle
import app.k9mail.core.ui.compose.testing.mvi.runMviTest
import app.k9mail.core.ui.compose.testing.mvi.turbinesWithInitialStateCheck
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEqualTo
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import net.thunderbird.core.testing.coroutines.MainDispatcherHelper
import net.thunderbird.feature.taskmail.internal.data.TaskMailSenderAccountSource
import net.thunderbird.feature.taskmail.internal.data.TaskMailStoreChangeObserver
import net.thunderbird.feature.taskmail.internal.data.TaskMailSyncRequester
import net.thunderbird.feature.taskmail.internal.domain.model.TaskMailProjectSyncProject
import net.thunderbird.feature.taskmail.internal.domain.model.TaskMailProjectSyncResult
import net.thunderbird.feature.taskmail.internal.domain.model.TaskMailProjectSyncRoot
import net.thunderbird.feature.taskmail.internal.domain.model.TaskMailSenderAccount
import net.thunderbird.feature.taskmail.internal.domain.repository.TaskMailProjectSyncRepository
import net.thunderbird.feature.taskmail.internal.domain.usecase.GetLatestTaskMailProjectSyncResult
import net.thunderbird.feature.taskmail.internal.domain.usecase.GetTaskMailSenderAccounts
import net.thunderbird.feature.taskmail.internal.domain.usecase.ObserveTaskMailStoreChanges
import net.thunderbird.feature.taskmail.internal.domain.usecase.RefreshTaskMail
import net.thunderbird.feature.taskmail.internal.domain.usecase.RequestTaskMailProjectSync

class TaskProjectSyncViewModelTest {

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
    fun `load data should auto select the only sender account and load latest result`() = runMviTest {
        with(TaskProjectSyncViewModelRobot(this, senderAccounts = listOf(primarySenderAccount))) {
            start()
            loadData()

            assertThat(viewModelState().selectedSenderAccountId).isEqualTo(primarySenderAccount.accountUuid)
            assertThat(viewModelState().latestResult?.roots?.single()?.projects?.map { it.repoPath }).isEqualTo(
                listOf("E:/projects/android_task_manager"),
            )
            ensureThatAllEventsAreConsumed()
        }
    }

    @Test
    fun `load data should block when no sender account exists`() = runMviTest {
        with(TaskProjectSyncViewModelRobot(this, senderAccounts = emptyList())) {
            start()
            loadData()

            assertThat(viewModelState().hasBlockingState).isEqualTo(true)
            assertThat(viewModelState().senderAccountBlockingError).isEqualTo(
                "Set up a mailbox account before requesting the TaskMail project list.",
            )
            assertThat(viewModelState().selectedSenderAccountId).isEqualTo(null)
            assertThat(viewModelState().latestResult).isEqualTo(null)
            ensureThatAllEventsAreConsumed()
        }
    }

    @Test
    fun `load data should block when multiple sender accounts exist`() = runMviTest {
        with(
            TaskProjectSyncViewModelRobot(
                this,
                senderAccounts = listOf(primarySenderAccount, secondarySenderAccount),
            ),
        ) {
            start()
            loadData()
            requestSync()

            assertThat(viewModelState().hasBlockingState).isEqualTo(true)
            assertThat(viewModelState().senderAccountBlockingError).isEqualTo(
                "Android currently supports project sync with exactly one mailbox account. " +
                    "Remove extra accounts before loading the project list.",
            )
            assertThat(viewModelState().selectedSenderAccountId).isEqualTo(null)
            assertThat(requestedSyncAccounts()).isEqualTo(emptyList())
            ensureThatAllEventsAreConsumed()
        }
    }

    @Test
    fun `sync requested should send request refresh mailbox and show message`() = runMviTest {
        val syncRequester = FakeTaskMailSyncRequester()

        with(
            TaskProjectSyncViewModelRobot(
                this,
                senderAccounts = listOf(primarySenderAccount),
                syncRequester = syncRequester,
            ),
        ) {
            start()
            loadData()
            requestSync()

            assertThat(requestedSyncAccounts()).containsExactly(primarySenderAccount.accountUuid)
            assertThat(syncRequester.requestCount).isEqualTo(1)
            assertThat(viewModelState().pendingSyncRequestStartedAt).isEqualTo(2_000L)
            assertThat(viewModelState().isAwaitingFreshResult).isEqualTo(true)
            assertThat(viewModelState().canRequestSync).isEqualTo(false)
            assertThat(viewModelState().canRetryWithMail).isEqualTo(false)
            assertThat(awaitEffect()).isEqualTo(
                TaskProjectSyncContract.Effect.ShowMessage(
                    "Project list sync requested. The repo list updates when the [SYNC] reply arrives.",
                ),
            )
            ensureThatAllEventsAreConsumed()
        }
    }

    @Test
    fun `sync requested should offer mail retry after follow-up delay when latest result is still stale`() =
        runMviTest {
        val repository = FakeTaskMailProjectSyncRepository(
            latestResult = sampleProjectSyncResult,
            requestResult = Result.success(Unit),
        )
        val syncRequester = FakeTaskMailSyncRequester(result = Result.success(Unit))

        with(
            TaskProjectSyncViewModelRobot(
                mviContext = this,
                senderAccounts = listOf(primarySenderAccount),
                overrideRepository = repository,
                syncRequester = syncRequester,
                enablePostSyncFollowUpRefresh = true,
                postSyncFollowUpDelaysMillis = listOf(0L),
                currentTimeProvider = { 1_000L },
            ),
        ) {
            start()
            loadData()
            requestSync()

            assertThat(syncRequester.requestCount).isEqualTo(2)
            assertThat(viewModelState().latestResult).isEqualTo(sampleProjectSyncResult)
            assertThat(viewModelState().isAwaitingFreshResult).isEqualTo(true)
            assertThat(viewModelState().canRetryWithMail).isEqualTo(true)
            assertThat(awaitEffect()).isEqualTo(
                TaskProjectSyncContract.Effect.ShowMessage(
                    "Project list sync requested. The repo list updates when the [SYNC] reply arrives.",
                ),
            )
            ensureThatAllEventsAreConsumed()
        }
    }

    @Test
    fun `sync requested should run a delayed follow-up refresh when latest result is still stale`() = runMviTest {
        val repository = FakeTaskMailProjectSyncRepository(
            latestResult = sampleProjectSyncResult,
            requestResult = Result.success(Unit),
        )
        val syncRequester = FakeTaskMailSyncRequester(
            result = Result.success(Unit),
            onRequestSync = { requestCount ->
                if (requestCount == 2) {
                    repository.latestResult = sampleFollowUpProjectSyncResult
                }
            },
        )

        with(
            TaskProjectSyncViewModelRobot(
                mviContext = this,
                senderAccounts = listOf(primarySenderAccount),
                overrideRepository = repository,
                syncRequester = syncRequester,
                enablePostSyncFollowUpRefresh = true,
                postSyncFollowUpDelaysMillis = listOf(0L),
                currentTimeProvider = { 1_000L },
            ),
        ) {
            start()
            loadData()
            requestSync()

            assertThat(syncRequester.requestCount).isEqualTo(2)
            assertThat(viewModelState().latestResult).isEqualTo(sampleFollowUpProjectSyncResult)
            assertThat(viewModelState().pendingSyncRequestStartedAt).isEqualTo(null)
            assertThat(viewModelState().isAwaitingFreshResult).isEqualTo(false)
            assertThat(awaitEffect()).isEqualTo(
                TaskProjectSyncContract.Effect.ShowMessage(
                    "Project list sync requested. The repo list updates when the [SYNC] reply arrives.",
                ),
            )
            ensureThatAllEventsAreConsumed()
        }
    }

    @Test
    fun `mail retry requested should send canonical sync mail request`() = runMviTest {
        val repository = FakeTaskMailProjectSyncRepository(
            latestResult = sampleProjectSyncResult,
            requestResult = Result.success(Unit),
            mailRetryResult = Result.success(Unit),
        )
        val syncRequester = FakeTaskMailSyncRequester(result = Result.success(Unit))

        with(
            TaskProjectSyncViewModelRobot(
                mviContext = this,
                senderAccounts = listOf(primarySenderAccount),
                overrideRepository = repository,
                syncRequester = syncRequester,
                enablePostSyncFollowUpRefresh = true,
                postSyncFollowUpDelaysMillis = listOf(0L),
                currentTimeProvider = { 1_000L },
            ),
        ) {
            start()
            loadData()
            requestSync()
            assertThat(awaitEffect()).isEqualTo(
                TaskProjectSyncContract.Effect.ShowMessage(
                    "Project list sync requested. The repo list updates when the [SYNC] reply arrives.",
                ),
            )

            requestMailRetry()

            assertThat(requestedMailRetryAccounts()).containsExactly(primarySenderAccount.accountUuid)
            assertThat(viewModelState().isAwaitingFreshResult).isEqualTo(true)
            assertThat(viewModelState().canRetryWithMail).isEqualTo(false)
            assertThat(awaitEffect()).isEqualTo(
                TaskProjectSyncContract.Effect.ShowMessage(
                    "Compatibility mail retry requested. The repo list updates when the next [SYNC] reply arrives.",
                ),
            )
            ensureThatAllEventsAreConsumed()
        }
    }

    @Test
    fun `mail store change with a fresh result should clear awaiting state`() = runMviTest {
        val repository = FakeTaskMailProjectSyncRepository(
            latestResult = sampleProjectSyncResult,
            requestResult = Result.success(Unit),
        )
        val changeObserver = FakeTaskMailStoreChangeObserver()

        with(
            TaskProjectSyncViewModelRobot(
                mviContext = this,
                senderAccounts = listOf(primarySenderAccount),
                overrideRepository = repository,
                changeObserver = changeObserver,
                currentTimeProvider = { 1_000L },
            ),
        ) {
            start()
            loadData()
            requestSync()

            assertThat(viewModelState().isAwaitingFreshResult).isEqualTo(true)

            repository.latestResult = sampleFollowUpProjectSyncResult
            emitStoreChange()

            assertThat(viewModelState().latestResult).isEqualTo(sampleFollowUpProjectSyncResult)
            assertThat(viewModelState().pendingSyncRequestStartedAt).isEqualTo(null)
            assertThat(viewModelState().isAwaitingFreshResult).isEqualTo(false)
            assertThat(awaitEffect()).isEqualTo(
                TaskProjectSyncContract.Effect.ShowMessage(
                    "Project list sync requested. The repo list updates when the [SYNC] reply arrives.",
                ),
            )
            ensureThatAllEventsAreConsumed()
        }
    }

    @Test
    fun `use repo clicked should emit return repo effect`() = runMviTest {
        with(TaskProjectSyncViewModelRobot(this, senderAccounts = listOf(primarySenderAccount))) {
            start()
            useRepo("E:/projects/android_task_manager")

            assertThat(awaitEffect()).isEqualTo(
                TaskProjectSyncContract.Effect.ReturnRepo("E:/projects/android_task_manager"),
            )
            ensureThatAllEventsAreConsumed()
        }
    }
}

private class TaskProjectSyncViewModelRobot(
    private val mviContext: MviContext,
    senderAccounts: List<TaskMailSenderAccount>,
    projectSyncResult: TaskMailProjectSyncResult = sampleProjectSyncResult,
    requestResult: Result<Unit> = Result.success(Unit),
    overrideRepository: FakeTaskMailProjectSyncRepository? = null,
    syncRequester: FakeTaskMailSyncRequester = FakeTaskMailSyncRequester(),
    changeObserver: FakeTaskMailStoreChangeObserver = FakeTaskMailStoreChangeObserver(),
    enablePostSyncFollowUpRefresh: Boolean = false,
    postSyncFollowUpDelaysMillis: List<Long> = listOf(30_000L, 60_000L),
    currentTimeProvider: () -> Long = { 2_000L },
) {
    private val senderAccountSource = FakeProjectSyncSenderAccountSource(senderAccounts)
    private val repository = overrideRepository ?: FakeTaskMailProjectSyncRepository(
        latestResult = projectSyncResult,
        requestResult = requestResult,
    )
    private val storeChangeObserver = changeObserver
    private val viewModel = TaskProjectSyncViewModel(
        getTaskMailSenderAccounts = GetTaskMailSenderAccounts(senderAccountSource),
        getLatestTaskMailProjectSyncResult = GetLatestTaskMailProjectSyncResult(repository),
        requestTaskMailProjectSync = RequestTaskMailProjectSync(repository),
        refreshTaskMail = RefreshTaskMail(syncRequester),
        observeTaskMailStoreChanges = ObserveTaskMailStoreChanges(storeChangeObserver),
        enablePostSyncFollowUpRefresh = enablePostSyncFollowUpRefresh,
        postSyncFollowUpDelaysMillis = postSyncFollowUpDelaysMillis,
        currentTimeProvider = currentTimeProvider,
    )
    private lateinit var turbines: MviTurbines<TaskProjectSyncContract.State, TaskProjectSyncContract.Effect>

    suspend fun start() {
        turbines = mviContext.turbinesWithInitialStateCheck(viewModel, TaskProjectSyncContract.State())
    }

    suspend fun loadData() {
        viewModel.event(TaskProjectSyncContract.Event.LoadData)
        mviContext.advanceUntilIdle()
    }

    suspend fun requestSync() {
        viewModel.event(TaskProjectSyncContract.Event.SyncRequested)
        mviContext.advanceUntilIdle()
    }

    suspend fun requestMailRetry() {
        viewModel.event(TaskProjectSyncContract.Event.MailRetryRequested)
        mviContext.advanceUntilIdle()
    }

    suspend fun emitStoreChange() {
        storeChangeObserver.emitChange()
        mviContext.advanceUntilIdle()
    }

    fun useRepo(repoPath: String) {
        viewModel.event(TaskProjectSyncContract.Event.UseRepoClicked(repoPath))
    }

    fun requestedSyncAccounts(): List<String> = repository.requestedAccounts

    fun requestedMailRetryAccounts(): List<String> = repository.requestedMailRetryAccounts

    suspend fun awaitEffect(): TaskProjectSyncContract.Effect {
        return turbines.awaitEffectItem()
    }

    fun viewModelState(): TaskProjectSyncContract.State = viewModel.state.value

    suspend fun ensureThatAllEventsAreConsumed() {
        turbines.stateTurbine.cancelAndIgnoreRemainingEvents()
        turbines.effectTurbine.ensureAllEventsConsumed()
    }
}

private class FakeProjectSyncSenderAccountSource(
    private val senderAccounts: List<TaskMailSenderAccount>,
) : TaskMailSenderAccountSource {
    override fun getSenderAccounts(): List<TaskMailSenderAccount> = senderAccounts

    override fun getAccount(accountUuid: String): net.thunderbird.core.android.account.LegacyAccountDto? = null
}

private class FakeTaskMailProjectSyncRepository(
    var latestResult: TaskMailProjectSyncResult?,
    private val requestResult: Result<Unit>,
    private val mailRetryResult: Result<Unit> = Result.success(Unit),
) : TaskMailProjectSyncRepository {
    val requestedAccounts = mutableListOf<String>()
    val requestedMailRetryAccounts = mutableListOf<String>()

    override suspend fun getLatestResult(accountUuid: String): TaskMailProjectSyncResult? = latestResult

    override suspend fun requestSync(accountUuid: String): Result<Unit> {
        requestedAccounts += accountUuid
        return requestResult
    }

    override suspend fun requestSyncViaMail(accountUuid: String): Result<Unit> {
        requestedMailRetryAccounts += accountUuid
        return mailRetryResult
    }
}

private class FakeTaskMailSyncRequester(
    private val result: Result<Unit> = Result.success(Unit),
    private val onRequestSync: (Int) -> Unit = {},
) : TaskMailSyncRequester {
    var requestCount: Int = 0

    override suspend fun requestSync(): Result<Unit> {
        requestCount += 1
        onRequestSync(requestCount)
        return result
    }
}

private class FakeTaskMailStoreChangeObserver : TaskMailStoreChangeObserver {
    private val changes = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    override fun changes(): Flow<Unit> = changes

    fun emitChange() {
        changes.tryEmit(Unit)
    }
}

private val sampleProjectSyncResult = TaskMailProjectSyncResult(
    receivedAt = 123L,
    scannedAt = "2026-03-17T12:34:56",
    roots = listOf(
        TaskMailProjectSyncRoot(
            rootPath = "E:/projects",
            isAvailable = true,
            folderCount = 1,
            unavailableReason = null,
            projects = listOf(
                TaskMailProjectSyncProject(
                    displayName = "android_task_manager",
                    repoPath = "E:/projects/android_task_manager",
                ),
            ),
        ),
    ),
)

private val sampleFollowUpProjectSyncResult = TaskMailProjectSyncResult(
    receivedAt = 5_000L,
    scannedAt = "2026-03-23T15:06:30",
    roots = listOf(
        TaskMailProjectSyncRoot(
            rootPath = "E:/projects",
            isAvailable = true,
            folderCount = 1,
            unavailableReason = null,
            projects = listOf(
                TaskMailProjectSyncProject(
                    displayName = "mail_based_task_manager",
                    repoPath = "E:/projects/mail_based_task_manager",
                ),
            ),
        ),
    ),
)

private val primarySenderAccount = TaskMailSenderAccount(
    accountUuid = "account_primary",
    displayName = "Primary",
    emailAddress = "primary@example.com",
)

private val secondarySenderAccount = TaskMailSenderAccount(
    accountUuid = "account_secondary",
    displayName = "Secondary",
    emailAddress = "secondary@example.com",
)
