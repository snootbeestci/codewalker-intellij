package com.snootbeestci.codewalker.session

import codewalker.v1.Codewalker.NavigateRequest
import codewalker.v1.Codewalker.SimpleDirection
import codewalker.v1.Codewalker.Step
import codewalker.v1.navigateRequest
import com.snootbeestci.codewalker.grpc.CodewalkerClient
import com.snootbeestci.codewalker.toolwindow.SessionPanel
import com.snootbeestci.codewalker.toolwindow.displayOrderedSteps
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class NavigationController(
    private val controller: ReviewSessionController,
    private val panel: SessionPanel
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var navigateJob: Job? = null

    fun navigateForward() {
        val nextStep = findNextStep() ?: return
        panel.applyHighlightFor(nextStep)
        navigate(navigateRequest {
            sessionId = controller.sessionId!!
            direction = SimpleDirection.SIMPLE_DIRECTION_FORWARD
        })
    }

    fun navigateBack() {
        val previousStep = findPreviousStep() ?: return
        panel.applyHighlightFor(previousStep)
        navigate(navigateRequest {
            sessionId = controller.sessionId!!
            direction = SimpleDirection.SIMPLE_DIRECTION_BACK
        })
    }

    fun navigateTo(stepId: String) {
        val step = controller.steps.firstOrNull { it.id == stepId } ?: return
        panel.applyHighlightFor(step)
        navigate(navigateRequest {
            sessionId = controller.sessionId!!
            this.stepId = stepId
        })
    }

    private fun orderedSteps(): List<Step> {
        val files = controller.forgeContext?.filesList.orEmpty()
        return displayOrderedSteps(controller.steps, files)
    }

    private fun findNextStep(): Step? {
        val ordered = orderedSteps()
        val currentIdx = ordered.indexOfFirst { it.id == controller.currentStepId }
        return ordered.getOrNull(currentIdx + 1)
    }

    private fun findPreviousStep(): Step? {
        val ordered = orderedSteps()
        val currentIdx = ordered.indexOfFirst { it.id == controller.currentStepId }
        return ordered.getOrNull(currentIdx - 1)
    }

    private fun navigate(request: NavigateRequest) {
        navigateJob?.cancel()
        navigateJob = scope.launch {
            withContext(Dispatchers.Main) { panel.clearNarration() }
            val stub = CodewalkerClient.getInstance().getStub() ?: run {
                withContext(Dispatchers.Main) {
                    panel.appendNarrationToken("[Error: not connected to backend]")
                }
                return@launch
            }
            try {
                stub.navigate(request).collect { event ->
                    when {
                        event.hasToken() -> withContext(Dispatchers.Main) {
                            panel.appendNarrationToken(event.token.text)
                        }
                        event.hasSummaryReady() -> withContext(Dispatchers.Main) {
                            panel.onSummaryReady(event.summaryReady.summary)
                        }
                        event.hasComplete() -> withContext(Dispatchers.Main) {
                            panel.onStepComplete(event.complete)
                        }
                        event.hasError() -> withContext(Dispatchers.Main) {
                            panel.appendNarrationToken("\n[Error: ${event.error.message}]")
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    panel.appendNarrationToken("\n[Error: ${e.message ?: "Unknown error"}]")
                }
            }
        }
    }

    fun dispose() {
        scope.cancel()
    }
}
