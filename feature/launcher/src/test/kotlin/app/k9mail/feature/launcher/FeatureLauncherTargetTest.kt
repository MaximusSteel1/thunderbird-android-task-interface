package app.k9mail.feature.launcher

import android.app.Application
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import net.thunderbird.feature.taskmail.api.TaskMailRoute
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class FeatureLauncherTargetTest {

    @Test
    fun `TaskMail target should deep link to workspace route`() {
        val target = FeatureLauncherTarget.TaskMail

        assertEquals(TaskMailRoute.Workspace.route(), target.deepLinkUri.toString())
        assertNull(target.flags)
    }

    @Test
    fun `TaskMailSettings target should deep link to settings route`() {
        val target = FeatureLauncherTarget.TaskMailSettings

        assertEquals(TaskMailRoute.Settings.route(), target.deepLinkUri.toString())
        assertNull(target.flags)
    }
}
