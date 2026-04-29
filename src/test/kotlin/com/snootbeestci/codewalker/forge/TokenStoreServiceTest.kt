package com.snootbeestci.codewalker.forge

import com.intellij.testFramework.fixtures.BasePlatformTestCase

/**
 * Regression test for the @Service registration. The unit-level
 * TokenStoreTest constructs the class directly, which would still pass
 * if the @Service annotation were accidentally removed; this test
 * exercises the platform service registry path that production code
 * goes through.
 */
class TokenStoreServiceTest : BasePlatformTestCase() {

    fun `test service is registered and resolvable`() {
        val instance = TokenStore.getInstance()
        assertNotNull(instance)
        // Same instance returned on subsequent calls — the platform's job,
        // but verifying it here protects against accidental @Service removal.
        assertSame(instance, TokenStore.getInstance())
    }
}
