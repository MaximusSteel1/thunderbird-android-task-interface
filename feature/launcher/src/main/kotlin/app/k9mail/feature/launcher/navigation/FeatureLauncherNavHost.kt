package app.k9mail.feature.launcher.navigation

import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import app.k9mail.feature.account.edit.navigation.AccountEditNavigation
import app.k9mail.feature.account.setup.navigation.AccountSetupNavigation
import app.k9mail.feature.account.setup.navigation.AccountSetupRoute
import app.k9mail.feature.launcher.FeatureLauncherExternalContract.MessageListLauncher
import app.k9mail.feature.onboarding.main.navigation.OnboardingNavigation
import app.k9mail.feature.onboarding.main.navigation.OnboardingRoute
import net.thunderbird.feature.account.settings.api.AccountSettingsNavigation
import net.thunderbird.feature.debug.settings.navigation.SecretDebugSettingsNavigation
import net.thunderbird.feature.debug.settings.navigation.SecretDebugSettingsRoute
import net.thunderbird.feature.funding.api.FundingNavigation
import net.thunderbird.feature.taskmail.api.TaskMailNavigation
import net.thunderbird.feature.taskmail.api.TaskMailNavigationResultKeys
import net.thunderbird.feature.taskmail.api.TaskMailRoute
import org.koin.compose.koinInject

@Composable
fun FeatureLauncherNavHost(
    navController: NavHostController,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    messageListLauncher: MessageListLauncher = koinInject(),
    accountEditNavigation: AccountEditNavigation = koinInject(),
    accountSettingsNavigation: AccountSettingsNavigation = koinInject(),
    accountSetupNavigation: AccountSetupNavigation = koinInject(),
    onboardingNavigation: OnboardingNavigation = koinInject(),
    fundingNavigation: FundingNavigation = koinInject(),
    secretDebugSettingsNavigation: SecretDebugSettingsNavigation = koinInject(),
    taskMailNavigation: TaskMailNavigation = koinInject(),
) {
    val activity = LocalActivity.current as ComponentActivity

    NavHost(
        navController = navController,
        startDestination = OnboardingRoute.Onboarding(),
        modifier = modifier,
    ) {
        registerOnboardingRoutes(
            onBack = onBack,
            activity = activity,
            messageListLauncher = messageListLauncher,
            onboardingNavigation = onboardingNavigation,
        )

        registerAccountSetupRoutes(
            onBack = onBack,
            messageListLauncher = messageListLauncher,
            accountSetupNavigation = accountSetupNavigation,
        )

        accountEditNavigation.registerRoutes(
            navGraphBuilder = this,
            onBack = onBack,
            onFinish = { activity.finish() },
        )

        accountSettingsNavigation.registerRoutes(
            navGraphBuilder = this,
            onBack = onBack,
            onFinish = { onBack() },
        )

        fundingNavigation.registerRoutes(
            navGraphBuilder = this,
            onBack = onBack,
            onFinish = { onBack() },
        )

        registerSecretDebugSettingsRoutes(
            onBack = onBack,
            messageListLauncher = messageListLauncher,
            secretDebugSettingsNavigation = secretDebugSettingsNavigation,
        )

        registerTaskMailRoutes(
            onBack = onBack,
            navController = navController,
            taskMailNavigation = taskMailNavigation,
        )
    }
}

private fun NavGraphBuilder.registerOnboardingRoutes(
    onBack: () -> Unit,
    activity: ComponentActivity,
    messageListLauncher: MessageListLauncher,
    onboardingNavigation: OnboardingNavigation,
) {
    onboardingNavigation.registerRoutes(
        navGraphBuilder = this,
        onBack = onBack,
        onFinish = {
            when (it) {
                is OnboardingRoute.Onboarding -> {
                    messageListLauncher.launch(it.accountId)
                    activity.finish()
                }
            }
        },
    )
}

private fun NavGraphBuilder.registerAccountSetupRoutes(
    onBack: () -> Unit,
    messageListLauncher: MessageListLauncher,
    accountSetupNavigation: AccountSetupNavigation,
) {
    accountSetupNavigation.registerRoutes(
        navGraphBuilder = this,
        onBack = onBack,
        onFinish = {
            when (it) {
                is AccountSetupRoute.AccountSetup -> {
                    messageListLauncher.launch(it.accountId)
                }
            }
        },
    )
}

private fun NavGraphBuilder.registerSecretDebugSettingsRoutes(
    onBack: () -> Unit,
    messageListLauncher: MessageListLauncher,
    secretDebugSettingsNavigation: SecretDebugSettingsNavigation,
) {
    secretDebugSettingsNavigation.registerRoutes(
        navGraphBuilder = this,
        onBack = onBack,
        onFinish = { route ->
            when (route.tab) {
                SecretDebugSettingsRoute.Tab.Notification -> onBack()
                SecretDebugSettingsRoute.Tab.FeatureFlag -> messageListLauncher.launch(accountUuid = null)
            }
        },
    )
}

private fun NavGraphBuilder.registerTaskMailRoutes(
    onBack: () -> Unit,
    navController: NavHostController,
    taskMailNavigation: TaskMailNavigation,
) {
    taskMailNavigation.registerRoutes(
        navGraphBuilder = this,
        onBack = taskMailOnBack(
            popBackStack = navController::popBackStack,
            exitLauncher = onBack,
        ),
        onFinish = taskMailOnFinish(
            navigate = { route -> navController.navigate(route) },
        ),
        onRepoSelected = taskMailOnRepoSelected(
            isReturningToExistingNewTask = {
                navController.previousBackStackEntry
                    ?.destination
                    ?.hasRoute(TaskMailRoute.NewTask::class) == true
            },
            setSelectedRepoPathOnNewTask = { repoPath ->
                navController.getBackStackEntry(TaskMailRoute.NewTask)
                    .savedStateHandle
                    .set(TaskMailNavigationResultKeys.SELECTED_REPO_PATH, repoPath)
            },
            popToExistingNewTask = {
                navController.popBackStack(
                    TaskMailRoute.NewTask,
                    inclusive = false,
                    saveState = false,
                )
            },
            popProjectSync = navController::popBackStack,
            openNewTask = { navController.navigate(TaskMailRoute.NewTask) },
        ),
    )
}

internal fun taskMailOnBack(
    popBackStack: () -> Boolean,
    exitLauncher: () -> Unit,
): () -> Unit {
    return {
        if (!popBackStack()) {
            exitLauncher()
        }
    }
}

internal fun taskMailOnFinish(
    navigate: (TaskMailRoute) -> Unit,
): (TaskMailRoute) -> Unit {
    return { route ->
        navigate(route)
    }
}

internal fun taskMailOnRepoSelected(
    isReturningToExistingNewTask: () -> Boolean,
    setSelectedRepoPathOnNewTask: (String) -> Unit,
    popToExistingNewTask: () -> Boolean,
    popProjectSync: () -> Boolean,
    openNewTask: () -> Unit,
): (String) -> Unit {
    return { repoPath ->
        if (isReturningToExistingNewTask()) {
            setSelectedRepoPathOnNewTask(repoPath)
            popToExistingNewTask()
        } else {
            popProjectSync()
            openNewTask()
            setSelectedRepoPathOnNewTask(repoPath)
        }
    }
}
