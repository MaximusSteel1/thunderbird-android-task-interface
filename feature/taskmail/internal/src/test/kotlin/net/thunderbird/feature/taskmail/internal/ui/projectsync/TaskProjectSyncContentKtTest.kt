package net.thunderbird.feature.taskmail.internal.ui.projectsync

import android.app.Application
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import assertk.assertThat
import assertk.assertions.isEqualTo
import kotlinx.collections.immutable.toImmutableList
import net.thunderbird.core.ui.compose.theme2.k9mail.K9MailTheme2
import net.thunderbird.feature.taskmail.internal.domain.model.TaskMailProjectSyncProject
import net.thunderbird.feature.taskmail.internal.domain.model.TaskMailProjectSyncResult
import net.thunderbird.feature.taskmail.internal.domain.model.TaskMailProjectSyncRoot
import net.thunderbird.feature.taskmail.internal.domain.model.TaskMailSenderAccount
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class TaskProjectSyncContentKtTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `content should dispatch use repo clicked`() {
        var selectedRepoPath: String? = null

        composeTestRule.setContent {
            K9MailTheme2 {
                TaskProjectSyncContent(
                    state = TaskProjectSyncContract.State(
                        senderAccounts = listOf(projectSyncSenderAccount).toImmutableList(),
                        selectedSenderAccountId = projectSyncSenderAccount.accountUuid,
                        latestResult = projectSyncResult,
                    ),
                    onEvent = { event ->
                        if (event is TaskProjectSyncContract.Event.UseRepoClicked) {
                            selectedRepoPath = event.repoPath
                        }
                    },
                )
            }
        }

        composeTestRule
            .onNodeWithTag("TaskProjectSyncList")
            .performScrollToNode(hasTestTag("TaskProjectSyncUseRepoButton"))
        composeTestRule.onNodeWithTag("TaskProjectSyncUseRepoButton").performClick()

        assertThat(selectedRepoPath).isEqualTo("E:/projects/android_task_manager")
    }
}

private val projectSyncSenderAccount = TaskMailSenderAccount(
    accountUuid = "account_primary",
    displayName = "Primary",
    emailAddress = "primary@example.com",
)

private val projectSyncResult = TaskMailProjectSyncResult(
    receivedAt = 123L,
    scannedAt = "2026-03-17T12:34:56",
    roots = listOf(
        TaskMailProjectSyncRoot(
            rootPath = "E:/projects",
            isAvailable = true,
            folderCount = 1,
            unavailableReason = null,
            projects = listOf(
                TaskMailProjectSyncProject(
                    displayName = "android_task_manager",
                    repoPath = "E:/projects/android_task_manager",
                ),
            ),
        ),
    ),
)
