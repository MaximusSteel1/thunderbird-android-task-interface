package net.thunderbird.feature.taskmail.internal.data

import com.fsck.k9.helper.IdentityHelper
import com.fsck.k9.helper.ReplyToParser
import com.fsck.k9.mail.internet.MimeMessage
import com.fsck.k9.message.MessageBuilder
import com.fsck.k9.message.QuotedTextMode
import com.fsck.k9.message.SimpleMessageBuilder
import com.fsck.k9.message.SimpleMessageFormat
import java.util.Date
import java.util.regex.Pattern
import kotlin.Result
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine
import net.thunderbird.core.preference.GeneralSettingsManager
import net.thunderbird.feature.taskmail.internal.domain.reply.TaskMailReplyRequest

internal class LegacyTaskMailMimeMessageFactory(
    private val replyToParser: ReplyToParser,
    private val generalSettingsManager: GeneralSettingsManager,
    private val replyAttachmentResolver: TaskMailReplyAttachmentResolver,
) : TaskMailMimeMessageFactory {

    override suspend fun create(
        request: TaskMailReplyRequest,
        sourceMessage: TaskMailReplySourceMessage,
    ): Result<MimeMessage> {
        val account = sourceMessage.account
        val message = sourceMessage.message
        val identity = IdentityHelper.getRecipientIdentityFromMessage(account, message)
        val recipients = replyToParser.getRecipientsToReplyTo(message, account)
        val repliedToMessageId = message.messageId
        val referencedMessageIds = buildReferencesHeader(message.messageId, message.references)
        val subject = buildReplySubject(message.subject)
        val attachments = replyAttachmentResolver.buildOutgoingAttachments(request.attachments)
            .getOrElse { return Result.failure(it) }

        val builder = SimpleMessageBuilder.newInstance()
            .setSubject(subject)
            .setSentDate(Date())
            .setHideTimeZone(generalSettingsManager.getConfig().privacy.isHideTimeZone)
            .setTo(recipients.to.toList())
            .setCc(recipients.cc.toList())
            .setBcc(emptyList())
            .setReplyTo(com.fsck.k9.mail.Address.parse(identity.replyTo))
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

        return builder.awaitMessageBuild()
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

    private fun buildReplySubject(subject: String?): String {
        val normalizedSubject = replyPrefixPattern.matcher(subject.orEmpty()).replaceFirst("").trim()
        return if (normalizedSubject.startsWith("re:", ignoreCase = true)) {
            normalizedSubject
        } else {
            "Re: $normalizedSubject".trim()
        }
    }

    private fun buildReferencesHeader(messageId: String?, references: Array<String>?): String? {
        if (messageId.isNullOrBlank()) return null

        return if (!references.isNullOrEmpty()) {
            references.joinToString(separator = "") + " " + messageId
        } else {
            messageId
        }
    }

    private companion object {
        val replyPrefixPattern: Pattern = Pattern.compile("^AW[:\\s]\\s*", Pattern.CASE_INSENSITIVE)
    }
}
