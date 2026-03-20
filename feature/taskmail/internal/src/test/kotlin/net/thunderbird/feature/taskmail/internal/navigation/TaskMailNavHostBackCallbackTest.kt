package net.thunderbird.feature.taskmail.internal.navigation

import kotlin.test.assertEquals
import org.junit.Test

class TaskMailNavHostBackCallbackTest {

    @Test
    fun `taskMailNavHostOnBack pops back stack before exiting host`() {
        var popBackStackCalls = 0
        var exitHostCalls = 0

        val onBack = taskMailNavHostOnBack(
            popBackStack = {
                popBackStackCalls++
                true
            },
            exitHost = {
                exitHostCalls++
            },
        )

        onBack()

        assertEquals(1, popBackStackCalls)
        assertEquals(0, exitHostCalls)
    }

    @Test
    fun `taskMailNavHostOnBack exits host when back stack is empty`() {
        var exitHostCalls = 0

        val onBack = taskMailNavHostOnBack(
            popBackStack = { false },
            exitHost = {
                exitHostCalls++
            },
        )

        onBack()

        assertEquals(1, exitHostCalls)
    }

    @Test
    fun `taskMailNavHostOnRepoSelected returns to existing new task when it is already on the back stack`() {
        var popToExistingNewTaskCalls = 0
        var popProjectSyncCalls = 0
        var openNewTaskCalls = 0
        var selectedRepoPath: String? = null

        val onRepoSelected = taskMailNavHostOnRepoSelected(
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
    fun `taskMailNavHostOnRepoSelected opens new task when project list was launched directly from workspace`() {
        val callLog = mutableListOf<String>()
        var selectedRepoPath: String? = null

        val onRepoSelected = taskMailNavHostOnRepoSelected(
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
