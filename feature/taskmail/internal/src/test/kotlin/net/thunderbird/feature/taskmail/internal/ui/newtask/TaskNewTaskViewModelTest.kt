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
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.collections.immutable.persistentListOf
import net.thunderbird.feature.taskmail.internal.data.TaskMailReplyAttachmentResolver
import net.thunderbird.core.android.account.LegacyAccountDto
import net.thunderbird.core.testing.coroutines.MainDispatcherHelper
import net.thunderbird.feature.taskmail.internal.data.TaskMailSenderAccountSource
import net.thunderbird.feature.taskmail.internal.domain.model.RelayBootstrapStatus
import net.thunderbird.feature.taskmail.internal.domain.model.TaskEnvironmentCapabilities
import net.thunderbird.feature.taskmail.internal.domain.model.TaskEnvironmentInventorySnapshot
import net.thunderbird.feature.taskmail.internal.domain.model.TaskEnvironmentPc
import net.thunderbird.feature.taskmail.internal.domain.model.TaskEnvironmentRouteAdmission
import net.thunderbird.feature.taskmail.internal.domain.model.TaskEnvironmentWorkspace
import net.thunderbird.feature.taskmail.internal.domain.model.TaskMailBackend
import net.thunderbird.feature.taskmail.internal.domain.model.TaskMailDirectOutcome
import net.thunderbird.feature.taskmail.internal.domain.model.TaskMailDirectSendEvidence
import net.thunderbird.feature.taskmail.internal.domain.model.TaskMailDirectSwitchGate
import net.thunderbird.feature.taskmail.internal.domain.model.TaskMailNewTaskSendRecord
import net.thunderbird.feature.taskmail.internal.domain.model.TaskMailSessionStatus
import net.thunderbird.feature.taskmail.internal.domain.model.TaskMailSenderAccount
import net.thunderbird.feature.taskmail.internal.domain.model.TaskReplyAttachment
import net.thunderbird.feature.taskmail.internal.domain.model.TaskSessionDetail
import net.thunderbird.feature.taskmail.internal.domain.model.TaskSessionKey
import net.thunderbird.feature.taskmail.internal.domain.model.TaskSessionProjectionDataSource
import net.thunderbird.feature.taskmail.internal.domain.newtask.TaskMailCreateSessionAckStatus
import net.thunderbird.feature.taskmail.internal.domain.newtask.TaskMailCreateSessionBinding
import net.thunderbird.feature.taskmail.internal.domain.newtask.TaskMailCreateSessionClient
import net.thunderbird.feature.taskmail.internal.domain.newtask.TaskMailCreateSessionResult
import net.thunderbird.feature.taskmail.internal.domain.newtask.TaskMailCreateSessionSubmitAck
import net.thunderbird.feature.taskmail.internal.domain.repository.TaskMailNewTaskSendRecordRepository
import net.thunderbird.feature.taskmail.internal.domain.repository.TaskSessionDetailRepository
import net.thunderbird.feature.taskmail.internal.domain.usecase.CreateTaskMailSession
import net.thunderbird.feature.taskmail.internal.domain.usecase.GetLatestTaskMailNewTaskSendRecord
import net.thunderbird.feature.taskmail.internal.domain.usecase.GetTaskEnvironmentInventory
import net.thunderbird.feature.taskmail.internal.domain.usecase.GetTaskMailSenderAccounts
import net.thunderbird.feature.taskmail.internal.domain.usecase.RecordTaskMailNewTaskSendRecord

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
    fun `load data should keep send available when no sender accounts exist`() = runMviTest {
        with(TaskNewTaskViewModelRobot(this, senderAccounts = emptyList())) {
            start()
            loadData()
            assertThat(viewModelState().senderAccounts).isEqualTo(persistentListOf())
            assertThat(viewModelState().selectedSenderAccountId).isEqualTo(null)
            assertThat(viewModelState().canSend).isEqualTo(true)
            ensureThatAllEventsAreConsumed()
        }
    }

    @Test
    fun `load data should auto resolve single sender account for evidence lookup`() = runMviTest {
        with(TaskNewTaskViewModelRobot(this, senderAccounts = listOf(primarySenderAccount))) {
            start()
            loadData()
            assertThat(viewModelState().selectedSenderAccountId).isEqualTo(primarySenderAccount.accountUuid)
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
    fun `load data should skip evidence sender resolution when multiple accounts exist`() = runMviTest {
        with(
            TaskNewTaskViewModelRobot(
                this,
                senderAccounts = listOf(primarySenderAccount, secondarySenderAccount),
            ),
        ) {
            start()
            loadData()
            assertThat(viewModelState().selectedSenderAccountId).isEqualTo(null)
            assertThat(viewModelState().lastDirectSendEvidence).isEqualTo(null)
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
    fun `send should not require sender selection when multiple accounts exist`() = runMviTest {
        with(
            TaskNewTaskViewModelRobot(
                this,
                senderAccounts = listOf(primarySenderAccount, secondarySenderAccount),
            ),
        ) {
            start()
            loadData()
            selectRouteTarget()
            selectBackend(TaskMailBackend.Codex)
            changeRepo("E:/projects/android_task_manager")
            changeTask("Audit the new flow")
            send()
            assertThat(createSessionCallCount()).isEqualTo(1)
            assertThat(latestSentDraft()?.senderAccountId).isEqualTo(null)
            assertThat(collectedEffects().toSet()).isEqualTo(
                setOf(
                    TaskNewTaskContract.Effect.ShowMessage(
                        "[VPS] Session accepted. Opening session detail.",
                    ),
                    TaskNewTaskContract.Effect.NavigateToSession(
                        workspaceId = "workspace_android_app",
                        sessionId = "sess_001",
                    ),
                ),
            )
            ensureThatAllEventsAreConsumed()
        }
    }

    @Test
    fun `send should not require sender account when no sender accounts exist`() = runMviTest {
        with(TaskNewTaskViewModelRobot(this, senderAccounts = emptyList())) {
            start()
            loadData()
            selectRouteTarget()
            selectBackend(TaskMailBackend.Codex)
            changeRepo("E:/projects/android_task_manager")
            changeTask("Audit the new flow")
            send()

            assertThat(createSessionCallCount()).isEqualTo(1)
            assertThat(latestSentDraft()?.senderAccountId).isEqualTo(null)
            assertThat(collectedEffects().toSet()).isEqualTo(
                setOf(
                    TaskNewTaskContract.Effect.ShowMessage(
                        "[VPS] Session accepted. Opening session detail.",
                    ),
                    TaskNewTaskContract.Effect.NavigateToSession(
                        workspaceId = "workspace_android_app",
                        sessionId = "sess_001",
                    ),
                ),
            )
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
            assertThat(viewModelState().validationErrors.pcError).isEqualTo("Select a target PC.")
            assertThat(viewModelState().validationErrors.workspaceError).isEqualTo("Select a workspace.")
            assertThat(viewModelState().validationErrors.backendError).isEqualTo("Select a backend.")
            assertThat(viewModelState().validationErrors.repoError).isEqualTo("Repository bridge is required.")
            assertThat(viewModelState().validationErrors.taskError).isEqualTo("Task details are required.")
            assertThat(viewModelState().validationErrors.titleError).isEqualTo("Title is required.")
            assertThat(viewModelState().validationErrors.timeoutError).isEqualTo("Timeout must be a positive integer.")
            assertThat(viewModelState().executionPolicyEditor.timeoutText).isEqualTo("0")
            assertThat(viewModelState().executionPolicyEditor.isExpanded).isEqualTo(true)
            assertThat(createSessionCallCount()).isEqualTo(0)
            ensureThatAllEventsAreConsumed()
        }
    }

    @Test
    fun `send should open session detail when create session returns binding`() = runMviTest {
        with(TaskNewTaskViewModelRobot(this, senderAccounts = listOf(primarySenderAccount))) {
            start()
            loadData()
            selectRouteTarget()
            selectBackend(TaskMailBackend.Codex)
            changeRepo("E:/projects/android_task_manager")
            changeTask("Audit the new flow\nList only blockers.")
            send()

            assertThat(createSessionCallCount()).isEqualTo(1)
            assertThat(collectedEffects().toSet()).isEqualTo(
                setOf(
                    TaskNewTaskContract.Effect.ShowMessage(
                        "[VPS] Session accepted. Opening session detail.",
                    ),
                    TaskNewTaskContract.Effect.NavigateToSession(
                        workspaceId = "workspace_android_app",
                        sessionId = "sess_001",
                    ),
                ),
            )
            assertThat(viewModelState().lastDirectSendEvidence).isEqualTo(null)
            assertThat(latestSendRecord(primarySenderAccount.accountUuid)).isEqualTo(null)
            ensureThatAllEventsAreConsumed()
        }
    }

    @Test
    fun `load data should populate route options from environment inventory`() = runMviTest {
        with(
            TaskNewTaskViewModelRobot(
                this,
                senderAccounts = listOf(primarySenderAccount),
                environmentInventoryResult = Result.success(sampleEnvironmentInventorySnapshot()),
            ),
        ) {
            start()
            loadData()

            assertThat(viewModelState().pcSelection.pcOptions.single().id).isEqualTo("pc_workstation_01")
            changePcId("pc_workstation_01")
            assertThat(viewModelState().workspaceSelection.workspaceOptions.single().id)
                .isEqualTo("workspace_android_app")
            changeWorkspaceId("workspace_android_app")
            assertThat(viewModelState().executionPolicyEditor.availableBackends).isEqualTo(
                persistentListOf(TaskMailBackend.Codex),
            )
            ensureThatAllEventsAreConsumed()
        }
    }

    @Test
    fun `attachments selected should populate input attachments and allow removal`() = runMviTest {
        val attachment = sampleReplyAttachment()
        with(
            TaskNewTaskViewModelRobot(
                this,
                senderAccounts = listOf(primarySenderAccount),
                attachmentResolver = FakeTaskMailReplyAttachmentResolver(
                    attachmentsToReturn = listOf(attachment),
                ),
            ),
        ) {
            start()
            loadData()
            selectAttachments("content://taskmail/final-report")

            assertThat(viewModelState().taskInput.attachments).isEqualTo(persistentListOf(attachment))

            removeAttachment(attachment.id)

            assertThat(viewModelState().taskInput.attachments).isEqualTo(persistentListOf())
            ensureThatAllEventsAreConsumed()
        }
    }

    @Test
    fun `send should persist provisional vps detail when create session returns binding`() = runMviTest {
        val detailRepository = FakeTaskSessionDetailRepository()

        with(
            TaskNewTaskViewModelRobot(
                this,
                senderAccounts = listOf(primarySenderAccount),
                detailRepository = detailRepository,
            ),
        ) {
            start()
            loadData()
            selectRouteTarget()
            selectBackend(TaskMailBackend.Codex)
            changeRepo("E:/projects/android_task_manager")
            changeTask("Audit the new flow\nList only blockers.")
            send()

            val persistedDetail = detailRepository.getTaskSessionDetails().single()
            assertThat(persistedDetail.key).isEqualTo(
                TaskSessionKey(
                    workspaceId = "workspace_android_app",
                    sessionId = "sess_001",
                ),
            )
            assertThat(persistedDetail.sessionName).isEqualTo("Audit the new flow")
            assertThat(persistedDetail.status).isEqualTo(TaskMailSessionStatus.Queued)
            assertThat(persistedDetail.lastSummary).isEqualTo(
                "[VPS] Task request submitted. Waiting for the first session update.",
            )
            assertThat(persistedDetail.timeline.single().summary).isEqualTo("Task request submitted")
            assertThat(persistedDetail.projectionSyncState.dataSource).isEqualTo(
                TaskSessionProjectionDataSource.VpsNative,
            )
            collectedEffects()
            ensureThatAllEventsAreConsumed()
        }
    }

    @Test
    fun `send should navigate back when create session submit has no binding`() = runMviTest {
        with(
            TaskNewTaskViewModelRobot(
                this,
                senderAccounts = listOf(primarySenderAccount),
                createSessionResult = TaskMailCreateSessionResult.Submitted(
                    commandId = "cmd_queued",
                    submitAck = TaskMailCreateSessionSubmitAck(
                        ackStatus = TaskMailCreateSessionAckStatus.AcceptedButQueued,
                        queuePosition = 1,
                    ),
                    sessionBinding = null,
                ),
            ),
        ) {
            start()
            loadData()
            selectRouteTarget()
            selectBackend(TaskMailBackend.Codex)
            changeRepo("E:/projects/android_task_manager")
            changeTask("Audit the new flow")
            send()

            assertThat(collectedEffects().toSet()).isEqualTo(
                setOf(
                    TaskNewTaskContract.Effect.ShowMessage("[VPS] Task request submitted."),
                    TaskNewTaskContract.Effect.NavigateBack,
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
                createSessionResult = TaskMailCreateSessionResult.Failed(
                    errorMessage = "Relay host, port, and transport token are required.",
                    evidence = TaskMailDirectSendEvidence(
                        bootstrapStatus = RelayBootstrapStatus.NotConfigured,
                        outcome = TaskMailDirectOutcome.DirectRejected,
                        switchGate = TaskMailDirectSwitchGate.SwitchBlocker,
                        errorMessage = "Relay host, port, and transport token are required.",
                    ),
                ),
            ),
        ) {
            start()
            loadData()
            selectRouteTarget()
            selectBackend(TaskMailBackend.Codex)
            changeRepo("E:/projects/android_task_manager")
            changeTask("Audit the new flow")
            send()

            assertThat(viewModelState().submitState.sendError).isEqualTo(
                "Relay host, port, and transport token are required.",
            )
            assertThat(createSessionCallCount()).isEqualTo(1)
            assertThat(viewModelState().lastDirectSendEvidence).isEqualTo(
                TaskMailDirectSendEvidence(
                    bootstrapStatus = RelayBootstrapStatus.NotConfigured,
                    outcome = TaskMailDirectOutcome.DirectRejected,
                    switchGate = TaskMailDirectSwitchGate.SwitchBlocker,
                    errorMessage = "Relay host, port, and transport token are required.",
                ),
            )
            ensureThatAllEventsAreConsumed()
        }
    }

    @Test
    fun `send should keep fallback-classified relay rejection local`() = runMviTest {
        with(
            TaskNewTaskViewModelRobot(
                this,
                senderAccounts = listOf(primarySenderAccount),
                createSessionResult = TaskMailCreateSessionResult.Failed(
                    errorMessage = "unsupported_action",
                    evidence = TaskMailDirectSendEvidence(
                        bootstrapStatus = RelayBootstrapStatus.HelloAck,
                        outcome = TaskMailDirectOutcome.DirectRejected,
                        switchGate = TaskMailDirectSwitchGate.SwitchBlocker,
                        fallbackReason = "unsupported_action",
                        errorMessage = "unsupported_action",
                    ),
                ),
            ),
        ) {
            start()
            loadData()
            selectRouteTarget()
            selectBackend(TaskMailBackend.Codex)
            changeRepo("E:/projects/android_task_manager")
            changeTask("Audit the new flow")
            send()

            assertThat(createSessionCallCount()).isEqualTo(1)
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
            ensureThatAllEventsAreConsumed()
        }
    }

    @Test
    fun `send failure should preserve draft and advanced state`() = runMviTest {
        with(
            TaskNewTaskViewModelRobot(
                this,
                senderAccounts = listOf(primarySenderAccount),
                createSessionResult = TaskMailCreateSessionResult.Failed(
                    errorMessage = "unsupported_action",
                    evidence = TaskMailDirectSendEvidence(
                        bootstrapStatus = RelayBootstrapStatus.HelloAck,
                        outcome = TaskMailDirectOutcome.DirectRejected,
                        switchGate = TaskMailDirectSwitchGate.SwitchBlocker,
                        fallbackReason = "unsupported_action",
                        errorMessage = "unsupported_action",
                    ),
                ),
            ),
        ) {
            start()
            loadData()
            toggleAdvanced()
            selectRouteTarget()
            selectBackend(TaskMailBackend.OpenCode)
            changeRepo("E:/projects/android_task_manager")
            changeTask("Audit the new flow")
            changeTimeout("60")
            send()

            assertThat(viewModelState().submitState.sendError).isEqualTo("unsupported_action")
            assertThat(viewModelState().workspaceSelection.repoPath).isEqualTo("E:/projects/android_task_manager")
            assertThat(viewModelState().taskInput.taskText).isEqualTo("Audit the new flow")
            assertThat(viewModelState().executionPolicyEditor.isExpanded).isEqualTo(true)
            assertThat(createSessionCallCount()).isEqualTo(1)
            ensureThatAllEventsAreConsumed()
        }
    }

    @Test
    fun `direct hard rejection should keep draft and skip fallback`() = runMviTest {
        with(
            TaskNewTaskViewModelRobot(
                this,
                senderAccounts = listOf(primarySenderAccount),
                createSessionResult = TaskMailCreateSessionResult.Rejected(
                    commandId = "cmd_001",
                    submitAck = TaskMailCreateSessionSubmitAck(
                        ackStatus = TaskMailCreateSessionAckStatus.Rejected,
                        reason = "invalid_payload: task_text is required",
                    ),
                    errorMessage = "invalid_payload: task_text is required",
                    evidence = TaskMailDirectSendEvidence(
                        bootstrapStatus = RelayBootstrapStatus.HelloAck,
                        outcome = TaskMailDirectOutcome.DirectRejected,
                        switchGate = TaskMailDirectSwitchGate.SwitchBlocker,
                        errorMessage = "invalid_payload: task_text is required",
                    ),
                ),
            ),
        ) {
            start()
            loadData()
            selectRouteTarget()
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
            assertThat(createSessionCallCount()).isEqualTo(1)
            ensureThatAllEventsAreConsumed()
        }
    }

    @Test
    fun `send should use default error message when relay rejection detail is blank`() = runMviTest {
        with(
            TaskNewTaskViewModelRobot(
                this,
                senderAccounts = listOf(primarySenderAccount),
                createSessionResult = TaskMailCreateSessionResult.Failed(
                    errorMessage = "Relay dispatch failed.",
                    evidence = TaskMailDirectSendEvidence(
                        bootstrapStatus = RelayBootstrapStatus.HelloAck,
                        outcome = TaskMailDirectOutcome.DirectRejected,
                        switchGate = TaskMailDirectSwitchGate.SwitchBlocker,
                        errorMessage = "Relay dispatch failed.",
                    ),
                ),
            ),
        ) {
            start()
            loadData()
            selectRouteTarget()
            selectBackend(TaskMailBackend.Codex)
            changeRepo("E:/projects/android_task_manager")
            changeTask("Audit the new flow")
            send()

            assertThat(viewModelState().submitState.sendError).isEqualTo("Relay dispatch failed.")
            assertThat(viewModelState().workspaceSelection.repoPath).isEqualTo("E:/projects/android_task_manager")
            assertThat(viewModelState().taskInput.taskText).isEqualTo("Audit the new flow")
            assertThat(createSessionCallCount()).isEqualTo(1)
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
            selectRouteTarget()
            selectBackend(TaskMailBackend.Codex)
            changeTask("Audit the new flow")
            send()

            assertThat(createSessionCallCount()).isEqualTo(1)
            assertThat(latestSentDraft()?.pcId).isEqualTo("pc_workstation_01")
            assertThat(latestSentDraft()?.workspaceId).isEqualTo("workspace_android_app")
            assertThat(latestSentDraft()?.repoPath).isEqualTo("E:/projects/android_task_manager")
            assertThat(latestSentDraft()?.workdir).isEqualTo("feature/taskmail")
            assertThat(collectedEffects().toSet()).isEqualTo(
                setOf(
                    TaskNewTaskContract.Effect.ShowMessage(
                        "[VPS] Session accepted. Opening session detail.",
                    ),
                    TaskNewTaskContract.Effect.NavigateToSession(
                        workspaceId = "workspace_android_app",
                        sessionId = "sess_001",
                    ),
                ),
            )
            ensureThatAllEventsAreConsumed()
        }
    }

    @Test
    fun `send should include selected input attachments in create session draft`() = runMviTest {
        val attachment = sampleReplyAttachment()
        with(
            TaskNewTaskViewModelRobot(
                this,
                senderAccounts = listOf(primarySenderAccount),
                attachmentResolver = FakeTaskMailReplyAttachmentResolver(
                    attachmentsToReturn = listOf(attachment),
                ),
            ),
        ) {
            start()
            loadData()
            selectRouteTarget()
            selectBackend(TaskMailBackend.Codex)
            changeRepo("E:/projects/android_task_manager")
            changeTask("Audit the new flow")
            selectAttachments(attachment.uriString)
            send()

            assertThat(latestSentDraft()?.attachments).isEqualTo(listOf(attachment))
            collectedEffects()
            ensureThatAllEventsAreConsumed()
        }
    }
}

private class TaskNewTaskViewModelRobot(
    private val mviContext: MviContext,
    senderAccounts: List<TaskMailSenderAccount>,
    createSessionResult: TaskMailCreateSessionResult = TaskMailCreateSessionResult.Submitted(
        commandId = "cmd_001",
        submitAck = TaskMailCreateSessionSubmitAck(
            ackStatus = TaskMailCreateSessionAckStatus.Accepted,
        ),
        sessionBinding = TaskMailCreateSessionBinding(
            sessionId = "sess_001",
            pcId = "pc_workstation_01",
            workspaceId = "workspace_android_app",
        ),
    ),
    latestSendRecord: TaskMailNewTaskSendRecord? = null,
    private val detailRepository: FakeTaskSessionDetailRepository? = null,
    attachmentResolver: TaskMailReplyAttachmentResolver = FakeTaskMailReplyAttachmentResolver(),
    environmentInventoryResult: Result<TaskEnvironmentInventorySnapshot> =
        Result.failure(IllegalStateException("inventory unavailable")),
    initialState: TaskNewTaskContract.State = TaskNewTaskContract.State(),
) {
    private val expectedInitialState = initialState
    private val senderAccountSource = FakeTaskMailSenderAccountSource(senderAccounts)
    private val createSessionClient = FakeTaskMailCreateSessionClient(createSessionResult)
    private val sendRecordRepository = FakeTaskMailNewTaskSendRecordRepository(latestSendRecord)
    private val environmentInventoryRepository = FakeTaskEnvironmentInventoryRepository(environmentInventoryResult)
    private val viewModel = TaskNewTaskViewModel(
        getTaskMailSenderAccounts = GetTaskMailSenderAccounts(senderAccountSource),
        getTaskEnvironmentInventory = GetTaskEnvironmentInventory(environmentInventoryRepository),
        getLatestTaskMailNewTaskSendRecord = GetLatestTaskMailNewTaskSendRecord(sendRecordRepository),
        recordTaskMailNewTaskSendRecord = RecordTaskMailNewTaskSendRecord(
            repository = sendRecordRepository,
            clock = { 456L },
        ),
        createTaskMailSession = CreateTaskMailSession(createSessionClient),
        replyAttachmentResolver = attachmentResolver,
        detailRepository = detailRepository,
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

    fun selectRouteTarget(
        pcId: String = "pc_workstation_01",
        workspaceId: String = "workspace_android_app",
    ) {
        changePcId(pcId)
        changeWorkspaceId(workspaceId)
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

    suspend fun selectAttachments(vararg uriStrings: String) {
        viewModel.event(TaskNewTaskContract.Event.AttachmentsSelected(uriStrings.toList()))
        mviContext.advanceUntilIdle()
    }

    fun removeAttachment(attachmentId: String) {
        viewModel.event(TaskNewTaskContract.Event.RemoveAttachmentClicked(attachmentId))
    }

    suspend fun send() {
        viewModel.event(TaskNewTaskContract.Event.SendClicked)
        mviContext.advanceUntilIdle()
    }

    suspend fun awaitEffect(): TaskNewTaskContract.Effect {
        return turbines.awaitEffectItem()
    }

    fun createSessionCallCount(): Int = createSessionClient.createSessionCallCount

    fun latestSentDraft(): net.thunderbird.feature.taskmail.internal.domain.newtask.TaskMailNewTaskDraft? {
        return createSessionClient.sentDrafts.lastOrNull()
    }

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

private class FakeTaskEnvironmentInventoryRepository(
    private val result: Result<TaskEnvironmentInventorySnapshot>,
) : net.thunderbird.feature.taskmail.internal.domain.repository.TaskEnvironmentInventoryRepository {
    override suspend fun getEnvironmentInventory(): Result<TaskEnvironmentInventorySnapshot> = result
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

private class FakeTaskSessionDetailRepository(
    initialDetails: List<TaskSessionDetail> = emptyList(),
) : TaskSessionDetailRepository {
    private var details = initialDetails

    override suspend fun getTaskSessionDetail(key: TaskSessionKey): TaskSessionDetail? {
        return details.firstOrNull { detail -> detail.key == key }
    }

    override suspend fun getTaskSessionDetails(): List<TaskSessionDetail> = details

    override suspend fun replaceAllSessionDetails(details: List<TaskSessionDetail>) {
        this.details = details
    }

    override suspend fun upsertSessionDetails(details: List<TaskSessionDetail>) {
        if (details.isEmpty()) return

        val merged = this.details.associateBy(TaskSessionDetail::key).toMutableMap()
        details.forEach { detail ->
            merged[detail.key] = detail
        }
        this.details = merged.values.toList()
    }

    override suspend fun removeSessionDetails(keys: List<TaskSessionKey>) {
        if (keys.isEmpty()) return
        val keysToRemove = keys.toSet()
        details = details.filterNot { detail -> detail.key in keysToRemove }
    }
}

private class FakeTaskMailCreateSessionClient(
    private val result: TaskMailCreateSessionResult,
) : TaskMailCreateSessionClient {
    var createSessionCallCount: Int = 0
    val sentDrafts = mutableListOf<net.thunderbird.feature.taskmail.internal.domain.newtask.TaskMailNewTaskDraft>()

    override suspend fun createSession(
        draft: net.thunderbird.feature.taskmail.internal.domain.newtask.TaskMailNewTaskDraft,
    ): TaskMailCreateSessionResult {
        createSessionCallCount += 1
        sentDrafts += draft
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

private fun sampleEnvironmentInventorySnapshot(): TaskEnvironmentInventorySnapshot {
    return TaskEnvironmentInventorySnapshot(
        snapshotId = "env_snap_001",
        generatedAt = "2026-03-27T09:40:00",
        inventoryState = "fresh",
        refreshAfterSeconds = 15,
        pcs = listOf(
            TaskEnvironmentPc(
                pcId = "pc_workstation_01",
                displayName = "Workstation",
                status = "online",
                lastSeenAt = "2026-03-27T09:39:58",
                workspaceInventoryState = "fresh",
                workspaceCount = 1,
                pcCapabilities = TaskEnvironmentCapabilities(
                    supportedBackends = listOf("codex", "opencode"),
                    permissionModes = listOf("default", "highest"),
                ),
                routeAdmission = TaskEnvironmentRouteAdmission(
                    allowed = true,
                ),
                workspaces = listOf(
                    TaskEnvironmentWorkspace(
                        workspaceId = "workspace_android_app",
                        pcId = "pc_workstation_01",
                        displayName = "Android app",
                        repoPath = "E:/projects/android_task_manager",
                        workdir = "feature/taskmail",
                        presence = "present",
                        effectiveExecutionCapabilities = TaskEnvironmentCapabilities(
                            supportedBackends = listOf("codex"),
                            permissionModes = listOf("default"),
                        ),
                        routeAdmission = TaskEnvironmentRouteAdmission(
                            allowed = true,
                        ),
                    ),
                ),
            ),
        ),
    )
}
