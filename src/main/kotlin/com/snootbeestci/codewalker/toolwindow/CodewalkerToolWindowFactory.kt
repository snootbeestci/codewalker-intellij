package com.snootbeestci.codewalker.toolwindow

import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory

class CodewalkerToolWindowFactory : ToolWindowFactory {
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val panel = CodewalkerPanel(project)
        val content = ContentFactory.getInstance()
            .createContent(panel.root, "", false)
        toolWindow.contentManager.addContent(content)
    }
}
