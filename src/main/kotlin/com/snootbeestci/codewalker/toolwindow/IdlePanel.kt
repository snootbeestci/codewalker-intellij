package com.snootbeestci.codewalker.toolwindow

import com.intellij.ui.components.JBTextField
import com.snootbeestci.codewalker.settings.CodewalkerSettings
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.Insets
import javax.swing.JButton
import javax.swing.JComboBox
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener

class IdlePanel {

    val root: JPanel = JPanel(GridBagLayout())
    private val urlField = JBTextField()
    private val experienceLevelCombo = JComboBox(arrayOf("Junior", "Mid", "Senior"))
    private val openReviewButton = JButton("Open Review")

    init {
        val settings = CodewalkerSettings.getInstance()
        experienceLevelCombo.selectedItem = displayLabel(settings.state.experienceLevel)

        openReviewButton.isEnabled = false
        urlField.emptyText.text = "Paste a GitHub PR, commit, or comparison URL"
        urlField.document.addDocumentListener(object : DocumentListener {
            override fun insertUpdate(e: DocumentEvent) = updateButton()
            override fun removeUpdate(e: DocumentEvent) = updateButton()
            override fun changedUpdate(e: DocumentEvent) = updateButton()
            private fun updateButton() {
                openReviewButton.isEnabled = urlField.text.isNotEmpty()
            }
        })

        fun gbc(row: Int, fill: Int = GridBagConstraints.NONE, weightx: Double = 0.0) =
            GridBagConstraints().apply {
                gridx = 0; gridy = row
                this.fill = fill
                this.weightx = weightx
                insets = Insets(4, 8, 4, 8)
                anchor = GridBagConstraints.CENTER
            }

        root.add(JLabel("Codewalker"), gbc(0))
        root.add(urlField, gbc(1, GridBagConstraints.HORIZONTAL, 1.0))
        root.add(experienceLevelCombo, gbc(2, GridBagConstraints.HORIZONTAL, 1.0))
        root.add(openReviewButton, gbc(3))

        root.add(JPanel(), GridBagConstraints().apply {
            gridy = 4; weighty = 1.0; fill = GridBagConstraints.VERTICAL
        })
    }

    private fun displayLabel(level: String): String = when (level) {
        "EXPERIENCE_LEVEL_JUNIOR" -> "Junior"
        "EXPERIENCE_LEVEL_SENIOR" -> "Senior"
        else -> "Mid"
    }
}
