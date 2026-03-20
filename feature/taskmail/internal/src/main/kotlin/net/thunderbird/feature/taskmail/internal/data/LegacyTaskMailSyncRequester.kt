package net.thunderbird.feature.taskmail.internal.data

import app.k9mail.legacy.message.controller.SimpleMessagingListener
import com.fsck.k9.controller.MessagingController
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine
import net.thunderbird.core.android.account.LegacyAccountDto

internal class LegacyTaskMailSyncRequester(
    private val messagingController: MessagingController,
    private val senderAccountSource: TaskMailSenderAccountSource,
) : TaskMailSyncRequester {
    override suspend fun requestSync(): Result<Unit> = requestSync(accountUuid = null)

    override suspend fun requestSync(accountUuid: String?): Result<Unit> {
        val account = accountUuid?.let(senderAccountSource::getAccount)

        if (accountUuid != null && account == null) {
            return Result.failure(IllegalStateException("TaskMail sending account is no longer available."))
        }

        return runCatching {
            checkMail(account)
        }
    }

    private suspend fun checkMail(account: LegacyAccountDto?) {
        suspendCancellableCoroutine { continuation ->
            val listener = object : SimpleMessagingListener() {
                override fun checkMailFinished(
                    context: android.content.Context?,
                    finishedAccount: LegacyAccountDto?,
                ) {
                    if (finishedAccount != null) Unit
                    if (continuation.isActive) {
                        continuation.resume(Unit)
                    }
                }
            }

            messagingController.checkMail(
                account,
                true,
                true,
                false,
                listener,
            )
        }
    }
}
