package com.snootbeestci.codewalker.forge

import java.net.URI

/**
 * Mirrors the server-side `forge.NormalizeHost` rule. The plugin must use
 * the canonical form for two reasons: it is what the server expects on
 * `host`-bearing RPCs, and it is the key under which per-host tokens are
 * stored. Both sides must agree byte-for-byte.
 */
object HostNormalizer {

    fun normalize(s: String): String {
        var v = s.trim()
        v = v.removePrefix("https://")
        v = v.removePrefix("http://")
        v = v.trimEnd('/')
        v = v.trimEnd('.')
        return v.lowercase()
    }

    /**
     * Extracts the canonical host from a forge URL such as
     * `https://github.com/owner/repo/pull/123`. Returns the empty string if
     * no host can be parsed.
     */
    fun fromUrl(url: String): String {
        val trimmed = url.trim()
        if (trimmed.isEmpty()) return ""
        val withScheme = if (trimmed.contains("://")) trimmed else "https://$trimmed"
        val host = try {
            URI(withScheme).host ?: return ""
        } catch (_: Exception) {
            return ""
        }
        val port = try {
            URI(withScheme).port
        } catch (_: Exception) {
            -1
        }
        val raw = if (port > 0) "$host:$port" else host
        return normalize(raw)
    }
}
