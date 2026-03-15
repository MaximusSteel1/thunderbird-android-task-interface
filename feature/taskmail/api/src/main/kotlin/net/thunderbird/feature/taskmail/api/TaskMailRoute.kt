package net.thunderbird.feature.taskmail.api

import kotlinx.serialization.Serializable
import net.thunderbird.core.ui.navigation.Route

sealed interface TaskMailRoute : Route {

    @Serializable
    data object Workspace : TaskMailRoute {
        override val basePath: String = BASE_PATH

        override fun route(): String = basePath

        const val BASE_PATH = "$TASKMAIL_BASE_PATH/workspace"
    }

    @Serializable
    data class SessionDetail(
        val sessionId: String,
        val threadId: String,
    ) : TaskMailRoute {
        override val basePath: String = BASE_PATH

        override fun route(): String = "$basePath/$sessionId/$threadId"

        companion object {
            const val BASE_PATH = "$TASKMAIL_BASE_PATH/session"
        }
    }

    companion object {
        const val TASKMAIL_BASE_PATH = "app://taskmail"
    }
}
