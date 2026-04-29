package com.snootbeestci.codewalker.forge

import com.snootbeestci.codewalker.settings.CodewalkerSettings
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class LegacyTokenMigrationTest {

    private class FakePasswordSafe : TokenStore.PasswordSafeAdapter {
        val map = mutableMapOf<String, String?>()
        override fun getPassword(key: String): String? = map[key]
        override fun setPassword(key: String, value: String?) {
            if (value == null) map.remove(key) else map[key] = value
        }
    }

    private fun fixture(): Triple<FakePasswordSafe, TokenStore, CodewalkerSettings> {
        val safe = FakePasswordSafe()
        val settings = CodewalkerSettings()
        val store = TokenStore(passwordSafe = safe, settings = { settings })
        return Triple(safe, store, settings)
    }

    @Test
    fun `legacy credential is migrated when no per-host token exists`() {
        val (safe, store, _) = fixture()
        safe.map[LegacyTokenMigration.LEGACY_KEY] = "ghp_legacy"

        LegacyTokenMigration.migrate(safe, store)

        assertEquals("ghp_legacy", store.get("github.com"))
        assertNull(safe.map[LegacyTokenMigration.LEGACY_KEY])
    }

    @Test
    fun `existing per-host token is not overwritten`() {
        val (safe, store, _) = fixture()
        store.set("github.com", "ghp_new")
        safe.map[LegacyTokenMigration.LEGACY_KEY] = "ghp_legacy"

        LegacyTokenMigration.migrate(safe, store)

        assertEquals("ghp_new", store.get("github.com"))
        assertNull(safe.map[LegacyTokenMigration.LEGACY_KEY])
    }

    @Test
    fun `absent legacy credential is a no-op`() {
        val (safe, store, _) = fixture()

        LegacyTokenMigration.migrate(safe, store)

        assertNull(store.get("github.com"))
        assertNull(safe.map[LegacyTokenMigration.LEGACY_KEY])
    }

    @Test
    fun `blank legacy credential is cleared without writing a per-host token`() {
        val (safe, store, _) = fixture()
        safe.map[LegacyTokenMigration.LEGACY_KEY] = "   "

        LegacyTokenMigration.migrate(safe, store)

        assertNull(store.get("github.com"))
        assertNull(safe.map[LegacyTokenMigration.LEGACY_KEY])
    }

    @Test
    fun `migration is idempotent when run twice`() {
        val (safe, store, _) = fixture()
        safe.map[LegacyTokenMigration.LEGACY_KEY] = "ghp_legacy"

        LegacyTokenMigration.migrate(safe, store)
        LegacyTokenMigration.migrate(safe, store)

        assertEquals("ghp_legacy", store.get("github.com"))
        assertNull(safe.map[LegacyTokenMigration.LEGACY_KEY])
    }
}
