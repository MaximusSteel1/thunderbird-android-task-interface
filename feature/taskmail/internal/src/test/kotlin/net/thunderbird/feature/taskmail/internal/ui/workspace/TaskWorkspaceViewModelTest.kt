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
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import net.thunderbird.core.testing.coroutines.MainDispatcherHelper
import net.thunderbird.feature.taskmail.internal.domain.model.TaskSessionDetail
import net.thunderbird.feature.taskmail.internal.domain.model.TaskSessionKey
import net.thunderbird.feature.taskmail.internal.domain.model.TaskWorkspaceSummary
import net.thunderbird.feature.taskmail.internal.domain.repository.TaskMailRepository
import net.thunderbird.feature.taskmail.internal.domain.usecase.GetTaskWorkspaceSummaries
import net.thunderbird.feature.taskmail.internal.preview.TaskMailPreviewData

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
        with(TaskWorkspaceViewModelRobot(this, FakeTaskMailRepository())) {
            start()
            loadData()
            assertLoadedContent()
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
}

private class TaskWorkspaceViewModelRobot(
    private val mviContext: MviContext,
    repository: TaskMailRepository,
) {
    private val viewModel = TaskWorkspaceViewModel(
        repository = repository,
        getTaskWorkspaceSummaries = GetTaskWorkspaceSummaries(repository),
    )
    private lateinit var turbines: MviTurbines<TaskWorkspaceContract.State, TaskWorkspaceContract.Effect>

    suspend fun start() {
        turbines = mviContext.turbinesWithInitialStateCheck(viewModel, TaskWorkspaceContract.State())
    }

    suspend fun loadData() {
        viewModel.event(TaskWorkspaceContract.Event.LoadData)
        mviContext.advanceUntilIdle()
    }

    suspend fun assertLoadedContent() {
        val state = viewModel.state.value
        assertThat(state.isLoading).isEqualTo(false)
        assertThat(state.error).isEqualTo(null)
        assertThat(state.workspaces).hasSize(1)
        assertThat(state.workspaces.first().sessions).hasSize(1)
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
                sessionId = "session_001",
                threadId = "thread_001",
            ),
        )
    }

    suspend fun assertOpenDetailEffect() {
        assertThat(turbines.awaitEffectItem()).isEqualTo(
            TaskWorkspaceContract.Effect.OpenSessionDetail(
                sessionId = "session_001",
                threadId = "thread_001",
            ),
        )
    }

    suspend fun ensureThatAllEventsAreConsumed() {
        turbines.stateTurbine.cancelAndIgnoreRemainingEvents()
        turbines.effectTurbine.ensureAllEventsConsumed()
    }
}

private class FakeTaskMailRepository(
    private val workspaces: List<TaskWorkspaceSummary> = TaskMailPreviewData.workspaceSummaries,
    private val shouldThrowWorkspaceError: Boolean = false,
) : TaskMailRepository {
    override suspend fun getTaskWorkspaceSummaries(): List<TaskWorkspaceSummary> {
        if (shouldThrowWorkspaceError) error("workspace error")
        return workspaces
    }

    override suspend fun getTaskSessionDetail(key: TaskSessionKey): TaskSessionDetail? = null
}
