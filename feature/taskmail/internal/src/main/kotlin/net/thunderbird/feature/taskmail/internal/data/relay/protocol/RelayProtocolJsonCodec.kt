package net.thunderbird.feature.taskmail.internal.data.relay.protocol

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

internal class RelayProtocolJsonCodec(
    private val json: Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    },
) {

    fun encodeHello(message: RelayHello): String {
        return json.encodeToString(RelayHello.serializer(), message)
    }

    fun encodePacket(message: RelayPacket): String {
        return json.encodeToString(RelayPacket.serializer(), message)
    }

    fun decodeServerMessage(payload: String): RelayServerMessage {
        val messageType = json.parseToJsonElement(payload)
            .jsonObject["message_type"]
            ?.jsonPrimitive
            ?.content
            .orEmpty()

        return when (messageType) {
            HELLO_ACK_TYPE -> {
                RelayServerMessage.HelloAck(
                    json.decodeFromString(RelayHelloAck.serializer(), payload),
                )
            }

            RelayPacketAck.MESSAGE_TYPE -> {
                RelayServerMessage.PacketAck(
                    json.decodeFromString(RelayPacketAck.serializer(), payload),
                )
            }

            RelaySessionUpdate.MESSAGE_TYPE -> {
                RelayServerMessage.SessionUpdate(
                    json.decodeFromString(RelaySessionUpdate.serializer(), payload),
                )
            }

            RelayError.MESSAGE_TYPE -> {
                RelayServerMessage.Error(
                    json.decodeFromString(RelayError.serializer(), payload),
                )
            }

            else -> throw SerializationException("Unsupported relay server message_type: $messageType")
        }
    }

    private companion object {
        const val HELLO_ACK_TYPE = "hello_ack"
    }
}

internal sealed interface RelayServerMessage {
    data class HelloAck(
        val message: RelayHelloAck,
    ) : RelayServerMessage

    data class PacketAck(
        val message: RelayPacketAck,
    ) : RelayServerMessage

    data class SessionUpdate(
        val message: RelaySessionUpdate,
    ) : RelayServerMessage

    data class Error(
        val message: RelayError,
    ) : RelayServerMessage
}
