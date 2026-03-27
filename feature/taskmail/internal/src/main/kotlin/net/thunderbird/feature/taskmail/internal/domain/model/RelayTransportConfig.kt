package net.thunderbird.feature.taskmail.internal.domain.model

import java.security.MessageDigest

internal data class RelayTransportConfig(
    val enabled: Boolean = false,
    val host: String = DEFAULT_HOST,
    val port: Int = DEFAULT_PORT,
    val useTls: Boolean = DEFAULT_USE_TLS,
    val path: String = DEFAULT_PATH,
    val transportToken: String = "",
    val androidAppToken: String = "",
) {
    fun healthUrl(): String {
        return absoluteHttpUrl("/healthz")
    }

    fun fileSurfaceUrl(): String {
        return absoluteHttpUrl("/v1/files")
    }

    fun androidCreateSessionUrl(): String {
        return absoluteHttpUrl(ANDROID_CREATE_SESSION_PATH)
    }

    fun androidEnvironmentInventoryUrl(): String {
        return absoluteHttpUrl(ANDROID_ENVIRONMENT_INVENTORY_PATH)
    }

    fun androidSessionSnapshotUrl(): String {
        return absoluteHttpUrl(ANDROID_SESSION_SNAPSHOT_PATH)
    }

    fun relayUrl(): String {
        return "${websocketScheme()}://$host:$port${normalizedPath()}"
    }

    fun absoluteHttpUrl(path: String): String {
        val trimmedPath = path.trim()
        if (trimmedPath.startsWith("http://") || trimmedPath.startsWith("https://")) {
            return trimmedPath
        }

        val normalizedPath = trimmedPath.ifEmpty { "/" }
            .let { value -> if (value.startsWith("/")) value else "/$value" }

        return "${httpScheme()}://$host:$port$normalizedPath"
    }

    fun isConfigured(): Boolean {
        return host.isNotBlank() && port > 0 && transportToken.isNotBlank()
    }

    fun isAndroidCreateSessionConfigured(): Boolean {
        return host.isNotBlank() && port > 0 && androidAppToken.isNotBlank()
    }

    fun isAndroidEnvironmentInventoryConfigured(): Boolean {
        return host.isNotBlank() && port > 0 && androidAppToken.isNotBlank()
    }

    fun isAndroidSessionSnapshotConfigured(): Boolean {
        return host.isNotBlank() && port > 0 && androidAppToken.isNotBlank()
    }

    fun tokenFingerprint(): String? {
        val token = transportToken.trim().takeIf(String::isNotBlank) ?: return null
        val digest = MessageDigest.getInstance("SHA-256").digest(token.toByteArray())
        return digest.joinToString(separator = "") { byte -> "%02x".format(byte) }
            .take(TOKEN_FINGERPRINT_LENGTH)
    }

    fun normalized(): RelayTransportConfig {
        return copy(
            host = host.trim(),
            path = normalizedPath(),
            transportToken = transportToken.trim(),
            androidAppToken = androidAppToken.trim(),
        )
    }

    private fun httpScheme(): String = if (useTls) "https" else "http"

    private fun websocketScheme(): String = if (useTls) "wss" else "ws"

    private fun normalizedPath(): String {
        val trimmed = path.trim().ifEmpty { DEFAULT_PATH }
        return if (trimmed.startsWith("/")) trimmed else "/$trimmed"
    }

    companion object {
        const val DEFAULT_HOST = "124.223.41.153"
        const val DEFAULT_PORT = 8787
        const val DEFAULT_USE_TLS = false
        const val DEFAULT_PATH = "/relay"
        const val ANDROID_CREATE_SESSION_PATH = "/v1/android/create-session"
        const val ANDROID_ENVIRONMENT_INVENTORY_PATH = "/v1/android/environment-inventory"
        const val ANDROID_SESSION_SNAPSHOT_PATH = "/v1/android/session-snapshot"
        private const val TOKEN_FINGERPRINT_LENGTH = 12
    }
}
