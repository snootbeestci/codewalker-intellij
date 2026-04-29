package com.snootbeestci.codewalker.forge

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class HostNormalizerTest {

    @Test
    fun `bare hostname is returned unchanged when already canonical`() {
        assertEquals("github.com", HostNormalizer.normalize("github.com"))
    }

    @Test
    fun `https scheme is stripped`() {
        assertEquals("github.com", HostNormalizer.normalize("https://github.com"))
    }

    @Test
    fun `http scheme is stripped`() {
        assertEquals("github.mycompany.com", HostNormalizer.normalize("http://github.mycompany.com"))
    }

    @Test
    fun `trailing slash is stripped`() {
        assertEquals("github.com", HostNormalizer.normalize("github.com/"))
    }

    @Test
    fun `mixed case is lowercased`() {
        assertEquals("github.com", HostNormalizer.normalize("GitHub.com"))
    }

    @Test
    fun `leading and trailing whitespace is trimmed`() {
        assertEquals("github.com", HostNormalizer.normalize("  github.com  "))
    }

    @Test
    fun `ghe-style subdomain is preserved`() {
        assertEquals("github.mycompany.com", HostNormalizer.normalize("https://GitHub.MyCompany.com/"))
    }

    @Test
    fun `port is preserved`() {
        assertEquals("gitea.internal:3000", HostNormalizer.normalize("https://gitea.internal:3000/"))
    }

    @Test
    fun `trailing dot is stripped`() {
        assertEquals("github.com", HostNormalizer.normalize("github.com."))
    }

    @Test
    fun `empty input yields empty string`() {
        assertEquals("", HostNormalizer.normalize(""))
        assertEquals("", HostNormalizer.normalize("   "))
    }

    @Test
    fun `fromUrl extracts host from a github PR URL`() {
        assertEquals(
            "github.com",
            HostNormalizer.fromUrl("https://github.com/snootbeestci/codewalker/pull/29")
        )
    }

    @Test
    fun `fromUrl extracts host from a GHE URL with mixed case`() {
        assertEquals(
            "github.mycompany.com",
            HostNormalizer.fromUrl("https://GitHub.MyCompany.com/owner/repo/pull/1")
        )
    }

    @Test
    fun `fromUrl preserves a non-default port`() {
        assertEquals(
            "gitea.internal:3000",
            HostNormalizer.fromUrl("https://gitea.internal:3000/owner/repo")
        )
    }

    @Test
    fun `fromUrl returns empty for an empty string`() {
        assertEquals("", HostNormalizer.fromUrl(""))
    }

    @Test
    fun `fromUrl handles a URL without scheme`() {
        assertEquals(
            "github.com",
            HostNormalizer.fromUrl("github.com/owner/repo/pull/1")
        )
    }
}
