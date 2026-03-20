package net.thunderbird.feature.taskmail.internal.ui.projectsync

import android.app.Application
import androidx.compose.ui.test.junit4.createComposeRule
import app.k9mail.core.ui.compose.testing.BaseFakeViewModel
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
class TaskProjectSyncScreenKtTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `screen should forward return repo effect to callback`() {
        val repoPath = "E:/projects/android_task_manager"
        val viewModel = FakeTaskProjectSyncViewModel()
        var selectedRepoPath: String? = null

        composeTestRule.setContent {
            K9MailTheme2 {
                TaskProjectSyncScreen(
                    onBack = {},
                    onRepoSelected = { selectedRepoPath = it },
                    viewModel = viewModel,
                )
            }
        }

        composeTestRule.runOnIdle {
            viewModel.effect(TaskProjectSyncContract.Effect.ReturnRepo(repoPath))
        }
        composeTestRule.waitForIdle()

        assertThat(selectedRepoPath).isEqualTo(repoPath)
    }
}

private class FakeTaskProjectSyncViewModel(
    initialState: TaskProjectSyncContract.State = TaskProjectSyncContract.State(),
) : BaseFakeViewModel<TaskProjectSyncContract.State, TaskProjectSyncContract.Event, TaskProjectSyncContract.Effect>(
    initialState = initialState,
),
    TaskProjectSyncContract.ViewModel
