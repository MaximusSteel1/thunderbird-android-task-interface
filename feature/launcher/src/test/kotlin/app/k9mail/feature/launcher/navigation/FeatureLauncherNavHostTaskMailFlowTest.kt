package app.k9mail.feature.launcher.navigation

import android.app.Application
import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.foundation.text.BasicText
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navDeepLink
import app.k9mail.feature.account.edit.navigation.AccountEditNavigation
import app.k9mail.feature.account.edit.navigation.AccountEditRoute
import app.k9mail.feature.account.setup.navigation.AccountSetupNavigation
import app.k9mail.feature.account.setup.navigation.AccountSetupRoute
import app.k9mail.feature.launcher.FeatureLauncherExternalContract.MessageListLauncher
import app.k9mail.feature.launcher.FeatureLauncherTarget
import app.k9mail.feature.onboarding.main.navigation.OnboardingNavigation
import app.k9mail.feature.onboarding.main.navigation.OnboardingRoute
import kotlin.test.assertEquals
import net.thunderbird.feature.account.settings.api.AccountSettingsNavigation
import net.thunderbird.feature.account.settings.api.AccountSettingsRoute
import net.thunderbird.feature.debug.settings.navigation.SecretDebugSettingsNavigation
import net.thunderbird.feature.debug.settings.navigation.SecretDebugSettingsRoute
import net.thunderbird.feature.funding.api.FundingNavigation
import net.thunderbird.feature.funding.api.FundingRoute
import net.thunderbird.feature.taskmail.api.TaskMailNavigation
import net.thunderbird.feature.taskmail.api.TaskMailNavigationResultKeys
import net.thunderbird.feature.taskmail.api.TaskMailRoute
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class FeatureLauncherNavHostTaskMailFlowTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `taskmail deep link should keep launcher back stack scoped to taskmail flow`() {
        var launcherExitCalls = 0
        lateinit var navController: NavHostController

        composeTestRule.setContent {
            navController = rememberNavController()

            androidx.compose.runtime.LaunchedEffect(Unit) {
                navController.handleDeepLink(
                    Intent(
                        Intent.ACTION_VIEW,
                        FeatureLauncherTarget.TaskMail.deepLinkUri,
                    ),
                )
            }

            FeatureLauncherNavHost(
                navController = navController,
                onBack = { launcherExitCalls++ },
                messageListLauncher = MessageListLauncher {},
                accountEditNavigation = EmptyAccountEditNavigation(),
                accountSettingsNavigation = EmptyAccountSettingsNavigation(),
                accountSetupNavigation = EmptyAccountSetupNavigation(),
                onboardingNavigation = FakeOnboardingNavigation(),
                fundingNavigation = EmptyFundingNavigation(),
                secretDebugSettingsNavigation = EmptySecretDebugSettingsNavigation(),
                taskMailNavigation = FakeTaskMailNavigation(),
            )
        }

        composeTestRule.onNodeWithText("TaskMail workspace").assertIsDisplayed()
        composeTestRule.onNodeWithText("Open detail").performClick()
        composeTestRule.onNodeWithText("TaskMail detail").assertIsDisplayed()
        composeTestRule.onNodeWithText("Detail back").performClick()
        composeTestRule.onNodeWithText("TaskMail workspace").assertIsDisplayed()
        assertEquals(0, launcherExitCalls)

        composeTestRule.runOnIdle {
            taskMailOnBack(
                popBackStack = navController::popBackStack,
                exitLauncher = { launcherExitCalls++ },
            ).invoke()
        }

        assertEquals(1, launcherExitCalls)
        composeTestRule.onAllNodesWithText("Onboarding").assertCountEquals(0)
    }

    @Test
    fun `taskmail project sync should return selected repo to new task in launcher host`() {
        lateinit var navController: NavHostController

        composeTestRule.setContent {
            navController = rememberNavController()

            androidx.compose.runtime.LaunchedEffect(Unit) {
                navController.handleDeepLink(
                    Intent(
                        Intent.ACTION_VIEW,
                        FeatureLauncherTarget.TaskMail.deepLinkUri,
                    ),
                )
            }

            FeatureLauncherNavHost(
                navController = navController,
                onBack = {},
                messageListLauncher = MessageListLauncher {},
                accountEditNavigation = EmptyAccountEditNavigation(),
                accountSettingsNavigation = EmptyAccountSettingsNavigation(),
                accountSetupNavigation = EmptyAccountSetupNavigation(),
                onboardingNavigation = FakeOnboardingNavigation(),
                fundingNavigation = EmptyFundingNavigation(),
                secretDebugSettingsNavigation = EmptySecretDebugSettingsNavigation(),
                taskMailNavigation = FakeTaskMailRepoSelectionNavigation(),
            )
        }

        composeTestRule.onNodeWithText("TaskMail workspace").assertIsDisplayed()
        composeTestRule.onNodeWithText("Open new task").performClick()
        composeTestRule.onNodeWithText("Repo: none").assertIsDisplayed()
        composeTestRule.onNodeWithText("Choose repo").performClick()
        composeTestRule.onNodeWithText("TaskMail project sync").assertIsDisplayed()
        composeTestRule.onNodeWithText("Use repo").performClick()
        composeTestRule.onNodeWithText("Repo: E:/projects/android_task_manager").assertIsDisplayed()
    }

    @Test
    fun `taskmail project sync launched from workspace should open new task with selected repo in launcher host`() {
        lateinit var navController: NavHostController

        composeTestRule.setContent {
            navController = rememberNavController()

            androidx.compose.runtime.LaunchedEffect(Unit) {
                navController.handleDeepLink(
                    Intent(
                        Intent.ACTION_VIEW,
                        FeatureLauncherTarget.TaskMail.deepLinkUri,
                    ),
                )
            }

            FeatureLauncherNavHost(
                navController = navController,
                onBack = {},
                messageListLauncher = MessageListLauncher {},
                accountEditNavigation = EmptyAccountEditNavigation(),
                accountSettingsNavigation = EmptyAccountSettingsNavigation(),
                accountSetupNavigation = EmptyAccountSetupNavigation(),
                onboardingNavigation = FakeOnboardingNavigation(),
                fundingNavigation = EmptyFundingNavigation(),
                secretDebugSettingsNavigation = EmptySecretDebugSettingsNavigation(),
                taskMailNavigation = FakeTaskMailRepoSelectionNavigation(),
            )
        }

        composeTestRule.onNodeWithText("TaskMail workspace").assertIsDisplayed()
        composeTestRule.onNodeWithText("Open project list").performClick()
        composeTestRule.onNodeWithText("TaskMail project sync").assertIsDisplayed()
        composeTestRule.onNodeWithText("Use repo").performClick()
        composeTestRule.onNodeWithText("Repo: E:/projects/android_task_manager").assertIsDisplayed()
    }

    @Test
    fun `taskmail settings deep link should open settings route in launcher host`() {
        composeTestRule.setContent {
            val navController = rememberNavController()

            androidx.compose.runtime.LaunchedEffect(Unit) {
                navController.handleDeepLink(
                    Intent(
                        Intent.ACTION_VIEW,
                        FeatureLauncherTarget.TaskMailSettings.deepLinkUri,
                    ),
                )
            }

            FeatureLauncherNavHost(
                navController = navController,
                onBack = {},
                messageListLauncher = MessageListLauncher {},
                accountEditNavigation = EmptyAccountEditNavigation(),
                accountSettingsNavigation = EmptyAccountSettingsNavigation(),
                accountSetupNavigation = EmptyAccountSetupNavigation(),
                onboardingNavigation = FakeOnboardingNavigation(),
                fundingNavigation = EmptyFundingNavigation(),
                secretDebugSettingsNavigation = EmptySecretDebugSettingsNavigation(),
                taskMailNavigation = FakeTaskMailSettingsNavigation(),
            )
        }

        composeTestRule.onNodeWithText("TaskMail settings").assertIsDisplayed()
    }
}

private class FakeOnboardingNavigation : OnboardingNavigation {
    override fun registerRoutes(
        navGraphBuilder: NavGraphBuilder,
        onBack: () -> Unit,
        onFinish: (OnboardingRoute) -> Unit,
    ) {
        navGraphBuilder.composable<OnboardingRoute.Onboarding> {
            BasicText("Onboarding")
        }
    }
}

private class FakeTaskMailNavigation : TaskMailNavigation {
    override fun registerRoutes(
        navGraphBuilder: NavGraphBuilder,
        onBack: () -> Unit,
        onFinish: (TaskMailRoute) -> Unit,
    ) {
        navGraphBuilder.composable<TaskMailRoute.Workspace>(
            deepLinks = listOf(
                navDeepLink<TaskMailRoute.Workspace>(
                    basePath = TaskMailRoute.Workspace.BASE_PATH,
                ),
            ),
        ) {
            Column {
                BasicText("TaskMail workspace")
                BasicText(
                    text = "Open detail",
                    modifier = Modifier.clickable {
                        onFinish(
                            TaskMailRoute.SessionDetail(
                                sessionId = "session-1",
                                threadId = "thread-1",
                            ),
                        )
                    },
                )
            }
        }

        navGraphBuilder.composable<TaskMailRoute.SessionDetail> {
            Column {
                BasicText("TaskMail detail")
                BasicText(
                    text = "Detail back",
                    modifier = Modifier.clickable(onClick = onBack),
                )
            }
        }
    }
}

private class FakeTaskMailRepoSelectionNavigation : TaskMailNavigation {
    override fun registerRoutes(
        navGraphBuilder: NavGraphBuilder,
        onBack: () -> Unit,
        onFinish: (TaskMailRoute) -> Unit,
    ) {
        registerRoutes(
            navGraphBuilder = navGraphBuilder,
            onBack = onBack,
            onFinish = onFinish,
            onRepoSelected = {},
        )
    }

    override fun registerRoutes(
        navGraphBuilder: NavGraphBuilder,
        onBack: () -> Unit,
        onFinish: (TaskMailRoute) -> Unit,
        onRepoSelected: (String) -> Unit,
    ) {
        navGraphBuilder.composable<TaskMailRoute.Workspace>(
            deepLinks = listOf(
                navDeepLink<TaskMailRoute.Workspace>(
                    basePath = TaskMailRoute.Workspace.BASE_PATH,
                ),
            ),
        ) {
            Column {
                BasicText("TaskMail workspace")
                BasicText(
                    text = "Open new task",
                    modifier = Modifier.clickable {
                        onFinish(TaskMailRoute.NewTask)
                    },
                )
                BasicText(
                    text = "Open project list",
                    modifier = Modifier.clickable {
                        onFinish(TaskMailRoute.ProjectSync)
                    },
                )
            }
        }

        navGraphBuilder.composable<TaskMailRoute.NewTask> { backStackEntry ->
            val selectedRepoPath by backStackEntry.savedStateHandle
                .getStateFlow<String?>(TaskMailNavigationResultKeys.SELECTED_REPO_PATH, null)
                .collectAsState()

            Column {
                BasicText("TaskMail new task")
                BasicText("Repo: ${selectedRepoPath ?: "none"}")
                BasicText(
                    text = "Choose repo",
                    modifier = Modifier.clickable {
                        onFinish(TaskMailRoute.ProjectSync)
                    },
                )
                BasicText(
                    text = "Back",
                    modifier = Modifier.clickable(onClick = onBack),
                )
            }
        }

        navGraphBuilder.composable<TaskMailRoute.ProjectSync> {
            Column {
                BasicText("TaskMail project sync")
                BasicText(
                    text = "Use repo",
                    modifier = Modifier.clickable {
                        onRepoSelected("E:/projects/android_task_manager")
                    },
                )
            }
        }
    }
}

private class FakeTaskMailSettingsNavigation : TaskMailNavigation {
    override fun registerRoutes(
        navGraphBuilder: NavGraphBuilder,
        onBack: () -> Unit,
        onFinish: (TaskMailRoute) -> Unit,
    ) {
        navGraphBuilder.composable<TaskMailRoute.Settings>(
            deepLinks = listOf(
                navDeepLink<TaskMailRoute.Settings>(
                    basePath = TaskMailRoute.Settings.BASE_PATH,
                ),
            ),
        ) {
            BasicText("TaskMail settings")
        }
    }
}

private class EmptyAccountEditNavigation : AccountEditNavigation {
    override fun registerRoutes(
        navGraphBuilder: NavGraphBuilder,
        onBack: () -> Unit,
        onFinish: (AccountEditRoute) -> Unit,
    ) = Unit
}

private class EmptyAccountSettingsNavigation : AccountSettingsNavigation {
    override fun registerRoutes(
        navGraphBuilder: NavGraphBuilder,
        onBack: () -> Unit,
        onFinish: (AccountSettingsRoute) -> Unit,
    ) = Unit
}

private class EmptyAccountSetupNavigation : AccountSetupNavigation {
    override fun registerRoutes(
        navGraphBuilder: NavGraphBuilder,
        onBack: () -> Unit,
        onFinish: (AccountSetupRoute) -> Unit,
    ) = Unit
}

private class EmptyFundingNavigation : FundingNavigation {
    override fun registerRoutes(
        navGraphBuilder: NavGraphBuilder,
        onBack: () -> Unit,
        onFinish: (FundingRoute) -> Unit,
    ) = Unit
}

private class EmptySecretDebugSettingsNavigation : SecretDebugSettingsNavigation {
    override fun registerRoutes(
        navGraphBuilder: NavGraphBuilder,
        onBack: () -> Unit,
        onFinish: (SecretDebugSettingsRoute) -> Unit,
    ) = Unit
}
