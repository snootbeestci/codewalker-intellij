package com.snootbeestci.codewalker.editor

import codewalker.v1.Codewalker.HunkSpan
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.LogicalPosition
import com.intellij.openapi.editor.ScrollType
import com.intellij.openapi.editor.markup.HighlighterLayer
import com.intellij.openapi.editor.markup.HighlighterTargetArea
import com.intellij.openapi.editor.markup.RangeHighlighter
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.search.FilenameIndex
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.ui.JBColor
import java.awt.Color

class EditorHighlighter(private val project: Project) {

    private val log = Logger.getInstance(EditorHighlighter::class.java)

    private var currentHighlighters: List<RangeHighlighter> = emptyList()
    private var currentEditor: Editor? = null

    fun highlightHunk(span: HunkSpan) {
        clearHighlight()

        val virtualFile = findFile(span.filePath)
        if (virtualFile == null) {
            log.debug("Codewalker: file not found in project: ${span.filePath} (basePath=${project.basePath})")
            return
        }
        val editor = openOrFocus(virtualFile)
        if (editor == null) {
            log.debug("Codewalker: openTextEditor returned null for ${virtualFile.path}")
            return
        }

        currentEditor = editor

        val document = editor.document
        val ranges = computeAddedLineRanges(span.rawDiff, span.newStart)

        val attributes = TextAttributes().apply {
            backgroundColor = JBColor(
                Color(255, 240, 200),
                Color(80, 70, 30)
            )
        }

        currentHighlighters = ranges.map { range ->
            val startLine = (range.first - 1).coerceAtLeast(0)
            val endLine = (range.last - 1).coerceAtMost(document.lineCount - 1)
            val startOffset = document.getLineStartOffset(startLine)
            val endOffset = document.getLineEndOffset(endLine)
            editor.markupModel.addRangeHighlighter(
                startOffset,
                endOffset,
                HighlighterLayer.SELECTION - 1,
                attributes,
                HighlighterTargetArea.EXACT_RANGE
            )
        }

        val scrollLine = (ranges.firstOrNull()?.first ?: span.newStart) - 1
        editor.scrollingModel.scrollTo(
            LogicalPosition(scrollLine.coerceAtLeast(0), 0),
            ScrollType.CENTER
        )
    }

    fun clearHighlight() {
        currentHighlighters.forEach { it.dispose() }
        currentHighlighters = emptyList()
        currentEditor = null
    }

    private fun findFile(filePath: String): VirtualFile? {
        val basePath = project.basePath
        if (basePath != null) {
            val direct = LocalFileSystem.getInstance().findFileByPath("$basePath/$filePath")
            if (direct != null) return direct
        }
        val fileName = filePath.substringAfterLast('/')
        return ReadAction.compute<VirtualFile?, RuntimeException> {
            FilenameIndex.getVirtualFilesByName(
                fileName,
                GlobalSearchScope.allScope(project)
            ).firstOrNull { it.path.endsWith(filePath) }
        }
    }

    private fun openOrFocus(file: VirtualFile): Editor? {
        val descriptor = OpenFileDescriptor(project, file)
        return FileEditorManager.getInstance(project)
            .openTextEditor(descriptor, true)
    }

    fun dispose() {
        clearHighlight()
    }

    companion object {
        // Walks a unified-diff hunk body and returns line ranges in the new file
        // covering only added (`+`) lines. Context (` `) and removed (`-`) lines
        // are skipped so the highlight tracks just the change.
        internal fun computeAddedLineRanges(rawDiff: String, newStart: Int): List<IntRange> {
            if (rawDiff.isEmpty()) return emptyList()
            val ranges = mutableListOf<IntRange>()
            var currentNewLine = newStart
            var rangeStart: Int? = null

            for (line in rawDiff.lineSequence()) {
                if (line.startsWith("@@") || line.startsWith("+++") || line.startsWith("---")) {
                    continue
                }
                when {
                    line.startsWith("+") -> {
                        if (rangeStart == null) rangeStart = currentNewLine
                        currentNewLine++
                    }
                    line.startsWith("-") -> {
                        // present only in old file; do not advance new-file counter
                    }
                    line.startsWith("\\") -> {
                        // "\ No newline at end of file" marker
                    }
                    else -> {
                        // context line (" " prefix or empty line within hunk body)
                        if (rangeStart != null) {
                            ranges.add(rangeStart..(currentNewLine - 1))
                            rangeStart = null
                        }
                        currentNewLine++
                    }
                }
            }

            if (rangeStart != null) {
                ranges.add(rangeStart..(currentNewLine - 1))
            }
            return ranges
        }
    }
}
