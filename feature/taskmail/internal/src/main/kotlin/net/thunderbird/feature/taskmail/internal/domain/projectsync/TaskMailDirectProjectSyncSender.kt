package net.thunderbird.feature.taskmail.internal.domain.projectsync

internal interface TaskMailDirectProjectSyncSender {
    suspend fun send(accountUuid: String): TaskMailDirectProjectSyncResult
}
