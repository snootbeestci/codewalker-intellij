package com.snootbeestci.codewalker.session

import io.grpc.Metadata
import io.grpc.Status
import io.grpc.StatusRuntimeException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ReviewErrorFormatterTest {

    private fun permissionDenied(description: String): StatusRuntimeException =
        StatusRuntimeException(
            Status.PERMISSION_DENIED.withDescription(description),
            Metadata()
        )

    @Test
    fun `non-status exception falls through to message`() {
        val r = ReviewErrorFormatter.format(IllegalStateException("boom"))
        assertEquals("boom", r.message)
        assertEquals(false, r.isAuthFailure)
    }

    @Test
    fun `null message yields fallback string`() {
        val r = ReviewErrorFormatter.format(RuntimeException())
        assertEquals("Unknown error", r.message)
        assertEquals(false, r.isAuthFailure)
    }

    @Test
    fun `permission denied without description yields generic string`() {
        val e = StatusRuntimeException(Status.PERMISSION_DENIED, Metadata())
        val r = ReviewErrorFormatter.format(e)
        assertEquals("Permission denied", r.message)
        assertEquals(true, r.isAuthFailure)
    }

    @Test
    fun `permission denied with plain bad-token body returns the body`() {
        val r = ReviewErrorFormatter.format(permissionDenied("Bad credentials"))
        assertEquals("Bad credentials", r.message)
        assertEquals(true, r.isAuthFailure)
    }

    @Test
    fun `permission denied with associated branch description is not tagged`() {
        val r = ReviewErrorFormatter.format(
            permissionDenied("This PR is associated with a protected branch")
        )
        assertEquals(false, r.message.startsWith("Authorization required:"))
        assertEquals(true, r.isAuthFailure)
    }

    @Test
    fun `permission denied with crossorigin description is not tagged`() {
        val r = ReviewErrorFormatter.format(
            permissionDenied("Cross-origin requests are not permitted on this endpoint")
        )
        assertEquals(false, r.message.startsWith("Authorization required:"))
        assertEquals(true, r.isAuthFailure)
    }

    @Test
    fun `permission denied with bare SSO acronym alone is not tagged`() {
        val r = ReviewErrorFormatter.format(permissionDenied("SSO is not the issue here"))
        assertEquals(false, r.message.startsWith("Authorization required:"))
        assertEquals(true, r.isAuthFailure)
    }

    @Test
    fun `permission denied with SAML enforcement body is tagged as authorization required`() {
        val body = "Resource protected by organization SAML enforcement. " +
            "You must grant your OAuth token access to this organization."
        val r = ReviewErrorFormatter.format(permissionDenied(body))
        assertEquals("Authorization required: $body", r.message)
        assertEquals(true, r.isAuthFailure)
    }

    @Test
    fun `permission denied with single sign-on marker is tagged as authorization required`() {
        val r = ReviewErrorFormatter.format(permissionDenied("Single sign-on required for org foo"))
        assertEquals(true, r.message.startsWith("Authorization required:"))
        assertEquals(true, r.isAuthFailure)
    }

    @Test
    fun `permission denied with admin rights marker is tagged as authorization required`() {
        val r = ReviewErrorFormatter.format(
            permissionDenied("Token must have admin rights to access this org")
        )
        assertEquals(true, r.message.startsWith("Authorization required:"))
        assertEquals(true, r.isAuthFailure)
    }

    @Test
    fun `non-permission-denied status falls through to default message`() {
        val e = StatusRuntimeException(
            Status.NOT_FOUND.withDescription("no such PR"),
            Metadata()
        )
        val r = ReviewErrorFormatter.format(e)
        assertEquals(true, r.message.contains("no such PR"))
    }

    @Test
    fun `unauthenticated status is auth failure`() {
        val e = StatusRuntimeException(
            Status.UNAUTHENTICATED.withDescription("token expired"),
            Metadata(),
        )
        val r = ReviewErrorFormatter.format(e)
        assertEquals("token expired", r.message)
        assertEquals(true, r.isAuthFailure)
    }

    @Test
    fun `unauthenticated without description has fallback message`() {
        val e = StatusRuntimeException(Status.UNAUTHENTICATED, Metadata())
        val r = ReviewErrorFormatter.format(e)
        assertEquals("Authentication required", r.message)
        assertEquals(true, r.isAuthFailure)
    }

    @Test
    fun `not-found status is not an auth failure`() {
        val e = StatusRuntimeException(
            Status.NOT_FOUND.withDescription("no such PR"),
            Metadata(),
        )
        val r = ReviewErrorFormatter.format(e)
        assertEquals(false, r.isAuthFailure)
    }
}
