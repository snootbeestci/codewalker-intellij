package com.snootbeestci.codewalker.session

import codewalker.v1.Codewalker.ExperienceLevel
import codewalker.v1.Codewalker.ForgeContext
import codewalker.v1.Codewalker.GlossaryTerm
import codewalker.v1.Codewalker.SessionEvent
import codewalker.v1.Codewalker.Step
import codewalker.v1.openReviewSessionRequest
import com.snootbeestci.codewalker.forge.HostNormalizer
import com.snootbeestci.codewalker.forge.TokenStore
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

class ReviewSessionController(private val panel: CodewalkerPanel) {

    var sessionId: String? = null
    var steps: List<Step> = emptyList()
    var currentStepId: String? = null
    var glossary: List<GlossaryTerm> = emptyList()
    var forgeContext: ForgeContext? = null
    var effectiveLevel: Int = 6

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var sessionJob: Job? = null

    init {
        panel.loadingPanel.cancelButton.addActionListener { cancel() }
        panel.idlePanel.setOpenReviewAction { openReview() }
    }

    private fun openReview() {
        val url = panel.idlePanel.getUrl()
        val level = panel.idlePanel.getExperienceLevel()
        panel.idlePanel.clearError()
        panel.showLoading()

        sessionJob = scope.launch {
            try {
                val stub = CodewalkerClient.getInstance().getStub() ?: run {
                    withContext(Dispatchers.Main) { panel.showError("Not connected to backend") }
                    return@launch
                }

                val expLevel = when (level) {
                    "EXPERIENCE_LEVEL_JUNIOR" -> ExperienceLevel.EXPERIENCE_LEVEL_JUNIOR
                    "EXPERIENCE_LEVEL_SENIOR" -> ExperienceLevel.EXPERIENCE_LEVEL_SENIOR
                    else -> ExperienceLevel.EXPERIENCE_LEVEL_MID
                }

                val host = HostNormalizer.fromUrl(url)
                val request = openReviewSessionRequest {
                    this.url = url
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
                val msg = ReviewErrorFormatter.format(e)
                withContext(Dispatchers.Main) { panel.showError(msg) }
            }
        }
    }

    fun dispose() {
        cancel()
        scope.cancel()
    }

    private fun cancel() {
        sessionJob?.cancel()
        sessionJob = null
        panel.showState(CodewalkerClient.getInstance().connectionState)
    }

    private fun resolveForgeToken(host: String): String {
        if (host.isEmpty()) return ""
        return TokenStore().get(host) ?: ""
    }
}
