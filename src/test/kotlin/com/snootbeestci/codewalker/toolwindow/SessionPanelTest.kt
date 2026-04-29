package com.snootbeestci.codewalker.toolwindow

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class SessionPanelTest {

    @Test
    fun `empty list returns empty prefix`() {
        assertEquals("", longestCommonDirectoryPrefix(emptyList()))
    }

    @Test
    fun `single file at root returns empty prefix`() {
        assertEquals("", longestCommonDirectoryPrefix(listOf("Foo.kt")))
    }

    @Test
    fun `single file in directory returns its directory`() {
        assertEquals("src/", longestCommonDirectoryPrefix(listOf("src/Foo.kt")))
    }

    @Test
    fun `single file in nested directory returns its full directory`() {
        assertEquals(
            "src/main/kotlin/",
            longestCommonDirectoryPrefix(listOf("src/main/kotlin/Foo.kt"))
        )
    }

    @Test
    fun `two files sharing prefix returns the prefix`() {
        assertEquals(
            "src/main/kotlin/",
            longestCommonDirectoryPrefix(
                listOf("src/main/kotlin/Foo.kt", "src/main/kotlin/Bar.kt")
            )
        )
    }

    @Test
    fun `two files with no common prefix returns empty`() {
        assertEquals(
            "",
            longestCommonDirectoryPrefix(listOf("src/Foo.kt", "test/Bar.kt"))
        )
    }

    @Test
    fun `one path is a directory prefix of the other returns shared prefix`() {
        assertEquals(
            "src/",
            longestCommonDirectoryPrefix(listOf("src/Foo.kt", "src/main/Bar.kt"))
        )
    }

    @Test
    fun `mixed depths with no common parent returns empty`() {
        assertEquals(
            "",
            longestCommonDirectoryPrefix(listOf("Foo.kt", "src/Bar.kt"))
        )
    }

    @Test
    fun `three files share two segments and diverge below`() {
        assertEquals(
            "a/b/",
            longestCommonDirectoryPrefix(
                listOf("a/b/c/Foo.kt", "a/b/d/Bar.kt", "a/b/e/Baz.kt")
            )
        )
    }

    @Test
    fun `identical paths return the directory portion`() {
        assertEquals(
            "src/main/",
            longestCommonDirectoryPrefix(
                listOf("src/main/Foo.kt", "src/main/Foo.kt")
            )
        )
    }

    @Test
    fun `directory boundary is respected not character boundary`() {
        // "src/main/foo" and "src/main/foobar" should collapse at directory boundary,
        // not include the partial segment match.
        assertEquals(
            "src/main/",
            longestCommonDirectoryPrefix(
                listOf("src/main/foo/A.kt", "src/main/foobar/B.kt")
            )
        )
    }
}
