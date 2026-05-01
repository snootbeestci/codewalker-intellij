package com.snootbeestci.codewalker.session

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Dimension
import javax.swing.JComponent
import javax.swing.JPanel

/**
 * Modal prompt shown when the working tree is dirty at the start of a
 * review session. The user picks between stashing their changes (OK) or
 * cancelling the session (Cancel). [showAndGet] returns true on stash.
 */
class DirtyTreeDialog(project: Project, private val branchName: String) : DialogWrapper(project) {

    init {
        title = "Working tree has uncommitted changes"
        setOKButtonText("Stash and continue")
        setCancelButtonText("Cancel")
        init()
    }

    override fun createCenterPanel(): JComponent {
        val panel = JPanel(BorderLayout())
        panel.border = JBUI.Borders.empty(8)
        val label = JBLabel(
            "<html>Codewalker will check out the PR branch <b>$branchName</b> so the editor " +
                "shows the exact code being reviewed.<br>" +
                "Your current changes need to be set aside first.</html>"
        )
        panel.add(label, BorderLayout.CENTER)
        panel.preferredSize = Dimension(460, 90)
        return panel
    }
}
