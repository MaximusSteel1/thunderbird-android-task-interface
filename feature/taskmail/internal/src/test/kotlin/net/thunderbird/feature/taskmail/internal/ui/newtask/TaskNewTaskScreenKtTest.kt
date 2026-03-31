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
import net.thunderbird.feature.taskmail.internal.domain.model.TaskMailSenderAccount
import net.thunderbird.feature.taskmail.internal.domain.model.TaskReplyAttachment
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
    fun `content should keep form available when sender account is unavailable`() {
        composeTestRule.setContent {
            K9MailTheme2 {
                TaskNewTaskContent(
                    state = formState(senderAccounts = emptyList()),
                    onEvent = {},
                )
            }
        }

        composeTestRule.onNodeWithText("Target environment").assertIsDisplayed()
        composeTestRule.onNodeWithTag("TaskNewTaskFormList").assertIsDisplayed()
    }

    @Test
    fun `content should not show sender selector when multiple accounts exist`() {
        composeTestRule.setContent {
            K9MailTheme2 {
                TaskNewTaskContent(
                    state = formState(senderAccounts = listOf(screenPrimarySenderAccount, screenSecondarySenderAccount)),
                    onEvent = {},
                )
            }
        }

        composeTestRule.onAllNodesWithText("Send from").assertCountEquals(0)
        composeTestRule.onAllNodesWithText("Select an account").assertCountEquals(0)
    }

    @Test
    fun `content should not show sender row when only one account exists`() {
        composeTestRule.setContent {
            K9MailTheme2 {
                TaskNewTaskContent(
                    state = formState(),
                    onEvent = {},
                )
            }
        }

        composeTestRule.onAllNodesWithText("Send from").assertCountEquals(0)
        composeTestRule.onAllNodesWithText("Primary <primary@example.com>").assertCountEquals(0)
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

        composeTestRule.onNodeWithText("Target environment").assertIsDisplayed()
        composeTestRule
            .onNodeWithTag("TaskNewTaskFormList")
            .performScrollToNode(hasText("PC inventory is unavailable right now. You can still enter the target ID manually."))
        composeTestRule.onNodeWithText(
            "PC inventory is unavailable right now. You can still enter the target ID manually.",
        ).assertIsDisplayed()
        composeTestRule
            .onNodeWithTag("TaskNewTaskFormList")
            .performScrollToNode(hasText("Select a PC first, or enter the target workspace ID manually."))
        composeTestRule.onNodeWithText(
            "Select a PC first, or enter the target workspace ID manually.",
        ).assertIsDisplayed()
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
        composeTestRule
            .onNodeWithTag("TaskNewTaskFormList")
            .performScrollToNode(hasText("TaskMail bot mailbox is not configured."))
        composeTestRule.onNodeWithText("TaskMail bot mailbox is not configured.").assertIsDisplayed()
        composeTestRule
            .onNodeWithTag("TaskNewTaskFormList")
            .performScrollToNode(hasText("Sending this form creates a new session on the selected target environment."))
        composeTestRule.onNodeWithText(
            "Sending this form creates a new session on the selected target environment.",
        ).assertIsDisplayed()
        composeTestRule.onAllNodesWithText("Compatibility bridge").assertCountEquals(0)
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
    fun `content should show selected input attachments and remove action`() {
        composeTestRule.setContent {
            K9MailTheme2 {
                TaskNewTaskContent(
                    state = formState().copy(
                        taskInput = TaskNewTaskTaskInputUiState(
                            attachments = listOf(
                                TaskReplyAttachment(
                                    id = "content://taskmail/sketch",
                                    uriString = "content://taskmail/sketch",
                                    displayName = "sketch.png",
                                    contentType = "image/png",
                                    sizeBytes = 2048L,
                                    isImage = true,
                                ),
                            ).toImmutableList(),
                        ),
                    ),
                    onEvent = {},
                )
            }
        }

        composeTestRule
            .onNodeWithTag("TaskNewTaskFormList")
            .performScrollToNode(hasText("sketch.png"))
        composeTestRule.onNodeWithText("sketch.png").assertIsDisplayed()
        composeTestRule.onNodeWithText("Remove").assertIsDisplayed()
        composeTestRule.onAllNodesWithText("Input attachments coming soon").assertCountEquals(0)
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
                    onOpenSession = { _, _ -> },
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
): TaskNewTaskContract.State {
    return TaskNewTaskContract.State(
        senderAccounts = senderAccounts.toImmutableList(),
        selectedSenderAccountId = senderAccounts.singleOrNull()?.accountUuid,
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
