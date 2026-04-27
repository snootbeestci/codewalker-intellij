package com.snootbeestci.codewalker.settings

import com.intellij.openapi.options.Configurable
import javax.swing.JComponent

class CodewalkerSettingsConfigurable : Configurable {
    private var panel: CodewalkerSettingsPanel? = null

    override fun getDisplayName() = "Codewalker"

    override fun createComponent(): JComponent {
        panel = CodewalkerSettingsPanel()
        return panel!!.root
    }

    override fun isModified() = panel?.isModified() ?: false
    override fun apply() { panel?.apply() }
    override fun reset() { panel?.reset() }
    override fun disposeUIResources() { panel = null }
}
