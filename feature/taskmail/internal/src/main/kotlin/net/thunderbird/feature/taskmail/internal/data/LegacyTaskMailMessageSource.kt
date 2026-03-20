package net.thunderbird.feature.taskmail.internal.data

import net.thunderbird.feature.taskmail.internal.data.ingress.EmailIngress
import net.thunderbird.feature.taskmail.internal.data.parser.EmailMessageParser

internal class LegacyTaskMailMessageSource(
    private val emailIngress: EmailIngress,
    private val emailMessageParser: EmailMessageParser,
) : TaskMailMessageSource {

    override suspend fun getMessages(): List<TaskMailMessage> {
        return emailIngress.fetchLatest(recentMessageLimit = Int.MAX_VALUE)
            .asSequence()
            .mapNotNull(emailMessageParser::parse)
            .filter { it.detection.isTaskMail }
            .sortedBy(TaskMailMessage::timestamp)
            .toList()
    }
}
