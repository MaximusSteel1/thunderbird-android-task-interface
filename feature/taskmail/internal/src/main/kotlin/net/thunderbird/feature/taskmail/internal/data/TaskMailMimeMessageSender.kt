package net.thunderbird.feature.taskmail.internal.data

import com.fsck.k9.mail.internet.MimeMessage
import net.thunderbird.core.android.account.LegacyAccountDto

internal interface TaskMailMimeMessageSender {
    suspend fun send(account: LegacyAccountDto, message: MimeMessage, plaintextSubject: String)
}
