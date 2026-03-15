package net.thunderbird.feature.taskmail.internal.data

import app.k9mail.legacy.message.controller.MessageReference
import com.fsck.k9.mail.Message
import net.thunderbird.core.android.account.LegacyAccountDto

internal data class TaskMailReplySourceMessage(
    val account: LegacyAccountDto,
    val message: Message,
    val messageReference: MessageReference,
)
