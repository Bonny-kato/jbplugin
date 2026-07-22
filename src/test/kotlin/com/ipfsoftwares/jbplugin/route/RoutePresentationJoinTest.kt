package com.ipfsoftwares.jbplugin.route

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure path helpers used by inspection / resolve decisions.
 */
class RoutePresentationJoinTest {

    @Test
    fun relativeJoinForInspectionBase() {
        assertEquals(
            "/customers/residents",
            RouteIndex.joinRoutePaths("/customers/residents/search", ".."),
        )
        assertEquals(
            "/customers/residents/search",
            RouteIndex.joinRoutePaths("/customers/residents", "./search"),
        )
    }

    @Test
    fun relativeDetection() {
        assertTrue(RouteIndex.isRelativePath(".."))
        assertTrue(RouteIndex.isRelativePath("edit"))
        assertFalse(RouteIndex.isRelativePath("/about"))
    }
}
