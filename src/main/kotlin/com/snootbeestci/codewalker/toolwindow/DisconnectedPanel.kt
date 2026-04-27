package com.snootbeestci.codewalker.toolwindow

import com.snootbeestci.codewalker.grpc.CodewalkerClient
import com.snootbeestci.codewalker.settings.CodewalkerSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.Insets
import javax.swing.JButton
import javax.swing.JLabel
import javax.swing.JPanel

class DisconnectedPanel {

    val root: JPanel = JPanel(GridBagLayout())
    private val messageLabel = JLabel()
    private val addressLabel = JLabel()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    init {
        val retryButton = JButton("Retry")
        retryButton.addActionListener {
            scope.launch {
                CodewalkerClient.getInstance().connect(
                    CodewalkerSettings.getInstance().state.backendAddress
                )
            }
        }

        fun gbc(row: Int) = GridBagConstraints().apply {
            gridx = 0; gridy = row
            anchor = GridBagConstraints.CENTER
            insets = Insets(4, 12, 4, 12)
        }

        root.add(messageLabel, gbc(0))
        root.add(addressLabel, gbc(1))
        root.add(retryButton, gbc(2))

        root.add(JPanel(), GridBagConstraints().apply {
            gridy = 3; weighty = 1.0; fill = GridBagConstraints.VERTICAL
        })

        update(CodewalkerClient.ConnectionState.DISCONNECTED)
    }

    fun dispose() {
        scope.cancel()
    }

    fun update(state: CodewalkerClient.ConnectionState) {
        messageLabel.text = when (state) {
            CodewalkerClient.ConnectionState.INCOMPATIBLE ->
                "Backend version incompatible. Please update your Docker image."
            else ->
                "<html>Backend not running. Start it with<br><tt>docker-compose up</tt> in the codewalker directory.</html>"
        }
        addressLabel.text = "Backend: ${CodewalkerSettings.getInstance().state.backendAddress}"
    }
}
