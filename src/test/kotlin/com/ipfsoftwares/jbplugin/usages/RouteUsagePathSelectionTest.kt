package com.ipfsoftwares.jbplugin.usages

import com.ipfsoftwares.jbplugin.route.PhysicalRoute
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Find Usages must search the same path-key style the tool window uses for a physical route,
 * and prefer the same display path (public over /_private layout ids).
 */
class RouteUsagePathSelectionTest {

    @Test
    fun preferredPathMatchesPhysicalRoutePickerForPrivateOnly() {
        val paths = listOf(
            "/_private/customers/residents",
            "/_private/customers/residents/",
        )
        val preferred = PhysicalRoute.pickPreferredPath(paths)
        assertEquals("/_private/customers/residents", preferred)
        assertTrue(preferred.startsWith("/_private"))
    }

    @Test
    fun preferredPathPrefersPublicOverPrivateLayoutId() {
        val paths = listOf(
            "/_private/customers/",
            "/customers",
            "/customers/",
        )
        assertEquals("/customers", PhysicalRoute.pickPreferredPath(paths))
    }

    @Test
    fun physicalAllPathsKeepsAlternatesForSearch() {
        // Mirrors tool window: search uses preferred + alternates, not sibling routes.
        val all = (listOf("/_private/customers/residents") + listOf("/_private/customers/residents/"))
            .distinct()
        assertEquals(
            setOf("/_private/customers/residents", "/_private/customers/residents/"),
            all.toSet(),
        )
        assertFalse(all.any { it == "/customers/residents" })
        assertFalse(all.any { "\$" in it })
    }

    @Test
    fun pathVariantsDoNotInventSiblingRoutes() {
        val expanded = listOf("/_private/customers/residents")
            .flatMap { RouteFindUsagesSupport.pathVariants(it) }
            .toSet()
        assertTrue("/_private/customers/residents" in expanded)
        assertTrue("/_private/customers/residents/" in expanded)
        assertFalse(expanded.any { "\$" in it })
        assertFalse("/customers/residents" in expanded)
    }
}
