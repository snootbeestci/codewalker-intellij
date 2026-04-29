package com.snootbeestci.codewalker.settings

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.ui.Messages
import com.intellij.ui.ToolbarDecorator
import com.intellij.ui.components.JBTextField
import com.snootbeestci.codewalker.forge.GhCli
import com.snootbeestci.codewalker.forge.HostNormalizer
import com.snootbeestci.codewalker.forge.TokenStore
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.Insets
import javax.swing.JComboBox
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JTable
import javax.swing.ListSelectionModel
import javax.swing.table.DefaultTableModel

class CodewalkerSettingsPanel(
    private val tokenStore: TokenStore = TokenStore.getInstance(),
    private val ghCli: GhCli = GhCli(),
) {
    val root: JPanel = JPanel(GridBagLayout())
    private val backendAddressField = JBTextField()
    private val experienceLevelCombo = JComboBox(arrayOf("Junior", "Mid", "Senior"))

    private val tokensModel = object : DefaultTableModel(arrayOf("Host", "Token"), 0) {
        override fun isCellEditable(row: Int, column: Int) = false
    }
    private val tokensTable = JTable(tokensModel).apply {
        selectionModel.selectionMode = ListSelectionModel.SINGLE_SELECTION
        autoCreateRowSorter = true
    }

    /** Pending edits — applied to PasswordSafe only on apply(). */
    private val pendingTokens = mutableMapOf<String, String?>()

    init {
        val labelInsets = Insets(4, 0, 4, 8)
        val fieldInsets = Insets(4, 0, 4, 0)

        fun labelConstraints(row: Int) = GridBagConstraints().apply {
            gridx = 0; gridy = row
            anchor = GridBagConstraints.LINE_START
            insets = labelInsets
        }
        fun fieldConstraints(row: Int) = GridBagConstraints().apply {
            gridx = 1; gridy = row
            fill = GridBagConstraints.HORIZONTAL
            weightx = 1.0
            insets = fieldInsets
        }

        root.add(JLabel("Backend address:"), labelConstraints(0))
        root.add(backendAddressField, fieldConstraints(0))
        root.add(JLabel("Experience level:"), labelConstraints(1))
        root.add(experienceLevelCombo, fieldConstraints(1))

        val hostsHeader = JLabel("Forge tokens (per host):")
        root.add(hostsHeader, GridBagConstraints().apply {
            gridx = 0; gridy = 2; gridwidth = 2
            anchor = GridBagConstraints.LINE_START
            insets = Insets(12, 0, 4, 0)
        })

        val tablePanel = ToolbarDecorator.createDecorator(tokensTable)
            .setAddAction { onAdd() }
            .setEditAction { onEdit() }
            .setRemoveAction { onRemove() }
            .addExtraAction(object : com.intellij.openapi.actionSystem.AnAction(
                "Import from gh CLI", "Import token from gh auth", null
            ) {
                override fun actionPerformed(e: com.intellij.openapi.actionSystem.AnActionEvent) {
                    onImportFromGh()
                }
                override fun getActionUpdateThread() =
                    com.intellij.openapi.actionSystem.ActionUpdateThread.EDT
            })
            .createPanel()

        root.add(tablePanel, GridBagConstraints().apply {
            gridx = 0; gridy = 3; gridwidth = 2
            fill = GridBagConstraints.BOTH
            weightx = 1.0; weighty = 1.0
            insets = Insets(0, 0, 0, 0)
        })

        reset()
    }

    fun isModified(): Boolean {
        val settings = CodewalkerSettings.getInstance()
        if (backendAddressField.text != settings.state.backendAddress) return true
        if (selectedExperienceLevel() != settings.state.experienceLevel) return true
        return pendingTokens.isNotEmpty()
    }

    fun apply() {
        val settings = CodewalkerSettings.getInstance()
        settings.state.backendAddress = backendAddressField.text
        settings.state.experienceLevel = selectedExperienceLevel()
        for ((host, token) in pendingTokens) {
            if (token.isNullOrEmpty()) {
                tokenStore.remove(host)
            } else {
                tokenStore.set(host, token)
            }
        }
        pendingTokens.clear()
    }

    fun reset() {
        val settings = CodewalkerSettings.getInstance()
        backendAddressField.text = settings.state.backendAddress
        experienceLevelCombo.selectedItem = displayLabel(settings.state.experienceLevel)
        pendingTokens.clear()
        rebuildTableFromStore()
    }

    private fun rebuildTableFromStore() {
        tokensModel.rowCount = 0
        for (host in tokenStore.knownHosts()) {
            val token = tokenStore.get(host) ?: continue
            tokensModel.addRow(arrayOf<Any>(host, maskedToken(token)))
        }
    }

    private fun maskedToken(token: String): String =
        if (token.length <= 8) "•".repeat(token.length) else "${token.take(4)}…${token.takeLast(4)}"

    private fun rowForHost(host: String): Int {
        for (row in 0 until tokensModel.rowCount) {
            if ((tokensModel.getValueAt(row, 0) as String) == host) return row
        }
        return -1
    }

    private fun upsertPending(host: String, token: String) {
        val canonical = HostNormalizer.normalize(host)
        if (canonical.isEmpty()) {
            Messages.showErrorDialog(root, "Host is empty after normalisation.", "Invalid Host")
            return
        }
        pendingTokens[canonical] = token
        val masked = maskedToken(token)
        val existing = rowForHost(canonical)
        if (existing >= 0) {
            tokensModel.setValueAt(masked, existing, 1)
        } else {
            tokensModel.addRow(arrayOf<Any>(canonical, masked))
        }
    }

    private fun onAdd() {
        val (host, token) = promptHostAndToken(initialHost = "", initialToken = "") ?: return
        if (token.isEmpty()) return
        upsertPending(host, token)
    }

    private fun onEdit() {
        val row = tokensTable.selectedRow.takeIf { it >= 0 } ?: return
        val modelRow = tokensTable.convertRowIndexToModel(row)
        val host = tokensModel.getValueAt(modelRow, 0) as String
        val (newHost, newToken) = promptHostAndToken(initialHost = host, initialToken = "") ?: return
        if (newToken.isEmpty()) return
        if (HostNormalizer.normalize(newHost) != host) {
            pendingTokens[host] = null
            tokensModel.removeRow(modelRow)
        }
        upsertPending(newHost, newToken)
    }

    private fun onRemove() {
        val row = tokensTable.selectedRow.takeIf { it >= 0 } ?: return
        val modelRow = tokensTable.convertRowIndexToModel(row)
        val host = tokensModel.getValueAt(modelRow, 0) as String
        pendingTokens[host] = null
        tokensModel.removeRow(modelRow)
    }

    private fun onImportFromGh() {
        val host = Messages.showInputDialog(
            root,
            "Hostname (e.g. github.com):",
            "Import Token from gh CLI",
            null
        )?.trim() ?: return
        if (host.isEmpty()) return

        val canonical = HostNormalizer.normalize(host)
        ApplicationManager.getApplication().executeOnPooledThread {
            val result = ghCli.fetchToken(canonical)
            ApplicationManager.getApplication().invokeLater {
                when (result) {
                    is GhCli.Result.Success -> upsertPending(canonical, result.token)
                    is GhCli.Result.NotInstalled -> Messages.showErrorDialog(root, result.message, "gh CLI Not Found")
                    is GhCli.Result.NoToken -> Messages.showErrorDialog(root, result.message, "No Token Available")
                    is GhCli.Result.Failed -> Messages.showErrorDialog(root, result.message, "gh CLI Failed")
                }
            }
        }
    }

    private fun promptHostAndToken(initialHost: String, initialToken: String): Pair<String, String>? {
        val dialog = HostTokenDialog(initialHost, initialToken)
        if (!dialog.showAndGet()) return null
        return dialog.hostValue() to dialog.tokenValue()
    }

    private fun selectedExperienceLevel(): String = when (experienceLevelCombo.selectedItem) {
        "Junior" -> "EXPERIENCE_LEVEL_JUNIOR"
        "Senior" -> "EXPERIENCE_LEVEL_SENIOR"
        else -> "EXPERIENCE_LEVEL_MID"
    }

    private fun displayLabel(level: String): String = when (level) {
        "EXPERIENCE_LEVEL_JUNIOR" -> "Junior"
        "EXPERIENCE_LEVEL_SENIOR" -> "Senior"
        else -> "Mid"
    }
}
