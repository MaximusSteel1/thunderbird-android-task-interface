package net.thunderbird.feature.taskmail.api

import assertk.assertThat
import assertk.assertions.isEqualTo
import kotlin.test.Test

class TaskMailNavigationTest {

    @Test
    fun `given Workspace route then basePath and route are correct`() {
        val expectedBase = "${TaskMailRoute.TASKMAIL_BASE_PATH}/workspace"

        val basePath = TaskMailRoute.Workspace.basePath
        val route = TaskMailRoute.Workspace.route()
        val constBase = TaskMailRoute.Workspace.BASE_PATH

        assertThat(TaskMailRoute.TASKMAIL_BASE_PATH).isEqualTo("app://taskmail")
        assertThat(constBase).isEqualTo(expectedBase)
        assertThat(basePath).isEqualTo(expectedBase)
        assertThat(route).isEqualTo(expectedBase)
    }

    @Test
    fun `given SessionDetail route then basePath and route are correct`() {
        val route = TaskMailRoute.SessionDetail(
            sessionId = "session_001",
            threadId = "thread_001",
        )
        val expectedBase = "${TaskMailRoute.TASKMAIL_BASE_PATH}/session"

        val basePath = route.basePath
        val routePath = route.route()
        val constBase = TaskMailRoute.SessionDetail.BASE_PATH

        assertThat(constBase).isEqualTo(expectedBase)
        assertThat(basePath).isEqualTo(expectedBase)
        assertThat(routePath).isEqualTo("$expectedBase/session_001/thread_001")
    }
}
