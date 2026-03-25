package net.thunderbird.feature.taskmail.internal.ui.newtask

import app.k9mail.core.ui.compose.testing.mvi.MviContext
import app.k9mail.core.ui.compose.testing.mvi.MviTurbines
import app.k9mail.core.ui.compose.testing.mvi.advanceUntilIdle
import app.k9mail.core.ui.compose.testing.mvi.runMviTest
import app.k9mail.core.ui.compose.testing.mvi.turbinesWithInitialStateCheck
import assertk.assertThat
import assertk.assertions.isEqualTo
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.collections.immutable.persistentListOf
import net.thunderbird.core.android.account.LegacyAccountDto
import net.thunderbird.core.testing.coroutines.MainDispatcherHelper
import net.thunderbird.feature.taskmail.internal.data.TaskMailSenderAccountSource
import net.thunderbird.feature.taskmail.internal.data.relay.RelayBootstrapManager
import net.thunderbird.feature.taskmail.internal.data.relay.protocol.RelayHelloAck
import net.thunderbird.feature.taskmail.internal.domain.model.RelayBootstrapResult
import net.thunderbird.feature.taskmail.internal.domain.model.RelayBootstrapStatus
import net.thunderbird.feature.taskmail.internal.domain.model.RelayConnectionState
import net.thunderbird.feature.taskmail.internal.domain.model.RelayHealthStatus
import net.thunderbird.feature.taskmail.internal.domain.model.RelayTransportConfig
import net.thunderbird.feature.taskmail.internal.domain.model.TaskMailBackend
import net.thunderbird.feature.taskmail.internal.domain.model.TaskMailDirectOutcome
import net.thunderbird.feature.taskmail.internal.domain.model.TaskMailDirectSendEvidence
import net.thunderbird.feature.taskmail.internal.domain.model.TaskMailDirectSwitchGate
import net.thunderbird.feature.taskmail.internal.domain.model.TaskMailNewTaskSendRecord
import net.thunderbird.feature.taskmail.internal.domain.model.TaskMailSenderAccount
import net.thunderbird.feature.taskmail.internal.domain.newtask.TaskMailDirectNewTaskResult
import net.thunderbird.feature.taskmail.internal.domain.newtask.TaskMailDirectNewTaskSender
import net.thunderbird.feature.taskmail.internal.domain.repository.TaskMailNewTaskSendRecordRepository
import net.thunderbird.feature.taskmail.internal.domain.usecase.GetLatestTaskMailNewTaskSendRecord
import net.thunderbird.feature.taskmail.internal.domain.usecase.GetTaskMailSenderAccounts
import net.thunderbird.feature.taskmail.internal.domain.usecase.RecordTaskMailNewTaskSendRecord
import net.thunderbird.feature.taskmail.internal.domain.usecase.RunTaskMailDirectDispatch
import net.thunderbird.feature.taskmail.internal.domain.usecase.SendTaskMailDirectNewTask

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
    fun `load data should restore latest direct send evidence for the selected sender account`() = runMviTest {
        with(
            TaskNewTaskViewModelRobot(
                this,
                senderAccounts = listOf(primarySenderAccount),
                latestSendRecord = TaskMailNewTaskSendRecord(
                    recordedAt = 123L,
                    senderAccountId = primarySenderAccount.accountUuid,
                    backend = TaskMailBackend.Codex,
                    repoPath = "E:/projects/android_task_manager",
                    evidence = TaskMailDirectSendEvidence(
                        bootstrapStatus = RelayBootstrapStatus.HelloAck,
                        outcome = TaskMailDirectOutcome.DirectAccepted,
                        switchGate = TaskMailDirectSwitchGate.KeepDirectDefault,
                        requestId = "req_restore",
                        receiptId = "receipt-restore",
                    ),
                ),
            ),
        ) {
            start()
            loadData()
            assertThat(viewModelState().lastDirectSendEvidence).isEqualTo(
                TaskMailDirectSendEvidence(
                    bootstrapStatus = RelayBootstrapStatus.HelloAck,
                    outcome = TaskMailDirectOutcome.DirectAccepted,
                    switchGate = TaskMailDirectSwitchGate.KeepDirectDefault,
                    requestId = "req_restore",
                    receiptId = "receipt-restore",
                ),
            )
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
            assertThat(viewModelState().taskInput.subjectTitle).isEqualTo("Audit the new flow")
            changeSubjectTitle("Custom title")
            changeTask("Different first line\nStill keep custom title.")
            assertThat(viewModelState().taskInput.subjectTitle).isEqualTo("Custom title")
            ensureThatAllEventsAreConsumed()
        }
    }

    @Test
    fun `task changes should derive title from the first non-empty line`() = runMviTest {
        with(TaskNewTaskViewModelRobot(this, senderAccounts = listOf(primarySenderAccount))) {
            start()
            loadData()
            changeTask("\n   \nAudit the new flow\nList only blockers.")
            assertThat(viewModelState().taskInput.subjectTitle).isEqualTo("Audit the new flow")
            ensureThatAllEventsAreConsumed()
        }
    }

    @Test
    fun `control target changes should update pc and workspace ids`() = runMviTest {
        with(TaskNewTaskViewModelRobot(this, senderAccounts = listOf(primarySenderAccount))) {
            start()
            loadData()
            changePcId("pc_workstation_01")
            changeWorkspaceId("workspace_android_app")
            assertThat(viewModelState().pcSelection.selectedPcId).isEqualTo("pc_workstation_01")
            assertThat(viewModelState().workspaceSelection.selectedWorkspaceId).isEqualTo("workspace_android_app")
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
            assertThat(viewModelState().validationErrors.senderAccountError).isEqualTo("Select the sending account.")
            assertThat(directSendCallCount()).isEqualTo(0)
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
            assertThat(viewModelState().validationErrors.backendError).isEqualTo("Select a backend.")
            assertThat(viewModelState().validationErrors.repoError).isEqualTo("Repository bridge is required.")
            assertThat(viewModelState().validationErrors.taskError).isEqualTo("Task details are required.")
            assertThat(viewModelState().validationErrors.titleError).isEqualTo("Title is required.")
            assertThat(viewModelState().validationErrors.timeoutError).isEqualTo("Timeout must be a positive integer.")
            assertThat(viewModelState().executionPolicyEditor.timeoutText).isEqualTo("0")
            assertThat(viewModelState().executionPolicyEditor.isExpanded).isEqualTo(true)
            assertThat(directSendCallCount()).isEqualTo(0)
            ensureThatAllEventsAreConsumed()
        }
    }

    @Test
    fun `send should prefer direct path after successful bootstrap`() = runMviTest {
        with(TaskNewTaskViewModelRobot(this, senderAccounts = listOf(primarySenderAccount))) {
            start()
            loadData()
            selectBackend(TaskMailBackend.Codex)
            changeRepo("E:/projects/android_task_manager")
            changeTask("Audit the new flow\nList only blockers.")
            send()

            assertThat(directSendCallCount()).isEqualTo(1)
            assertThat(collectedEffects().toSet()).isEqualTo(
                setOf(
                    TaskNewTaskContract.Effect.ShowMessage(
                        "[Relay] Task request sent. It will appear after the first TaskMail update arrives.",
                    ),
                    TaskNewTaskContract.Effect.NavigateBack,
                ),
            )
            assertThat(viewModelState().lastDirectSendEvidence).isEqualTo(
                TaskMailDirectSendEvidence(
                    bootstrapStatus = RelayBootstrapStatus.HelloAck,
                    outcome = TaskMailDirectOutcome.DirectAccepted,
                    switchGate = TaskMailDirectSwitchGate.KeepDirectDefault,
                    requestId = "req_001",
                    receiptId = "receipt-1",
                ),
            )
            assertThat(bootstrapCallCount()).isEqualTo(1)
            assertThat(disconnectCallCount()).isEqualTo(1)
            assertThat(latestSendRecord(primarySenderAccount.accountUuid)).isEqualTo(
                TaskMailNewTaskSendRecord(
                    recordedAt = 456L,
                    senderAccountId = primarySenderAccount.accountUuid,
                    backend = TaskMailBackend.Codex,
                    repoPath = "E:/projects/android_task_manager",
                    evidence = TaskMailDirectSendEvidence(
                        bootstrapStatus = RelayBootstrapStatus.HelloAck,
                        outcome = TaskMailDirectOutcome.DirectAccepted,
                        switchGate = TaskMailDirectSwitchGate.KeepDirectDefault,
                        requestId = "req_001",
                        receiptId = "receipt-1",
                    ),
                ),
            )
            ensureThatAllEventsAreConsumed()
        }
    }

    @Test
    fun `send should surface bootstrap failure without fallback`() = runMviTest {
        with(
            TaskNewTaskViewModelRobot(
                this,
                senderAccounts = listOf(primarySenderAccount),
                bootstrapResult = RelayBootstrapResult(
                    status = RelayBootstrapStatus.NotConfigured,
                    detailMessage = "Relay host, port, and transport token are required.",
                ),
            ),
        ) {
            start()
            loadData()
            selectBackend(TaskMailBackend.Codex)
            changeRepo("E:/projects/android_task_manager")
            changeTask("Audit the new flow")
            send()

            assertThat(viewModelState().submitState.sendError).isEqualTo(
                "Relay host, port, and transport token are required.",
            )
            assertThat(directSendCallCount()).isEqualTo(0)
            assertThat(viewModelState().lastDirectSendEvidence).isEqualTo(
                TaskMailDirectSendEvidence(
                    bootstrapStatus = RelayBootstrapStatus.NotConfigured,
                    outcome = TaskMailDirectOutcome.DirectRejected,
                    switchGate = TaskMailDirectSwitchGate.SwitchBlocker,
                    errorMessage = "Relay host, port, and transport token are required.",
                ),
            )
            assertThat(bootstrapCallCount()).isEqualTo(1)
            assertThat(disconnectCallCount()).isEqualTo(0)
            ensureThatAllEventsAreConsumed()
        }
    }

    @Test
    fun `send should keep fallback-classified relay rejection local`() = runMviTest {
        with(
            TaskNewTaskViewModelRobot(
                this,
                senderAccounts = listOf(primarySenderAccount),
                directSendResult = TaskMailDirectNewTaskResult.FallbackToMail("unsupported_action"),
            ),
        ) {
            start()
            loadData()
            selectBackend(TaskMailBackend.Codex)
            changeRepo("E:/projects/android_task_manager")
            changeTask("Audit the new flow")
            send()

            assertThat(directSendCallCount()).isEqualTo(1)
            assertThat(viewModelState().submitState.sendError).isEqualTo("unsupported_action")
            assertThat(viewModelState().lastDirectSendEvidence).isEqualTo(
                TaskMailDirectSendEvidence(
                    bootstrapStatus = RelayBootstrapStatus.HelloAck,
                    outcome = TaskMailDirectOutcome.DirectRejected,
                    switchGate = TaskMailDirectSwitchGate.SwitchBlocker,
                    fallbackReason = "unsupported_action",
                    errorMessage = "unsupported_action",
                ),
            )
            assertThat(disconnectCallCount()).isEqualTo(1)
            ensureThatAllEventsAreConsumed()
        }
    }

    @Test
    fun `send failure should preserve draft and advanced state`() = runMviTest {
        with(
            TaskNewTaskViewModelRobot(
                this,
                senderAccounts = listOf(primarySenderAccount),
                directSendResult = TaskMailDirectNewTaskResult.FallbackToMail("unsupported_action"),
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

            assertThat(viewModelState().submitState.sendError).isEqualTo("unsupported_action")
            assertThat(viewModelState().workspaceSelection.repoPath).isEqualTo("E:/projects/android_task_manager")
            assertThat(viewModelState().taskInput.taskText).isEqualTo("Audit the new flow")
            assertThat(viewModelState().executionPolicyEditor.isExpanded).isEqualTo(true)
            assertThat(directSendCallCount()).isEqualTo(1)
            assertThat(disconnectCallCount()).isEqualTo(1)
            ensureThatAllEventsAreConsumed()
        }
    }

    @Test
    fun `direct hard rejection should keep draft and skip fallback`() = runMviTest {
        with(
            TaskNewTaskViewModelRobot(
                this,
                senderAccounts = listOf(primarySenderAccount),
                directSendResult = TaskMailDirectNewTaskResult.Rejected(
                    "invalid_payload: task_text is required",
                ),
            ),
        ) {
            start()
            loadData()
            selectBackend(TaskMailBackend.Codex)
            changeRepo("E:/projects/android_task_manager")
            changeTask("Audit the new flow")
            send()

            assertThat(viewModelState().submitState.sendError).isEqualTo("invalid_payload: task_text is required")
            assertThat(viewModelState().lastDirectSendEvidence).isEqualTo(
                TaskMailDirectSendEvidence(
                    bootstrapStatus = RelayBootstrapStatus.HelloAck,
                    outcome = TaskMailDirectOutcome.DirectRejected,
                    switchGate = TaskMailDirectSwitchGate.SwitchBlocker,
                    errorMessage = "invalid_payload: task_text is required",
                ),
            )
            assertThat(viewModelState().workspaceSelection.repoPath).isEqualTo("E:/projects/android_task_manager")
            assertThat(viewModelState().taskInput.taskText).isEqualTo("Audit the new flow")
            assertThat(directSendCallCount()).isEqualTo(1)
            assertThat(disconnectCallCount()).isEqualTo(1)
            ensureThatAllEventsAreConsumed()
        }
    }

    @Test
    fun `send should use default error message when relay rejection detail is blank`() = runMviTest {
        with(
            TaskNewTaskViewModelRobot(
                this,
                senderAccounts = listOf(primarySenderAccount),
                directSendResult = TaskMailDirectNewTaskResult.FallbackToMail(),
            ),
        ) {
            start()
            loadData()
            selectBackend(TaskMailBackend.Codex)
            changeRepo("E:/projects/android_task_manager")
            changeTask("Audit the new flow")
            send()

            assertThat(viewModelState().submitState.sendError).isEqualTo("Relay dispatch failed.")
            assertThat(viewModelState().workspaceSelection.repoPath).isEqualTo("E:/projects/android_task_manager")
            assertThat(viewModelState().taskInput.taskText).isEqualTo("Audit the new flow")
            assertThat(directSendCallCount()).isEqualTo(1)
            assertThat(disconnectCallCount()).isEqualTo(1)
            ensureThatAllEventsAreConsumed()
        }
    }

    @Test
    fun `workspace selection should prefill repository bridge from selected workspace option`() = runMviTest {
        with(
            TaskNewTaskViewModelRobot(
                this,
                senderAccounts = listOf(primarySenderAccount),
                initialState = TaskNewTaskContract.State(
                    workspaceSelection = TaskNewTaskWorkspaceSelectionUiState(
                        workspaceOptions = persistentListOf(
                            TaskNewTaskWorkspaceOptionUi(
                                id = "workspace_android_app",
                                title = "Android app",
                                repoPath = "E:/projects/android_task_manager",
                                workdir = "feature/taskmail",
                            ),
                        ),
                    ),
                ),
            ),
        ) {
            start()
            loadData()
            changeWorkspaceId("workspace_android_app")
            assertThat(viewModelState().workspaceSelection.repoPath).isEqualTo("E:/projects/android_task_manager")
            assertThat(viewModelState().workspaceSelection.workdir).isEqualTo("feature/taskmail")
            ensureThatAllEventsAreConsumed()
        }
    }

    @Test
    fun `send should resolve repository bridge from selected workspace option`() = runMviTest {
        with(
            TaskNewTaskViewModelRobot(
                this,
                senderAccounts = listOf(primarySenderAccount),
                initialState = TaskNewTaskContract.State(
                    workspaceSelection = TaskNewTaskWorkspaceSelectionUiState(
                        workspaceOptions = persistentListOf(
                            TaskNewTaskWorkspaceOptionUi(
                                id = "workspace_android_app",
                                title = "Android app",
                                repoPath = "E:/projects/android_task_manager",
                                workdir = "feature/taskmail",
                            ),
                        ),
                    ),
                ),
            ),
        ) {
            start()
            loadData()
            changePcId("pc_workstation_01")
            changeWorkspaceId("workspace_android_app")
            selectBackend(TaskMailBackend.Codex)
            changeTask("Audit the new flow")
            send()

            assertThat(directSendCallCount()).isEqualTo(1)
            assertThat(latestSentDraft()?.pcId).isEqualTo("pc_workstation_01")
            assertThat(latestSentDraft()?.workspaceId).isEqualTo("workspace_android_app")
            assertThat(latestSentDraft()?.repoPath).isEqualTo("E:/projects/android_task_manager")
            assertThat(latestSentDraft()?.workdir).isEqualTo("feature/taskmail")
            assertThat(collectedEffects().toSet()).isEqualTo(
                setOf(
                    TaskNewTaskContract.Effect.ShowMessage(
                        "[Relay] Task request sent. It will appear after the first TaskMail update arrives.",
                    ),
                    TaskNewTaskContract.Effect.NavigateBack,
                ),
            )
            ensureThatAllEventsAreConsumed()
        }
    }
}

private class TaskNewTaskViewModelRobot(
    private val mviContext: MviContext,
    senderAccounts: List<TaskMailSenderAccount>,
    directSendResult: TaskMailDirectNewTaskResult = TaskMailDirectNewTaskResult.Accepted(
        requestId = "req_001",
        receiptId = "receipt-1",
    ),
    latestSendRecord: TaskMailNewTaskSendRecord? = null,
    bootstrapResult: RelayBootstrapResult = RelayBootstrapResult(
        status = RelayBootstrapStatus.HelloAck,
    ),
    initialState: TaskNewTaskContract.State = TaskNewTaskContract.State(),
) {
    private val expectedInitialState = initialState
    private val senderAccountSource = FakeTaskMailSenderAccountSource(senderAccounts)
    private val directNewTaskSender = FakeTaskMailDirectNewTaskSender(directSendResult)
    private val sendRecordRepository = FakeTaskMailNewTaskSendRecordRepository(latestSendRecord)
    private val relayBootstrapManager = FakeRelayBootstrapManager(bootstrapResult)
    private val viewModel = TaskNewTaskViewModel(
        getTaskMailSenderAccounts = GetTaskMailSenderAccounts(senderAccountSource),
        getLatestTaskMailNewTaskSendRecord = GetLatestTaskMailNewTaskSendRecord(sendRecordRepository),
        recordTaskMailNewTaskSendRecord = RecordTaskMailNewTaskSendRecord(
            repository = sendRecordRepository,
            clock = { 456L },
        ),
        sendTaskMailDirectNewTask = SendTaskMailDirectNewTask(directNewTaskSender),
        runTaskMailDirectDispatch = RunTaskMailDirectDispatch(relayBootstrapManager),
        initialState = initialState,
    )
    private lateinit var turbines: MviTurbines<TaskNewTaskContract.State, TaskNewTaskContract.Effect>

    suspend fun start() {
        turbines = mviContext.turbinesWithInitialStateCheck(viewModel, expectedInitialState)
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

    fun changePcId(value: String) {
        viewModel.event(TaskNewTaskContract.Event.PcChanged(value))
    }

    fun changeWorkspaceId(value: String) {
        viewModel.event(TaskNewTaskContract.Event.WorkspaceChanged(value))
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

    fun directSendCallCount(): Int = directNewTaskSender.sendCallCount

    fun latestSentDraft(): net.thunderbird.feature.taskmail.internal.domain.newtask.TaskMailNewTaskDraft? {
        return directNewTaskSender.sentDrafts.lastOrNull()
    }

    fun bootstrapCallCount(): Int = relayBootstrapManager.bootstrapCallCount

    fun disconnectCallCount(): Int = relayBootstrapManager.disconnectCallCount

    suspend fun latestSendRecord(senderAccountId: String): TaskMailNewTaskSendRecord? {
        return sendRecordRepository.getLatestRecord(senderAccountId)
    }

    suspend fun collectedEffects(): List<TaskNewTaskContract.Effect> {
        mviContext.advanceUntilIdle()
        return listOf(
            turbines.awaitEffectItem(),
            turbines.awaitEffectItem(),
        )
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

private class FakeTaskMailNewTaskSendRecordRepository(
    latestRecord: TaskMailNewTaskSendRecord? = null,
) : TaskMailNewTaskSendRecordRepository {
    private val records = latestRecord
        ?.let(::listOf)
        ?.toMutableList()
        ?: mutableListOf()

    override suspend fun getLatestRecord(senderAccountId: String): TaskMailNewTaskSendRecord? {
        return records.firstOrNull { record -> record.senderAccountId == senderAccountId }
    }

    override suspend fun saveRecord(record: TaskMailNewTaskSendRecord) {
        records.add(0, record)
    }
}

private class FakeTaskMailDirectNewTaskSender(
    private val result: TaskMailDirectNewTaskResult,
) : TaskMailDirectNewTaskSender {
    var sendCallCount: Int = 0
    val sentDrafts = mutableListOf<net.thunderbird.feature.taskmail.internal.domain.newtask.TaskMailNewTaskDraft>()

    override suspend fun send(
        draft: net.thunderbird.feature.taskmail.internal.domain.newtask.TaskMailNewTaskDraft,
    ): TaskMailDirectNewTaskResult {
        sendCallCount += 1
        sentDrafts += draft
        return result
    }
}

private class FakeRelayBootstrapManager(
    private val bootstrapResult: RelayBootstrapResult,
) : RelayBootstrapManager {
    private val mutableConnectionState = MutableStateFlow<RelayConnectionState>(RelayConnectionState.Idle)

    var bootstrapCallCount: Int = 0
    var disconnectCallCount: Int = 0

    override val connectionState = mutableConnectionState

    override fun loadConfig(): RelayTransportConfig = RelayTransportConfig()

    override fun saveConfig(config: RelayTransportConfig): Boolean = true

    override suspend fun probeHealth(config: RelayTransportConfig): Result<RelayHealthStatus> {
        return Result.success(
            RelayHealthStatus(
                status = "ok",
                service = "mail-runner-relay",
                listenHost = "0.0.0.0",
                listenPort = 8787,
                sessionCount = 0,
                packetCount = 0,
                tlsEnabled = false,
                transportTokenId = "token-id",
            ),
        )
    }

    override suspend fun connect(config: RelayTransportConfig): Result<RelayHelloAck> {
        return Result.success(
            RelayHelloAck(
                messageType = "hello_ack",
                connectionId = "connection-1",
                serverTime = "2026-03-21T12:00:00Z",
                heartbeatSeconds = 30,
            ),
        )
    }

    override suspend fun disconnect() {
        disconnectCallCount += 1
        mutableConnectionState.value = RelayConnectionState.Idle
    }

    override suspend fun bootstrap(config: RelayTransportConfig): RelayBootstrapResult {
        bootstrapCallCount += 1
        return bootstrapResult
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
