package net.thunderbird.feature.taskmail.internal.domain.filesample

internal enum class TaskMailFileSampleStatus {
    Completed,
    Failed,
}

internal data class TaskMailFileSampleResult(
    val sampleId: String,
    val status: TaskMailFileSampleStatus,
    val artifactDirectoryPath: String,
    val fileId: String? = null,
    val metadataUrl: String? = null,
    val downloadUrl: String? = null,
    val byteSize: Long? = null,
    val expectedSha256: String? = null,
    val observedSha256: String? = null,
    val errorCode: String? = null,
    val errorMessage: String? = null,
)
