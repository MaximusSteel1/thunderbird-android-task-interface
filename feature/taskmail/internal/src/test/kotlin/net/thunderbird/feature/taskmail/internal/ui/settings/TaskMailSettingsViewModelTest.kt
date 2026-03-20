package net.thunderbird.feature.taskmail.internal.ui.settings

import app.k9mail.core.ui.compose.testing.mvi.MviContext
import app.k9mail.core.ui.compose.testing.mvi.MviTurbines
import app.k9mail.core.ui.compose.testing.mvi.advanceUntilIdle
import app.k9mail.core.ui.compose.testing.mvi.runMviTest
import app.k9mail.core.ui.compose.testing.mvi.turbinesWithInitialStateCheck
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEqualTo
import com.fsck.k9.EmailAddressValidator
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import net.thunderbird.core.testing.coroutines.MainDispatcherHelper
import net.thunderbird.feature.taskmail.internal.domain.model.TaskMailBotMailboxSettings
import net.thunderbird.feature.taskmail.internal.domain.repository.TaskMailBotMailboxSettingsRepository
import net.thunderbird.feature.taskmail.internal.domain.usecase.GetTaskMailBotMailboxSettings
import net.thunderbird.feature.taskmail.internal.domain.usecase.SaveTaskMailBotMailboxSettings

class TaskMailSettingsViewModelTest {

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
    fun `load data should surface build default address`() = runMviTest {
        with(
            TaskMailSettingsViewModelRobot(
                mviContext = this,
                initialSettings = TaskMailBotMailboxSettings(
                    address = "bot@example.org",
                    source = TaskMailBotMailboxSettings.Source.BuildDefault,
                ),
            ),
        ) {
            start()
            loadData()

            assertThat(viewModelState().address).isEqualTo("bot@example.org")
            assertThat(viewModelState().isUsingBuildDefault).isEqualTo(true)
            ensureThatAllEventsAreConsumed()
        }
    }

    @Test
    fun `confirm should require address`() = runMviTest {
        with(TaskMailSettingsViewModelRobot(mviContext = this)) {
            start()
            loadData()
            confirm()

            assertThat(viewModelState().addressError).isEqualTo("Bot mailbox address is required.")
            assertThat(savedAddresses()).isEqualTo(emptyList())
            ensureThatAllEventsAreConsumed()
        }
    }

    @Test
    fun `confirm should validate email address`() = runMviTest {
        with(TaskMailSettingsViewModelRobot(mviContext = this)) {
            start()
            loadData()
            changeAddress("not-an-email")
            confirm()

            assertThat(viewModelState().addressError).isEqualTo("Enter a valid email address.")
            assertThat(savedAddresses()).isEqualTo(emptyList())
            ensureThatAllEventsAreConsumed()
        }
    }

    @Test
    fun `confirm should save trimmed address and navigate back`() = runMviTest {
        with(TaskMailSettingsViewModelRobot(mviContext = this)) {
            start()
            loadData()
            changeAddress("  bot@example.org  ")
            confirm()

            assertThat(savedAddresses()).containsExactly("bot@example.org")
            assertThat(viewModelState().address).isEqualTo("bot@example.org")
            assertThat(viewModelState().isUsingBuildDefault).isEqualTo(false)
            assertThat(awaitEffects()).containsExactly(
                TaskMailSettingsContract.Effect.ShowMessage("TaskMail bot mailbox saved."),
                TaskMailSettingsContract.Effect.NavigateBack,
            )
            ensureThatAllEventsAreConsumed()
        }
    }
}

private class TaskMailSettingsViewModelRobot(
    private val mviContext: MviContext,
    initialSettings: TaskMailBotMailboxSettings = TaskMailBotMailboxSettings(
        address = null,
        source = TaskMailBotMailboxSettings.Source.Missing,
    ),
    saveResult: Boolean = true,
) {
    private val repository = FakeTaskMailBotMailboxSettingsRepository(initialSettings, saveResult)
    private val viewModel = TaskMailSettingsViewModel(
        getTaskMailBotMailboxSettings = GetTaskMailBotMailboxSettings(repository),
        saveTaskMailBotMailboxSettings = SaveTaskMailBotMailboxSettings(repository),
        emailAddressValidator = EmailAddressValidator(),
    )
    private lateinit var turbines: MviTurbines<TaskMailSettingsContract.State, TaskMailSettingsContract.Effect>

    suspend fun start() {
        turbines = mviContext.turbinesWithInitialStateCheck(viewModel, TaskMailSettingsContract.State())
    }

    suspend fun loadData() {
        viewModel.event(TaskMailSettingsContract.Event.LoadData)
        mviContext.advanceUntilIdle()
    }

    fun changeAddress(value: String) {
        viewModel.event(TaskMailSettingsContract.Event.AddressChanged(value))
    }

    suspend fun confirm() {
        viewModel.event(TaskMailSettingsContract.Event.ConfirmClicked)
        mviContext.advanceUntilIdle()
    }

    suspend fun awaitEffects(): List<TaskMailSettingsContract.Effect> {
        return listOf(
            turbines.awaitEffectItem(),
            turbines.awaitEffectItem(),
        )
    }

    fun savedAddresses(): List<String> = repository.savedAddresses

    fun viewModelState(): TaskMailSettingsContract.State = viewModel.state.value

    suspend fun ensureThatAllEventsAreConsumed() {
        turbines.stateTurbine.cancelAndIgnoreRemainingEvents()
        turbines.effectTurbine.ensureAllEventsConsumed()
    }
}

private class FakeTaskMailBotMailboxSettingsRepository(
    initialSettings: TaskMailBotMailboxSettings,
    private val saveResult: Boolean,
) : TaskMailBotMailboxSettingsRepository {
    private var currentSettings = initialSettings
    val savedAddresses = mutableListOf<String>()

    override fun getSettings(): TaskMailBotMailboxSettings = currentSettings

    override fun saveAddress(address: String): Boolean {
        savedAddresses += address

        if (saveResult) {
            currentSettings = TaskMailBotMailboxSettings(
                address = address,
                source = TaskMailBotMailboxSettings.Source.Saved,
            )
        }

        return saveResult
    }
}
