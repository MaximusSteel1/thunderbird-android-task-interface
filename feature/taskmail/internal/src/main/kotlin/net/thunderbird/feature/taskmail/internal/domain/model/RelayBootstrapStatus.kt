package net.thunderbird.feature.taskmail.internal.domain.model

internal enum class RelayBootstrapStatus(
    val wireValue: String,
) {
    HelloAck("hello_ack"),
    NotConfigured("not_configured"),
    Unauthorized("unauthorized"),
    TokenIdMismatch("token_id_mismatch"),
    UnexpectedResponse("unexpected_response"),
    InvalidJson("invalid_json"),
    SchemeMismatch("scheme_mismatch"),
    TlsFailure("tls_failure"),
    ConnectFailure("connect_failure"),
    Timeout("timeout"),
    InvalidHttpResponse("invalid_http_response"),
    InvalidHandshake("invalid_handshake"),
}
