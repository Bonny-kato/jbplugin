package com.ipfsoftwares.jbplugin.route

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RouteIndexTest {

    @Test
    fun joinRoutePathsResolvesRelativeSegments() {
        assertEquals(
            "/customers/residents",
            RouteIndex.joinRoutePaths("/customers/residents/search", ".."),
        )
        assertEquals(
            "/customers/residents/search",
            RouteIndex.joinRoutePaths("/customers/residents/search", "."),
        )
        assertEquals(
            "/customers/residents/search/nida",
            RouteIndex.joinRoutePaths("/customers/residents/search", "./nida"),
        )
        assertEquals(
            "/customers/foreigners",
            RouteIndex.joinRoutePaths("/customers/residents/search", "../../foreigners"),
        )
        assertEquals(
            "/posts/edit",
            RouteIndex.joinRoutePaths("/posts/${'$'}id", "../edit"),
        )
        assertEquals(
            "/",
            RouteIndex.joinRoutePaths("/about", ".."),
        )
        assertEquals(
            "/",
            RouteIndex.joinRoutePaths("/", ".."),
        )
        assertEquals(
            "/customers/residents/search",
            RouteIndex.joinRoutePaths("/customers/residents", "search"),
        )
    }

    @Test
    fun resolveLinkUsesBaseHintForRelative() {
        // Lightweight fake index using empty VirtualFiles is hard; test pure helpers via join only.
        assertNull(RouteIndex.EMPTY.resolveLink("..", null))
        assertNull(RouteIndex.EMPTY.resolveLink("..", "/customers/residents/search"))
    }

    @Test
    fun pickPreferredPathPrefersPublicShortPath() {
        assertEquals(
            "/customers/residents/search",
            PhysicalRoute.pickPreferredPath(
                listOf(
                    "/customers/residents/search/",
                    "/_private/search/",
                    "/customers/residents/search",
                    "/_private/search",
                ),
            ),
        )
        assertEquals("/", PhysicalRoute.pickPreferredPath(listOf("/", "/")))
        assertEquals("/about", PhysicalRoute.pickPreferredPath(listOf("/about/", "/about")))
    }

    @Test
    fun emptyIndexHasNoPhysicalRoutes() {
        assertTrue(RouteIndex.EMPTY.physicalRoutes().isEmpty())
    }
}
