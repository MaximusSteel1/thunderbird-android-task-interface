package net.thunderbird.feature.taskmail.internal.data.relay

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEqualTo
import kotlin.test.Test
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import net.thunderbird.feature.taskmail.internal.data.relay.protocol.RelayEvent
import net.thunderbird.feature.taskmail.internal.data.relay.protocol.RelayHelloAck
import net.thunderbird.feature.taskmail.internal.data.relay.protocol.RelayPacket
import net.thunderbird.feature.taskmail.internal.data.relay.protocol.RelayPacketAck
import net.thunderbird.feature.taskmail.internal.data.relay.protocol.RelayResult
import net.thunderbird.feature.taskmail.internal.data.relay.protocol.RelaySessionUpdate
import net.thunderbird.feature.taskmail.internal.domain.model.RelayConnectionState
import net.thunderbird.feature.taskmail.internal.domain.model.RelayTransportConfig
import net.thunderbird.feature.taskmail.internal.domain.projectsync.TaskMailDirectProjectSyncResult

class RelayTaskMailDirectProjectSyncSenderTest {

    @Test
    fun `send should build canonical project sync packet and return accepted result`() = runTest {
        val relayConnectionClient = FakeProjectSyncRelayConnectionClient(
            sendPacketResult = Result.success(
                RelayPacketAck(
                    packetId = "android-taskmail:project-sync:req_001",
                    accepted = true,
                    receiptId = "receipt-1",
                    receivedAt = "2026-03-23T10:00:01Z",
                    transportMessageId = "transport-1",
                ),
            ),
        )
        val testSubject = RelayTaskMailDirectProjectSyncSender(
            relayConnectionClient = relayConnectionClient,
            timestampProvider = { "2026-03-23T10:00:00Z" },
            requestIdFactory = { "req_001" },
        )

        val result = testSubject.send("account-1")

        assertThat(result).isEqualTo(
            TaskMailDirectProjectSyncResult.Accepted(
                requestId = "req_001",
                receiptId = "receipt-1",
                transportMessageId = "transport-1",
            ),
        )
        assertThat(relayConnectionClient.sentPackets.map(RelayPacket::packetId))
            .containsExactly("android-taskmail:project-sync:req_001")
        assertThat(relayConnectionClient.sentAckTimeoutMillis)
            .containsExactly(30_000L)
        assertCanonicalPacket(relayConnectionClient.sentPackets.single())
    }

    @Test
    fun `send should route capability rejection to mail fallback`() = runTest {
        val testSubject = RelayTaskMailDirectProjectSyncSender(
            relayConnectionClient = FakeProjectSyncRelayConnectionClient(
                sendPacketResult = Result.failure(
                    RelayServerException(
                        code = "unsupported_action",
                        message = "direct action is not available",
                    ),
                ),
            ),
            timestampProvider = { "2026-03-23T10:00:00Z" },
            requestIdFactory = { "req_001" },
        )

        val result = testSubject.send("account-1")

        assertThat(result).isEqualTo(
            TaskMailDirectProjectSyncResult.FallbackToMail(
                detailMessage = "direct action is not available",
                requestId = "req_001",
            ),
        )
    }

    @Test
    fun `send should surface packet ack hard rejection when relay returns error code`() = runTest {
        val testSubject = RelayTaskMailDirectProjectSyncSender(
            relayConnectionClient = FakeProjectSyncRelayConnectionClient(
                sendPacketResult = Result.success(
                    RelayPacketAck(
                        packetId = "android-taskmail:project-sync:req_001",
                        accepted = false,
                        receiptId = "receipt-1",
                        receivedAt = "2026-03-23T10:00:01Z",
                        errorCode = "invalid_payload",
                        errorMessage = "origin.sender_account_uuid must be a non-empty string",
                    ),
                ),
            ),
            timestampProvider = { "2026-03-23T10:00:00Z" },
            requestIdFactory = { "req_001" },
        )

        val result = testSubject.send("account-1")

        assertThat(result).isEqualTo(
            TaskMailDirectProjectSyncResult.Rejected(
                errorMessage = "origin.sender_account_uuid must be a non-empty string",
                requestId = "req_001",
                receiptId = "receipt-1",
            ),
        )
    }

    @Test
    fun `send should keep packet ack temporary direct failure on mail fallback path`() = runTest {
        val testSubject = RelayTaskMailDirectProjectSyncSender(
            relayConnectionClient = FakeProjectSyncRelayConnectionClient(
                sendPacketResult = Result.success(
                    RelayPacketAck(
                        packetId = "android-taskmail:project-sync:req_001",
                        accepted = false,
                        receiptId = "receipt-1",
                        receivedAt = "2026-03-23T10:00:01Z",
                        errorCode = "direct_temporarily_unavailable",
                        errorMessage = "direct bridge is temporarily unavailable",
                    ),
                ),
            ),
            timestampProvider = { "2026-03-23T10:00:00Z" },
            requestIdFactory = { "req_001" },
        )

        val result = testSubject.send("account-1")

        assertThat(result).isEqualTo(
            TaskMailDirectProjectSyncResult.FallbackToMail(
                detailMessage = "direct bridge is temporarily unavailable",
                requestId = "req_001",
                receiptId = "receipt-1",
            ),
        )
    }
}

private class FakeProjectSyncRelayConnectionClient(
    private val connectResult: Result<RelayHelloAck> = Result.success(
        RelayHelloAck(
            messageType = "hello_ack",
            connectionId = "connection-1",
            serverTime = "2026-03-23T10:00:00Z",
            heartbeatSeconds = 30,
        ),
    ),
    private val sendPacketResult: Result<RelayPacketAck> = Result.success(
        RelayPacketAck(
            packetId = "packet-1",
            accepted = true,
            receiptId = "receipt-1",
            receivedAt = "2026-03-23T10:00:01Z",
        ),
    ),
) : RelayConnectionClient {
    private val mutableConnectionState = MutableStateFlow<RelayConnectionState>(RelayConnectionState.Idle)
    private val mutableSessionUpdates = MutableSharedFlow<RelaySessionUpdate>(extraBufferCapacity = 1)
    private val mutableServerEvents = MutableSharedFlow<RelayEvent>(extraBufferCapacity = 1)
    private val mutableServerResults = MutableSharedFlow<RelayResult>(extraBufferCapacity = 1)

    val sentPackets = mutableListOf<RelayPacket>()
    val sentAckTimeoutMillis = mutableListOf<Long>()

    override val connectionState = mutableConnectionState
    override val sessionUpdates: SharedFlow<RelaySessionUpdate> = mutableSessionUpdates
    override val serverEvents: SharedFlow<RelayEvent> = mutableServerEvents
    override val serverResults: SharedFlow<RelayResult> = mutableServerResults

    override suspend fun connect(config: RelayTransportConfig): Result<RelayHelloAck> = connectResult

    override suspend fun sendPacket(
        packet: RelayPacket,
        ackTimeoutMillis: Long,
    ): Result<RelayPacketAck> {
        sentPackets += packet
        sentAckTimeoutMillis += ackTimeoutMillis
        return sendPacketResult
    }

    override suspend fun disconnect() {
        mutableConnectionState.value = RelayConnectionState.Idle
    }
}

private fun assertCanonicalPacket(sentPacket: RelayPacket) {
    assertThat(sentPacket.clientTraceId).isEqualTo("req_001")
    assertThat(sentPacket.sentAt).isEqualTo("2026-03-23T10:00:00Z")
    assertThat(sentPacket.dispatchMetadata.string("channel")).isEqualTo("taskmail_android_direct")
    assertThat(sentPacket.dispatchMetadata.string("schema_version"))
        .isEqualTo("taskmail-bootstrap-control-contract-v1")
    assertThat(sentPacket.dispatchMetadata.string("action")).isEqualTo("sync_project_folders")
    assertThat(sentPacket.dispatchMetadata.string("fallback_policy")).isEqualTo("mail")
    assertThat(sentPacket.taskRunPacket.string("schema_version"))
        .isEqualTo("taskmail-bootstrap-control-contract-v1")
    assertThat(sentPacket.taskRunPacket.string("action")).isEqualTo("sync_project_folders")
    assertThat(sentPacket.taskRunPacket.string("request_id")).isEqualTo("req_001")

    val origin = sentPacket.taskRunPacket.jsonObject("origin")
    assertThat(origin.string("client")).isEqualTo("android_taskmail")
    assertThat(origin.string("sender_account_uuid")).isEqualTo("account-1")
    assertThat(sentPacket.taskRunPacket.jsonObject("sync_project_folders").keys).isEqualTo(emptySet())
}

private fun JsonObject.string(key: String): String {
    return getValue(key).jsonPrimitive.content
}

private fun JsonObject.jsonObject(key: String): JsonObject {
    return getValue(key).jsonObject
}
