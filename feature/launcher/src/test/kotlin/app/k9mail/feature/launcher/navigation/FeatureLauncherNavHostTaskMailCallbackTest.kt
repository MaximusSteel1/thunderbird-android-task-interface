package app.k9mail.feature.launcher.navigation

import net.thunderbird.feature.taskmail.api.TaskMailRoute
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FeatureLauncherNavHostTaskMailCallbackTest {

    @Test
    fun `taskMailOnBack pops back stack before exiting launcher`() {
        var popBackStackCalls = 0
        var exitLauncherCalls = 0

        val onBack = taskMailOnBack(
            popBackStack = {
                popBackStackCalls++
                true
            },
            exitLauncher = {
                exitLauncherCalls++
            },
        )

        onBack()

        assertEquals(1, popBackStackCalls)
        assertEquals(0, exitLauncherCalls)
    }

    @Test
    fun `taskMailOnBack exits launcher when back stack is empty`() {
        var exitLauncherCalls = 0

        val onBack = taskMailOnBack(
            popBackStack = { false },
            exitLauncher = {
                exitLauncherCalls++
            },
        )

        onBack()

        assertEquals(1, exitLauncherCalls)
    }

    @Test
    fun `taskMailOnFinish navigates to taskmail route`() {
        var navigatedRoute: TaskMailRoute? = null
        val targetRoute = TaskMailRoute.SessionDetail(
            workspaceId = "workspace-1",
            sessionId = "session-1",
            threadId = "thread-1",
        )

        val onFinish = taskMailOnFinish { route ->
            navigatedRoute = route
        }

        onFinish(targetRoute)

        assertTrue(navigatedRoute is TaskMailRoute.SessionDetail)
        assertEquals(targetRoute, navigatedRoute)
        assertFalse(navigatedRoute == TaskMailRoute.Workspace)
    }

    @Test
    fun `taskMailOnRepoSelected returns to existing new task when it is already on the back stack`() {
        var popToExistingNewTaskCalls = 0
        var popProjectSyncCalls = 0
        var openNewTaskCalls = 0
        var selectedRepoPath: String? = null

        val onRepoSelected = taskMailOnRepoSelected(
            isReturningToExistingNewTask = { true },
            setSelectedRepoPathOnNewTask = { repoPath ->
                selectedRepoPath = repoPath
            },
            popToExistingNewTask = {
                popToExistingNewTaskCalls++
                true
            },
            popProjectSync = {
                popProjectSyncCalls++
                true
            },
            openNewTask = {
                openNewTaskCalls++
            },
        )

        onRepoSelected("E:/projects/android_task_manager")

        assertEquals("E:/projects/android_task_manager", selectedRepoPath)
        assertEquals(1, popToExistingNewTaskCalls)
        assertEquals(0, popProjectSyncCalls)
        assertEquals(0, openNewTaskCalls)
    }

    @Test
    fun `taskMailOnRepoSelected opens new task when project list was launched directly from workspace`() {
        val callLog = mutableListOf<String>()
        var selectedRepoPath: String? = null

        val onRepoSelected = taskMailOnRepoSelected(
            isReturningToExistingNewTask = { false },
            setSelectedRepoPathOnNewTask = { repoPath ->
                callLog += "setRepo"
                selectedRepoPath = repoPath
            },
            popToExistingNewTask = {
                callLog += "popToExistingNewTask"
                true
            },
            popProjectSync = {
                callLog += "popProjectSync"
                true
            },
            openNewTask = {
                callLog += "openNewTask"
            },
        )

        onRepoSelected("E:/projects/android_task_manager")

        assertEquals("E:/projects/android_task_manager", selectedRepoPath)
        assertEquals(
            listOf("popProjectSync", "openNewTask", "setRepo"),
            callLog,
        )
    }
}
