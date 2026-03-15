package net.thunderbird.feature.taskmail.internal.validation

import java.io.File
import kotlinx.serialization.json.Json
import net.thunderbird.feature.taskmail.internal.data.TaskMailMessage
import net.thunderbird.feature.taskmail.internal.data.TaskMailMessageSource
import net.thunderbird.feature.taskmail.internal.domain.parser.TaskMailEnvelope
import net.thunderbird.feature.taskmail.internal.domain.parser.TaskMailMessageDetector

internal class JsonMessageSource(
    private val jsonMessages: List<JsonMailMessage>,
    private val messageDetector: TaskMailMessageDetector = TaskMailMessageDetector(),
) : TaskMailMessageSource {

    override suspend fun getMessages(): List<TaskMailMessage> {
        val threads = ThreadResolver().resolve(jsonMessages)
        val threadRootIndex = mutableMapOf<String, Long>()
        var threadIndex = 0L

        return jsonMessages.mapNotNull { jsonMessage ->
            val thread = threads.find { it.messages.contains(jsonMessage) } ?: return@mapNotNull null

            val threadRootId = threadRootCache.getOrPut(thread.threadRootId) {
                threadIndex++.also { threadRootIndex[thread.threadRootId] = it }
            }

            val detection = messageDetector.detect(
                TaskMailEnvelope(
                    messageId = jsonMessage.messageId,
                    subject = jsonMessage.subject,
                    fromAddress = jsonMessage.fromAddr,
                    timestamp = jsonMessage.parsedTimestamp,
                    inReplyTo = jsonMessage.inReplyTo,
                    references = jsonMessage.references,
                    plainTextBody = jsonMessage.bodyText,
                ),
            )

            TaskMailMessage(
                accountUuid = ACCOUNT_UUID,
                folderId = FOLDER_ID,
                messageServerId = jsonMessage.messageId,
                threadRootId = threadRootId,
                timestamp = jsonMessage.parsedTimestamp,
                subject = jsonMessage.subject,
                rawBodyText = jsonMessage.bodyText,
                detection = detection,
                isFromCurrentUser = jsonMessage.isFromCurrentUser,
            )
        }.sortedBy { it.timestamp }
    }

    companion object {
        private const val ACCOUNT_UUID = "test_account"
        private const val FOLDER_ID = 1L
        private val threadRootCache = mutableMapOf<String, Long>()

        fun fromFile(filePath: String): JsonMessageSource {
            val json = Json { ignoreUnknownKeys = true }
            val content = File(filePath).readText()
            val messages = json.decodeFromString<List<JsonMailMessage>>(content)
            return JsonMessageSource(messages)
        }
    }
}
