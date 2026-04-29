package com.snootbeestci.codewalker.forge

import com.intellij.credentialStore.CredentialAttributes
import com.intellij.ide.passwordSafe.PasswordSafe
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.snootbeestci.codewalker.settings.CodewalkerSettings

/**
 * Per-host forge token storage.
 *
 * Tokens are stored in IDE `PasswordSafe` under one credential per host,
 * keyed by the normalised host string. Host enumeration is impossible
 * through `PasswordSafe` itself, so the set of known hosts is mirrored in
 * `CodewalkerSettings.knownHosts` — never read this list as authoritative
 * for whether a token exists, only for which hosts to display in the UI.
 *
 * Registered as an application-level service. Production code accesses
 * the singleton via `TokenStore.getInstance()`. Tests construct instances
 * directly with their own adapters; `@Service` does not prevent direct
 * construction, it only registers a singleton for `service<T>()` lookups.
 */
@Service(Service.Level.APP)
class TokenStore(
    private val passwordSafe: PasswordSafeAdapter = DefaultPasswordSafeAdapter,
    private val settings: () -> CodewalkerSettings = { CodewalkerSettings.getInstance() },
) {

    fun get(host: String): String? {
        val key = HostNormalizer.normalize(host)
        if (key.isEmpty()) return null
        val v = passwordSafe.getPassword(credentialKey(key))
        return v?.takeIf { it.isNotEmpty() }
    }

    fun set(host: String, token: String) {
        val key = HostNormalizer.normalize(host)
        if (key.isEmpty()) return
        if (token.isEmpty()) {
            // Empty token means "remove" — write null to PasswordSafe rather
            // than an empty-string sentinel, so external credential viewers
            // and future code paths see no entry at all.
            passwordSafe.setPassword(credentialKey(key), null)
            forgetHost(key)
            return
        }
        passwordSafe.setPassword(credentialKey(key), token)
        rememberHost(key)
    }

    fun remove(host: String) {
        val key = HostNormalizer.normalize(host)
        if (key.isEmpty()) return
        passwordSafe.setPassword(credentialKey(key), null)
        forgetHost(key)
    }

    /**
     * Hosts the user has stored a token for. Sourced from settings so the
     * UI does not have to enumerate the credential store.
     */
    fun knownHosts(): List<String> = settings().state.knownHosts.toList()

    private fun rememberHost(host: String) {
        val s = settings().state
        if (host !in s.knownHosts) {
            s.knownHosts.add(host)
            s.knownHosts.sort()
        }
    }

    private fun forgetHost(host: String) {
        settings().state.knownHosts.remove(host)
    }

    interface PasswordSafeAdapter {
        fun getPassword(key: String): String?
        fun setPassword(key: String, value: String?)
    }

    internal object DefaultPasswordSafeAdapter : PasswordSafeAdapter {
        override fun getPassword(key: String): String? =
            PasswordSafe.instance.getPassword(CredentialAttributes(key))

        override fun setPassword(key: String, value: String?) {
            PasswordSafe.instance.setPassword(CredentialAttributes(key), value)
        }
    }

    companion object {
        const val KEY_PREFIX = "Codewalker.ForgeToken."

        fun credentialKey(host: String): String = KEY_PREFIX + host

        /**
         * Application-level singleton accessor. Tests construct instances
         * directly with their own adapters; production code uses this.
         */
        fun getInstance(): TokenStore =
            ApplicationManager.getApplication().getService(TokenStore::class.java)
    }
}
