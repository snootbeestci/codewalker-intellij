package com.snootbeestci.codewalker.editor

import codewalker.v1.Codewalker.ForgeContext
import codewalker.v1.Codewalker.HunkSpan
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.LogicalPosition
import com.intellij.openapi.editor.ScrollType
import com.intellij.openapi.editor.event.EditorMouseEvent
import com.intellij.openapi.editor.event.EditorMouseEventArea
import com.intellij.openapi.editor.event.EditorMouseListener
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.highlighter.EditorHighlighterFactory
import com.intellij.openapi.editor.markup.HighlighterLayer
import com.intellij.openapi.editor.markup.HighlighterTargetArea
import com.intellij.openapi.editor.markup.RangeHighlighter
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.search.FilenameIndex
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.testFramework.LightVirtualFile
import com.intellij.ui.JBColor
import com.snootbeestci.codewalker.forge.HostNormalizer
import com.snootbeestci.codewalker.forge.TokenStore
import com.snootbeestci.codewalker.grpc.CodewalkerClient
import com.snootbeestci.codewalker.session.ReviewErrorFormatter
import io.grpc.Status
import io.grpc.StatusRuntimeException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.awt.Color
import java.awt.Dimension
import java.awt.Point
import java.awt.event.MouseEvent

class EditorHighlighter(
    private val project: Project,
    private val statusReporter: (String) -> Unit = {},
) : Disposable {

    private val log = Logger.getInstance(EditorHighlighter::class.java)

    private var currentHighlighters: List<RangeHighlighter> = emptyList()
    private var currentEditor: Editor? = null
    private var clickListener: EditorMouseListener? = null
    private var activePopup: JBPopup? = null
    private var currentRawDiff: String = ""

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val contentCache = mutableMapOf<Pair<String, String>, ByteArray>()
    private var boundContext: BoundContext? = null

    fun bind(forgeContext: ForgeContext?) {
        boundContext = forgeContext?.let {
            val host = HostNormalizer.fromUrl(it.url)
            if (host.isEmpty() || it.owner.isEmpty() || it.repo.isEmpty() || it.headRef.isEmpty()) {
                null
            } else {
                BoundContext(host = host, owner = it.owner, repo = it.repo, headRef = it.headRef)
            }
        }
        contentCache.clear()
    }

    fun highlightHunk(span: HunkSpan) {
        val ctx = boundContext
        if (ctx == null) {
            log.debug("Codewalker: highlightHunk called before forge context bound")
            return
        }

        scope.launch {
            val headContent = try {
                fetchHeadContent(ctx, span.filePath)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                handleFetchFailure(span, e)
                return@launch
            }

            withContext(Dispatchers.Main) {
                applyHighlightToBestEditor(span, ctx.headRef, headContent)
            }
        }
    }

    private suspend fun fetchHeadContent(ctx: BoundContext, path: String): ByteArray {
        val key = path to ctx.headRef
        contentCache[key]?.let { return it }
        val token = TokenStore.getInstance().get(ctx.host) ?: ""
        val content = CodewalkerClient.getInstance().fetchFileAtRef(
            host = ctx.host,
            owner = ctx.owner,
            repo = ctx.repo,
            path = path,
            ref = ctx.headRef,
            forgeToken = token,
        )
        contentCache[key] = content
        return content
    }

    private suspend fun handleFetchFailure(span: HunkSpan, e: Exception) {
        val notFound = e is StatusRuntimeException && e.status.code == Status.Code.NOT_FOUND
        if (notFound) {
            withContext(Dispatchers.Main) {
                clearHighlight()
                statusReporter("${span.filePath} not found at PR head ref")
            }
            return
        }
        val formatted = ReviewErrorFormatter.format(e)
        val baseMsg = if (formatted.isAuthFailure) {
            formatted.message
        } else {
            "Couldn't fetch ${span.filePath} at PR head — showing working-tree copy, " +
                "which may not match the diff."
        }
        withContext(Dispatchers.Main) {
            statusReporter(baseMsg)
            applyHighlightWorkingTreeFallback(span)
        }
    }

    private fun applyHighlightWorkingTreeFallback(span: HunkSpan) {
        clearHighlight()
        val workingFile = findWorkingTreeFile(span.filePath)
        if (workingFile == null) {
            log.debug("Codewalker: no working-tree fallback available for ${span.filePath}")
            return
        }
        val editor = openOrFocus(workingFile) ?: return
        currentEditor = editor
        applyHighlightRanges(editor, span)
    }

    private fun applyHighlightToBestEditor(span: HunkSpan, headRef: String, headContent: ByteArray) {
        clearHighlight()
        val workingFile = findWorkingTreeFile(span.filePath)
        val workingBytes = workingFile?.let { file ->
            ReadAction.compute<ByteArray, RuntimeException> { file.contentsToByteArray() }
        }
        val editor = when (val choice = chooseEditor(workingFile, workingBytes, headContent, headRef)) {
            is EditorChoice.WorkingTree -> openOrFocus(choice.file)
            is EditorChoice.LightVirtual -> openHeadRefContent(span.filePath, choice.ref, choice.content)
        }
        if (editor == null) {
            log.debug("Codewalker: could not open an editor for ${span.filePath}")
            return
        }
        currentEditor = editor
        applyHighlightRanges(editor, span)
    }

    private fun applyHighlightRanges(editor: Editor, span: HunkSpan) {
        val document = editor.document
        val ranges = computeAddedLineRanges(span.rawDiff, span.newStart)
        currentRawDiff = span.rawDiff

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

        val firstHighlightLine = ranges.firstOrNull()?.first ?: span.newStart
        val scrollLine = (firstHighlightLine - 1)
            .coerceAtLeast(0)
            .coerceAtMost(document.lineCount - 1)

        editor.scrollingModel.scrollTo(
            LogicalPosition(scrollLine, 0),
            ScrollType.CENTER
        )

        attachClickListener(editor)
    }

    private fun attachClickListener(editor: Editor) {
        val listener = object : EditorMouseListener {
            override fun mouseClicked(e: EditorMouseEvent) {
                val mouseEvent = e.mouseEvent
                if (mouseEvent.button != MouseEvent.BUTTON1) return
                if (mouseEvent.modifiersEx and (
                    MouseEvent.SHIFT_DOWN_MASK or
                        MouseEvent.CTRL_DOWN_MASK or
                        MouseEvent.ALT_DOWN_MASK or
                        MouseEvent.META_DOWN_MASK
                    ) != 0) return
                if (e.area != EditorMouseEventArea.EDITING_AREA) return

                val clickedLine = editor.xyToLogicalPosition(mouseEvent.point).line
                if (!isLineHighlighted(clickedLine)) {
                    activePopup?.cancel()
                    activePopup = null
                    return
                }

                if (activePopup?.isVisible == true) {
                    activePopup?.cancel()
                    activePopup = null
                    return
                }

                showDiffPopup(editor, mouseEvent.locationOnScreen)
            }
        }
        editor.addEditorMouseListener(listener)
        clickListener = listener
    }

    private fun isLineHighlighted(line: Int): Boolean {
        val editor = currentEditor ?: return false
        return currentHighlighters.any { highlighter ->
            val startLine = editor.document.getLineNumber(highlighter.startOffset)
            val endLine = editor.document.getLineNumber(highlighter.endOffset)
            line in startLine..endLine
        }
    }

    private fun showDiffPopup(editor: Editor, screenLocation: Point) {
        if (currentRawDiff.isEmpty()) return

        val editorFactory = EditorFactory.getInstance()
        val document = editorFactory.createDocument(currentRawDiff)
        val viewer = editorFactory.createViewer(document, project) as EditorEx

        val diffFileType = FileTypeManager.getInstance().findFileTypeByName("Diff")
        if (diffFileType != null) {
            viewer.highlighter = EditorHighlighterFactory.getInstance()
                .createEditorHighlighter(project, diffFileType)
        }

        viewer.settings.apply {
            isLineNumbersShown = true
            isLineMarkerAreaShown = false
            isFoldingOutlineShown = false
            isRightMarginShown = false
            additionalLinesCount = 0
            additionalColumnsCount = 0
            isCaretRowShown = false
        }
        viewer.isViewer = true
        viewer.component.preferredSize = Dimension(700, 350)

        val popup = JBPopupFactory.getInstance()
            .createComponentPopupBuilder(viewer.component, viewer.contentComponent)
            .setRequestFocus(false)
            .setFocusable(false)
            .setMovable(true)
            .setResizable(true)
            .setCancelOnClickOutside(true)
            .setCancelOnOtherWindowOpen(true)
            .createPopup()

        Disposer.register(popup) {
            editorFactory.releaseEditor(viewer)
        }

        popup.showInScreenCoordinates(editor.component, screenLocation)
        activePopup = popup
    }

    fun clearHighlight() {
        activePopup?.cancel()
        activePopup = null
        clickListener?.let { l -> currentEditor?.removeEditorMouseListener(l) }
        clickListener = null
        currentHighlighters.forEach { it.dispose() }
        currentHighlighters = emptyList()
        currentEditor = null
        currentRawDiff = ""
    }

    private fun findWorkingTreeFile(filePath: String): VirtualFile? {
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

    private fun openHeadRefContent(path: String, ref: String, content: ByteArray): Editor? {
        val fileName = path.substringAfterLast('/')
        val displayName = "$fileName @ ${ref.take(7)}"
        val virtualFile = LightVirtualFile(
            displayName,
            FileTypeManager.getInstance().getFileTypeByFileName(fileName),
            String(content, Charsets.UTF_8),
        ).apply {
            isWritable = false
        }
        val descriptor = OpenFileDescriptor(project, virtualFile)
        return FileEditorManager.getInstance(project).openTextEditor(descriptor, true)
    }

    override fun dispose() {
        scope.cancel()
        contentCache.clear()
        clearHighlight()
    }

    internal data class BoundContext(
        val host: String,
        val owner: String,
        val repo: String,
        val headRef: String,
    )

    internal sealed class EditorChoice {
        data class WorkingTree(val file: VirtualFile) : EditorChoice()
        data class LightVirtual(val content: ByteArray, val ref: String) : EditorChoice()
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

        internal fun chooseEditor(
            workingTree: VirtualFile?,
            workingTreeBytes: ByteArray?,
            headContent: ByteArray,
            ref: String,
        ): EditorChoice = when {
            workingTree == null || workingTreeBytes == null ->
                EditorChoice.LightVirtual(headContent, ref)
            workingTreeBytes.contentEquals(headContent) ->
                EditorChoice.WorkingTree(workingTree)
            else ->
                EditorChoice.LightVirtual(headContent, ref)
        }
    }
}
