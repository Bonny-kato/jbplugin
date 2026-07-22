package com.ipfsoftwares.jbplugin.route

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RouteDocFactsTest {

    @Test
    fun extractParamsFromSegments() {
        assertEquals(listOf("$" + "postId"), RouteDocFacts.extractParams("/posts/$" + "postId"))
        assertEquals(listOf("$" + "id"), RouteDocFacts.extractParams("/x/{$" + "id}"))
        assertEquals(listOf("$" + "locale"), RouteDocFacts.extractParams("/{-$" + "locale}/home"))
        assertTrue(RouteDocFacts.extractParams("/about").isEmpty())
    }

    @Test
    fun inferKindsLeafParam() {
        val kinds = RouteDocFacts.inferKinds("/customers/$" + "id", "\$id.tsx")
        assertTrue(kinds.contains("param"))
        assertTrue(kinds.contains("leaf"))
    }

    @Test
    fun inferKindsLayoutWhenChildren() {
        val kinds = RouteDocFacts.inferKinds("/login", "route.tsx", childCount = 3)
        assertTrue(kinds.contains("layout"))
        assertFalse(kinds.contains("leaf"))
    }

    @Test
    fun inferKindsPathlessFromAlternate() {
        val kinds = RouteDocFacts.inferKinds(
            "/login",
            "route.tsx",
            alternatePaths = listOf("/(public)/login"),
        )
        assertTrue(kinds.contains("pathless"))
    }

    @Test
    fun inferKindsIndex() {
        val kinds = RouteDocFacts.inferKinds("/home/", "index.tsx")
        assertTrue(kinds.contains("index"))
    }

    @Test
    fun parentCandidatesNearestFirst() {
        assertEquals(
            listOf("/a/b", "/a", "/"),
            RouteDocFacts.parentCandidates("/a/b/c"),
        )
        assertEquals(emptyList<String>(), RouteDocFacts.parentCandidates("/"))
    }

    @Test
    fun directChildDetection() {
        assertTrue(RouteDocFacts.isDirectChild("/login", "/login/callback"))
        assertFalse(RouteDocFacts.isDirectChild("/login", "/login/callback/extra"))
        assertFalse(RouteDocFacts.isDirectChild("/login", "/logout"))
        assertTrue(RouteDocFacts.isDescendant("/", "/a"))
    }

    @Test
    fun publicPathPrefersCleanUrl() {
        val pub = RouteDocFacts.publicPath(
            "/(public)/login/callback",
            listOf("/login/callback", "/(public)/login/callback"),
        )
        assertEquals("/login/callback", pub)
    }

    @Test
    fun routeIdWhenInternalDiffers() {
        val id = RouteDocFacts.routeId(
            publicPath = "/login/callback",
            resolvedPath = "/login/callback",
            alternatePaths = listOf("/(public)/login/callback"),
        )
        assertEquals("/(public)/login/callback", id)
        assertNull(
            RouteDocFacts.routeId("/about", "/about", emptyList()),
        )
    }

    @Test
    fun projectRelativePath() {
        assertEquals(
            "src/routes/about.tsx",
            RouteDocFacts.projectRelativePath(
                "/Users/me/proj",
                "/Users/me/proj/src/routes/about.tsx",
                "about.tsx",
            ),
        )
        assertEquals(
            "about.tsx",
            RouteDocFacts.projectRelativePath(null, "/x", "about.tsx"),
        )
    }

    @Test
    fun formatHelpers() {
        assertEquals("0 links", RouteDocFacts.formatUsageLabel(0))
        assertEquals("1 link", RouteDocFacts.formatUsageLabel(1))
        assertEquals("4 links", RouteDocFacts.formatUsageLabel(4))
        assertEquals(
            "/a, /b +1 more",
            RouteDocFacts.formatChildrenSummary(listOf("/a", "/b", "/c"), limit = 2),
        )
        assertEquals("leaf · param", RouteDocFacts.formatKinds(listOf("leaf", "param")))
    }
}
