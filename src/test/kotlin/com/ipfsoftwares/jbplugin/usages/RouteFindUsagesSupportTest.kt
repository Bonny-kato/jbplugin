package com.ipfsoftwares.jbplugin.usages

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RouteFindUsagesSupportTest {

    @Test
    fun pathVariantsAddsTrailingSlashTwin() {
        assertEquals(listOf("/customers", "/customers/"), RouteFindUsagesSupport.pathVariants("/customers"))
        assertEquals(listOf("/customers/", "/customers"), RouteFindUsagesSupport.pathVariants("/customers/"))
        assertEquals(listOf("/"), RouteFindUsagesSupport.pathVariants("/"))
        assertTrue(RouteFindUsagesSupport.pathVariants("").isEmpty())
        assertTrue(RouteFindUsagesSupport.pathVariants("   ").isEmpty())
    }

    @Test
    fun isRouteExportBindingDetectsCreateFileRouteAssignment() {
        val file = """
            import { createFileRoute } from '@tanstack/react-router'
            export const Route = createFileRoute('/_private/customers/')({
              component: CustomersPage,
            })
        """.trimIndent()

        val offset = file.indexOf("export const Route") + "export const ".length
        assertTrue(RouteFindUsagesSupport.isRouteExportBindingAt("Route", offset, file))
    }

    @Test
    fun isRouteExportBindingRejectsUnrelatedIdentifier() {
        val file = """
            export const Route = createFileRoute('/x')({
              component: CustomersPage,
            })
            const helper = 1
        """.trimIndent()
        val offset = file.indexOf("const helper") + "const ".length
        assertFalse(RouteFindUsagesSupport.isRouteExportBindingAt("helper", offset, file))
    }

    @Test
    fun isRouteExportBindingRejectsComponentOptionKey() {
        val file = "const component = createFileRoute('/x')({})"
        val offset = file.indexOf("component")
        assertFalse(RouteFindUsagesSupport.isRouteExportBindingAt("component", offset, file))
    }

    @Test
    fun isRouteDefinitionTextForFactoryCall() {
        val file = """export const Route = createFileRoute('/about')({ component: A })"""
        val factoryOffset = file.indexOf("createFileRoute")
        assertTrue(RouteFindUsagesSupport.isRouteDefinitionOffset(file, factoryOffset))
        assertTrue(RouteFindUsagesSupport.isRouteDefinitionOffset(file, file.indexOf("'/about'")))
        assertTrue(RouteFindUsagesSupport.isRouteDefinitionOffset(file, file.indexOf("Route =")))
    }

    @Test
    fun bodyIdentifierIsNotRouteDefinitionOffset() {
        val file = """
            export const Route = createFileRoute('/_private/')({ component: Page })
            function GroupedList() {
              const filteredItems = []
              return filteredItems
            }
        """.trimIndent()
        val offset = file.indexOf("filteredItems")
        assertFalse(
            "body variable must not be treated as route definition",
            RouteFindUsagesSupport.isRouteDefinitionOffset(file, offset),
        )
        assertFalse(RouteFindUsagesSupport.isRouteExportBindingAt("filteredItems", offset, file))
    }

    @Test
    fun functionNameIsNotRouteExportBinding() {
        val file = """
            export const Route = createFileRoute('/_private/')({ component: Page })
            function GroupedList() { return null }
        """.trimIndent()
        val offset = file.indexOf("GroupedList")
        assertFalse(RouteFindUsagesSupport.isRouteExportBindingAt("GroupedList", offset, file))
        assertFalse(RouteFindUsagesSupport.isRouteDefinitionOffset(file, offset))
    }
}
