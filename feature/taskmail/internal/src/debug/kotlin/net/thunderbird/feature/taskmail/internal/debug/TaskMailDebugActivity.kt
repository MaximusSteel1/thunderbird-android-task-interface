package net.thunderbird.feature.taskmail.internal.debug

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.fsck.k9.ui.base.BaseActivity
import net.thunderbird.core.ui.theme.api.FeatureThemeProvider
import net.thunderbird.feature.taskmail.internal.navigation.TaskMailNavHost
import org.koin.android.ext.android.inject

internal class TaskMailDebugActivity : BaseActivity() {
    private val themeProvider: FeatureThemeProvider by inject()
    private var deepLinkIntent by mutableStateOf<Intent?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        deepLinkIntent = intent

        setContent {
            themeProvider.WithTheme {
                val relayDebug = isTaskMailRelayDebugUri(deepLinkIntent?.data)
                val previewDetail = resolveTaskMailDebugPreviewDetail(deepLinkIntent?.data)
                if (relayDebug) {
                    TaskMailRelayDebugScreen(
                        onBack = ::finish,
                    )
                } else if (previewDetail != null) {
                    TaskMailDebugPreviewDetailContent(
                        detail = previewDetail,
                        onBack = ::finish,
                    )
                } else {
                    TaskMailNavHost(
                        deepLinkIntent = deepLinkIntent,
                        onExit = ::finish,
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        deepLinkIntent = intent
    }
}
