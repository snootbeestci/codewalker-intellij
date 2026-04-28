package com.snootbeestci.codewalker.session

import codewalker.v1.Codewalker.NavigateRequest
import codewalker.v1.Codewalker.SimpleDirection
import codewalker.v1.navigateRequest
import com.snootbeestci.codewalker.grpc.CodewalkerClient
import com.snootbeestci.codewalker.toolwindow.SessionPanel
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

    fun navigateForward() = navigate(navigateRequest {
        sessionId = controller.sessionId!!
        direction = SimpleDirection.SIMPLE_DIRECTION_FORWARD
    })

    fun navigateBack() = navigate(navigateRequest {
        sessionId = controller.sessionId!!
        direction = SimpleDirection.SIMPLE_DIRECTION_BACK
    })

    fun navigateTo(stepId: String) = navigate(navigateRequest {
        sessionId = controller.sessionId!!
        this.stepId = stepId
    })

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
