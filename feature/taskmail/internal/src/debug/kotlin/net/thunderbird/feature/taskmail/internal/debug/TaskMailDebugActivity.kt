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
                TaskMailNavHost(
                    deepLinkIntent = deepLinkIntent,
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        deepLinkIntent = intent
    }
}
