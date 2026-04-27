package com.snootbeestci.codewalker.toolwindow

import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import javax.swing.JLabel
import javax.swing.JPanel

class SessionPanel {

    val root: JPanel = JPanel(GridBagLayout())

    init {
        root.add(JLabel("Session active"), GridBagConstraints())
    }
}
