package com.snootbeestci.codewalker.toolwindow

import codewalker.v1.Codewalker.HunkSpan
import codewalker.v1.Codewalker.ReviewFile
import codewalker.v1.Codewalker.Step
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SessionPanelTest {

    private fun step(id: String, filePath: String, newStart: Int, newLines: Int = 1): Step =
        Step.newBuilder()
            .setId(id)
            .setHunkSpan(
                HunkSpan.newBuilder()
                    .setFilePath(filePath)
                    .setNewStart(newStart)
                    .setNewLines(newLines)
                    .build()
            )
            .build()

    private fun file(path: String): ReviewFile =
        ReviewFile.newBuilder().setFilePath(path).build()

    private fun headerOrder(items: List<StepListItem>): List<String> =
        items.filterIsInstance<StepListItem.Header>().map { it.filePath }

    private fun rowSteps(items: List<StepListItem>): List<String> =
        items.filterIsInstance<StepListItem.StepRow>().map { it.stepId }

    @Test
    fun `files render in forge_context order, not steps wire order`() {
        val steps = listOf(
            step("hunk:b.kt:10", "b.kt", 10),
            step("hunk:a.kt:5", "a.kt", 5),
            step("hunk:b.kt:30", "b.kt", 30),
        )
        val files = listOf(file("a.kt"), file("b.kt"))

        val items = buildStepListItems(steps, files)

        assertEquals(listOf("a.kt", "b.kt"), headerOrder(items))
    }

    @Test
    fun `hunks within a file are sorted by new_start`() {
        val steps = listOf(
            step("hunk:a.kt:30", "a.kt", 30),
            step("hunk:a.kt:5", "a.kt", 5),
            step("hunk:a.kt:15", "a.kt", 15),
        )
        val files = listOf(file("a.kt"))

        val items = buildStepListItems(steps, files)

        assertEquals(
            listOf("hunk:a.kt:5", "hunk:a.kt:15", "hunk:a.kt:30"),
            rowSteps(items),
        )
    }

    @Test
    fun `steps without a matching forge_context file are dropped from rendering`() {
        val steps = listOf(
            step("hunk:a.kt:5", "a.kt", 5),
            step("hunk:ghost.kt:1", "ghost.kt", 1),
        )
        val files = listOf(file("a.kt"))

        val items = buildStepListItems(steps, files)

        assertEquals(listOf("a.kt"), headerOrder(items))
        assertEquals(listOf("hunk:a.kt:5"), rowSteps(items))
    }

    @Test
    fun `orphaned step paths are reported`() {
        val steps = listOf(
            step("hunk:a.kt:5", "a.kt", 5),
            step("hunk:ghost.kt:1", "ghost.kt", 1),
        )
        val files = listOf(file("a.kt"))

        val orphans = orphanedStepPaths(steps, files)

        assertEquals(setOf("ghost.kt"), orphans)
    }

    @Test
    fun `display order walks files then sorted hunks`() {
        val steps = listOf(
            step("hunk:b.kt:10", "b.kt", 10),
            step("hunk:a.kt:30", "a.kt", 30),
            step("hunk:a.kt:5", "a.kt", 5),
            step("hunk:b.kt:1", "b.kt", 1),
        )
        val files = listOf(file("a.kt"), file("b.kt"))

        val ordered = displayOrderedSteps(steps, files)

        assertEquals(
            listOf("hunk:a.kt:5", "hunk:a.kt:30", "hunk:b.kt:1", "hunk:b.kt:10"),
            ordered.map { it.id },
        )
    }

    @Test
    fun `files with no steps are skipped from headers`() {
        val steps = listOf(step("hunk:a.kt:5", "a.kt", 5))
        val files = listOf(file("a.kt"), file("empty.kt"))

        val items = buildStepListItems(steps, files)

        assertEquals(listOf("a.kt"), headerOrder(items))
    }

    @Test
    fun `common directory prefix is emitted as a subtitle`() {
        val steps = listOf(
            step("hunk:1", "src/main/A.kt", 5),
            step("hunk:2", "src/main/B.kt", 5),
        )
        val files = listOf(file("src/main/A.kt"), file("src/main/B.kt"))

        val items = buildStepListItems(steps, files)

        val subtitles = items.filterIsInstance<StepListItem.Subtitle>()
        assertEquals(listOf("src/main/"), subtitles.map { it.text })
        assertEquals(listOf("A.kt", "B.kt"), headerOrder(items))
    }

    @Test
    fun `empty inputs render to no items`() {
        assertTrue(buildStepListItems(emptyList(), emptyList()).isEmpty())
    }

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
    fun `label shows first and last added line, not hunk header range`() {
        val rawDiff = """
            @@ -12,7 +12,8 @@
             context line 12
             context line 13
             context line 14
            +added line 15
             context line 16
             context line 17
             context line 18
        """.trimIndent()
        val step = stepWithDiff("hunk:foo.kt:12", "foo.kt", newStart = 12, newLines = 8, rawDiff = rawDiff)
        val items = buildStepListItems(listOf(step), listOf(file("foo.kt")))

        val row = items.filterIsInstance<StepListItem.StepRow>().single()
        assertTrue(row.label.contains("15"), "label should mention line 15: ${row.label}")
        assertFalse(row.label.contains("12–"), "label should not show header start: ${row.label}")
    }

    @Test
    fun `single-line addition uses single line number, not range`() {
        val rawDiff = """
            @@ -10,3 +10,4 @@
             context
            +new
             context
             context
        """.trimIndent()
        val step = stepWithDiff("hunk:foo.kt:10", "foo.kt", newStart = 10, newLines = 4, rawDiff = rawDiff)
        val items = buildStepListItems(listOf(step), listOf(file("foo.kt")))

        val row = items.filterIsInstance<StepListItem.StepRow>().single()
        assertTrue(row.label.contains("line 11") || row.label.contains("lines 11"), "label: ${row.label}")
    }

    @Test
    fun `removal only hunk falls back to header range`() {
        val rawDiff = """
            @@ -10,5 +10,4 @@
             context
            -removed
             context
             context
             context
        """.trimIndent()
        val step = stepWithDiff("hunk:foo.kt:10", "foo.kt", newStart = 10, newLines = 4, rawDiff = rawDiff)
        val items = buildStepListItems(listOf(step), listOf(file("foo.kt")))

        val row = items.filterIsInstance<StepListItem.StepRow>().single()
        assertTrue(row.label.contains("10"), "label: ${row.label}")
    }

    private fun stepWithDiff(
        id: String,
        filePath: String,
        newStart: Int,
        newLines: Int,
        rawDiff: String,
    ): Step =
        Step.newBuilder()
            .setId(id)
            .setHunkSpan(
                HunkSpan.newBuilder()
                    .setFilePath(filePath)
                    .setNewStart(newStart)
                    .setNewLines(newLines)
                    .setRawDiff(rawDiff)
                    .build()
            )
            .build()

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
