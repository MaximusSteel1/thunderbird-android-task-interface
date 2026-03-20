package net.thunderbird.feature.taskmail.internal.ui.workspace

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import assertk.assertThat
import assertk.assertions.isEqualTo
import net.thunderbird.core.ui.compose.theme2.k9mail.K9MailTheme2
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class TaskWorkspaceScreenKtTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `content should show empty state`() {
        composeTestRule.setContent {
            K9MailTheme2 {
                TaskWorkspaceContent(
                    state = TaskWorkspaceContract.State(),
                    onEvent = {},
                )
            }
        }

        composeTestRule.onNodeWithText("No TaskMail sessions yet").assertIsDisplayed()
        composeTestRule.onNodeWithText("New task").assertIsDisplayed()
        composeTestRule.onNodeWithText("Project list").assertIsDisplayed()
    }

    @Test
    fun `content should dispatch project list clicked when top bar action is pressed`() {
        var projectListClicked = false

        composeTestRule.setContent {
            K9MailTheme2 {
                TaskWorkspaceContent(
                    state = TaskWorkspaceContract.State(),
                    onEvent = { event ->
                        if (event == TaskWorkspaceContract.Event.ProjectListClicked) {
                            projectListClicked = true
                        }
                    },
                )
            }
        }

        composeTestRule.onNodeWithContentDescription("Project list").performClick()

        assertThat(projectListClicked).isEqualTo(true)
    }

    @Test
    fun `content should dispatch new task clicked when top bar action is pressed`() {
        var newTaskClicked = false

        composeTestRule.setContent {
            K9MailTheme2 {
                TaskWorkspaceContent(
                    state = TaskWorkspaceContract.State(),
                    onEvent = { event ->
                        if (event == TaskWorkspaceContract.Event.NewTaskClicked) {
                            newTaskClicked = true
                        }
                    },
                )
            }
        }

        composeTestRule.onNodeWithContentDescription("New task").performClick()

        assertThat(newTaskClicked).isEqualTo(true)
    }

    @Test
    fun `content should dispatch project list clicked when empty state CTA is pressed`() {
        var projectListClicked = false

        composeTestRule.setContent {
            K9MailTheme2 {
                TaskWorkspaceContent(
                    state = TaskWorkspaceContract.State(),
                    onEvent = { event ->
                        if (event == TaskWorkspaceContract.Event.ProjectListClicked) {
                            projectListClicked = true
                        }
                    },
                )
            }
        }

        composeTestRule.onNodeWithText("Project list").performClick()

        assertThat(projectListClicked).isEqualTo(true)
    }

    @Test
    fun `content should dispatch new task clicked when empty state CTA is pressed`() {
        var newTaskClicked = false

        composeTestRule.setContent {
            K9MailTheme2 {
                TaskWorkspaceContent(
                    state = TaskWorkspaceContract.State(),
                    onEvent = { event ->
                        if (event == TaskWorkspaceContract.Event.NewTaskClicked) {
                            newTaskClicked = true
                        }
                    },
                )
            }
        }

        composeTestRule.onNodeWithText("New task").performClick()

        assertThat(newTaskClicked).isEqualTo(true)
    }

    @Test
    fun `content should dispatch session clicked when session row is pressed`() {
        var clickedSessionId: String? = null

        composeTestRule.setContent {
            K9MailTheme2 {
                TaskWorkspaceContent(
                    state = TaskWorkspaceContract.State(
                        workspaces = listOf(
                            TaskWorkspaceItemUi(
                                title = "android_task_manager",
                                subtitle = "feature/taskmail",
                                sessionCountLabel = "1 session",
                                sessions = listOf(
                                    TaskSessionItemUi(
                                        sessionId = "session_001",
                                        threadId = "thread_001",
                                        sessionName = "Build TaskMail Phase 1",
                                        status = "WaitingUser",
                                        backend = "Codex",
                                        lastSummary = "Parser layer is complete.",
                                        pendingQuestion = true,
                                    ),
                                ),
                            ),
                        ),
                    ),
                    onEvent = { event ->
                        if (event is TaskWorkspaceContract.Event.SessionClicked) {
                            clickedSessionId = event.sessionId
                        }
                    },
                )
            }
        }

        composeTestRule.onNodeWithText("Build TaskMail Phase 1", substring = true).performClick()

        assertThat(clickedSessionId).isEqualTo("session_001")
    }

    @Test
    fun `content should keep null session id when fallback thread session row is pressed`() {
        var clickedSessionId: String? = "initial"

        composeTestRule.setContent {
            K9MailTheme2 {
                TaskWorkspaceContent(
                    state = TaskWorkspaceContract.State(
                        workspaces = listOf(
                            TaskWorkspaceItemUi(
                                title = "android_task_manager",
                                subtitle = "feature/taskmail",
                                sessionCountLabel = "1 session",
                                sessions = listOf(
                                    TaskSessionItemUi(
                                        sessionId = null,
                                        threadId = "thread_321",
                                        sessionName = "Fallback thread session",
                                        status = "Unknown",
                                        backend = "Codex",
                                        lastSummary = "No session id yet.",
                                        pendingQuestion = false,
                                    ),
                                ),
                            ),
                        ),
                    ),
                    onEvent = { event ->
                        if (event is TaskWorkspaceContract.Event.SessionClicked) {
                            clickedSessionId = event.sessionId
                        }
                    },
                )
            }
        }

        composeTestRule.onNodeWithText("Fallback thread session", substring = true).performClick()

        assertThat(clickedSessionId).isEqualTo(null)
    }

    @Test
    fun `content should show refresh warning without hiding workspace list`() {
        composeTestRule.setContent {
            K9MailTheme2 {
                TaskWorkspaceContent(
                    state = TaskWorkspaceContract.State(
                        refreshError = "TaskMail sync did not complete.",
                        workspaces = listOf(
                            TaskWorkspaceItemUi(
                                title = "android_task_manager",
                                subtitle = "feature/taskmail",
                                sessionCountLabel = "1 session",
                                sessions = listOf(
                                    TaskSessionItemUi(
                                        sessionId = "session_001",
                                        threadId = "thread_001",
                                        sessionName = "Build TaskMail Phase 1",
                                        status = "WaitingUser",
                                        backend = "Codex",
                                        lastSummary = "Parser layer is complete.",
                                        pendingQuestion = true,
                                    ),
                                ),
                            ),
                        ),
                    ),
                    onEvent = {},
                )
            }
        }

        composeTestRule.onNodeWithText("TaskMail refresh failed").assertIsDisplayed()
        composeTestRule.onNodeWithText("Build TaskMail Phase 1", substring = true).assertExists()
    }
}
