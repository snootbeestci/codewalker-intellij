package com.snootbeestci.codewalker.toolwindow

import codewalker.v1.Codewalker.StepSummary
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Color
import java.awt.FlowLayout
import java.awt.Font
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.Insets
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JTextArea
import javax.swing.UIManager

class SummaryTable {

    val root: JPanel = JPanel(BorderLayout())
    private val rowsPanel: JPanel = JPanel(GridBagLayout())

    private val pillLabel: JBLabel = JBLabel("").apply {
        border = JBUI.Borders.empty(2, 8)
        isOpaque = true
        isVisible = false
        font = font.deriveFont(Font.BOLD, font.size2D - 1f)
    }

    private val rows: List<Pair<String, JTextArea>> = listOf(
        "Breaking" to makeValueCell(),
        "Risk" to makeValueCell(),
        "What changed" to makeValueCell(),
        "Side effects" to makeValueCell(),
        "Tests" to makeValueCell(),
        "Reviewer focus" to makeValueCell(),
        "Suggestion" to makeValueCell(),
        "Confidence" to makeValueCell(),
    )

    init {
        val pillWrapper = JPanel(FlowLayout(FlowLayout.LEFT, 8, 4)).apply {
            add(pillLabel)
        }
        root.add(pillWrapper, BorderLayout.NORTH)
        root.add(rowsPanel, BorderLayout.CENTER)

        rows.forEachIndexed { index, (label, valueCell) ->
            valueCell.text = EMPTY_VALUE
            val keyLabel = JLabel(label).apply {
                font = font.deriveFont(Font.BOLD)
                foreground = JBColor.GRAY
            }
            rowsPanel.add(keyLabel, GridBagConstraints().apply {
                gridx = 0; gridy = index
                anchor = GridBagConstraints.NORTHWEST
                insets = Insets(2, 8, 2, 12)
            })
            rowsPanel.add(valueCell, GridBagConstraints().apply {
                gridx = 1; gridy = index
                weightx = 1.0
                weighty = 0.0
                fill = GridBagConstraints.BOTH
                anchor = GridBagConstraints.NORTHWEST
                insets = Insets(2, 0, 2, 8)
            })
        }
    }

    fun update(summary: StepSummary?) {
        if (summary == null) {
            clear()
            return
        }
        val breakingSeverity = SeverityParser.forBreaking(summary.breaking)
        val riskSeverity = SeverityParser.forRisk(summary.risk)
        val testsSeverity = SeverityParser.forTests(summary.tests)

        setCell(0, summary.breaking, breakingSeverity)
        setCell(1, summary.risk, riskSeverity)
        setCell(2, summary.whatChanged, Severity.NEUTRAL)
        setCell(3, summary.sideEffects, Severity.NEUTRAL)
        setCell(4, summary.tests, testsSeverity)
        setCell(5, summary.reviewerFocus, Severity.NEUTRAL)
        setCell(6, summary.suggestion, Severity.NEUTRAL)
        setCell(7, summary.confidence, Severity.NEUTRAL)

        updatePill(SeverityParser.worst(breakingSeverity, riskSeverity, testsSeverity))
    }

    fun clear() {
        rows.forEach { (_, cell) ->
            cell.text = EMPTY_VALUE
            applyForeground(cell, Severity.NEUTRAL)
        }
        updatePill(Severity.NEUTRAL)
    }

    fun valueLabelAt(index: Int): JTextArea = rows[index].second

    private fun setCell(index: Int, value: String, severity: Severity) {
        val cell = rows[index].second
        cell.text = value.ifEmpty { EMPTY_VALUE }
        applyForeground(cell, severity)
    }

    private fun applyForeground(cell: JTextArea, severity: Severity) {
        val colour = colourFor(severity)
        cell.foreground = colour
        cell.disabledTextColor = colour
    }

    private fun updatePill(severity: Severity) {
        when (severity) {
            Severity.NEUTRAL -> {
                pillLabel.isVisible = false
            }
            Severity.GREEN -> {
                pillLabel.text = "Looks fine"
                pillLabel.foreground = JBColor.WHITE
                pillLabel.background = GREEN_FG
                pillLabel.isVisible = true
            }
            Severity.AMBER -> {
                pillLabel.text = "Worth a look"
                pillLabel.foreground = JBColor.WHITE
                pillLabel.background = AMBER_FG
                pillLabel.isVisible = true
            }
            Severity.RED -> {
                pillLabel.text = "Needs attention"
                pillLabel.foreground = JBColor.WHITE
                pillLabel.background = RED_FG
                pillLabel.isVisible = true
            }
        }
    }

    private fun colourFor(severity: Severity): Color = when (severity) {
        Severity.NEUTRAL -> UIManager.getColor("Label.foreground") ?: JBColor.foreground()
        Severity.GREEN -> GREEN_FG
        Severity.AMBER -> AMBER_FG
        Severity.RED -> RED_FG
    }

    companion object {
        const val EMPTY_VALUE: String = "—"

        private val GREEN_FG = JBColor(Color(0x2E7D32), Color(0x81C784))
        private val AMBER_FG = JBColor(Color(0xE65100), Color(0xFFB74D))
        private val RED_FG = JBColor(Color(0xC62828), Color(0xEF5350))

        private fun makeValueCell(): JTextArea = JTextArea().apply {
            isEditable = false
            lineWrap = true
            wrapStyleWord = true
            isOpaque = false
            border = null
            background = null
            font = UIManager.getFont("Label.font") ?: font
        }
    }
}

internal enum class Severity { NEUTRAL, GREEN, AMBER, RED }

internal object SeverityParser {

    fun forBreaking(value: String): Severity = when (firstWord(value)) {
        "" -> Severity.NEUTRAL
        "no" -> Severity.GREEN
        "yes" -> Severity.RED
        else -> Severity.GREEN
    }

    fun forRisk(value: String): Severity = when (firstWord(value)) {
        "" -> Severity.NEUTRAL
        "low" -> Severity.GREEN
        "medium" -> Severity.AMBER
        "high" -> Severity.RED
        else -> Severity.GREEN
    }

    fun forTests(value: String): Severity = when (firstWord(value)) {
        "" -> Severity.NEUTRAL
        "added", "modified" -> Severity.GREEN
        "missing" -> Severity.AMBER
        else -> Severity.GREEN
    }

    fun worst(breaking: Severity, risk: Severity, tests: Severity): Severity {
        val signals = listOf(breaking, risk, tests).filter { it != Severity.NEUTRAL }
        if (signals.isEmpty()) return Severity.NEUTRAL
        return when {
            signals.any { it == Severity.RED } -> Severity.RED
            signals.any { it == Severity.AMBER } -> Severity.AMBER
            else -> Severity.GREEN
        }
    }

    private fun firstWord(value: String): String {
        if (value.isBlank()) return ""
        if (value.trim() == SummaryTable.EMPTY_VALUE) return ""
        return value.trim()
            .substringBefore(' ')
            .substringBefore('—')
            .substringBefore(',')
            .lowercase()
            .trimEnd('.', ':', ';', '!', '?')
    }
}
