package net.thunderbird.feature.taskmail.internal.ui.workspace

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
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
}
