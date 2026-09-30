package app.securemessenger.ui

import java.net.URI

enum class RelayClientAddressWarning {
    PHONE_LOOPBACK,
    UNSPECIFIED_BIND_ADDRESS,
}

enum class RelayTestMode {
    LAN_RELAY,
    PUBLIC_RELAY,
}

fun relayTestMode(url: String): RelayTestMode? {
    val scheme = runCatching { URI(url.trim()).scheme?.lowercase() }.getOrNull() ?: return null
    return when (scheme) {
        "http" -> RelayTestMode.LAN_RELAY
        "https" -> RelayTestMode.PUBLIC_RELAY
        else -> null
    }
}

/**
 * Hints for typical relay URL mistakes on a physical phone. 127.0.0.1 is still correct with
 * `adb reverse`, so this is a hint, never a block.
 */
fun relayClientAddressWarning(url: String, isPhysicalDevice: Boolean): RelayClientAddressWarning? {
    if (!isPhysicalDevice) return null
    val host = runCatching { URI(url.trim()).host?.lowercase() }.getOrNull() ?: return null
    // java.net.URI keeps the brackets of an IPv6 literal ("[::1]").
    return when (host.removePrefix("[").removeSuffix("]")) {
        "127.0.0.1", "localhost", "::1" -> RelayClientAddressWarning.PHONE_LOOPBACK
        "0.0.0.0" -> RelayClientAddressWarning.UNSPECIFIED_BIND_ADDRESS
        else -> null
    }
}
