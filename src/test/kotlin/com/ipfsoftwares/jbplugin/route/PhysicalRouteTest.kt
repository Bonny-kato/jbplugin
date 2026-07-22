package com.ipfsoftwares.jbplugin.route

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PhysicalRouteTest {

    @Test
    fun pickPreferredPathSkipsInternalIdsAndTrailingSlash() {
        assertEquals(
            "/home",
            PhysicalRoute.pickPreferredPath(listOf("/_layout/home", "/home/", "/home")),
        )
        assertEquals(
            "/customers/residents/search",
            PhysicalRoute.pickPreferredPath(
                listOf(
                    "/customers/residents/search/",
                    "/_private/customers/residents/search",
                    "/customers/residents/search",
                ),
            ),
        )
    }

    @Test
    fun pickPreferredPathEmpty() {
        assertEquals("", PhysicalRoute.pickPreferredPath(emptyList()))
    }

    @Test
    fun allPathsIncludesPreferredAndAlternates() {
        // path-only structural check without VirtualFile
        val paths = listOf("/a", "/a/", "/_x/a")
        val preferred = PhysicalRoute.pickPreferredPath(paths)
        assertEquals("/a", preferred)
        val alternates = paths.filter { it != preferred }
        assertTrue(alternates.contains("/a/"))
        assertTrue(alternates.contains("/_x/a"))
        assertFalse(alternates.contains(preferred))
    }
}
