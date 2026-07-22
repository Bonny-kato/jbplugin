package com.ipfsoftwares.jbplugin.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure-logic checks around path coercion used by anchor/inspection dedupe.
 */
class TanStackRouteAnchorTest {

    @Test
    fun coercePathHandlesQuotedAndJsxForms() {
        assertEquals("/customers/x", TanStackLinkContext.coerceRoutePath("\"/customers/x\""))
        assertEquals("/customers/x", TanStackLinkContext.coerceRoutePath("{'/customers/x'}"))
        assertEquals("/customers/x", TanStackLinkContext.coerceRoutePath("{ \"/customers/x\" }"))
        assertEquals("/customers/x", TanStackLinkContext.coerceRoutePath("/customers/x"))
    }

    @Test
    fun plausibleAbsoluteAndRelative() {
        assertTrue(TanStackLinkContext.isPlausibleRoutePath("/customers/incomplete-kyc"))
        assertTrue(TanStackLinkContext.isPlausibleRoutePath("../edit"))
        assertTrue(TanStackLinkContext.isPlausibleRoutePath("."))
        assertFalse(TanStackLinkContext.isPlausibleRoutePath("https://example.com"))
        assertFalse(TanStackLinkContext.isPlausibleRoutePath(""))
    }

    @Test
    fun pathVariantsStyleDedupKeysAreStable() {
        // Mirrors inspection range-key idea: same path string normalizes consistently
        val a = TanStackLinkContext.coerceRoutePath("\"/a/b\"")
        val b = TanStackLinkContext.coerceRoutePath("'/a/b'")
        assertEquals(a, b)
    }
}
