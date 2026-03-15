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
}
