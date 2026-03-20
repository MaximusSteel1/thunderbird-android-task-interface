package net.thunderbird.feature.taskmail.internal.ui

internal fun String?.toDisplayWorkdir(): String? {
    val normalized = this
        ?.trim()
        ?.trimEnd('/', '\\')
        ?.takeIf { it.isNotEmpty() }
        ?.takeUnless { it == "." }

    return normalized?.let { value ->
        value
            .substringAfterLast('/')
            .substringAfterLast('\\')
            .ifBlank { value }
    }
}
