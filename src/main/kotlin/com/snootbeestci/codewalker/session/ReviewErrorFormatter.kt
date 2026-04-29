package com.snootbeestci.codewalker.session

import io.grpc.Status
import io.grpc.StatusRuntimeException

/**
 * Renders a server-side error to a user-facing string, with special handling
 * for `PERMISSION_DENIED` so SSO-required responses are distinguishable from
 * plain "bad token" responses.
 *
 * The server includes the forge's response body (truncated to ~500 chars) in
 * the gRPC status description for 403 responses. GitHub Enterprise SSO
 * responses contain markers like "SAML enforcement" or "single sign-on" —
 * we surface those with an "Authorization required:" prefix so the user
 * knows to authorise the token rather than replace it.
 */
object ReviewErrorFormatter {

    private val ssoMarkers = listOf(
        "SAML enforcement",
        "SSO authorization",
        "SSO authorisation",
        "single sign-on",
        "must have admin rights",
        "configure SSO",
    )

    fun format(e: Throwable): String {
        if (e is StatusRuntimeException && e.status.code == Status.Code.PERMISSION_DENIED) {
            val description = e.status.description.orEmpty()
            val hasSsoMarker = ssoMarkers.any { description.contains(it, ignoreCase = true) }
            return when {
                hasSsoMarker -> "Authorization required: $description"
                description.isNotBlank() -> description
                else -> "Permission denied"
            }
        }
        return e.message ?: "Unknown error"
    }
}
