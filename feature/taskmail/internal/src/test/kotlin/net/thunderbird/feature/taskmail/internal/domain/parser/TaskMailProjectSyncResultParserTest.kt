package net.thunderbird.feature.taskmail.internal.domain.parser

import assertk.assertThat
import assertk.assertions.isEqualTo
import kotlin.test.Test

class TaskMailProjectSyncResultParserTest {

    private val testSubject = TaskMailProjectSyncResultParser()

    @Test
    fun `parse should return roots and projects from sync reply`() {
        val result = testSubject.parse(
            bodyText = """
                Project folder sync completed. No task was created.

                Scanned at: 2026-03-16T23:01:59

                Scanned roots:
                - D:\projects | available | 2 folders
                - E:\projects | available | 3 folders

                D:\projects
                - fahad_diana | D:\projects\fahad_diana
                - filesystem | D:\projects\filesystem

                E:\projects
                - android_task_manager | E:\projects\android_task_manager
                - mail_based_task_manager | E:\projects\mail_based_task_manager
                - wechat_desolver | E:\projects\wechat_desolver

                To start a task, send a new [OC] or [CX] mail and copy one path into Repo:.
            """.trimIndent(),
            receivedAt = 123L,
        )

        assertThat(result?.receivedAt).isEqualTo(123L)
        assertThat(result?.scannedAt).isEqualTo("2026-03-16T23:01:59")
        assertThat(result?.roots?.map { it.rootPath }).isEqualTo(
            listOf(
                "D:\\projects",
                "E:\\projects",
            ),
        )
        assertThat(result?.roots?.first()?.folderCount).isEqualTo(2)
        assertThat(result?.roots?.first()?.projects?.map { it.repoPath }).isEqualTo(
            listOf(
                "D:\\projects\\fahad_diana",
                "D:\\projects\\filesystem",
            ),
        )
        assertThat(result?.roots?.last()?.projects?.map { it.displayName }).isEqualTo(
            listOf(
                "android_task_manager",
                "mail_based_task_manager",
                "wechat_desolver",
            ),
        )
    }

    @Test
    fun `parse should preserve unavailable roots without project entries`() {
        val result = testSubject.parse(
            bodyText = """
                Project folder sync completed. No task was created.

                Scanned roots:
                - D:\projects | unavailable | path does not exist
                - E:\projects | available | 1 folders

                E:\projects
                - android_task_manager | E:\projects\android_task_manager

                To start a task, send a new [OC] or [CX] mail and copy one path into Repo:.
            """.trimIndent(),
            receivedAt = 456L,
        )

        assertThat(result?.roots?.first()?.rootPath).isEqualTo("D:\\projects")
        assertThat(result?.roots?.first()?.isAvailable).isEqualTo(false)
        assertThat(result?.roots?.first()?.unavailableReason).isEqualTo("path does not exist")
        assertThat(result?.roots?.first()?.projects).isEqualTo(emptyList())
    }
}
