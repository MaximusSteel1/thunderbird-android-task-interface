package net.thunderbird.feature.taskmail.internal.validation

internal data class ResolvedThread(
    val threadRootId: String,
    val messages: List<JsonMailMessage>,
)

internal class ThreadResolver {

    fun resolve(messages: List<JsonMailMessage>): List<ResolvedThread> {
        val messageMap = messages.associateBy { it.messageId }
        val threadRootCache = mutableMapOf<String, String>()

        fun findThreadRoot(messageId: String): String {
            threadRootCache[messageId]?.let { return it }

            val message = messageMap[messageId]
            val rootId = message?.let { currentMessage ->
                resolveReferencedThreadRoot(
                    message = currentMessage,
                    messageMap = messageMap,
                    findThreadRoot = ::findThreadRoot,
                )
            } ?: messageId

            threadRootCache[messageId] = rootId
            return rootId
        }

        val grouped = messages.groupBy { message ->
            findThreadRoot(message.messageId)
        }

        return grouped.map { (threadRootId, threadMessages) ->
            ResolvedThread(
                threadRootId = threadRootId,
                messages = threadMessages.sortedBy { it.parsedTimestamp },
            )
        }.sortedByDescending { thread ->
            thread.messages.maxOfOrNull { it.parsedTimestamp } ?: 0L
        }
    }

    private fun resolveReferencedThreadRoot(
        message: JsonMailMessage,
        messageMap: Map<String, JsonMailMessage>,
        findThreadRoot: (String) -> String,
    ): String {
        return if (message.inReplyTo != null && message.inReplyTo in messageMap) {
            findThreadRoot(message.inReplyTo)
        } else {
            message.references
                .asReversed()
                .firstOrNull { it in messageMap }
                ?.let(findThreadRoot)
                ?: message.messageId
        }
    }
}
