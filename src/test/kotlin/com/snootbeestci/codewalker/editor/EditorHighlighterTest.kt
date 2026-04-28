package com.snootbeestci.codewalker.editor

import org.junit.jupiter.api.Assertions.assertEquals
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
}
