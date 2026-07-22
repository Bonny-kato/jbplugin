package com.ipfsoftwares.jbplugin.documentation

import com.ipfsoftwares.jbplugin.route.RouteDocFacts
import com.ipfsoftwares.jbplugin.route.RoutePresentation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RoutePresentationTest {

    @Test
    fun documentationIncludesPathAndFile() {
        val html = RoutePresentation.documentationHtml(
            resolvedPath = "/about",
            fileHint = "about.tsx",
            fullPath = "/proj/src/routes/about.tsx",
            compact = false,
        )
        assertTrue(html.contains("/about"))
        assertTrue(html.contains("about.tsx"))
        assertTrue(html.contains("TanStack route"))
        assertTrue(html.contains("Full path"))
    }

    @Test
    fun hoverHidesAbsolutePath() {
        val html = RoutePresentation.documentationHtml(
            resolvedPath = "/about",
            fileHint = "src/routes/about.tsx",
            fullPath = "/Users/me/proj/src/routes/about.tsx",
            compact = true,
        )
        assertTrue(html.contains("src/routes/about.tsx"))
        assertFalse(html.contains("Full path"))
        assertFalse(html.contains("/Users/me/proj"))
    }

    @Test
    fun documentationIncludesApiLabel() {
        val html = RoutePresentation.documentationHtml(
            resolvedPath = "/x",
            fileHint = "x.tsx",
            fullPath = "/x.tsx",
            apiLabel = "Link",
        )
        assertTrue(html.contains("Link"))
        assertTrue(html.contains("·"))
    }

    @Test
    fun documentationIncludesUsageCount() {
        val html = RoutePresentation.documentationHtml(
            resolvedPath = "/x",
            fileHint = "x.tsx",
            fullPath = "/x.tsx",
            usageCount = 3,
        )
        assertTrue(html.contains("3 links"))
    }

    @Test
    fun zeroUsagesHighlighted() {
        val html = RoutePresentation.documentationHtml(
            resolvedPath = "/dead",
            fileHint = "dead.tsx",
            usageCount = 0,
        )
        assertTrue(html.contains("0 links"))
    }

    @Test
    fun showsParentParamsChildrenAndId() {
        val html = RoutePresentation.documentationHtml(
            resolvedPath = "/customers/$" + "id",
            requestedPath = "/(app)/customers/$" + "id",
            fileHint = "src/routes/customers/$" + "id.tsx",
            alternatePaths = listOf("/(app)/customers/$" + "id"),
            parentPath = "/customers",
            parentFileHint = "customers/route.tsx",
            childPaths = emptyList(),
            usageCount = 2,
            compact = true,
        )
        assertTrue(html.contains("Parent"))
        assertTrue(html.contains("/customers"))
        assertTrue(html.contains("Params"))
        assertTrue(html.contains("$" + "id"))
        assertTrue(html.contains("param"))
        // id when different from public
        assertTrue(html.contains("Id") || html.contains("(app)"))
    }

    @Test
    fun childrenSummaryInHtml() {
        val html = RoutePresentation.documentationHtml(
            resolvedPath = "/login",
            fileHint = "login/route.tsx",
            childPaths = listOf("/login/callback", "/login/error", "/login/success", "/login/extra"),
        )
        assertTrue(html.contains("Children"))
        assertTrue(html.contains("4"))
        assertTrue(html.contains("+1 more"))
    }

    @Test
    fun skipsRequestedWhenSameAsPublic() {
        val html = RoutePresentation.documentationHtml(
            resolvedPath = "/about",
            requestedPath = "/about",
            fileHint = "about.tsx",
        )
        assertFalse(html.contains("Requested"))
    }

    @Test
    fun alternatePathsCollapsedToId() {
        val html = RoutePresentation.documentationHtml(
            resolvedPath = "/login/callback",
            fileHint = "callback/route.tsx",
            alternatePaths = listOf("/(public)/login/callback"),
            compact = true,
        )
        // Should not dump "Also known as" list
        assertFalse(html.contains("Also known as"))
        assertTrue(html.contains("Id") || html.contains("(public)"))
    }

    @Test
    fun quickNavigateInfoFormat() {
        val q = RoutePresentation.quickNavigateInfo(
            "/about",
            "about.tsx",
            apiLabel = "navigate",
            kinds = listOf("leaf"),
            usageCount = 1,
        )
        assertTrue(q.contains("navigate"))
        assertTrue(q.contains("/about"))
        assertTrue(q.contains("about.tsx"))
        assertTrue(q.contains("leaf"))
        assertTrue(q.contains("1 link"))
    }

    @Test
    fun lookupTexts() {
        assertEquals("index.tsx", RoutePresentation.lookupTypeText("index.tsx"))
        assertTrue(RoutePresentation.lookupTailText("posts").contains("posts"))
        assertEquals("", RoutePresentation.lookupTailText(null))
    }

    @Test
    fun escapesHtmlInPaths() {
        val html = RoutePresentation.documentationHtml(
            resolvedPath = "/a/<b>",
            fileHint = "x.tsx",
            fullPath = "/a/<b>/x.tsx",
            compact = false,
        )
        assertTrue(html.contains("&lt;b&gt;"))
        assertFalse(html.contains("/a/<b>"))
    }
}
