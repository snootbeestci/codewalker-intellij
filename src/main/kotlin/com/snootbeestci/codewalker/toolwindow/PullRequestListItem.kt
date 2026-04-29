package com.snootbeestci.codewalker.toolwindow

import codewalker.v1.Codewalker.PullRequestSummary
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Cursor
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.BorderFactory
import javax.swing.BoxLayout
import javax.swing.JPanel

class PullRequestListItem(
    private val pr: PullRequestSummary,
    private val onClick: (PullRequestSummary) -> Unit,
) {

    val root: JPanel = JPanel(BorderLayout())

    init {
        val content = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            isOpaque = false
        }

        val titleLine = JBLabel("#${pr.number} ${pr.title}")
        val authorLine = JBLabel("@${pr.author}").apply {
            foreground = JBColor.GRAY
        }

        content.add(titleLine)
        content.add(authorLine)

        root.add(content, BorderLayout.CENTER)
        root.border = BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0, 0, 1, 0, JBColor.border()),
            JBUI.Borders.empty(8, 12),
        )
        root.cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)

        val hoverListener = object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                onClick(pr)
            }

            override fun mouseEntered(e: MouseEvent) {
                root.background = JBColor.namedColor("List.hoverBackground", JBColor.LIGHT_GRAY)
                root.isOpaque = true
                root.repaint()
            }

            override fun mouseExited(e: MouseEvent) {
                root.isOpaque = false
                root.repaint()
            }
        }
        root.addMouseListener(hoverListener)
    }
}
