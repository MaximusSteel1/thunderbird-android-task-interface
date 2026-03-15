package net.thunderbird.feature.taskmail.internal.validation

import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class JsonMailMessage(
    @SerialName("message_id")
    val messageId: String,
    val subject: String,
    @SerialName("from_addr")
    val fromAddr: String,
    @SerialName("to_addr")
    val toAddr: String,
    val date: String,
    @SerialName("in_reply_to")
    val inReplyTo: String? = null,
    val references: List<String> = emptyList(),
    @SerialName("body_text")
    val bodyText: String,
    @SerialName("raw_headers")
    val rawHeaders: Map<String, String> = emptyMap(),
) {
    val parsedTimestamp: Long by lazy {
        parseRfc2822Date(date)
    }

    val isFromCurrentUser: Boolean by lazy {
        fromAddr.equals(toAddr, ignoreCase = true)
    }

    companion object {
        private val dateFormatter = DateTimeFormatter.RFC_1123_DATE_TIME

        fun parseRfc2822Date(dateStr: String): Long {
            return try {
                val normalized = normalizeRfc2822Date(dateStr)
                val parsed = OffsetDateTime.parse(normalized, dateFormatter)
                parsed.toInstant().toEpochMilli()
            } catch (_: RuntimeException) {
                System.currentTimeMillis()
            }
        }

        private fun normalizeRfc2822Date(dateStr: String): String {
            return dateStr
                .replace(Regex("(\\d{1,2}):(\\d{2}):(\\d{2})"), "$1$2$3")
                .let { str ->
                    val match = Regex("(\\d{1,2})(\\d{2})(\\d{2})").find(str)
                    if (match != null) {
                        str.replaceRange(
                            match.range,
                            "${match.groupValues[1]}:${match.groupValues[2]}:${match.groupValues[3]}",
                        )
                    } else {
                        str
                    }
                }
        }
    }
}
