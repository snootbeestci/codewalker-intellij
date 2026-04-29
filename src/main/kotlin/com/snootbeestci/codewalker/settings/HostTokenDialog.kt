package com.snootbeestci.codewalker.settings

import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.ui.components.JBTextField
import com.snootbeestci.codewalker.forge.HostNormalizer
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.Insets
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JPasswordField

class HostTokenDialog(initialHost: String, initialToken: String) :
    DialogWrapper(true) {

    private val hostField = JBTextField(initialHost, 30)
    private val tokenField = JPasswordField(initialToken, 30)

    init {
        title = "Forge Token"
        init()
    }

    override fun createCenterPanel(): JComponent {
        val panel = JPanel(GridBagLayout())
        val labelGbc = { row: Int -> GridBagConstraints().apply {
            gridx = 0; gridy = row
            anchor = GridBagConstraints.LINE_START
            insets = Insets(4, 0, 4, 8)
        } }
        val fieldGbc = { row: Int -> GridBagConstraints().apply {
            gridx = 1; gridy = row
            fill = GridBagConstraints.HORIZONTAL
            weightx = 1.0
            insets = Insets(4, 0, 4, 0)
        } }
        panel.add(JLabel("Host:"), labelGbc(0))
        panel.add(hostField, fieldGbc(0))
        panel.add(JLabel("Token:"), labelGbc(1))
        panel.add(tokenField, fieldGbc(1))
        return panel
    }

    override fun getPreferredFocusedComponent(): JComponent =
        if (hostField.text.isEmpty()) hostField else tokenField

    override fun doValidate(): ValidationInfo? {
        val canonical = HostNormalizer.normalize(hostField.text)
        if (canonical.isEmpty()) {
            return ValidationInfo("Host is required", hostField)
        }
        if (tokenField.password.isEmpty()) {
            return ValidationInfo("Token is required", tokenField)
        }
        return null
    }

    fun hostValue(): String = hostField.text.trim()
    fun tokenValue(): String = String(tokenField.password)
}
