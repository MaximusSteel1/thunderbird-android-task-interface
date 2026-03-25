package net.thunderbird.feature.taskmail.internal.ui.workspace

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
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
        var clickedWorkspaceId: String? = null

        composeTestRule.setContent {
            K9MailTheme2 {
                TaskWorkspaceContent(
                    state = TaskWorkspaceContract.State(
                        attentionSessions = listOf(
                            TaskSessionItemUi(
                                workspaceId = "workspace_001",
                                sessionId = "session_001",
                                stableId = "workspace_001::session_001",
                                sessionName = "Build TaskMail Phase 1",
                                status = "WaitingUser",
                                backend = "Codex",
                                lastSummary = "Parser layer is complete.",
                                pendingQuestion = true,
                                routeLabel = "android_task_manager · feature/taskmail",
                                lastUpdatedAt = 1_742_000_000_000,
                            ),
                        ),
                        workspaceSummaries = sampleWorkspaceSummaries(),
                    ),
                    onEvent = { event ->
                        if (event is TaskWorkspaceContract.Event.SessionClicked) {
                            clickedWorkspaceId = event.workspaceId
                            clickedSessionId = event.sessionId
                        }
                    },
                )
            }
        }

        composeTestRule.onNodeWithText("Build TaskMail Phase 1", substring = true).performClick()

        assertThat(clickedSessionId).isEqualTo("session_001")
        assertThat(clickedWorkspaceId).isEqualTo("workspace_001")
    }

    @Test
    fun `content should ignore session rows that do not have a session id`() {
        var clickedSessionId: String? = "initial"

        composeTestRule.setContent {
            K9MailTheme2 {
                TaskWorkspaceContent(
                    state = TaskWorkspaceContract.State(
                        recentSessions = listOf(
                            TaskSessionItemUi(
                                workspaceId = "workspace_001",
                                sessionId = null,
                                stableId = "compat::workspace_001::thread_321",
                                sessionName = "Fallback thread session",
                                status = "Unknown",
                                backend = "Codex",
                                lastSummary = "No session id yet.",
                                pendingQuestion = false,
                                routeLabel = "android_task_manager · feature/taskmail",
                                lastUpdatedAt = 1_742_000_000_000,
                            ),
                        ),
                        workspaceSummaries = sampleWorkspaceSummaries(),
                    ),
                    onEvent = { event ->
                        if (event is TaskWorkspaceContract.Event.SessionClicked) {
                            clickedSessionId = event.sessionId
                        }
                    },
                )
            }
        }

        composeTestRule
            .onNodeWithTag("TaskWorkspaceHomeList")
            .performScrollToNode(hasText("Fallback thread session", substring = true))
        composeTestRule.onNodeWithText("Fallback thread session", substring = true).performClick()

        assertThat(clickedSessionId).isEqualTo("initial")
    }

    @Test
    fun `content should show refresh warning without hiding workspace list`() {
        composeTestRule.setContent {
            K9MailTheme2 {
                TaskWorkspaceContent(
                    state = TaskWorkspaceContract.State(
                        refreshError = "TaskMail sync did not complete.",
                        attentionSessions = listOf(
                            TaskSessionItemUi(
                                workspaceId = "workspace_001",
                                sessionId = "session_001",
                                stableId = "workspace_001::session_001",
                                sessionName = "Build TaskMail Phase 1",
                                status = "WaitingUser",
                                backend = "Codex",
                                lastSummary = "Parser layer is complete.",
                                pendingQuestion = true,
                                routeLabel = "android_task_manager · feature/taskmail",
                                lastUpdatedAt = 1_742_000_000_000,
                            ),
                        ),
                        workspaceSummaries = sampleWorkspaceSummaries(),
                    ),
                    onEvent = {},
                )
            }
        }

        composeTestRule.onNodeWithText("TaskMail refresh failed").assertIsDisplayed()
        composeTestRule.onNodeWithText("Build TaskMail Phase 1", substring = true).assertExists()
    }
}

private fun sampleWorkspaceSummaries(): List<TaskWorkspaceItemUi> {
    return listOf(
        TaskWorkspaceItemUi(
            title = "android_task_manager",
            subtitle = "feature/taskmail",
            sessionCountLabel = "1 session",
            sessions = listOf(
                TaskSessionItemUi(
                    workspaceId = "workspace_001",
                    sessionId = "session_001",
                    stableId = "workspace_001::session_001",
                    sessionName = "Build TaskMail Phase 1",
                    status = "WaitingUser",
                    backend = "Codex",
                    lastSummary = "Parser layer is complete.",
                    pendingQuestion = true,
                    routeLabel = "android_task_manager · feature/taskmail",
                    lastUpdatedAt = 1_742_000_000_000,
                ),
            ),
        ),
    )
}
