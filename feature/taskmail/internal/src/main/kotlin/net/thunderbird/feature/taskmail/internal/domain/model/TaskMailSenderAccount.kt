package net.thunderbird.feature.taskmail.internal.domain.model

internal data class TaskMailSenderAccount(
    val accountUuid: String,
    val displayName: String,
    val emailAddress: String,
) {
    val displayLabel: String = if (displayName.equals(emailAddress, ignoreCase = true)) {
        emailAddress
    } else {
        "$displayName <$emailAddress>"
    }
}
