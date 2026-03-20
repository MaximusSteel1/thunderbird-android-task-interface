package net.thunderbird.feature.taskmail.api

import androidx.navigation.NavGraphBuilder
import net.thunderbird.core.ui.navigation.Navigation

interface TaskMailNavigation : Navigation<TaskMailRoute> {
    fun registerRoutes(
        navGraphBuilder: NavGraphBuilder,
        onBack: () -> Unit,
        onFinish: (TaskMailRoute) -> Unit,
        onRepoSelected: (String) -> Unit,
    ) {
        registerRoutes(
            navGraphBuilder = navGraphBuilder,
            onBack = onBack,
            onFinish = onFinish,
        )
    }
}
