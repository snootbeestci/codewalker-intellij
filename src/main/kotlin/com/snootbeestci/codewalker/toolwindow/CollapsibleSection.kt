package com.snootbeestci.codewalker.toolwindow

import com.intellij.icons.AllIcons
import com.intellij.ui.components.JBLabel
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Cursor
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.BorderFactory
import javax.swing.JPanel

class CollapsibleSection(title: String, private val content: Component, expanded: Boolean = false) {

    val root: JPanel = JPanel(BorderLayout())

    private val arrowLabel = JBLabel(if (expanded) AllIcons.General.ArrowDown else AllIcons.General.ArrowRight)
    private val titleLabel = JBLabel(title)
    private var isExpanded = expanded

    init {
        val header = JPanel(BorderLayout()).apply {
            border = BorderFactory.createEmptyBorder(4, 8, 4, 8)
            add(arrowLabel, BorderLayout.WEST)
            add(titleLabel, BorderLayout.CENTER)
            cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
            addMouseListener(object : MouseAdapter() {
                override fun mouseClicked(e: MouseEvent) {
                    toggle()
                }
            })
        }

        root.add(header, BorderLayout.NORTH)
        root.add(content, BorderLayout.CENTER)
        content.isVisible = expanded
    }

    private fun toggle() {
        isExpanded = !isExpanded
        content.isVisible = isExpanded
        arrowLabel.icon = if (isExpanded) AllIcons.General.ArrowDown else AllIcons.General.ArrowRight
        root.revalidate()
        root.repaint()
    }

    fun expand() {
        if (!isExpanded) toggle()
    }

    fun collapse() {
        if (isExpanded) toggle()
    }

    fun isExpanded(): Boolean = isExpanded
}
