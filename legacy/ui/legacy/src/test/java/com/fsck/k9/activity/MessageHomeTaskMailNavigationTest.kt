package com.fsck.k9.activity

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import app.k9mail.feature.launcher.FeatureLauncherActivity
import app.k9mail.feature.launcher.FeatureLauncherTarget
import com.fsck.k9.K9RobolectricTest
import com.fsck.k9.TestApp
import kotlin.test.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = TestApp::class)
class MessageHomeTaskMailNavigationTest : K9RobolectricTest() {

    @Test
    fun `createTaskMailDrawerIntent should target feature launcher workspace route`() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        val intent = createTaskMailDrawerIntent(context)

        assertEquals(FeatureLauncherActivity::class.java.name, intent.component?.className)
        assertEquals(FeatureLauncherTarget.TaskMail.deepLinkUri.toString(), intent.dataString)
    }
}
