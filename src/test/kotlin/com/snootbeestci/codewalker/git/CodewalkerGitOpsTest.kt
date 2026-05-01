package com.snootbeestci.codewalker.git

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CodewalkerGitOpsTest {

    @Test
    fun `stash message embeds the codewalker tag and session token`() {
        val msg = CodewalkerGitOps.stashMessage("abc123")
        assertEquals("codewalker-abc123", msg)
        assertTrue(msg.startsWith(CodewalkerGitOps.CODEWALKER_STASH_TAG))
    }

    @Test
    fun `filterCodewalkerStashes keeps only entries containing the tag`() {
        val lines = listOf(
            "stash@{0} On main: codewalker-abc123",
            "stash@{1} WIP on main: hand-rolled fix",
            "stash@{2} On feature: codewalker-deadbee",
            "stash@{3} something else",
        )
        val filtered = CodewalkerGitOps.filterCodewalkerStashes(lines)
        assertEquals(
            listOf(
                "stash@{0} On main: codewalker-abc123",
                "stash@{2} On feature: codewalker-deadbee",
            ),
            filtered,
        )
    }

    @Test
    fun `filterCodewalkerStashes returns empty when no codewalker entries`() {
        val lines = listOf(
            "stash@{0} WIP on main: foo",
            "stash@{1} WIP on main: bar",
        )
        assertEquals(emptyList<String>(), CodewalkerGitOps.filterCodewalkerStashes(lines))
    }

    @Test
    fun `filterCodewalkerStashes returns empty for empty input`() {
        assertEquals(emptyList<String>(), CodewalkerGitOps.filterCodewalkerStashes(emptyList()))
    }
}
