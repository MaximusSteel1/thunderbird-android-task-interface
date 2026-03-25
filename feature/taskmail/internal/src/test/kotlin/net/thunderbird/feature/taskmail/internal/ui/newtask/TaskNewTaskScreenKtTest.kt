package net.thunderbird.feature.taskmail.internal.ui.newtask

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import app.k9mail.core.ui.compose.testing.BaseFakeViewModel
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEqualTo
import kotlinx.collections.immutable.toImmutableList
import net.thunderbird.core.ui.compose.theme2.k9mail.K9MailTheme2
import net.thunderbird.feature.taskmail.internal.domain.model.RelayBootstrapStatus
import net.thunderbird.feature.taskmail.internal.domain.model.TaskMailDirectOutcome
import net.thunderbird.feature.taskmail.internal.domain.model.TaskMailDirectSendEvidence
import net.thunderbird.feature.taskmail.internal.domain.model.TaskMailDirectSwitchGate
import net.thunderbird.feature.taskmail.internal.domain.model.TaskMailSenderAccount
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class TaskNewTaskScreenKtTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `content should show blocking state when sender account is unavailable`() {
        composeTestRule.setContent {
            K9MailTheme2 {
                TaskNewTaskContent(
                    state = TaskNewTaskContract.State(
                        senderAccountBlockingError = "Set up a mailbox account before sending TaskMail requests.",
                    ),
                    onEvent = {},
                )
            }
        }

        composeTestRule.onNodeWithText("Cannot send TaskMail yet").assertIsDisplayed()
        composeTestRule.onNodeWithText("Set up a mailbox account before sending TaskMail requests.").assertIsDisplayed()
    }

    @Test
    fun `content should show sender account selector when multiple accounts exist`() {
        composeTestRule.setContent {
            K9MailTheme2 {
                TaskNewTaskContent(
                    state = formState(
                        senderAccounts = listOf(screenPrimarySenderAccount, screenSecondarySenderAccount),
                        selectedSenderAccountId = null,
                    ),
                    onEvent = {},
                )
            }
        }

        composeTestRule
            .onNodeWithTag("TaskNewTaskFormList")
            .performScrollToNode(hasText("Select an account"))
        composeTestRule.onNodeWithText("Select an account").assertIsDisplayed()
    }

    @Test
    fun `content should show read only sender account row when only one account exists`() {
        composeTestRule.setContent {
            K9MailTheme2 {
                TaskNewTaskContent(
                    state = formState(),
                    onEvent = {},
                )
            }
        }

        composeTestRule
            .onNodeWithTag("TaskNewTaskFormList")
            .performScrollToNode(hasText("Primary <primary@example.com>"))
        composeTestRule.onNodeWithText("Primary <primary@example.com>").assertIsDisplayed()
    }

    @Test
    fun `content should show control target fields`() {
        composeTestRule.setContent {
            K9MailTheme2 {
                TaskNewTaskContent(
                    state = formState(),
                    onEvent = {},
                )
            }
        }

        composeTestRule.onNodeWithText("Route target").assertIsDisplayed()
        composeTestRule.onAllNodes(hasText("PC ID", substring = true)).assertCountEquals(1)
        composeTestRule
            .onNodeWithTag("TaskNewTaskFormList")
            .performScrollToNode(hasText("Workspace ID", substring = true))
        composeTestRule.onAllNodes(hasText("Workspace ID", substring = true)).assertCountEquals(1)
    }

    @Test
    fun `content should reveal advanced fields when toggle is pressed`() {
        composeTestRule.setContent {
            var state by remember { mutableStateOf(formState()) }

            K9MailTheme2 {
                TaskNewTaskContent(
                    state = state,
                    onEvent = { event ->
                        if (event == TaskNewTaskContract.Event.AdvancedToggleClicked) {
                            state = state.copy(
                                executionPolicyEditor = state.executionPolicyEditor.copy(
                                    isExpanded = !state.executionPolicyEditor.isExpanded,
                                ),
                            )
                        }
                    },
                )
            }
        }

        composeTestRule
            .onNodeWithTag("TaskNewTaskFormList")
            .performScrollToNode(hasText("Advanced options"))
        composeTestRule.onNodeWithText("Advanced options").performClick()

        composeTestRule
            .onNodeWithTag("TaskNewTaskFormList")
            .performScrollToNode(hasText("Timeout (minutes)"))
        composeTestRule.onNodeWithText("Timeout (minutes)").assertIsDisplayed()
        composeTestRule
            .onNodeWithTag("TaskNewTaskFormList")
            .performScrollToNode(hasText("Permission"))
        composeTestRule.onNodeWithText("Permission").assertIsDisplayed()
    }

    @Test
    fun `content should show send failure without hiding routing note`() {
        composeTestRule.setContent {
            K9MailTheme2 {
                TaskNewTaskContent(
                    state = formState().copy(
                        submitState = TaskNewTaskSubmitUiState(
                            sendError = "TaskMail bot mailbox is not configured.",
                        ),
                    ),
                    onEvent = {},
                )
            }
        }

        composeTestRule
            .onNodeWithTag("TaskNewTaskFormList")
            .performScrollToNode(hasText("TaskMail send failed"))
        composeTestRule.onNodeWithText("TaskMail send failed").assertIsDisplayed()
        composeTestRule.onNodeWithText("TaskMail bot mailbox is not configured.").assertIsDisplayed()
        composeTestRule.onNodeWithText(
            "PC/workspace is the route target now. Sender identity plus repository bridge remains only as a temporary compatibility bridge.",
        ).assertIsDisplayed()
        composeTestRule.onAllNodesWithText("Compatibility bridge").assertCountEquals(0)
    }

    @Test
    fun `content should show latest direct evidence summary for accepted result`() {
        composeTestRule.setContent {
            K9MailTheme2 {
                TaskNewTaskContent(
                    state = formState().copy(
                        lastDirectSendEvidence = TaskMailDirectSendEvidence(
                            bootstrapStatus = RelayBootstrapStatus.HelloAck,
                            outcome = TaskMailDirectOutcome.DirectAccepted,
                            switchGate = TaskMailDirectSwitchGate.KeepDirectDefault,
                            requestId = "req_001",
                            receiptId = "receipt-restore",
                            transportMessageId = "transport-1",
                        ),
                    ),
                    onEvent = {},
                )
            }
        }

        composeTestRule
            .onNodeWithTag("TaskNewTaskFormList")
            .performScrollToNode(hasTestTag("TaskNewTaskLatestDirectEvidence"))
        composeTestRule.onNodeWithTag("TaskNewTaskLatestDirectEvidence").assertIsDisplayed()
        composeTestRule.onNodeWithText("Latest dispatch result").assertIsDisplayed()
        composeTestRule.onNodeWithText("Direct accepted").assertIsDisplayed()
        composeTestRule.onNodeWithText("Switch gate: Keep direct default").assertIsDisplayed()
        composeTestRule.onNodeWithText("Hello ack").assertIsDisplayed()
        composeTestRule.onNodeWithText("req_001").assertIsDisplayed()
        composeTestRule
            .onNodeWithTag("TaskNewTaskFormList")
            .performScrollToNode(hasText("receipt-restore"))
        composeTestRule.onNodeWithText("receipt-restore").assertIsDisplayed()
        composeTestRule
            .onNodeWithTag("TaskNewTaskFormList")
            .performScrollToNode(hasText("transport-1"))
        composeTestRule.onNodeWithText("transport-1").assertIsDisplayed()
    }

    @Test
    fun `content should show fallback and error details for latest direct evidence`() {
        composeTestRule.setContent {
            K9MailTheme2 {
                TaskNewTaskContent(
                    state = formState().copy(
                        lastDirectSendEvidence = TaskMailDirectSendEvidence(
                            bootstrapStatus = RelayBootstrapStatus.HelloAck,
                            outcome = TaskMailDirectOutcome.DirectRejected,
                            switchGate = TaskMailDirectSwitchGate.SwitchBlocker,
                            fallbackReason = "unsupported_action",
                            errorMessage = "invalid_payload: task_text is required",
                        ),
                    ),
                    onEvent = {},
                )
            }
        }

        composeTestRule
            .onNodeWithTag("TaskNewTaskFormList")
            .performScrollToNode(hasText("Direct rejected"))
        composeTestRule.onNodeWithText("Direct rejected").assertIsDisplayed()
        composeTestRule.onNodeWithText("Switch gate: Switch blocker").assertIsDisplayed()
        composeTestRule
            .onNodeWithTag("TaskNewTaskFormList")
            .performScrollToNode(hasText("Dispatch detail"))
        composeTestRule.onNodeWithText("Dispatch detail").assertIsDisplayed()
        composeTestRule
            .onNodeWithTag("TaskNewTaskFormList")
            .performScrollToNode(hasText("unsupported_action"))
        composeTestRule.onNodeWithText("unsupported_action").assertIsDisplayed()
        composeTestRule
            .onNodeWithTag("TaskNewTaskFormList")
            .performScrollToNode(hasText("Error"))
        composeTestRule.onNodeWithText("Error").assertIsDisplayed()
        composeTestRule
            .onNodeWithTag("TaskNewTaskFormList")
            .performScrollToNode(hasText("invalid_payload: task_text is required"))
        composeTestRule.onNodeWithText("invalid_payload: task_text is required").assertIsDisplayed()
    }

    @Test
    fun `content should dispatch send clicked`() {
        var sendClicked = false

        composeTestRule.setContent {
            K9MailTheme2 {
                TaskNewTaskContent(
                    state = formState(),
                    onEvent = { event ->
                        if (event == TaskNewTaskContract.Event.SendClicked) {
                            sendClicked = true
                        }
                    },
                )
            }
        }

        composeTestRule
            .onNodeWithTag("TaskNewTaskFormList")
            .performScrollToNode(hasTestTag("TaskNewTaskSendButton"))
        composeTestRule.onNodeWithTag("TaskNewTaskSendButton").performClick()

        assertThat(sendClicked).isEqualTo(true)
    }

    @Test
    fun `content should dispatch choose repo clicked`() {
        var chooseRepoClicked = false

        composeTestRule.setContent {
            K9MailTheme2 {
                TaskNewTaskContent(
                    state = formState(),
                    onEvent = { event ->
                        if (event == TaskNewTaskContract.Event.ChooseRepoClicked) {
                            chooseRepoClicked = true
                        }
                    },
                )
            }
        }

        composeTestRule
            .onNodeWithTag("TaskNewTaskFormList")
            .performScrollToNode(hasTestTag("TaskNewTaskChooseRepoButton"))
        composeTestRule.onNodeWithTag("TaskNewTaskChooseRepoButton").performClick()

        assertThat(chooseRepoClicked).isEqualTo(true)
    }

    @Test
    fun `screen should dispatch selected repo path and consume it`() {
        val repoPath = "E:/projects/android_task_manager"
        val viewModel = FakeTaskNewTaskViewModel()
        var consumed = false

        composeTestRule.setContent {
            K9MailTheme2 {
                TaskNewTaskScreen(
                    onBack = {},
                    onOpenProjectSync = {},
                    selectedRepoPath = repoPath,
                    onSelectedRepoPathConsumed = {
                        consumed = true
                    },
                    viewModel = viewModel,
                )
            }
        }

        composeTestRule.waitForIdle()

        assertThat(
            viewModel.events.filterIsInstance<TaskNewTaskContract.Event.RepoChanged>().map { it.value },
        ).containsExactly(repoPath)
        assertThat(consumed).isEqualTo(true)
    }
}

private fun formState(
    senderAccounts: List<TaskMailSenderAccount> = listOf(screenPrimarySenderAccount),
    selectedSenderAccountId: String? = screenPrimarySenderAccount.accountUuid,
): TaskNewTaskContract.State {
    return TaskNewTaskContract.State(
        senderAccounts = senderAccounts.toImmutableList(),
        selectedSenderAccountId = selectedSenderAccountId,
    )
}

private val screenPrimarySenderAccount = TaskMailSenderAccount(
    accountUuid = "account_primary",
    displayName = "Primary",
    emailAddress = "primary@example.com",
)

private val screenSecondarySenderAccount = TaskMailSenderAccount(
    accountUuid = "account_secondary",
    displayName = "Secondary",
    emailAddress = "secondary@example.com",
)

private class FakeTaskNewTaskViewModel(
    initialState: TaskNewTaskContract.State = TaskNewTaskContract.State(),
) : BaseFakeViewModel<TaskNewTaskContract.State, TaskNewTaskContract.Event, TaskNewTaskContract.Effect>(
    initialState = initialState,
),
    TaskNewTaskContract.ViewModel
