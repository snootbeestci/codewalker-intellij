package com.snootbeestci.codewalker.settings

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.options.Configurable
import com.snootbeestci.codewalker.grpc.CodewalkerClient
import kotlinx.coroutines.launch
import javax.swing.JComponent

class CodewalkerSettingsConfigurable : Configurable {
    private var panel: CodewalkerSettingsPanel? = null

    override fun getDisplayName() = "Codewalker"

    override fun createComponent(): JComponent {
        panel = CodewalkerSettingsPanel()
        return panel!!.root
    }

    override fun isModified() = panel?.isModified() ?: false
    override fun apply() {
        panel?.apply()
        ApplicationManager.getApplication().coroutineScope.launch {
            CodewalkerClient.getInstance().connect(
                CodewalkerSettings.getInstance().state.backendAddress
            )
        }
    }
    override fun reset() { panel?.reset() }
    override fun disposeUIResources() { panel = null }
}
