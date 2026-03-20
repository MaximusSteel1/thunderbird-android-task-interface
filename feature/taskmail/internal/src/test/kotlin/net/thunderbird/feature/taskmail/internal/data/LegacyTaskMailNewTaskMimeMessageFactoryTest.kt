package net.thunderbird.feature.taskmail.internal.data

import android.app.Application
import assertk.assertThat
import assertk.assertions.isEqualTo
import com.fsck.k9.CoreResourceProvider
import com.fsck.k9.mail.Message.RecipientType
import kotlin.test.Test
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import net.thunderbird.core.android.account.Identity
import net.thunderbird.core.android.account.LegacyAccountDto
import net.thunderbird.core.common.appConfig.PlatformConfigProvider
import net.thunderbird.core.logging.LogTag
import net.thunderbird.core.logging.Logger
import net.thunderbird.core.logging.legacy.Log
import net.thunderbird.core.preference.GeneralSettings
import net.thunderbird.core.preference.GeneralSettingsManager
import net.thunderbird.feature.taskmail.internal.domain.newtask.TaskMailNewTaskRequest
import org.junit.After
import org.junit.Before
import org.junit.runner.RunWith
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
@LooperMode(LooperMode.Mode.LEGACY)
class LegacyTaskMailNewTaskMimeMessageFactoryTest {

    private val generalSettingsManager = FakeNewTaskGeneralSettingsManager(
        initialGeneralSettings = GeneralSettings(
            platformConfigProvider = FakeNewTaskPlatformConfigProvider(),
        ),
    )

    @Before
    fun setUp() {
        stopKoin()
        Log.logger = FakeNewTaskLegacyLogger()
        startKoin {
            modules(
                module {
                    single<CoreResourceProvider> { FakeNewTaskCoreResourceProvider() }
                    single<GeneralSettingsManager> { generalSettingsManager }
                },
            )
        }
    }

    @After
    fun tearDown() {
        stopKoin()
    }

    @Test
    fun `create should target configured bot mailbox without reply headers`() = runTest {
        val testSubject = LegacyTaskMailNewTaskMimeMessageFactory(
            generalSettingsManager = generalSettingsManager,
            destinationAddressProvider = FakeNewTaskDestinationAddressProvider("TaskMail Bot <bot@example.org>"),
        )

        val result = testSubject.create(
            request = newTaskRequest(),
            account = senderAccount(),
        )

        val mimeMessage = result.getOrThrow()

        assertThat(mimeMessage.getRecipients(RecipientType.TO).map { it.address }.toList())
            .isEqualTo(listOf("bot@example.org"))
        assertThat(mimeMessage.getRecipients(RecipientType.CC).toList()).isEqualTo(emptyList())
        assertThat(mimeMessage.getHeader("In-Reply-To")?.toList()).isEqualTo(emptyList())
        assertThat(mimeMessage.getReferences()?.toList()).isEqualTo(emptyList())
        assertThat(mimeMessage.subject).isEqualTo("[CX] Audit TaskMail")
    }

    @Test
    fun `create should fail when bot mailbox is not configured`() = runTest {
        val testSubject = LegacyTaskMailNewTaskMimeMessageFactory(
            generalSettingsManager = generalSettingsManager,
            destinationAddressProvider = FakeNewTaskDestinationAddressProvider(null),
        )

        val result = testSubject.create(
            request = newTaskRequest(),
            account = senderAccount(),
        )

        assertThat(result.isFailure).isEqualTo(true)
        assertThat(result.exceptionOrNull()?.message).isEqualTo("TaskMail bot mailbox is not configured.")
    }

    @Test
    fun `create should fail when bot mailbox address is invalid`() = runTest {
        val testSubject = LegacyTaskMailNewTaskMimeMessageFactory(
            generalSettingsManager = generalSettingsManager,
            destinationAddressProvider = FakeNewTaskDestinationAddressProvider("not an email address"),
        )

        val result = testSubject.create(
            request = newTaskRequest(),
            account = senderAccount(),
        )

        assertThat(result.isFailure).isEqualTo(true)
        assertThat(result.exceptionOrNull()?.message).isEqualTo("TaskMail bot mailbox address is invalid.")
    }

    @Test
    fun `create should fail when bot mailbox resolves to multiple recipients`() = runTest {
        val testSubject = LegacyTaskMailNewTaskMimeMessageFactory(
            generalSettingsManager = generalSettingsManager,
            destinationAddressProvider = FakeNewTaskDestinationAddressProvider(
                "bot@example.org, backup@example.org",
            ),
        )

        val result = testSubject.create(
            request = newTaskRequest(),
            account = senderAccount(),
        )

        assertThat(result.isFailure).isEqualTo(true)
        assertThat(result.exceptionOrNull()?.message).isEqualTo(
            "TaskMail bot mailbox must resolve to exactly one address.",
        )
    }

    @Test
    fun `create should fail when sender account has no identity`() = runTest {
        val testSubject = LegacyTaskMailNewTaskMimeMessageFactory(
            generalSettingsManager = generalSettingsManager,
            destinationAddressProvider = FakeNewTaskDestinationAddressProvider("bot@example.org"),
        )

        val result = testSubject.create(
            request = newTaskRequest(),
            account = LegacyAccountDto(uuid = TEST_ACCOUNT_UUID).apply {
                isFinishedSetup = true
                identities = mutableListOf()
            },
        )

        assertThat(result.isFailure).isEqualTo(true)
        assertThat(result.exceptionOrNull()?.message).isEqualTo("TaskMail sending account has no identity.")
    }
}

private class FakeNewTaskDestinationAddressProvider(
    private val destinationAddress: String?,
) : TaskMailDestinationAddressProvider {
    override fun getDestinationAddress(): String? = destinationAddress
}

private class FakeNewTaskGeneralSettingsManager(
    initialGeneralSettings: GeneralSettings,
) : GeneralSettingsManager {
    private val generalSettings = MutableStateFlow(initialGeneralSettings)

    @Deprecated(
        message = "Use PreferenceManager<GeneralSettings>.getConfig() instead",
        replaceWith = ReplaceWith("getConfig()"),
    )
    override fun getSettings(): GeneralSettings = generalSettings.value

    @Deprecated(
        message = "Use PreferenceManager<GeneralSettings>.getConfigFlow() instead",
        replaceWith = ReplaceWith("getConfigFlow()"),
    )
    override fun getSettingsFlow(): Flow<GeneralSettings> = generalSettings

    override fun save(config: GeneralSettings) {
        error("not implemented")
    }

    override fun getConfig(): GeneralSettings = generalSettings.value

    override fun getConfigFlow(): Flow<GeneralSettings> = generalSettings
}

private class FakeNewTaskPlatformConfigProvider : PlatformConfigProvider {
    override val isDebug: Boolean = true
}

private class FakeNewTaskLegacyLogger : Logger {
    override fun verbose(tag: LogTag?, throwable: Throwable?, message: () -> String) = Unit

    override fun debug(tag: LogTag?, throwable: Throwable?, message: () -> String) = Unit

    override fun info(tag: LogTag?, throwable: Throwable?, message: () -> String) = Unit

    override fun warn(tag: LogTag?, throwable: Throwable?, message: () -> String) = Unit

    override fun error(tag: LogTag?, throwable: Throwable?, message: () -> String) = Unit
}

private class FakeNewTaskCoreResourceProvider : CoreResourceProvider {
    override fun defaultIdentityDescription(): String = "initial identity"

    override fun contactDisplayNamePrefix(): String = "To:"

    override fun contactUnknownSender(): String = "<Unknown Sender>"

    override fun contactUnknownRecipient(): String = "<Unknown Recipient>"

    override fun messageHeaderFrom(): String = "From:"

    override fun messageHeaderTo(): String = "To:"

    override fun messageHeaderCc(): String = "Cc:"

    override fun messageHeaderDate(): String = "Sent:"

    override fun messageHeaderSubject(): String = "Subject:"

    override fun messageHeaderSeparator(): String = "-------- Original Message --------"

    override fun noSubject(): String = "(No subject)"

    override fun userAgent(): String = "K-9 Mail for Android"

    override fun replyHeader(sender: String): String = "$sender wrote:"

    override fun replyHeader(sender: String, sentDate: String): String = "On $sentDate, $sender wrote:"

    override fun searchUnifiedFoldersTitle(): String = "Unified Folders"

    override fun searchUnifiedFoldersDetail(): String = "All messages in unified folders"

    override fun pushNotificationText(notificationState: com.fsck.k9.notification.PushNotificationState): String {
        error("not implemented")
    }

    override fun pushNotificationInfoText(): String = error("not implemented")

    override fun pushNotificationGrantAlarmPermissionText(): String = error("not implemented")
}

private fun senderAccount(): LegacyAccountDto {
    return LegacyAccountDto(uuid = TEST_ACCOUNT_UUID).apply {
        isFinishedSetup = true
        identities = mutableListOf(
            Identity(
                email = "user@example.org",
                replyTo = "user@example.org",
                signature = "sig",
            ),
        )
    }
}

private fun newTaskRequest(): TaskMailNewTaskRequest {
    return TaskMailNewTaskRequest(
        accountUuid = TEST_ACCOUNT_UUID,
        subject = "[CX] Audit TaskMail",
        body = """
            Repo: repo

            Task:
            Audit TaskMail
        """.trimIndent(),
    )
}

private const val TEST_ACCOUNT_UUID = "11111111-1111-1111-1111-111111111111"
