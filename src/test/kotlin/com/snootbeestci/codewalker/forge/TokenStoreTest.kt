package com.snootbeestci.codewalker.forge

import com.snootbeestci.codewalker.settings.CodewalkerSettings
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TokenStoreTest {

    private class FakePasswordSafe : TokenStore.PasswordSafeAdapter {
        val map = mutableMapOf<String, String?>()
        override fun getPassword(key: String): String? = map[key]
        override fun setPassword(key: String, value: String?) {
            if (value == null) map.remove(key) else map[key] = value
        }
    }

    private fun store(): Pair<TokenStore, Pair<FakePasswordSafe, CodewalkerSettings>> {
        val safe = FakePasswordSafe()
        val settings = CodewalkerSettings()
        return TokenStore(passwordSafe = safe, settings = { settings }) to (safe to settings)
    }

    @Test
    fun `set and get a token under canonical host`() {
        val (s, _) = store()
        s.set("github.com", "tok-1")
        assertEquals("tok-1", s.get("github.com"))
    }

    @Test
    fun `host is normalised before storage`() {
        val (s, deps) = store()
        s.set("https://GitHub.com/", "tok-1")
        assertEquals("tok-1", s.get("github.com"))
        assertTrue(deps.first.map.keys.any { it == "Codewalker.ForgeToken.github.com" })
    }

    @Test
    fun `tokens are scoped per host`() {
        val (s, _) = store()
        s.set("github.com", "tok-public")
        s.set("github.mycompany.com", "tok-ghe")
        assertEquals("tok-public", s.get("github.com"))
        assertEquals("tok-ghe", s.get("github.mycompany.com"))
    }

    @Test
    fun `get returns null for unknown host`() {
        val (s, _) = store()
        assertNull(s.get("github.com"))
    }

    @Test
    fun `setting empty token clears credential and removes from known hosts`() {
        val (s, deps) = store()
        s.set("github.com", "tok-1")
        s.set("github.com", "")
        assertNull(s.get("github.com"))
        assertTrue(deps.second.state.knownHosts.isEmpty())
    }

    @Test
    fun `remove deletes credential and known-hosts entry`() {
        val (s, deps) = store()
        s.set("github.com", "tok-1")
        s.remove("github.com")
        assertNull(s.get("github.com"))
        assertTrue(deps.second.state.knownHosts.isEmpty())
    }

    @Test
    fun `knownHosts reports stored hosts`() {
        val (s, _) = store()
        s.set("github.com", "tok-1")
        s.set("github.mycompany.com", "tok-2")
        assertEquals(listOf("github.com", "github.mycompany.com"), s.knownHosts())
    }

    @Test
    fun `empty host is rejected`() {
        val (s, deps) = store()
        s.set("", "tok-1")
        assertTrue(deps.first.map.isEmpty())
        assertTrue(deps.second.state.knownHosts.isEmpty())
    }
}
