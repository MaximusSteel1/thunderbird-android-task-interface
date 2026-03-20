package app.k9mail.feature.launcher

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlin.test.assertEquals
import net.thunderbird.feature.taskmail.api.TaskMailRoute
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class FeatureLauncherActivityIntentTest {

    @Test
    fun `getIntent should target feature launcher workspace route for taskmail`() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        val intent = FeatureLauncherActivity.getIntent(context, FeatureLauncherTarget.TaskMail)

        assertEquals(FeatureLauncherActivity::class.java.name, intent.component?.className)
        assertEquals(TaskMailRoute.Workspace.route(), intent.dataString)
        assertEquals(0, intent.flags)
    }

    @Test
    fun `getIntent should target feature launcher settings route for taskmail settings`() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        val intent = FeatureLauncherActivity.getIntent(context, FeatureLauncherTarget.TaskMailSettings)

        assertEquals(FeatureLauncherActivity::class.java.name, intent.component?.className)
        assertEquals(TaskMailRoute.Settings.route(), intent.dataString)
        assertEquals(0, intent.flags)
    }

    @Test
    fun `getIntent should preserve target flags when present`() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        val intent = FeatureLauncherActivity.getIntent(context, FeatureLauncherTarget.Onboarding)

        assertEquals(FeatureLauncherActivity::class.java.name, intent.component?.className)
        assertEquals(FeatureLauncherTarget.Onboarding.deepLinkUri.toString(), intent.dataString)
        assertEquals(FeatureLauncherTarget.Onboarding.flags, intent.flags)
    }
}
