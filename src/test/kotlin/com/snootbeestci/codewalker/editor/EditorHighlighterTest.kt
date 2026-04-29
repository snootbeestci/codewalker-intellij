package com.snootbeestci.codewalker.editor

import com.intellij.testFramework.LightVirtualFile
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class EditorHighlighterTest {

    @Test
    fun `single contiguous block of added lines`() {
        val diff = """
            @@ -10,3 +10,5 @@
             context line one
            +added line one
            +added line two
             context line two
        """.trimIndent()

        val ranges = EditorHighlighter.computeAddedLineRanges(diff, 10)

        assertEquals(listOf(11..12), ranges)
    }

    @Test
    fun `multiple non-contiguous blocks separated by context`() {
        val diff = """
            @@ -10,7 +10,9 @@
             context one
            +added one
             context two
            +added two
            +added three
             context three
        """.trimIndent()

        val ranges = EditorHighlighter.computeAddedLineRanges(diff, 10)

        assertEquals(listOf(11..11, 13..14), ranges)
    }

    @Test
    fun `pure deletion produces no ranges`() {
        val diff = """
            @@ -10,3 +10,1 @@
             context
            -removed one
            -removed two
        """.trimIndent()

        val ranges = EditorHighlighter.computeAddedLineRanges(diff, 10)

        assertEquals(emptyList<IntRange>(), ranges)
    }

    @Test
    fun `deletions interleaved with additions`() {
        val diff = """
            @@ -10,4 +10,4 @@
             context
            -removed
            +added
             context
        """.trimIndent()

        val ranges = EditorHighlighter.computeAddedLineRanges(diff, 10)

        assertEquals(listOf(11..11), ranges)
    }

    @Test
    fun `no newline at end of file marker is ignored`() {
        val diff = """
            @@ -10,2 +10,3 @@
             context
            +added one
            +added two
            \ No newline at end of file
        """.trimIndent()

        val ranges = EditorHighlighter.computeAddedLineRanges(diff, 10)

        assertEquals(listOf(11..12), ranges)
    }

    @Test
    fun `empty diff returns empty list`() {
        val ranges = EditorHighlighter.computeAddedLineRanges("", 10)

        assertEquals(emptyList<IntRange>(), ranges)
    }

    @Test
    fun `addition at the very start of the hunk`() {
        val diff = """
            @@ -10,3 +10,4 @@
            +added at start
             context one
             context two
             context three
        """.trimIndent()

        val ranges = EditorHighlighter.computeAddedLineRanges(diff, 10)

        assertEquals(listOf(10..10), ranges)
    }

    @Test
    fun `addition at the very end of the hunk`() {
        val diff = """
            @@ -10,3 +10,4 @@
             context one
             context two
             context three
            +added at end
        """.trimIndent()

        val ranges = EditorHighlighter.computeAddedLineRanges(diff, 10)

        assertEquals(listOf(13..13), ranges)
    }

    @Test
    fun `hunk header line is skipped`() {
        val diff = """
            @@ -10,3 +10,4 @@
             context
            +added
             context
        """.trimIndent()

        val ranges = EditorHighlighter.computeAddedLineRanges(diff, 10)

        assertEquals(listOf(11..11), ranges)
    }

    @Test
    fun `content equality - same bytes match`() {
        assertTrue(byteArrayOf(1, 2, 3).contentEquals(byteArrayOf(1, 2, 3)))
    }

    @Test
    fun `content equality - different bytes don't match`() {
        assertFalse(byteArrayOf(1, 2, 3).contentEquals(byteArrayOf(1, 2, 4)))
    }

    @Test
    fun `content equality - empty arrays match each other`() {
        assertTrue(byteArrayOf().contentEquals(byteArrayOf()))
    }

    @Test
    fun `chooseEditor - no working tree file picks LightVirtual`() {
        val head = byteArrayOf(1, 2, 3)
        val choice = EditorHighlighter.chooseEditor(null, null, head, "abc1234")

        assertTrue(choice is EditorHighlighter.EditorChoice.LightVirtual)
        choice as EditorHighlighter.EditorChoice.LightVirtual
        assertSame(head, choice.content)
        assertEquals("abc1234", choice.ref)
    }

    @Test
    fun `chooseEditor - working tree with matching content picks WorkingTree`() {
        val file = LightVirtualFile("a.kt")
        val bytes = byteArrayOf(1, 2, 3)
        val choice = EditorHighlighter.chooseEditor(file, bytes, byteArrayOf(1, 2, 3), "abc1234")

        assertTrue(choice is EditorHighlighter.EditorChoice.WorkingTree)
        choice as EditorHighlighter.EditorChoice.WorkingTree
        assertSame(file, choice.file)
    }

    @Test
    fun `chooseEditor - working tree with different content picks LightVirtual`() {
        val file = LightVirtualFile("a.kt")
        val choice = EditorHighlighter.chooseEditor(
            file,
            byteArrayOf(1, 2, 3),
            byteArrayOf(9, 9, 9),
            "abc1234",
        )

        assertTrue(choice is EditorHighlighter.EditorChoice.LightVirtual)
    }

    @Test
    fun `chooseEditor - working tree empty with non-empty head picks LightVirtual`() {
        val file = LightVirtualFile("a.kt")
        val head = byteArrayOf(1, 2, 3)
        val choice = EditorHighlighter.chooseEditor(file, byteArrayOf(), head, "abc1234")

        assertTrue(choice is EditorHighlighter.EditorChoice.LightVirtual)
    }

    @Test
    fun `chooseEditor - both empty picks WorkingTree`() {
        val file = LightVirtualFile("a.kt")
        val choice = EditorHighlighter.chooseEditor(file, byteArrayOf(), byteArrayOf(), "abc1234")

        assertTrue(choice is EditorHighlighter.EditorChoice.WorkingTree)
    }
}
