package com.snootbeestci.codewalker.settings

import com.intellij.credentialStore.CredentialAttributes
import com.intellij.ide.passwordSafe.PasswordSafe
import com.intellij.ui.ComboBox
import com.intellij.ui.components.JBTextField
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.Insets
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JPasswordField

class CodewalkerSettingsPanel {
    val root: JPanel = JPanel(GridBagLayout())
    private val backendAddressField = JBTextField()
    private val experienceLevelCombo = ComboBox(arrayOf("Junior", "Mid", "Senior"))
    private val githubTokenField = JPasswordField()

    private val credentialAttributes = CredentialAttributes("Codewalker.GitHubToken")

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
        root.add(JLabel("GitHub token:"), labelConstraints(2))
        root.add(githubTokenField, fieldConstraints(2))

        // push everything to the top
        root.add(JPanel(), GridBagConstraints().apply {
            gridy = 3; weighty = 1.0; fill = GridBagConstraints.VERTICAL
        })

        reset()
    }

    fun isModified(): Boolean {
        val settings = CodewalkerSettings.getInstance()
        val savedToken = PasswordSafe.instance.getPassword(credentialAttributes) ?: ""
        return backendAddressField.text != settings.state.backendAddress
            || selectedExperienceLevel() != settings.state.experienceLevel
            || githubTokenField.password.concatToString() != savedToken
    }

    fun apply() {
        val settings = CodewalkerSettings.getInstance()
        settings.state.backendAddress = backendAddressField.text
        settings.state.experienceLevel = selectedExperienceLevel()
        PasswordSafe.instance.setPassword(credentialAttributes, githubTokenField.password.concatToString())
    }

    fun reset() {
        val settings = CodewalkerSettings.getInstance()
        backendAddressField.text = settings.state.backendAddress
        experienceLevelCombo.selectedItem = displayLabel(settings.state.experienceLevel)
        githubTokenField.text = PasswordSafe.instance.getPassword(credentialAttributes) ?: ""
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
