package com.snootbeestci.codewalker.toolwindow

import codewalker.v1.Codewalker.EdgeLabel
import codewalker.v1.Codewalker.ReviewFile
import codewalker.v1.Codewalker.Step
import codewalker.v1.Codewalker.StepComplete
import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.ui.JBSplitter
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBUI
import com.snootbeestci.codewalker.editor.EditorHighlighter
import com.snootbeestci.codewalker.session.NavigationController
import com.snootbeestci.codewalker.session.ReviewSessionController
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Dimension
import java.awt.Font
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.Insets
import javax.swing.BorderFactory
import javax.swing.DefaultListModel
import javax.swing.JButton
import javax.swing.JLabel
import javax.swing.JList
import javax.swing.JPanel
import javax.swing.JTextPane
import javax.swing.ListSelectionModel
import javax.swing.SwingConstants

internal sealed class StepListItem {
    data class Subtitle(val text: String) : StepListItem()
    data class Header(val filePath: String) : StepListItem()
    data class StepRow(val stepId: String, val label: String) : StepListItem()
}

internal fun orderStepsByFile(
    steps: List<Step>,
    files: List<ReviewFile>,
): List<Pair<String, List<Step>>> {
    val stepsByFile = steps.groupBy { it.hunkSpan.filePath }
    return files
        .map { file ->
            file.filePath to stepsByFile[file.filePath].orEmpty()
                .sortedBy { it.hunkSpan.newStart }
        }
        .filter { it.second.isNotEmpty() }
}

internal fun displayOrderedSteps(steps: List<Step>, files: List<ReviewFile>): List<Step> =
    orderStepsByFile(steps, files).flatMap { it.second }

internal fun orphanedStepPaths(steps: List<Step>, files: List<ReviewFile>): Set<String> {
    val filePaths = files.map { it.filePath }.toSet()
    return steps.map { it.hunkSpan.filePath }.toSet() - filePaths
}

internal fun buildStepListItems(
    steps: List<Step>,
    files: List<ReviewFile>,
): List<StepListItem> {
    val grouped = orderStepsByFile(steps, files)
    if (grouped.isEmpty()) return emptyList()

    val items = mutableListOf<StepListItem>()
    val prefix = longestCommonDirectoryPrefix(grouped.map { it.first })
    if (prefix.isNotEmpty()) {
        items.add(StepListItem.Subtitle(prefix))
    }
    for ((path, fileSteps) in grouped) {
        val displayPath = if (prefix.isNotEmpty() && path.startsWith(prefix)) {
            path.removePrefix(prefix)
        } else {
            path
        }
        items.add(StepListItem.Header(displayPath))
        fileSteps.forEachIndexed { index, step ->
            val span = step.hunkSpan
            val end = span.newStart + maxOf(span.newLines, 1) - 1
            val rangeLabel = "${span.newStart}–$end"
            val label = step.label.ifEmpty { "Hunk ${index + 1} (lines $rangeLabel)" }
            items.add(StepListItem.StepRow(step.id, label))
        }
    }
    return items
}

internal fun longestCommonDirectoryPrefix(paths: List<String>): String {
    if (paths.isEmpty()) return ""
    if (paths.size == 1) {
        return paths[0].substringBeforeLast('/', "").let { if (it.isEmpty()) "" else "$it/" }
    }
    val split = paths.map { it.split('/') }
    val minLen = split.minOf { it.size } - 1
    var common = 0
    outer@ for (i in 0 until minLen) {
        val first = split[0][i]
        for (other in split.drop(1)) {
            if (other[i] != first) break@outer
        }
        common++
    }
    if (common == 0) return ""
    return split[0].take(common).joinToString("/", postfix = "/")
}

class SessionPanel(
    private val project: Project,
    private val onStatusMessage: (String) -> Unit = {},
) : Disposable {

    val root: JPanel = JPanel(GridBagLayout())

    private val log = Logger.getInstance(SessionPanel::class.java)
    private val highlighter = EditorHighlighter(project, onStatusMessage).also {
        Disposer.register(this, it)
    }

    private val titleLabel = JLabel("Codewalker")
    private val languageLabel = JLabel(" ")
    private val levelLabel = JLabel(" ")
    private val breadcrumbLabel = JLabel(" ")
    private val stepListModel = DefaultListModel<StepListItem>()
    private val stepList = JList(stepListModel).apply {
        selectionMode = ListSelectionModel.SINGLE_SELECTION
        cellRenderer = StepListRenderer()
    }
    private val narrationPane = JTextPane().apply {
        isEditable = false
    }
    private val summaryTable = SummaryTable()
    private val narrationScroll = JBScrollPane(narrationPane).apply {
        preferredSize = Dimension(360, 180)
        minimumSize = Dimension(200, 120)
    }
    private val narrationCollapsible = CollapsibleSection(
        title = "Detailed narration",
        content = narrationScroll,
        expanded = false,
    )
    private val backButton = JButton("← Back").apply { isEnabled = false }
    private val forwardButton = JButton("Forward →").apply { isEnabled = false }

    private var controller: ReviewSessionController? = null
    private var navigationController: NavigationController? = null

    init {
        buildLayout()
        wireListeners()
    }

    private fun buildLayout() {
        val header = JPanel(GridBagLayout()).apply {
            border = BorderFactory.createEmptyBorder(6, 8, 6, 8)
            titleLabel.font = titleLabel.font.deriveFont(Font.BOLD)
            add(titleLabel, GridBagConstraints().apply {
                gridx = 0; gridy = 0; anchor = GridBagConstraints.WEST
                insets = Insets(0, 0, 0, 8)
            })
            add(languageLabel, GridBagConstraints().apply {
                gridx = 1; gridy = 0; anchor = GridBagConstraints.WEST
                insets = Insets(0, 0, 0, 8)
            })
            add(JPanel().apply { isOpaque = false }, GridBagConstraints().apply {
                gridx = 2; gridy = 0; weightx = 1.0; fill = GridBagConstraints.HORIZONTAL
            })
            add(levelLabel, GridBagConstraints().apply {
                gridx = 3; gridy = 0; anchor = GridBagConstraints.EAST
            })
        }

        breadcrumbLabel.border = BorderFactory.createEmptyBorder(4, 8, 4, 8)

        val stepListScroll = JBScrollPane(stepList).apply {
            minimumSize = Dimension(120, 80)
        }

        val navBar = JPanel(GridBagLayout()).apply {
            border = BorderFactory.createEmptyBorder(4, 8, 6, 8)
            add(backButton, GridBagConstraints().apply {
                gridx = 0; gridy = 0; anchor = GridBagConstraints.WEST
                insets = Insets(0, 0, 0, 8)
            })
            add(JPanel().apply { isOpaque = false }, GridBagConstraints().apply {
                gridx = 1; gridy = 0; weightx = 1.0; fill = GridBagConstraints.HORIZONTAL
            })
            add(forwardButton, GridBagConstraints().apply {
                gridx = 2; gridy = 0; anchor = GridBagConstraints.EAST
            })
        }

        val summaryWrapper = JPanel(BorderLayout()).apply {
            border = JBUI.Borders.empty(8, 0, 0, 0)
            add(summaryTable.root, BorderLayout.NORTH)
            add(narrationCollapsible.root, BorderLayout.CENTER)
        }

        val bodySplitter = JBSplitter(false, 0.3f).apply {
            firstComponent = stepListScroll
            secondComponent = summaryWrapper
            dividerWidth = 3
        }

        fun row(component: Component, gridy: Int, weighty: Double, fill: Int) {
            root.add(component, GridBagConstraints().apply {
                gridx = 0; this.gridy = gridy
                weightx = 1.0; this.weighty = weighty
                this.fill = fill
            })
        }

        row(header, 0, 0.0, GridBagConstraints.HORIZONTAL)
        row(breadcrumbLabel, 1, 0.0, GridBagConstraints.HORIZONTAL)
        row(bodySplitter, 2, 1.0, GridBagConstraints.BOTH)
        row(navBar, 3, 0.0, GridBagConstraints.HORIZONTAL)
    }

    private fun wireListeners() {
        backButton.addActionListener { navigationController?.navigateBack() }
        forwardButton.addActionListener { navigationController?.navigateForward() }
        stepList.addListSelectionListener { event ->
            if (event.valueIsAdjusting) return@addListSelectionListener
            val item = stepList.selectedValue ?: return@addListSelectionListener
            if (item is StepListItem.Header || item is StepListItem.Subtitle) {
                stepList.clearSelection()
                return@addListSelectionListener
            }
            val row = item as StepListItem.StepRow
            if (row.stepId == controller?.currentStepId) return@addListSelectionListener
            navigationController?.navigateTo(row.stepId)
        }
    }

    fun bind(controller: ReviewSessionController) {
        this.controller = controller
        this.navigationController?.dispose()
        this.navigationController = NavigationController(controller, this)
        highlighter.bind(controller.forgeContext)
        updateLanguageBadge(controller)
        updateLevelPips(controller.effectiveLevel)
        populateStepList(controller.steps, controller.forgeContext?.filesList.orEmpty())
        updateBreadcrumb(listOf(controller.forgeContext?.prTitle?.takeIf { it.isNotEmpty() } ?: "Review"))
        clearNarration()
        backButton.isEnabled = false
        forwardButton.isEnabled = false
        root.revalidate()
        root.repaint()
        controller.currentStepId?.let { navigationController?.navigateTo(it) }
    }

    override fun dispose() {
        navigationController?.dispose()
        navigationController = null
    }

    fun clearNarration() {
        narrationPane.text = ""
        summaryTable.clear()
    }

    fun appendNarrationToken(text: String) {
        val doc = narrationPane.document
        doc.insertString(doc.length, text, null)
        narrationPane.caretPosition = doc.length
    }

    fun onStepComplete(complete: StepComplete) {
        controller?.currentStepId = complete.stepId
        updateBreadcrumb(complete.breadcrumbList)
        updateStepHighlight(complete.stepId)
        summaryTable.update(if (complete.hasSummary()) complete.summary else null)
        val hasForward = complete.availableEdgesList.any {
            it.label == EdgeLabel.EDGE_LABEL_NEXT && it.navigable
        }
        forwardButton.isEnabled = hasForward
        backButton.isEnabled = complete.breadcrumbList.size > 1
    }

    fun applyHighlightFor(step: Step) {
        if (step.hasHunkSpan()) {
            highlighter.highlightHunk(step.hunkSpan)
        }
    }

    private fun updateBreadcrumb(crumbs: List<String>) {
        breadcrumbLabel.text = if (crumbs.isEmpty()) " " else crumbs.joinToString(" › ")
    }

    private fun updateStepHighlight(stepId: String) {
        val idx = (0 until stepListModel.size).firstOrNull {
            val item = stepListModel.getElementAt(it)
            item is StepListItem.StepRow && item.stepId == stepId
        }
        if (idx != null) {
            stepList.selectedIndex = idx
            stepList.ensureIndexIsVisible(idx)
        } else {
            stepList.clearSelection()
        }
    }

    private fun populateStepList(steps: List<Step>, files: List<ReviewFile>) {
        stepListModel.clear()
        val orphaned = orphanedStepPaths(steps, files)
        if (orphaned.isNotEmpty()) {
            log.warn(
                "Codewalker: ${orphaned.size} step(s) reference files not in forge_context: $orphaned"
            )
        }
        for (item in buildStepListItems(steps, files)) {
            stepListModel.addElement(item)
        }
    }

    private fun updateLanguageBadge(controller: ReviewSessionController) {
        val files = controller.forgeContext?.filesList.orEmpty()
        val language = files.firstOrNull { it.language.isNotEmpty() }?.language ?: ""
        languageLabel.text = if (language.isEmpty()) " " else "[$language]"
    }

    private fun updateLevelPips(level: Int) {
        val capped = level.coerceIn(0, MAX_LEVEL)
        levelLabel.text = "●".repeat(capped) + "○".repeat(MAX_LEVEL - capped)
    }

    private class StepListRenderer : javax.swing.ListCellRenderer<StepListItem> {
        private val delegate = javax.swing.DefaultListCellRenderer()

        override fun getListCellRendererComponent(
            list: JList<out StepListItem>,
            value: StepListItem,
            index: Int,
            isSelected: Boolean,
            cellHasFocus: Boolean
        ): Component {
            val text = when (value) {
                is StepListItem.Subtitle -> value.text
                is StepListItem.Header -> value.filePath
                is StepListItem.StepRow -> "    " + value.label
            }
            val showSelected = isSelected && value is StepListItem.StepRow
            val component = delegate.getListCellRendererComponent(
                list, text, index, showSelected, cellHasFocus
            ) as JLabel
            when (value) {
                is StepListItem.Subtitle -> {
                    val baseSize = component.font.size2D
                    component.font = component.font.deriveFont(Font.ITALIC, baseSize - 1f)
                    component.horizontalAlignment = SwingConstants.LEFT
                    component.background = list.background
                    component.foreground = list.foreground
                }
                is StepListItem.Header -> {
                    component.font = component.font.deriveFont(Font.BOLD)
                    component.horizontalAlignment = SwingConstants.LEFT
                    component.background = list.background
                    component.foreground = list.foreground
                }
                is StepListItem.StepRow -> {
                    component.font = component.font.deriveFont(Font.PLAIN)
                }
            }
            return component
        }
    }

    companion object {
        private const val MAX_LEVEL = 10
    }
}
