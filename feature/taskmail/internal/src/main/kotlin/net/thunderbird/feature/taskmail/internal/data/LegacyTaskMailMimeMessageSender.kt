package net.thunderbird.feature.taskmail.internal.data

import com.fsck.k9.controller.MessagingController
import com.fsck.k9.mail.internet.MimeMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.thunderbird.core.android.account.LegacyAccountDto

internal class LegacyTaskMailMimeMessageSender(
    private val messagingController: MessagingController,
) : TaskMailMimeMessageSender {

    override suspend fun send(account: LegacyAccountDto, message: MimeMessage, plaintextSubject: String) {
        withContext(Dispatchers.IO) {
            messagingController.sendMessage(account, message, plaintextSubject, null)
        }
    }
}
