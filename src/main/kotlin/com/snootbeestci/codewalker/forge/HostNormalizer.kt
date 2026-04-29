package com.snootbeestci.codewalker.forge

import java.net.URI

/**
 * Mirrors the server-side `forge.NormalizeHost` rule. The plugin must use
 * the canonical form for two reasons: it is what the server expects on
 * `host`-bearing RPCs, and it is the key under which per-host tokens are
 * stored. Both sides must agree byte-for-byte.
 */
object HostNormalizer {

    sealed class UrlParseResult {
        data class Ok(val host: String) : UrlParseResult()
        data object Empty : UrlParseResult()
        data class ParseFailed(val reason: String) : UrlParseResult()
    }

    fun normalize(s: String): String {
        var v = s.trim()
        v = v.removePrefix("https://")
        v = v.removePrefix("http://")
        v = v.trimEnd('/')
        v = v.trimEnd('.')
        return v.lowercase()
    }

    /**
     * Result-bearing variant of `fromUrl`. Use this when the caller needs to
     * distinguish "the user gave an empty URL" from "the URL is malformed".
     */
    fun fromUrlResult(url: String): UrlParseResult {
        val trimmed = url.trim()
        if (trimmed.isEmpty()) return UrlParseResult.Empty

        val withScheme = if (trimmed.contains("://")) trimmed else "https://$trimmed"
        val uri = try {
            URI(withScheme)
        } catch (e: Exception) {
            return UrlParseResult.ParseFailed("malformed URL: ${e.message ?: "could not parse"}")
        }
        val host = uri.host
        if (host.isNullOrEmpty()) {
            return UrlParseResult.ParseFailed("URL contains no host component")
        }
        val raw = if (uri.port > 0) "$host:${uri.port}" else host
        return UrlParseResult.Ok(normalize(raw))
    }

    /**
     * Extracts the canonical host from a forge URL such as
     * `https://github.com/owner/repo/pull/123`. Returns the empty string if
     * no host can be parsed. Thin wrapper over `fromUrlResult` for callers
     * that don't need to distinguish empty input from parse failure.
     */
    fun fromUrl(url: String): String = when (val r = fromUrlResult(url)) {
        is UrlParseResult.Ok -> r.host
        else -> ""
    }
}
