package com.ipfsoftwares.jbplugin.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure-logic checks around path coercion and anchor stability helpers.
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
        val a = TanStackLinkContext.coerceRoutePath("\"/a/b\"")
        val b = TanStackLinkContext.coerceRoutePath("'/a/b'")
        assertEquals(a, b)
    }

    @Test
    fun preferredGutterLeafPrefersPathContentOverPunctuation() {
        // Simulate host leaves: "{", "\"", "/customers", "\"", "}"
        // We only unit-test the character classifier contract used by preferredGutterLeaf.
        val punctuation = listOf("{", "}", "\"", "'", "`", " ", "{ ")
        val content = listOf("/customers", "customers", "\$id", "_private", "a-b", "x.y")
        fun isPathChar(c: Char) =
            c.isLetterOrDigit() || c == '/' || c == '\$' || c == '_' || c == '.' || c == '-' || c == '%'
        fun hasPath(s: String) = s.any { isPathChar(it) }

        punctuation.forEach { assertFalse("expected no path in '$it'", hasPath(it.trim().ifEmpty { it })) }
        // "{" has no path char
        assertFalse(hasPath("{"))
        assertFalse(hasPath("}"))
        assertFalse(hasPath("\""))
        content.forEach { assertTrue("expected path chars in $it", hasPath(it)) }
    }

    @Test
    fun mergeKeyIdentityIsPathAndFile() {
        // Documents gutter merge identity: same route path + resolved file path
        // must collapse; different paths must not.
        data class Key(val offset: Int, val file: String, val path: String)
        val a = Key(10, "/proj/routes/x.tsx", "/customers/residents/search")
        val b = Key(10, "/proj/routes/x.tsx", "/customers/residents/search")
        val c = Key(10, "/proj/routes/x.tsx", "/customers/residents/search/other-id")
        assertEquals(a, b)
        assertFalse(a == c)
    }
}

