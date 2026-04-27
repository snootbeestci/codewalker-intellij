package com.snootbeestci.codewalker.toolwindow

import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.Insets
import javax.swing.JButton
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JProgressBar

class LoadingPanel {

    val root: JPanel = JPanel(GridBagLayout())
    val progressLabel = JLabel("Loading…")
    val progressBar = JProgressBar(0, 100).apply { isIndeterminate = false }
    val cancelButton = JButton("Cancel")

    init {
        fun gbc(row: Int, fill: Int = GridBagConstraints.NONE, weightx: Double = 0.0) =
            GridBagConstraints().apply {
                gridx = 0; gridy = row
                this.fill = fill
                this.weightx = weightx
                insets = Insets(4, 8, 4, 8)
                anchor = GridBagConstraints.CENTER
            }

        root.add(progressBar, gbc(0, GridBagConstraints.HORIZONTAL, 1.0))
        root.add(progressLabel, gbc(1))
        root.add(cancelButton, gbc(2))

        root.add(JPanel(), GridBagConstraints().apply {
            gridy = 3; weighty = 1.0; fill = GridBagConstraints.VERTICAL
        })
    }

    fun updateProgress(message: String, percent: Int) {
        progressLabel.text = message
        progressBar.value = percent
    }
}
