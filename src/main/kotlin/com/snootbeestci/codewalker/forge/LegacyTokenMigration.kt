package com.snootbeestci.codewalker.forge

import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity

/**
 * Migrates the pre-multi-host `Codewalker.GitHubToken` credential to
 * `Codewalker.ForgeToken.github.com` on first run after upgrade.
 *
 * Runs once per project open as a no-op when the legacy credential is
 * absent. Idempotent — safe to run repeatedly. The legacy credential is
 * cleared after a successful copy.
 *
 * If a token already exists at the new key for github.com, the legacy
 * credential is cleared without overwriting the new one — the user has
 * already configured a per-host token and we do not silently replace it.
 */
class LegacyTokenMigration : ProjectActivity {

    override suspend fun execute(project: Project) {
        migrate(TokenStore.DefaultPasswordSafeAdapter, TokenStore.getInstance())
    }

    companion object {
        const val LEGACY_KEY = "Codewalker.GitHubToken"
        const val LEGACY_HOST = "github.com"

        internal fun migrate(
            legacySafe: TokenStore.PasswordSafeAdapter,
            store: TokenStore,
        ) {
            val legacy = legacySafe.getPassword(LEGACY_KEY)
            if (legacy.isNullOrBlank()) {
                if (legacy != null) {
                    // Defensive: clear an empty-string sentinel left by an
                    // earlier write so PasswordSafe stays clean.
                    legacySafe.setPassword(LEGACY_KEY, null)
                }
                return
            }
            val existing = store.get(LEGACY_HOST)
            if (existing.isNullOrEmpty()) {
                store.set(LEGACY_HOST, legacy)
                thisLogger().info(
                    "Migrated legacy GitHub token to per-host store under $LEGACY_HOST"
                )
            } else {
                thisLogger().info(
                    "Legacy GitHub token found but per-host token already set for " +
                        "$LEGACY_HOST; legacy entry will be cleared"
                )
            }
            legacySafe.setPassword(LEGACY_KEY, null)
        }
    }
}
