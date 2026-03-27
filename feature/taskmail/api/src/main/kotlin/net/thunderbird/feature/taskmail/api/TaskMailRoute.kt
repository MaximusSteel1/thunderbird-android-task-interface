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
        val workspaceId: String? = null,
        val sessionId: String,
    ) : TaskMailRoute {
        override val basePath: String = BASE_PATH

        override fun route(): String {
            val detailPath = "$basePath/$sessionId"
            return workspaceId?.let { "$detailPath?workspaceId=$it" } ?: detailPath
        }

        companion object {
            const val BASE_PATH = "$TASKMAIL_BASE_PATH/session"
        }
    }

    @Serializable
    data class SessionHistory(
        val workspaceId: String? = null,
        val sessionId: String,
    ) : TaskMailRoute {
        override val basePath: String = BASE_PATH

        override fun route(): String {
            val historyPath = "$basePath/$sessionId"
            return workspaceId?.let { "$historyPath?workspaceId=$it" } ?: historyPath
        }

        companion object {
            const val BASE_PATH = "$TASKMAIL_BASE_PATH/session-history"
        }
    }

    @Serializable
    data object NewTask : TaskMailRoute {
        override val basePath: String = BASE_PATH

        override fun route(): String = basePath

        const val BASE_PATH = "$TASKMAIL_BASE_PATH/new-task"
    }

    @Serializable
    data object ProjectSync : TaskMailRoute {
        override val basePath: String = BASE_PATH

        override fun route(): String = basePath

        const val BASE_PATH = "$TASKMAIL_BASE_PATH/project-sync"
    }

    @Serializable
    data object Settings : TaskMailRoute {
        override val basePath: String = BASE_PATH

        override fun route(): String = basePath

        const val BASE_PATH = "$TASKMAIL_BASE_PATH/settings"
    }

    companion object {
        const val TASKMAIL_BASE_PATH = "app://taskmail"
    }
}
