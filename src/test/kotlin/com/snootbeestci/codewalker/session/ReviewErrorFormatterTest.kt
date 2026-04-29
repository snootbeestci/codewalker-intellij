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
        val msg = ReviewErrorFormatter.format(IllegalStateException("boom"))
        assertEquals("boom", msg)
    }

    @Test
    fun `null message yields fallback string`() {
        val msg = ReviewErrorFormatter.format(RuntimeException())
        assertEquals("Unknown error", msg)
    }

    @Test
    fun `permission denied without description yields generic string`() {
        val e = StatusRuntimeException(Status.PERMISSION_DENIED, Metadata())
        assertEquals("Permission denied", ReviewErrorFormatter.format(e))
    }

    @Test
    fun `permission denied with plain bad-token body returns the body`() {
        val e = permissionDenied("Bad credentials")
        assertEquals("Bad credentials", ReviewErrorFormatter.format(e))
    }

    @Test
    fun `permission denied with SAML enforcement body is tagged as authorization required`() {
        val body = "Resource protected by organization SAML enforcement. " +
            "You must grant your OAuth token access to this organization."
        val e = permissionDenied(body)
        val msg = ReviewErrorFormatter.format(e)
        assertEquals("Authorization required: $body", msg)
    }

    @Test
    fun `permission denied with single sign-on marker is tagged as authorization required`() {
        val e = permissionDenied("Single sign-on required for org foo")
        val msg = ReviewErrorFormatter.format(e)
        assertEquals(true, msg.startsWith("Authorization required:"))
    }

    @Test
    fun `permission denied with admin rights marker is tagged as authorization required`() {
        val e = permissionDenied("Token must have admin rights to access this org")
        val msg = ReviewErrorFormatter.format(e)
        assertEquals(true, msg.startsWith("Authorization required:"))
    }

    @Test
    fun `non-permission-denied status falls through to default message`() {
        val e = StatusRuntimeException(
            Status.NOT_FOUND.withDescription("no such PR"),
            Metadata()
        )
        val msg = ReviewErrorFormatter.format(e)
        // StatusRuntimeException.message is "NOT_FOUND: no such PR" — we don't
        // dictate the format here, just confirm the path is the default.
        assertEquals(true, msg.contains("no such PR"))
    }
}
