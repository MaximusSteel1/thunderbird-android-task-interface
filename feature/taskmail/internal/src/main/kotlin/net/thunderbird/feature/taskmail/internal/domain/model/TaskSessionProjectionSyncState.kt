package net.thunderbird.feature.taskmail.internal.domain.model

internal data class TaskSessionProjectionSyncState(
    val dataSource: TaskSessionProjectionDataSource = TaskSessionProjectionDataSource.MailCompatibilityImport,
    val lastSequence: Long? = null,
    val lastEventId: String? = null,
    val lastResultId: String? = null,
    val lastProjectionUpdatedAt: Long? = null,
    val subscriptionStatus: TaskSessionProjectionSubscriptionStatus =
        TaskSessionProjectionSubscriptionStatus.Unknown,
)

internal enum class TaskSessionProjectionDataSource {
    MailCompatibilityImport,
    VpsNative,
    MixedRepair,
}

internal enum class TaskSessionProjectionSubscriptionStatus {
    Unknown,
    Active,
    Reconnecting,
    Idle,
}

internal fun TaskSessionDetail.prefersVpsProjection(): Boolean {
    return projectionSyncState.dataSource != TaskSessionProjectionDataSource.MailCompatibilityImport
}

