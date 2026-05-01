package com.snootbeestci.codewalker.toolwindow

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.snootbeestci.codewalker.grpc.CodewalkerClient
import com.snootbeestci.codewalker.grpc.ConnectionStateListener
import com.snootbeestci.codewalker.session.ReviewSessionController
import java.awt.CardLayout
import javax.swing.JPanel

class CodewalkerPanel(val project: Project) : Disposable {

    val root: JPanel = JPanel(CardLayout())

    private val disconnectedPanel = DisconnectedPanel()
    internal val idlePanel = IdlePanel(project)
    internal val loadingPanel = LoadingPanel()
    private val sessionPanel = SessionPanel(project, onStatusMessage = ::showError)
    private val controller = ReviewSessionController(this)

    init {
        root.add(disconnectedPanel.root, "DISCONNECTED")
        root.add(idlePanel.root, "IDLE")
        root.add(loadingPanel.root, "LOADING")
        root.add(sessionPanel.root, "SESSION")

        Disposer.register(this, idlePanel)
        Disposer.register(this, sessionPanel)

        project.messageBus.connect(project).subscribe(
            CodewalkerClient.CONNECTION_STATE_TOPIC,
            object : ConnectionStateListener {
                override fun stateChanged(state: CodewalkerClient.ConnectionState) {
                    ApplicationManager.getApplication().invokeLater { showState(state) }
                }
            }
        )

        showState(CodewalkerClient.getInstance().connectionState)
    }

    fun showState(state: CodewalkerClient.ConnectionState) {
        val card = when (state) {
            CodewalkerClient.ConnectionState.DISCONNECTED,
            CodewalkerClient.ConnectionState.INCOMPATIBLE -> {
                disconnectedPanel.update(state)
                "DISCONNECTED"
            }
            CodewalkerClient.ConnectionState.CONNECTED -> {
                idlePanel.refreshPullRequests()
                "IDLE"
            }
        }
        (root.layout as CardLayout).show(root, card)
    }

    fun showLoading() = (root.layout as CardLayout).show(root, "LOADING")
    fun showSession() {
        sessionPanel.bind(controller)
        (root.layout as CardLayout).show(root, "SESSION")
    }

    fun showError(message: String) {
        idlePanel.showError(message)
        (root.layout as CardLayout).show(root, "IDLE")
    }

    override fun dispose() {
        controller.dispose()
        disconnectedPanel.dispose()
    }
}
