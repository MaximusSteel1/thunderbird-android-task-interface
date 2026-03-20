package net.thunderbird.feature.taskmail.internal.data

import android.app.Application
import app.k9mail.legacy.message.controller.MessageReference
import assertk.assertThat
import assertk.assertions.isEqualTo
import com.fsck.k9.CoreResourceProvider
import com.fsck.k9.mail.Address
import com.fsck.k9.mail.Message.RecipientType
import com.fsck.k9.mail.internet.MimeMessage
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
import net.thunderbird.feature.taskmail.internal.domain.model.TaskSessionReplyContext
import net.thunderbird.feature.taskmail.internal.domain.reply.TaskMailReplyMode
import net.thunderbird.feature.taskmail.internal.domain.reply.TaskMailReplyRequest
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
class LegacyTaskMailMimeMessageFactoryTest {

    private val generalSettingsManager = FakeGeneralSettingsManager(
        initialGeneralSettings = GeneralSettings(
            platformConfigProvider = FakePlatformConfigProvider(),
        ),
    )

    @Before
    fun setUp() {
        stopKoin()
        Log.logger = FakeLegacyLogger()
        startKoin {
            modules(
                module {
                    single<CoreResourceProvider> { FakeCoreResourceProvider() }
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
    fun `create should target configured bot mailbox and preserve reply headers`() = runTest {
        val testSubject = LegacyTaskMailMimeMessageFactory(
            generalSettingsManager = generalSettingsManager,
            replyAttachmentResolver = FakeTaskMailReplyAttachmentResolver(),
            destinationAddressProvider = FakeTaskMailDestinationAddressProvider("TaskMail Bot <bot@example.org>"),
        )

        val result = testSubject.create(
            request = replyRequest(),
            sourceMessage = sourceMessage(),
        )

        val mimeMessage = result.getOrThrow()

        assertThat(mimeMessage.getRecipients(RecipientType.TO).map { it.address }.toList())
            .isEqualTo(listOf("bot@example.org"))
        assertThat(mimeMessage.getRecipients(RecipientType.CC).toList()).isEqualTo(emptyList())
        assertThat(mimeMessage.getHeader("In-Reply-To").single()).isEqualTo("<msg-1@example.org>")
        assertThat(mimeMessage.getReferences().single()).isEqualTo("<ref-1@example.org> <msg-1@example.org>")
        assertThat(mimeMessage.subject).isEqualTo("Re: [DONE] [S:session-42] [CX] Analyze floor_shear")
    }

    @Test
    fun `create should fail when bot mailbox is not configured`() = runTest {
        val testSubject = LegacyTaskMailMimeMessageFactory(
            generalSettingsManager = generalSettingsManager,
            replyAttachmentResolver = FakeTaskMailReplyAttachmentResolver(),
            destinationAddressProvider = FakeTaskMailDestinationAddressProvider(null),
        )

        val result = testSubject.create(
            request = replyRequest(),
            sourceMessage = sourceMessage(),
        )

        assertThat(result.isFailure).isEqualTo(true)
        assertThat(result.exceptionOrNull()?.message).isEqualTo("TaskMail bot mailbox is not configured.")
    }

    @Test
    fun `create should fail when bot mailbox address is invalid`() = runTest {
        val testSubject = LegacyTaskMailMimeMessageFactory(
            generalSettingsManager = generalSettingsManager,
            replyAttachmentResolver = FakeTaskMailReplyAttachmentResolver(),
            destinationAddressProvider = FakeTaskMailDestinationAddressProvider("not an email"),
        )

        val result = testSubject.create(
            request = replyRequest(),
            sourceMessage = sourceMessage(),
        )

        assertThat(result.isFailure).isEqualTo(true)
        assertThat(result.exceptionOrNull()?.message).isEqualTo("TaskMail bot mailbox address is invalid.")
    }
}

private class FakeTaskMailDestinationAddressProvider(
    private val destinationAddress: String?,
) : TaskMailDestinationAddressProvider {
    override fun getDestinationAddress(): String? = destinationAddress
}

private class FakeTaskMailReplyAttachmentResolver : TaskMailReplyAttachmentResolver {
    override suspend fun resolveSelectedAttachments(uriStrings: List<String>) = error("not needed")

    override suspend fun buildOutgoingAttachments(
        attachments: List<net.thunderbird.feature.taskmail.internal.domain.model.TaskReplyAttachment>,
    ) = Result.success(emptyList<com.fsck.k9.message.Attachment>())
}

private class FakeGeneralSettingsManager(
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

private class FakePlatformConfigProvider : PlatformConfigProvider {
    override val isDebug: Boolean = true
}

private class FakeLegacyLogger : Logger {
    override fun verbose(tag: LogTag?, throwable: Throwable?, message: () -> String) = Unit

    override fun debug(tag: LogTag?, throwable: Throwable?, message: () -> String) = Unit

    override fun info(tag: LogTag?, throwable: Throwable?, message: () -> String) = Unit

    override fun warn(tag: LogTag?, throwable: Throwable?, message: () -> String) = Unit

    override fun error(tag: LogTag?, throwable: Throwable?, message: () -> String) = Unit
}

private class FakeCoreResourceProvider : CoreResourceProvider {
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

private fun replyRequest(): TaskMailReplyRequest {
    return TaskMailReplyRequest(
        context = TaskSessionReplyContext(
            accountUuid = TEST_ACCOUNT_UUID,
            folderId = 1L,
            messageServerId = "msg-1",
        ),
        body = "Please continue",
        mode = TaskMailReplyMode.ContinueSession,
    )
}

private fun sourceMessage(): TaskMailReplySourceMessage {
    val account = LegacyAccountDto(uuid = TEST_ACCOUNT_UUID).apply {
        identities = mutableListOf(
            Identity(
                email = "user@example.org",
                replyTo = "user@example.org",
                signature = "sig",
            ),
        )
    }
    val message = MimeMessage.create().apply {
        subject = "[DONE][S:session-42] [CX] Analyze floor_shear"
        setMessageId("<msg-1@example.org>")
        setReferences("<ref-1@example.org>")
        setHeader("To", "User <user@example.org>")
        setFrom(Address("bot@example.org"))
    }

    return TaskMailReplySourceMessage(
        account = account,
        message = message,
        messageReference = MessageReference(TEST_ACCOUNT_UUID, 1L, "msg-1"),
    )
}

private const val TEST_ACCOUNT_UUID = "11111111-1111-1111-1111-111111111111"
