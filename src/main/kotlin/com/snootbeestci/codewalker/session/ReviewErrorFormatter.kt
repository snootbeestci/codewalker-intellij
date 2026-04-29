package com.snootbeestci.codewalker.session

import io.grpc.Status
import io.grpc.StatusRuntimeException

/**
 * Structured render of a server-side error.
 *
 * `message` is the user-facing string (with SSO prefix when applicable).
 * `isAuthFailure` is true for any error that suggests the request failed
 * because of a missing, expired, or improperly authorised token. Callers
 * use this to decide whether to surface a "Configure tokens" affordance —
 * no string-matching required.
 */
data class FormattedError(
    val message: String,
    val isAuthFailure: Boolean,
)

object ReviewErrorFormatter {

    private val ssoMarkers = listOf(
        "SAML enforcement",
        "SSO authorization",
        "SSO authorisation",
        "single sign-on",
        "must have admin rights",
        "configure SSO",
    )

    fun format(e: Throwable): FormattedError {
        if (e is StatusRuntimeException) {
            val code = e.status.code
            val description = e.status.description.orEmpty()
            val isAuth = code == Status.Code.PERMISSION_DENIED ||
                code == Status.Code.UNAUTHENTICATED

            if (code == Status.Code.PERMISSION_DENIED) {
                val hasSsoMarker = ssoMarkers.any { description.contains(it, ignoreCase = true) }
                val message = when {
                    hasSsoMarker -> "Authorization required: $description"
                    description.isNotBlank() -> description
                    else -> "Permission denied"
                }
                return FormattedError(message, isAuthFailure = true)
            }

            if (code == Status.Code.UNAUTHENTICATED) {
                val message = description.ifBlank { "Authentication required" }
                return FormattedError(message, isAuthFailure = true)
            }

            return FormattedError(
                e.message ?: "Unknown error",
                isAuthFailure = isAuth,
            )
        }
        return FormattedError(e.message ?: "Unknown error", isAuthFailure = false)
    }
}
