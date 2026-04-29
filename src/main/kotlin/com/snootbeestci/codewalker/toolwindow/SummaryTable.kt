package com.snootbeestci.codewalker.toolwindow

import codewalker.v1.Codewalker.StepSummary
import com.intellij.ui.JBColor
import java.awt.Font
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.Insets
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JTextArea
import javax.swing.UIManager

class SummaryTable {

    val root: JPanel = JPanel(GridBagLayout())

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
        rows.forEachIndexed { index, (label, valueCell) ->
            valueCell.text = EMPTY_VALUE
            val keyLabel = JLabel(label).apply {
                font = font.deriveFont(Font.BOLD)
                foreground = JBColor.GRAY
            }
            root.add(keyLabel, GridBagConstraints().apply {
                gridx = 0; gridy = index
                anchor = GridBagConstraints.NORTHWEST
                insets = Insets(2, 8, 2, 12)
            })
            root.add(valueCell, GridBagConstraints().apply {
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
        rows[0].second.text = summary.breaking.ifEmpty { EMPTY_VALUE }
        rows[1].second.text = summary.risk.ifEmpty { EMPTY_VALUE }
        rows[2].second.text = summary.whatChanged.ifEmpty { EMPTY_VALUE }
        rows[3].second.text = summary.sideEffects.ifEmpty { EMPTY_VALUE }
        rows[4].second.text = summary.tests.ifEmpty { EMPTY_VALUE }
        rows[5].second.text = summary.reviewerFocus.ifEmpty { EMPTY_VALUE }
        rows[6].second.text = summary.suggestion.ifEmpty { EMPTY_VALUE }
        rows[7].second.text = summary.confidence.ifEmpty { EMPTY_VALUE }
    }

    fun clear() {
        rows.forEach { (_, cell) -> cell.text = EMPTY_VALUE }
    }

    fun valueLabelAt(index: Int): JTextArea = rows[index].second

    companion object {
        const val EMPTY_VALUE: String = "—"

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
