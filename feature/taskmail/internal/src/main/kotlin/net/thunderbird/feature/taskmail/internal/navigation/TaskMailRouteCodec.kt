package net.thunderbird.feature.taskmail.internal.navigation

internal const val MISSING_TASKMAIL_SESSION_ID = "__taskmail_missing_session_id__"

internal fun encodeTaskMailSessionId(sessionId: String?): String {
    return sessionId ?: MISSING_TASKMAIL_SESSION_ID
}

internal fun decodeTaskMailSessionId(sessionId: String): String? {
    return sessionId.takeUnless { it == MISSING_TASKMAIL_SESSION_ID }
}
