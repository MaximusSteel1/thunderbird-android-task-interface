package net.thunderbird.feature.taskmail.internal.ui.newtask

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
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import net.thunderbird.core.android.account.LegacyAccountDto
import net.thunderbird.core.testing.coroutines.MainDispatcherHelper
import net.thunderbird.feature.taskmail.internal.data.TaskMailSenderAccountSource
import net.thunderbird.feature.taskmail.internal.domain.model.TaskMailBackend
import net.thunderbird.feature.taskmail.internal.domain.model.TaskMailSenderAccount
import net.thunderbird.feature.taskmail.internal.domain.newtask.TaskMailNewTaskRequest
import net.thunderbird.feature.taskmail.internal.domain.newtask.TaskMailNewTaskResult
import net.thunderbird.feature.taskmail.internal.domain.newtask.TaskMailNewTaskSender
import net.thunderbird.feature.taskmail.internal.domain.usecase.GetTaskMailSenderAccounts
import net.thunderbird.feature.taskmail.internal.domain.usecase.SendTaskMailNewTask

class TaskNewTaskViewModelTest {

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
    fun `load data should block when no sender accounts exist`() = runMviTest {
        with(TaskNewTaskViewModelRobot(this, senderAccounts = emptyList())) {
            start()
            loadData()
            assertThat(viewModelState().senderAccountBlockingError).isEqualTo(
                "Set up a mailbox account before sending TaskMail requests.",
            )
            assertThat(viewModelState().hasBlockingState).isEqualTo(true)
            ensureThatAllEventsAreConsumed()
        }
    }

    @Test
    fun `load data should auto select the only sender account`() = runMviTest {
        with(TaskNewTaskViewModelRobot(this, senderAccounts = listOf(primarySenderAccount))) {
            start()
            loadData()
            assertThat(viewModelState().selectedSenderAccountId).isEqualTo(primarySenderAccount.accountUuid)
            assertThat(viewModelState().requiresSenderAccountSelection).isEqualTo(false)
            ensureThatAllEventsAreConsumed()
        }
    }

    @Test
    fun `load data should leave sender account unselected when multiple accounts exist`() = runMviTest {
        with(
            TaskNewTaskViewModelRobot(
                this,
                senderAccounts = listOf(primarySenderAccount, secondarySenderAccount),
            ),
        ) {
            start()
            loadData()
            assertThat(viewModelState().selectedSenderAccountId).isEqualTo(null)
            assertThat(viewModelState().requiresSenderAccountSelection).isEqualTo(true)
            ensureThatAllEventsAreConsumed()
        }
    }

    @Test
    fun `task changes should derive title until user edits it`() = runMviTest {
        with(TaskNewTaskViewModelRobot(this, senderAccounts = listOf(primarySenderAccount))) {
            start()
            loadData()
            changeTask("Audit the new flow\nList only blockers.")
            assertThat(viewModelState().subjectTitle).isEqualTo("Audit the new flow")
            changeSubjectTitle("Custom title")
            changeTask("Different first line\nStill keep custom title.")
            assertThat(viewModelState().subjectTitle).isEqualTo("Custom title")
            ensureThatAllEventsAreConsumed()
        }
    }

    @Test
    fun `task changes should derive title from the first non-empty line`() = runMviTest {
        with(TaskNewTaskViewModelRobot(this, senderAccounts = listOf(primarySenderAccount))) {
            start()
            loadData()
            changeTask("\n   \nAudit the new flow\nList only blockers.")
            assertThat(viewModelState().subjectTitle).isEqualTo("Audit the new flow")
            ensureThatAllEventsAreConsumed()
        }
    }

    @Test
    fun `choose repo clicked should emit open project sync effect`() = runMviTest {
        with(TaskNewTaskViewModelRobot(this, senderAccounts = listOf(primarySenderAccount))) {
            start()
            clickChooseRepo()
            assertThat(awaitEffect()).isEqualTo(TaskNewTaskContract.Effect.OpenProjectSync)
            ensureThatAllEventsAreConsumed()
        }
    }

    @Test
    fun `send should require explicit sender account selection when multiple accounts exist`() = runMviTest {
        with(
            TaskNewTaskViewModelRobot(
                this,
                senderAccounts = listOf(primarySenderAccount, secondarySenderAccount),
            ),
        ) {
            start()
            loadData()
            selectBackend(TaskMailBackend.Codex)
            changeRepo("E:/projects/android_task_manager")
            changeTask("Audit the new flow")
            send()
            assertThat(viewModelState().senderAccountError).isEqualTo("Select the sending account.")
            assertThat(sentRequests()).isEqualTo(emptyList())
            ensureThatAllEventsAreConsumed()
        }
    }

    @Test
    fun `send should surface local validation errors and keep advanced state`() = runMviTest {
        with(TaskNewTaskViewModelRobot(this, senderAccounts = listOf(primarySenderAccount))) {
            start()
            loadData()
            toggleAdvanced()
            changeTimeout("0")
            send()
            assertThat(viewModelState().backendError).isEqualTo("Select a backend.")
            assertThat(viewModelState().repoError).isEqualTo("Repo is required.")
            assertThat(viewModelState().taskError).isEqualTo("Task details are required.")
            assertThat(viewModelState().titleError).isEqualTo("Title is required.")
            assertThat(viewModelState().timeoutError).isEqualTo("Timeout must be a positive integer.")
            assertThat(viewModelState().timeoutText).isEqualTo("0")
            assertThat(viewModelState().isAdvancedExpanded).isEqualTo(true)
            assertThat(sentRequests()).isEqualTo(emptyList())
            ensureThatAllEventsAreConsumed()
        }
    }

    @Test
    fun `send should serialize canonical subject and body on success`() = runMviTest {
        with(TaskNewTaskViewModelRobot(this, senderAccounts = listOf(primarySenderAccount))) {
            start()
            loadData()
            selectBackend(TaskMailBackend.Codex)
            changeRepo("E:/projects/android_task_manager")
            changeTask("Audit the new flow\nList only blockers.")
            send()

            assertThat(sentRequests()).containsExactly(
                TaskMailNewTaskRequest(
                    accountUuid = primarySenderAccount.accountUuid,
                    subject = "[CX] Audit the new flow",
                    body = """
                        Repo: E:/projects/android_task_manager

                        Task:
                        Audit the new flow
                        List only blockers.
                    """.trimIndent(),
                ),
            )
            assertThat(collectedEffects().toSet()).isEqualTo(
                setOf(
                    TaskNewTaskContract.Effect.ShowMessage(
                        "Task request sent. It will appear after the first TaskMail status mail arrives.",
                    ),
                    TaskNewTaskContract.Effect.NavigateBack,
                ),
            )
            ensureThatAllEventsAreConsumed()
        }
    }

    @Test
    fun `send failure should preserve draft and advanced state`() = runMviTest {
        with(
            TaskNewTaskViewModelRobot(
                this,
                senderAccounts = listOf(primarySenderAccount),
                sendResult = TaskMailNewTaskResult.failure("send failed"),
            ),
        ) {
            start()
            loadData()
            toggleAdvanced()
            selectBackend(TaskMailBackend.OpenCode)
            changeRepo("E:/projects/android_task_manager")
            changeTask("Audit the new flow")
            changeTimeout("60")
            send()
            assertThat(viewModelState().sendError).isEqualTo("send failed")
            assertThat(viewModelState().repoPath).isEqualTo("E:/projects/android_task_manager")
            assertThat(viewModelState().taskText).isEqualTo("Audit the new flow")
            assertThat(viewModelState().isAdvancedExpanded).isEqualTo(true)
            ensureThatAllEventsAreConsumed()
        }
    }

    @Test
    fun `send failure should surface bot mailbox configuration error without clearing draft`() = runMviTest {
        with(
            TaskNewTaskViewModelRobot(
                this,
                senderAccounts = listOf(primarySenderAccount),
                sendResult = TaskMailNewTaskResult.failure("TaskMail bot mailbox is not configured."),
            ),
        ) {
            start()
            loadData()
            selectBackend(TaskMailBackend.Codex)
            changeRepo("E:/projects/android_task_manager")
            changeTask("Audit the new flow")
            send()
            assertThat(viewModelState().sendError).isEqualTo("TaskMail bot mailbox is not configured.")
            assertThat(viewModelState().repoPath).isEqualTo("E:/projects/android_task_manager")
            assertThat(viewModelState().taskText).isEqualTo("Audit the new flow")
            ensureThatAllEventsAreConsumed()
        }
    }
}

private class TaskNewTaskViewModelRobot(
    private val mviContext: MviContext,
    senderAccounts: List<TaskMailSenderAccount>,
    sendResult: TaskMailNewTaskResult = TaskMailNewTaskResult.success(),
) {
    private val senderAccountSource = FakeTaskMailSenderAccountSource(senderAccounts)
    private val newTaskSender = FakeTaskMailNewTaskSender(sendResult)
    private val viewModel = TaskNewTaskViewModel(
        getTaskMailSenderAccounts = GetTaskMailSenderAccounts(senderAccountSource),
        sendTaskMailNewTask = SendTaskMailNewTask(newTaskSender),
    )
    private lateinit var turbines: MviTurbines<TaskNewTaskContract.State, TaskNewTaskContract.Effect>

    suspend fun start() {
        turbines = mviContext.turbinesWithInitialStateCheck(viewModel, TaskNewTaskContract.State())
    }

    suspend fun loadData() {
        viewModel.event(TaskNewTaskContract.Event.LoadData)
        mviContext.advanceUntilIdle()
    }

    fun selectBackend(backend: TaskMailBackend) {
        viewModel.event(TaskNewTaskContract.Event.BackendSelected(backend))
    }

    fun changeRepo(value: String) {
        viewModel.event(TaskNewTaskContract.Event.RepoChanged(value))
    }

    fun changeTask(value: String) {
        viewModel.event(TaskNewTaskContract.Event.TaskChanged(value))
    }

    fun changeSubjectTitle(value: String) {
        viewModel.event(TaskNewTaskContract.Event.SubjectTitleChanged(value))
    }

    fun changeTimeout(value: String) {
        viewModel.event(TaskNewTaskContract.Event.TimeoutChanged(value))
    }

    fun toggleAdvanced() {
        viewModel.event(TaskNewTaskContract.Event.AdvancedToggleClicked)
    }

    fun clickChooseRepo() {
        viewModel.event(TaskNewTaskContract.Event.ChooseRepoClicked)
    }

    suspend fun send() {
        viewModel.event(TaskNewTaskContract.Event.SendClicked)
        mviContext.advanceUntilIdle()
    }

    suspend fun awaitEffect(): TaskNewTaskContract.Effect {
        return turbines.awaitEffectItem()
    }

    fun sentRequests(): List<TaskMailNewTaskRequest> = newTaskSender.requests

    suspend fun collectedEffects(): List<TaskNewTaskContract.Effect> {
        mviContext.advanceUntilIdle()
        val effects = mutableListOf<TaskNewTaskContract.Effect>()

        while (true) {
            effects += turbines.awaitEffectItem()
            if (effects.size >= 2 || newTaskSender.requests.isEmpty()) break
        }

        return effects
    }

    fun viewModelState(): TaskNewTaskContract.State = viewModel.state.value

    suspend fun ensureThatAllEventsAreConsumed() {
        turbines.stateTurbine.cancelAndIgnoreRemainingEvents()
        turbines.effectTurbine.ensureAllEventsConsumed()
    }
}

private class FakeTaskMailSenderAccountSource(
    private val senderAccounts: List<TaskMailSenderAccount>,
) : TaskMailSenderAccountSource {
    override fun getSenderAccounts(): List<TaskMailSenderAccount> = senderAccounts

    override fun getAccount(accountUuid: String): LegacyAccountDto? = null
}

private class FakeTaskMailNewTaskSender(
    private val result: TaskMailNewTaskResult,
) : TaskMailNewTaskSender {
    val requests = mutableListOf<TaskMailNewTaskRequest>()

    override suspend fun send(request: TaskMailNewTaskRequest): TaskMailNewTaskResult {
        requests += request
        return result
    }
}

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
