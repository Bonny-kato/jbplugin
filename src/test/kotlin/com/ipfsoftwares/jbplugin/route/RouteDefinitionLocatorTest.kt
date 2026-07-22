package com.ipfsoftwares.jbplugin.route

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RouteDefinitionLocatorTest {

    @Test
    fun findsCreateFileRouteOffset() {
        val text = """
            import { createFileRoute } from '@tanstack/react-router'
            
            export const Route = createFileRoute("/_private/customers/")({
              component: CustomersPage,
            })
        """.trimIndent()

        val offset = RouteDefinitionLocator.findDefinitionOffset(text)
        assertNotNull(offset)
        assertTrue(text.substring(offset!!).startsWith("createFileRoute"))
    }

    @Test
    fun prefersPathAwareMatchWhenMultipleCallsExist() {
        val text = """
            // weird dual export fixture
            const a = createFileRoute("/other")({})
            export const Route = createFileRoute("/_private/customers/")({})
        """.trimIndent()

        val offset = RouteDefinitionLocator.findDefinitionOffset(
            text,
            preferredPath = "/_private/customers/",
        )
        assertNotNull(offset)
        assertTrue(text.substring(offset!!).startsWith("createFileRoute(\"/_private/customers/\")"))
    }

    @Test
    fun matchesTrailingSlashVariant() {
        val text = """export const Route = createFileRoute("/home/")({})"""
        val offset = RouteDefinitionLocator.findDefinitionOffset(text, preferredPath = "/home")
        assertNotNull(offset)
        assertTrue(text.substring(offset!!).startsWith("createFileRoute(\"/home/\")"))
    }

    @Test
    fun prefersCreateFileRouteOverCreateRoute() {
        val text = """
            const legacy = createRoute({ path: '/x' })
            export const Route = createFileRoute("/x")({})
        """.trimIndent()

        val offset = RouteDefinitionLocator.findDefinitionOffset(text)
        assertNotNull(offset)
        assertTrue(text.substring(offset!!).startsWith("createFileRoute"))
    }

    @Test
    fun findsCreateLazyFileRoute() {
        val text = """export const Route = createLazyFileRoute("/lazy")({})"""
        val offset = RouteDefinitionLocator.findDefinitionOffset(text)
        assertNotNull(offset)
        assertEquals(text.indexOf("createLazyFileRoute"), offset)
    }

    @Test
    fun returnsNullWhenNoFactory() {
        val text = """export default function Page() { return null }"""
        assertNull(RouteDefinitionLocator.findDefinitionOffset(text))
    }

    @Test
    fun fuzzyMatchesPublicToAgainstPrivateRouteId() {
        val text = """
            import { createFileRoute } from '@tanstack/react-router'
            export const Route = createFileRoute("/_private/customers/")({
              component: Page,
            })
        """.trimIndent()

        val offset = RouteDefinitionLocator.findDefinitionOffset(
            text,
            preferredPath = "/customers",
        )
        assertNotNull(offset)
        assertTrue(text.substring(offset!!).startsWith("createFileRoute(\"/_private/customers/\")"))
    }

    @Test
    fun ignoresImportAndLandsOnCall() {
        val text = """
            import { createFileRoute } from '@tanstack/react-router'
            const x = 1
            export const Route = createFileRoute("/a")({})
        """.trimIndent()
        val offset = RouteDefinitionLocator.findDefinitionOffset(text)
        assertNotNull(offset)
        assertEquals(text.indexOf("createFileRoute(\"/a\")"), offset)
    }
}
