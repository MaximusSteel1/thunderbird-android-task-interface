package net.thunderbird.feature.taskmail.internal.ui.component

import android.app.Application
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
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
class TaskCodeLocatorTextTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `component should show markdown label then open menu and copy full reference`() {
        val reference = "[README.md#L34](/E:/projects/mail_based_task_manager/README.md#L34)"
        var copiedLocator: String? = null

        composeTestRule.setContent {
            K9MailTheme2 {
                TaskCodeLocatorText(
                    text = reference,
                    style = TaskCodeLocatorTextStyle.BodyMedium,
                    onCopyLocator = { copiedLocator = it },
                )
            }
        }

        composeTestRule.onAllNodesWithText("README.md#L34", substring = true).assertCountEquals(2)
        composeTestRule.onAllNodesWithText(reference, substring = true).assertCountEquals(0)

        composeTestRule.onNodeWithTag("TaskCodeLocatorToken:$reference").performClick()
        composeTestRule.onNodeWithTag("TaskCodeLocatorMenu").assertIsDisplayed()
        composeTestRule.onNodeWithText(reference, substring = true).assertIsDisplayed()

        composeTestRule.onNodeWithTag("TaskCodeLocatorCopyButton").performClick()

        assertThat(copiedLocator).isEqualTo(reference)
        composeTestRule.onAllNodesWithText(reference, substring = true).assertCountEquals(0)
    }

    @Test
    fun `component should collapse bare absolute path to short tail`() {
        val text = "Command: node E:\\projects\\mail_based_task_manager\\scripts\\codex_sdk_sidecar\\dist\\index.js"

        composeTestRule.setContent {
            K9MailTheme2 {
                TaskCodeLocatorText(
                    text = text,
                    style = TaskCodeLocatorTextStyle.BodyMedium,
                )
            }
        }

        composeTestRule.onNodeWithText("Command: node ", substring = true).assertIsDisplayed()
        composeTestRule.onAllNodesWithText("index.js", substring = true).assertCountEquals(2)
        composeTestRule.onAllNodesWithText(
            "E:\\projects\\mail_based_task_manager\\scripts\\codex_sdk_sidecar\\dist\\index.js",
            substring = true,
        ).assertCountEquals(0)
    }
}
