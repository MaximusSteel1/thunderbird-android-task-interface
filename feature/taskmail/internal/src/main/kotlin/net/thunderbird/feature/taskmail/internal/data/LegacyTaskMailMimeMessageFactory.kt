package net.thunderbird.feature.taskmail.internal.data

import com.fsck.k9.helper.IdentityHelper
import com.fsck.k9.mail.Address
import com.fsck.k9.mail.internet.MimeMessage
import com.fsck.k9.message.MessageBuilder
import com.fsck.k9.message.QuotedTextMode
import com.fsck.k9.message.SimpleMessageBuilder
import com.fsck.k9.message.SimpleMessageFormat
import java.util.Date
import kotlin.Result
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine
import net.thunderbird.core.preference.GeneralSettingsManager
import net.thunderbird.feature.taskmail.internal.domain.reply.TaskMailReplyRequest

internal class LegacyTaskMailMimeMessageFactory(
    private val generalSettingsManager: GeneralSettingsManager,
    private val replyAttachmentResolver: TaskMailReplyAttachmentResolver,
    private val destinationAddressProvider: TaskMailDestinationAddressProvider,
    private val replySubjectBuilder: TaskMailReplySubjectBuilder = TaskMailReplySubjectBuilder(),
) : TaskMailMimeMessageFactory {

    override suspend fun create(
        request: TaskMailReplyRequest,
        sourceMessage: TaskMailReplySourceMessage,
    ): Result<MimeMessage> {
        val account = sourceMessage.account
        val message = sourceMessage.message
        val identity = IdentityHelper.getRecipientIdentityFromMessage(account, message)
        val repliedToMessageId = message.messageId
        val referencedMessageIds = buildReferencesHeader(message.messageId, message.references)
        val subject = replySubjectBuilder.build(message.subject)
        return resolveBotMailboxRecipients().fold(
            onSuccess = { botMailboxRecipients ->
                replyAttachmentResolver.buildOutgoingAttachments(request.attachments).fold(
                    onSuccess = { attachments ->
                        SimpleMessageBuilder.newInstance()
                            .setSubject(subject)
                            .setSentDate(Date())
                            .setHideTimeZone(generalSettingsManager.getConfig().privacy.isHideTimeZone)
                            .setTo(botMailboxRecipients)
                            .setCc(emptyList())
                            .setBcc(emptyList())
                            .setReplyTo(Address.parse(identity.replyTo))
                            .setInReplyTo(repliedToMessageId)
                            .setReferences(referencedMessageIds)
                            .setRequestReadReceipt(false)
                            .setIdentity(identity)
                            .setMessageFormat(SimpleMessageFormat.TEXT)
                            .setText(request.body)
                            .setAttachments(attachments)
                            .setInlineAttachments(emptyMap())
                            .setSignature(identity.signature)
                            .setQuotedTextMode(QuotedTextMode.NONE)
                            .setMessageReference(sourceMessage.messageReference)
                            .setDraft(false)
                            .setIsPgpInlineEnabled(false)
                            .awaitMessageBuild()
                    },
                    onFailure = { Result.failure(it) },
                )
            },
            onFailure = { Result.failure(it) },
        )
    }

    private fun resolveBotMailboxRecipients(): Result<List<Address>> {
        val destinationAddress = destinationAddressProvider.getDestinationAddress()
            ?: return Result.failure(IllegalStateException("TaskMail bot mailbox is not configured."))
        val recipients = Address.parse(destinationAddress).toList()

        return when {
            recipients.isEmpty() -> Result.failure(IllegalStateException("TaskMail bot mailbox address is invalid."))
            recipients.size > 1 -> {
                Result.failure(IllegalStateException("TaskMail bot mailbox must resolve to exactly one address."))
            }
            else -> Result.success(recipients)
        }
    }

    private suspend fun MessageBuilder.awaitMessageBuild(): Result<MimeMessage> =
        suspendCancellableCoroutine { continuation ->
            buildAsync(
                object : MessageBuilder.Callback {
                    override fun onMessageBuildSuccess(message: MimeMessage, isDraft: Boolean) {
                        continuation.resume(Result.success(message))
                    }

                    override fun onMessageBuildException(
                        exception: net.thunderbird.core.common.exception.MessagingException,
                    ) {
                        continuation.resume(Result.failure(exception))
                    }

                    override fun onMessageBuildReturnPendingIntent(
                        pendingIntent: android.app.PendingIntent,
                        requestCode: Int,
                    ) {
                        continuation.resume(
                            Result.failure(
                                IllegalStateException("Unexpected pending intent while building TaskMail reply"),
                            ),
                        )
                    }

                    override fun onMessageBuildCancel() {
                        continuation.resume(
                            Result.failure(IllegalStateException("TaskMail reply build canceled")),
                        )
                    }
                },
            )
        }
    private fun buildReferencesHeader(messageId: String?, references: Array<String>?): String? {
        if (messageId.isNullOrBlank()) return null

        return if (!references.isNullOrEmpty()) {
            references.joinToString(separator = "") + " " + messageId
        } else {
            messageId
        }
    }
}
