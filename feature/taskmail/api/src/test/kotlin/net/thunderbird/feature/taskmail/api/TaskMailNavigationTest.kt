package net.thunderbird.feature.taskmail.api

import assertk.assertThat
import assertk.assertions.isEqualTo
import kotlin.test.Test

class TaskMailNavigationTest {

    @Test
    fun `given selected repo result key then value is correct`() {
        assertThat(TaskMailNavigationResultKeys.SELECTED_REPO_PATH).isEqualTo("taskmail_selected_repo_path")
    }

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

    @Test
    fun `given NewTask route then basePath and route are correct`() {
        val expectedBase = "${TaskMailRoute.TASKMAIL_BASE_PATH}/new-task"

        val basePath = TaskMailRoute.NewTask.basePath
        val route = TaskMailRoute.NewTask.route()
        val constBase = TaskMailRoute.NewTask.BASE_PATH

        assertThat(constBase).isEqualTo(expectedBase)
        assertThat(basePath).isEqualTo(expectedBase)
        assertThat(route).isEqualTo(expectedBase)
    }

    @Test
    fun `given ProjectSync route then basePath and route are correct`() {
        val expectedBase = "${TaskMailRoute.TASKMAIL_BASE_PATH}/project-sync"

        val basePath = TaskMailRoute.ProjectSync.basePath
        val route = TaskMailRoute.ProjectSync.route()
        val constBase = TaskMailRoute.ProjectSync.BASE_PATH

        assertThat(constBase).isEqualTo(expectedBase)
        assertThat(basePath).isEqualTo(expectedBase)
        assertThat(route).isEqualTo(expectedBase)
    }

    @Test
    fun `given Settings route then basePath and route are correct`() {
        val expectedBase = "${TaskMailRoute.TASKMAIL_BASE_PATH}/settings"

        val basePath = TaskMailRoute.Settings.basePath
        val route = TaskMailRoute.Settings.route()
        val constBase = TaskMailRoute.Settings.BASE_PATH

        assertThat(constBase).isEqualTo(expectedBase)
        assertThat(basePath).isEqualTo(expectedBase)
        assertThat(route).isEqualTo(expectedBase)
    }
}
