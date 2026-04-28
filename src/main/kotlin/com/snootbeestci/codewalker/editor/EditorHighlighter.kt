package com.snootbeestci.codewalker.editor

import com.intellij.openapi.application.ReadAction
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
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.search.FilenameIndex
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.ui.JBColor
import java.awt.Color

class EditorHighlighter(private val project: Project) {

    private var currentHighlighters: List<RangeHighlighter> = emptyList()
    private var currentEditor: Editor? = null

    fun highlightHunk(filePath: String, newStart: Int, newLines: Int) {
        clearHighlight()

        val virtualFile = findFile(filePath) ?: return
        val editor = openOrFocus(virtualFile) ?: return

        currentEditor = editor

        val document = editor.document
        val startOffset = document.getLineStartOffset((newStart - 1).coerceAtLeast(0))
        val endLine = (newStart + newLines - 2).coerceAtMost(document.lineCount - 1)
        val endOffset = document.getLineEndOffset(endLine)

        val attributes = TextAttributes().apply {
            backgroundColor = JBColor(
                Color(255, 240, 200),
                Color(80, 70, 30)
            )
        }

        val highlighter = editor.markupModel.addRangeHighlighter(
            startOffset,
            endOffset,
            HighlighterLayer.SELECTION - 1,
            attributes,
            HighlighterTargetArea.EXACT_RANGE
        )

        currentHighlighters = listOf(highlighter)

        editor.scrollingModel.scrollTo(
            LogicalPosition(newStart - 1, 0),
            ScrollType.CENTER
        )
    }

    fun clearHighlight() {
        currentHighlighters.forEach { it.dispose() }
        currentHighlighters = emptyList()
        currentEditor = null
    }

    private fun findFile(filePath: String): VirtualFile? {
        val fileName = filePath.substringAfterLast('/')
        return ReadAction.compute<VirtualFile?, RuntimeException> {
            FilenameIndex.getVirtualFilesByName(
                fileName,
                GlobalSearchScope.projectScope(project)
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
}
