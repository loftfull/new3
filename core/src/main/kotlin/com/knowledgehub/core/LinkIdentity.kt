package com.knowledgehub.core

import java.net.IDN
import java.net.URI
import java.net.URISyntaxException
import java.util.Locale

internal data class LinkIdentity(val canonicalUrl: String, val host: String) {
    companion object {
        fun parse(value: String): LinkIdentity? {
            val uri = try {
                URI(value.trim())
            } catch (_: URISyntaxException) {
                return null
            }
            val scheme = uri.scheme?.lowercase(Locale.ROOT)
            if (scheme != "https" && scheme != "http") return null
            val authority = uri.rawAuthority ?: return null
            if ('@' in authority) return null
            val hostEnd = if (authority.startsWith('[')) {
                authority.indexOf(']') + 1
            } else {
                authority.indexOf(':').takeUnless { it == -1 } ?: authority.length
            }
            val host = normalizeHost(authority.substring(0, hostEnd)) ?: return null
            val portPart = authority.substring(hostEnd)
            val port = if (portPart.isEmpty()) {
                null
            } else {
                val digits = portPart.removePrefix(":")
                if (!portPart.startsWith(':') || digits.any { it !in '0'..'9' }) return null
                digits.toIntOrNull()?.takeIf { it in 1..65535 } ?: return null
            }
            val defaultPort = if (scheme == "https") 443 else 80
            val canonicalUrl = buildString {
                append(scheme)
                append("://")
                append(host)
                if (port != null && port != defaultPort) append(":$port")
                append(uri.rawPath.ifEmpty { "/" })
                uri.rawQuery?.let { append("?$it") }
                uri.rawFragment?.let { append("#$it") }
            }
            return LinkIdentity(canonicalUrl, host)
        }

        private fun normalizeHost(host: String): String? {
            val ascii = if (host.startsWith('[')) {
                host
            } else {
                try {
                    IDN.toASCII(host, IDN.USE_STD3_ASCII_RULES)
                } catch (_: IllegalArgumentException) {
                    return null
                }
            }
            return ascii.lowercase(Locale.ROOT).ifEmpty { null }
        }
    }
}
