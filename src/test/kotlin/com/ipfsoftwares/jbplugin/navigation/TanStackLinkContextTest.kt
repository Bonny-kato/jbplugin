package com.ipfsoftwares.jbplugin.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TanStackLinkContextTest {

    @Test
    fun extractQuotedHandlesQuotes() {
        assertEquals("/about", TanStackLinkContext.extractQuoted("'/about'"))
        assertEquals("/about", TanStackLinkContext.extractQuoted("\"/about\""))
        assertEquals("/x", TanStackLinkContext.extractQuoted("`/x`"))
        assertNull(TanStackLinkContext.extractQuoted("`/x/${'$'}{id}`"))
        assertNull(TanStackLinkContext.extractQuoted("/about"))
    }

    @Test
    fun coerceRoutePathHandlesJsxExpressionAttributes() {
        assertEquals(
            "/customers/residents/search",
            TanStackLinkContext.coerceRoutePath("{\"/customers/residents/search\"}"),
        )
        assertEquals(
            "/customers/residents/search",
            TanStackLinkContext.coerceRoutePath("{ '/customers/residents/search' }"),
        )
        assertEquals("/about", TanStackLinkContext.coerceRoutePath("\"/about\""))
        assertEquals("/about", TanStackLinkContext.coerceRoutePath("/about"))
        assertEquals("..", TanStackLinkContext.coerceRoutePath("{\"..\"}"))
        assertEquals("../edit", TanStackLinkContext.coerceRoutePath("'../edit'"))
        assertNull(TanStackLinkContext.coerceRoutePath("{\"https://example.com\"}"))
    }

    @Test
    fun plausibleRoutePaths() {
        assertTrue(TanStackLinkContext.isPlausibleRoutePath("/"))
        assertTrue(TanStackLinkContext.isPlausibleRoutePath("/customers/${'$'}id"))
        assertTrue(TanStackLinkContext.isPlausibleRoutePath("."))
        assertTrue(TanStackLinkContext.isPlausibleRoutePath(".."))
        assertTrue(TanStackLinkContext.isPlausibleRoutePath("../edit"))
        assertTrue(TanStackLinkContext.isPlausibleRoutePath("edit"))
        assertTrue(TanStackLinkContext.isRelativeRoutePath(".."))
        assertFalse(TanStackLinkContext.isRelativeRoutePath("/about"))
        assertFalse(TanStackLinkContext.isPlausibleRoutePath("https://example.com"))
        assertFalse(TanStackLinkContext.isPlausibleRoutePath("{\"/x\"}"))
    }

    @Test
    fun extractNamedStringProp() {
        assertEquals(
            "/posts/${'$'}id",
            TanStackLinkContext.extractNamedStringProp("""{ to: '..', from: '/posts/${'$'}id' }""", "from"),
        )
        assertEquals(
            "..",
            TanStackLinkContext.extractNamedStringProp("""navigate({ to: "..", from: "/posts" })""", "to"),
        )
        assertEquals(
            "/login",
            TanStackLinkContext.extractNamedStringProp("""<Link to={"/login"} from="/x" />""", "to"),
        )
    }
}
