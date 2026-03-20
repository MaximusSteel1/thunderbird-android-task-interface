package net.thunderbird.feature.taskmail.internal.ui.settings

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import app.k9mail.core.ui.compose.testing.BaseFakeViewModel
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEqualTo
import net.thunderbird.core.ui.compose.theme2.k9mail.K9MailTheme2
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class TaskMailSettingsScreenKtTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `screen should dispatch load data on first composition`() {
        val viewModel = FakeTaskMailSettingsViewModel()

        composeTestRule.setContent {
            K9MailTheme2 {
                TaskMailSettingsScreen(
                    onBack = {},
                    viewModel = viewModel,
                )
            }
        }

        composeTestRule.waitForIdle()

        assertThat(viewModel.events).containsExactly(TaskMailSettingsContract.Event.LoadData)
    }

    @Test
    fun `content should show build default note`() {
        composeTestRule.setContent {
            K9MailTheme2 {
                TaskMailSettingsContent(
                    state = TaskMailSettingsContract.State(
                        address = "bot@example.org",
                        isUsingBuildDefault = true,
                    ),
                    onEvent = {},
                )
            }
        }

        composeTestRule.onNodeWithText("Using build default").assertIsDisplayed()
    }

    @Test
    fun `content should dispatch confirm clicked`() {
        var confirmClicked = false

        composeTestRule.setContent {
            K9MailTheme2 {
                TaskMailSettingsContent(
                    state = TaskMailSettingsContract.State(address = "bot@example.org"),
                    onEvent = { event ->
                        if (event == TaskMailSettingsContract.Event.ConfirmClicked) {
                            confirmClicked = true
                        }
                    },
                )
            }
        }

        composeTestRule.onNodeWithTag("TaskMailSettingsConfirmButton").performClick()

        assertThat(confirmClicked).isEqualTo(true)
    }
}

private class FakeTaskMailSettingsViewModel(
    initialState: TaskMailSettingsContract.State = TaskMailSettingsContract.State(),
) : BaseFakeViewModel<TaskMailSettingsContract.State, TaskMailSettingsContract.Event, TaskMailSettingsContract.Effect>(
    initialState = initialState,
),
    TaskMailSettingsContract.ViewModel
