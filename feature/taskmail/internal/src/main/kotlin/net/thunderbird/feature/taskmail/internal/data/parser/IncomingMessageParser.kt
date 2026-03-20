package net.thunderbird.feature.taskmail.internal.data.parser

internal interface IncomingMessageParser<RawMessage, ParsedMessage> {
    fun parse(message: RawMessage): ParsedMessage?
}
