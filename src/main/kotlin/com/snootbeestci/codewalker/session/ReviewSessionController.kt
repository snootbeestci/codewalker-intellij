package com.snootbeestci.codewalker.session

import codewalker.v1.Codewalker.ExperienceLevel
import codewalker.v1.Codewalker.ForgeContext
import codewalker.v1.Codewalker.GlossaryTerm
import codewalker.v1.Codewalker.PullRequestSummary
import codewalker.v1.Codewalker.SessionEvent
import codewalker.v1.Codewalker.Step
import codewalker.v1.openReviewSessionRequest
import com.intellij.openapi.diagnostic.Logger
import com.snootbeestci.codewalker.forge.HostNormalizer
import com.snootbeestci.codewalker.forge.TokenStore
import com.snootbeestci.codewalker.git.CodewalkerGitOps
import com.snootbeestci.codewalker.git.GitOperationException
import com.snootbeestci.codewalker.grpc.CodewalkerClient
import com.snootbeestci.codewalker.toolwindow.CodewalkerPanel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean

class ReviewSessionController(private val panel: CodewalkerPanel) {

    var sessionId: String? = null
    var steps: List<Step> = emptyList()
    var currentStepId: String? = null
    var glossary: List<GlossaryTerm> = emptyList()
    var forgeContext: ForgeContext? = null
    var effectiveLevel: Int = 6

    private val log = Logger.getInstance(ReviewSessionController::class.java)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var sessionJob: Job? = null
    private val sessionActive = AtomicBoolean(false)
    private val gitOps: CodewalkerGitOps = CodewalkerGitOps(panel.project)

    init {
        panel.loadingPanel.cancelButton.addActionListener { cancel() }
        panel.idlePanel.setPullRequestClickHandler { pr ->
            openReview(pr, panel.idlePanel.getExperienceLevel())
        }
    }

    private fun openReview(pr: PullRequestSummary, level: String) {
        if (!sessionActive.compareAndSet(false, true)) {
            panel.showError("A Codewalker session is already active. Close it before starting another.")
            return
        }

        panel.idlePanel.clearError()
        panel.showLoading()

        sessionJob = scope.launch {
            warnAboutLeftoverStashes()
            var sessionStarted = false
            try {
                // Working-tree prep happens first. If the user cancels or
                // anything fails, we never open the gRPC stream and never
                // create a server-side session.
                if (pr.headRef.isNotEmpty()) {
                    if (!prepareWorkingTree(pr.headRef)) {
                        return@launch
                    }
                } else {
                    log.info("Codewalker: no head_ref on PR ${pr.number}, skipping working-tree checkout")
                }

                val stub = CodewalkerClient.getInstance().getStub() ?: run {
                    withContext(Dispatchers.Main) { panel.showError("Not connected to backend") }
                    return@launch
                }

                val expLevel = when (level) {
                    "EXPERIENCE_LEVEL_JUNIOR" -> ExperienceLevel.EXPERIENCE_LEVEL_JUNIOR
                    "EXPERIENCE_LEVEL_SENIOR" -> ExperienceLevel.EXPERIENCE_LEVEL_SENIOR
                    else -> ExperienceLevel.EXPERIENCE_LEVEL_MID
                }

                val host = when (val parsed = HostNormalizer.fromUrlResult(pr.url)) {
                    is HostNormalizer.UrlParseResult.Ok -> parsed.host
                    is HostNormalizer.UrlParseResult.Empty -> {
                        withContext(Dispatchers.Main) { panel.showError("Please enter a review URL.") }
                        return@launch
                    }
                    is HostNormalizer.UrlParseResult.ParseFailed -> {
                        withContext(Dispatchers.Main) { panel.showError("Invalid URL: ${parsed.reason}") }
                        return@launch
                    }
                }

                val request = openReviewSessionRequest {
                    this.url = pr.url
                    experienceLevel = expLevel
                    forgeToken = resolveForgeToken(host)
                }

                stub.openReviewSession(request).collect { event ->
                    when (event.eventCase) {
                        SessionEvent.EventCase.PROGRESS -> {
                            val p = event.progress
                            withContext(Dispatchers.Main) {
                                panel.loadingPanel.updateProgress(p.message, p.percent)
                            }
                        }
                        SessionEvent.EventCase.REVIEW_READY -> {
                            val ready = event.reviewReady
                            sessionId = ready.sessionId
                            steps = ready.stepsList
                            currentStepId = ready.entryStepId
                            glossary = ready.glossaryList
                            forgeContext = if (ready.hasForgeContext()) ready.forgeContext else null
                            effectiveLevel = ready.effectiveLevel
                            sessionStarted = true
                            withContext(Dispatchers.Main) { panel.showSession() }
                        }
                        SessionEvent.EventCase.ERROR -> {
                            val msg = event.error.message
                            withContext(Dispatchers.Main) { panel.showError(msg) }
                        }
                        else -> {}
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val formatted = ReviewErrorFormatter.format(e)
                withContext(Dispatchers.Main) { panel.showError(formatted.message) }
            } finally {
                if (!sessionStarted) {
                    sessionActive.set(false)
                }
            }
        }
    }

    /**
     * Returns true if the session should proceed, false if it shouldn't.
     * On false, the panel has already been navigated back to a non-loading
     * state with an explanation.
     */
    private suspend fun prepareWorkingTree(headRef: String): Boolean {
        val repo = gitOps.firstRepository()
        if (repo == null) {
            log.info("Codewalker: no git repository in project, skipping checkout")
            return true
        }

        if (gitOps.isWorkingTreeDirty(repo)) {
            val proceed = withContext(Dispatchers.Main) {
                DirtyTreeDialog(panel.project, headRef).showAndGet()
            }
            if (!proceed) {
                log.info("Codewalker: user cancelled session due to dirty working tree")
                withContext(Dispatchers.Main) {
                    panel.showState(CodewalkerClient.getInstance().connectionState)
                }
                return false
            }
            try {
                val tag = generateStashTag(headRef)
                gitOps.stashChanges(repo, CodewalkerGitOps.stashMessage(tag))
            } catch (e: GitOperationException) {
                withContext(Dispatchers.Main) {
                    panel.showError("Couldn't stash changes: ${e.message}. Session not started.")
                }
                return false
            }
        }

        try {
            gitOps.fetchHeadRef(repo, headRef)
            gitOps.checkoutBranch(repo, headRef)
        } catch (e: GitOperationException) {
            withContext(Dispatchers.Main) {
                panel.showError("Couldn't switch to branch `$headRef`: ${e.message}")
            }
            return false
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                panel.showError("Couldn't switch to branch `$headRef`: ${e.message ?: e::class.simpleName}")
            }
            return false
        }

        return true
    }

    private fun generateStashTag(headRef: String): String {
        val safeName = headRef.replace(Regex("[^A-Za-z0-9_-]"), "_").take(40)
        val timestamp = System.currentTimeMillis()
        return "$safeName-$timestamp"
    }

    private fun warnAboutLeftoverStashes() {
        val repo = gitOps.firstRepository() ?: return
        val leftover = gitOps.findCodewalkerStashes(repo)
        if (leftover.isNotEmpty()) {
            log.warn(
                "Codewalker: found ${leftover.size} stash(es) from prior sessions: " +
                    leftover.joinToString(separator = " | ") +
                    ". You can restore them via Git → Uncommitted Changes → Show Stashes."
            )
        }
    }

    fun dispose() {
        cancel()
        scope.cancel()
    }

    private fun cancel() {
        sessionJob?.cancel()
        sessionJob = null
        sessionActive.set(false)
        panel.showState(CodewalkerClient.getInstance().connectionState)
    }

    private fun resolveForgeToken(host: String): String {
        if (host.isEmpty()) return ""
        return TokenStore.getInstance().get(host) ?: ""
    }
}
