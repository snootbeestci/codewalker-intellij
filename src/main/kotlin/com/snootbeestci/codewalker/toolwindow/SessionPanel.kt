package com.snootbeestci.codewalker.toolwindow

import codewalker.v1.Codewalker.EdgeLabel
import codewalker.v1.Codewalker.Step
import codewalker.v1.Codewalker.StepComplete
import com.intellij.openapi.project.Project
import com.intellij.ui.components.JBScrollPane
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

class SessionPanel(private val project: Project) {

    val root: JPanel = JPanel(GridBagLayout())

    private val highlighter = EditorHighlighter(project)

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
            preferredSize = Dimension(220, 200)
            minimumSize = Dimension(160, 120)
        }

        val narrationScroll = JBScrollPane(narrationPane).apply {
            preferredSize = Dimension(360, 200)
            minimumSize = Dimension(200, 120)
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

        val body = JPanel(BorderLayout()).apply {
            add(stepListScroll, BorderLayout.WEST)
            add(narrationScroll, BorderLayout.CENTER)
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
        row(body, 2, 1.0, GridBagConstraints.BOTH)
        row(navBar, 3, 0.0, GridBagConstraints.HORIZONTAL)
    }

    private fun wireListeners() {
        backButton.addActionListener { navigationController?.navigateBack() }
        forwardButton.addActionListener { navigationController?.navigateForward() }
        stepList.addListSelectionListener { event ->
            if (event.valueIsAdjusting) return@addListSelectionListener
            val item = stepList.selectedValue ?: return@addListSelectionListener
            if (item is StepListItem.Header) {
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
        updateLanguageBadge(controller)
        updateLevelPips(controller.effectiveLevel)
        populateStepList(controller.steps)
        updateBreadcrumb(listOf(controller.forgeContext?.prTitle?.takeIf { it.isNotEmpty() } ?: "Review"))
        clearNarration()
        backButton.isEnabled = false
        forwardButton.isEnabled = false
        root.revalidate()
        root.repaint()
        controller.currentStepId?.let { navigationController?.navigateTo(it) }
    }

    fun dispose() {
        navigationController?.dispose()
        navigationController = null
        highlighter.dispose()
    }

    fun clearNarration() {
        narrationPane.text = ""
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
        val hasForward = complete.availableEdgesList.any {
            it.label == EdgeLabel.EDGE_LABEL_NEXT && it.navigable
        }
        forwardButton.isEnabled = hasForward
        backButton.isEnabled = complete.breadcrumbList.size > 1

        val step = controller?.steps?.firstOrNull { it.id == complete.stepId }
        if (step != null && step.hasHunkSpan()) {
            val span = step.hunkSpan
            highlighter.highlightHunk(span.filePath, span.newStart, span.newLines)
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

    private fun populateStepList(steps: List<Step>) {
        stepListModel.clear()
        val grouped = LinkedHashMap<String, MutableList<Step>>()
        for (step in steps) {
            val path = step.hunkSpan.filePath.ifEmpty { "(unknown)" }
            grouped.getOrPut(path) { mutableListOf() }.add(step)
        }
        for ((path, fileSteps) in grouped) {
            stepListModel.addElement(StepListItem.Header(path))
            fileSteps.forEachIndexed { index, step ->
                val span = step.hunkSpan
                val end = span.newStart + maxOf(span.newLines, 1) - 1
                val rangeLabel = "${span.newStart}–$end"
                val label = step.label.ifEmpty { "Hunk ${index + 1} (lines $rangeLabel)" }
                stepListModel.addElement(StepListItem.StepRow(step.id, label))
            }
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

    private sealed class StepListItem {
        data class Header(val filePath: String) : StepListItem()
        data class StepRow(val stepId: String, val label: String) : StepListItem()
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
                is StepListItem.Header -> value.filePath
                is StepListItem.StepRow -> "    " + value.label
            }
            val showSelected = isSelected && value is StepListItem.StepRow
            val component = delegate.getListCellRendererComponent(
                list, text, index, showSelected, cellHasFocus
            ) as JLabel
            if (value is StepListItem.Header) {
                component.font = component.font.deriveFont(Font.BOLD)
                component.horizontalAlignment = SwingConstants.LEFT
                component.background = list.background
                component.foreground = list.foreground
            } else {
                component.font = component.font.deriveFont(Font.PLAIN)
            }
            return component
        }
    }

    companion object {
        private const val MAX_LEVEL = 10
    }
}
