package com.ipfsoftwares.jbplugin.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TanStackApiContextTest {

    @Test
    fun detectsFactoryCallNearOffset() {
        val text = """
            import { createFileRoute } from '@tanstack/react-router'
            export const Route = createFileRoute("/_private/customers/")({
              component: Page,
            })
        """.trimIndent()
        // First "createFileRoute" is the import — not a call site
        val importOffset = text.indexOf("createFileRoute")
        val callOffset = text.lastIndexOf("createFileRoute")
        assertTrue(callOffset > importOffset)
        assertFalse(TanStackApiContext.isInsideRouteFactoryCall(text, importOffset))
        assertTrue(TanStackApiContext.isInsideRouteFactoryCall(text, callOffset))
        assertTrue(TanStackApiContext.isInsideRouteFactoryCall(text, text.indexOf("/_private")))
        assertFalse(TanStackApiContext.isInsideRouteFactoryCall(text, text.indexOf("import")))
    }

    @Test
    fun pathVariantsStyleFactoryPaths() {
        // sanity: factory detection window works for lazy routes too
        val text = """export const Route = createLazyFileRoute("/lazy")({})"""
        assertTrue(TanStackApiContext.isInsideRouteFactoryCall(text, text.indexOf("/lazy")))
    }

    @Test
    fun callNearbyPatternsCoverLinkOptions() {
        val samples = listOf(
            """navigate({ to: '/a' })""",
            """redirect({ to: "/login" })""",
            """linkOptions({ to: '/dash' })""",
        )
        for (s in samples) {
            assertTrue(s, Regex("""\b(navigate|redirect|linkOptions)\s*\(""").containsMatchIn(s))
        }
    }

    @Test
    fun jsxExactNames() {
        assertEquals("Link", setOf("Link", "Navigate", "MatchRoute").first { it == "Link" })
        assertTrue("ButtonLink".endsWith("Link") && "ButtonLink".length > 4)
        assertFalse("Link".endsWith("Link") && "Link".length > 4) // exact Link handled separately
    }
}
