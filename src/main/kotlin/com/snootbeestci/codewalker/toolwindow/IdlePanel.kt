package com.snootbeestci.codewalker.toolwindow

import codewalker.v1.Codewalker.PullRequestSummary
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.Project
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBUI
import com.snootbeestci.codewalker.forge.TokenStore
import com.snootbeestci.codewalker.git.GitHubRemoteResolver
import com.snootbeestci.codewalker.git.ProjectRepoInfo
import com.snootbeestci.codewalker.grpc.CodewalkerClient
import com.snootbeestci.codewalker.session.ReviewErrorFormatter
import com.snootbeestci.codewalker.settings.CodewalkerSettings
import com.snootbeestci.codewalker.settings.CodewalkerSettingsConfigurable
import git4idea.repo.GitRepositoryManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Dimension
import java.awt.FlowLayout
import javax.swing.BoxLayout
import javax.swing.JButton
import javax.swing.JComboBox
import javax.swing.JPanel
import javax.swing.SwingConstants

class IdlePanel(private val project: Project) {

    val root: JPanel = JPanel(BorderLayout())

    private val experienceLevelCombo = JComboBox(arrayOf("Junior", "Mid", "Senior"))
    private val refreshButton = JButton("Refresh")
    private val subtitleLabel = JBLabel("").apply {
        border = JBUI.Borders.empty(8, 12)
    }
    private val statusLabel = JBLabel("", SwingConstants.CENTER).apply {
        border = JBUI.Borders.empty(16, 12)
    }
    private val errorLabel = JBLabel("").apply {
        foreground = JBColor.RED
        border = JBUI.Borders.empty(4, 12)
        isVisible = false
    }
    private val listContainer = JPanel().apply {
        layout = BoxLayout(this, BoxLayout.Y_AXIS)
    }
    private val listScroll = JBScrollPane(listContainer).apply {
        border = null
    }
    private val errorActions = JPanel(FlowLayout(FlowLayout.LEFT, 8, 4)).apply {
        isVisible = false
    }
    private val configureTokensButton = JButton("Configure tokens").apply {
        addActionListener {
            ShowSettingsUtil.getInstance().showSettingsDialog(
                project,
                CodewalkerSettingsConfigurable::class.java,
            )
        }
    }
    private val retryButton = JButton("Retry").apply {
        addActionListener { refreshPullRequests() }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var prClickHandler: ((PullRequestSummary) -> Unit)? = null

    init {
        val settings = CodewalkerSettings.getInstance()
        experienceLevelCombo.selectedItem = displayLabel(settings.state.experienceLevel)

        refreshButton.addActionListener { refreshPullRequests() }

        val header = JPanel(BorderLayout()).apply {
            border = JBUI.Borders.empty(8, 12, 0, 12)
            add(JBLabel("Codewalker"), BorderLayout.WEST)
            add(refreshButton, BorderLayout.EAST)
        }

        val centre = JPanel(BorderLayout()).apply {
            add(subtitleLabel, BorderLayout.NORTH)
            add(listScroll, BorderLayout.CENTER)
            val statusWrapper = JPanel(BorderLayout()).apply {
                add(statusLabel, BorderLayout.NORTH)
                add(errorLabel, BorderLayout.CENTER)
                add(errorActions, BorderLayout.SOUTH)
            }
            add(statusWrapper, BorderLayout.SOUTH)
        }

        val footer = JPanel(FlowLayout(FlowLayout.LEFT, 8, 8)).apply {
            border = JBUI.Borders.customLineTop(JBColor.border())
            add(JBLabel("Experience level:"))
            add(experienceLevelCombo)
        }

        root.add(header, BorderLayout.NORTH)
        root.add(centre, BorderLayout.CENTER)
        root.add(footer, BorderLayout.SOUTH)
    }

    fun getExperienceLevel(): String = selectedExperienceLevel()

    fun setPullRequestClickHandler(handler: (PullRequestSummary) -> Unit) {
        prClickHandler = handler
    }

    fun showError(message: String) {
        errorLabel.text = message
        errorLabel.isVisible = true
    }

    fun clearError() {
        errorLabel.text = ""
        errorLabel.isVisible = false
    }

    fun dispose() {
        scope.cancel()
    }

    fun refreshPullRequests() {
        clearError()
        clearList()
        errorActions.isVisible = false

        if (!hasGitRemote()) {
            subtitleLabel.text = ""
            showStatus("This project has no git remote configured. Configure VCS settings and try again.")
            return
        }

        val info = GitHubRemoteResolver.resolve(project)
        if (info == null) {
            subtitleLabel.text = ""
            showStatus(unsupportedRemoteMessage())
            return
        }

        subtitleLabel.text = "Reviewing PRs for ${info.owner}/${info.repo}"
        showStatus("Loading pull requests…")

        val token = TokenStore.getInstance().get(info.host) ?: ""

        scope.launch {
            try {
                val response = CodewalkerClient.getInstance().listPullRequests(
                    host = info.host,
                    owner = info.owner,
                    repo = info.repo,
                    forgeToken = token,
                )
                withContext(Dispatchers.Main) { renderPRs(info, response.pullRequestsList) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val msg = ReviewErrorFormatter.format(e)
                withContext(Dispatchers.Main) { renderFetchError(msg) }
            }
        }
    }

    private fun hasGitRemote(): Boolean {
        val repos = GitRepositoryManager.getInstance(project).repositories
        return repos.any { it.remotes.isNotEmpty() }
    }

    private fun unsupportedRemoteMessage(): String {
        val repos = GitRepositoryManager.getInstance(project).repositories
        val origin = repos.firstNotNullOfOrNull { repo ->
            repo.remotes.firstOrNull { it.name == "origin" }
        } ?: return "This project has no `origin` remote configured. Configure VCS settings and try again."

        val url = origin.firstUrl
            ?: return "The `origin` remote has no URL. Configure VCS settings and try again."

        // Try parsing as github first to give a more specific error
        val parsed = GitHubRemoteResolver.parseRemoteUrl(url)
        if (parsed == null) {
            // Either non-GitHub host or unparseable
            val host = extractHost(url)
            return if (host != null) {
                "Codewalker currently supports GitHub repositories only. The current project's `origin` remote points to $host."
            } else {
                "Could not parse the project's `origin` remote URL: $url."
            }
        }
        return "Codewalker currently supports GitHub repositories only."
    }

    private fun extractHost(url: String): String? {
        val cleaned = url.trim().removeSuffix("/").let {
            if (it.endsWith(".git")) it.removeSuffix(".git") else it
        }
        Regex("""(?:ssh://)?git@([^:/]+)[:/].*""").matchEntire(cleaned)?.let {
            return it.groupValues[1]
        }
        Regex("""https?://([^/]+)/.*""").matchEntire(cleaned)?.let {
            return it.groupValues[1]
        }
        return null
    }

    private fun renderPRs(info: ProjectRepoInfo, prs: List<PullRequestSummary>) {
        clearList()
        if (prs.isEmpty()) {
            showStatus("No open pull requests for ${info.owner}/${info.repo}.")
            return
        }
        statusLabel.text = ""
        statusLabel.isVisible = false
        for (pr in prs) {
            val item = PullRequestListItem(pr) { selected ->
                prClickHandler?.invoke(selected)
            }
            item.root.alignmentX = Component.LEFT_ALIGNMENT
            item.root.maximumSize = Dimension(Int.MAX_VALUE, item.root.preferredSize.height)
            listContainer.add(item.root)
        }
        listContainer.revalidate()
        listContainer.repaint()
    }

    private fun renderFetchError(message: String) {
        clearList()
        showStatus("")
        showError("Couldn't load pull requests: $message")
        errorActions.removeAll()
        if (message.startsWith("Authorization required:") ||
            message.contains("PERMISSION_DENIED", ignoreCase = true) ||
            message.contains("UNAUTHENTICATED", ignoreCase = true)
        ) {
            errorActions.add(configureTokensButton)
        }
        errorActions.add(retryButton)
        errorActions.isVisible = true
        errorActions.revalidate()
        errorActions.repaint()
    }

    private fun showStatus(text: String) {
        statusLabel.text = text
        statusLabel.isVisible = text.isNotEmpty()
    }

    private fun clearList() {
        listContainer.removeAll()
        listContainer.revalidate()
        listContainer.repaint()
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
