package com.ipfsoftwares.jbplugin.documentation

import com.ipfsoftwares.jbplugin.route.RoutePresentation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RoutePresentationTest {

    @Test
    fun documentationHtmlContainsPathAndFile() {
        val html = RoutePresentation.documentationHtml(
            resolvedPath = "/customers",
            requestedPath = "/customers",
            fileHint = "routes/customers.tsx",
            fullPath = "/app/src/routes/customers.tsx",
            alternatePaths = listOf("/customers/", "/_private/customers/"),
            sourceTreeName = "routeTree.gen.ts",
            apiLabel = "Link",
            usageCount = 3,
        )
        assertTrue(html.contains("/customers"))
        assertTrue(html.contains("customers.tsx"))
        assertTrue(html.contains("via Link"))
        assertTrue(html.contains("Usages"))
        assertTrue(html.contains("3"))
        assertTrue(html.contains("/_private/customers/"))
        assertTrue(html.contains("routeTree.gen.ts"))
        assertFalse(html.contains("<script>"))
    }

    @Test
    fun quickNavigateInfoFormat() {
        val q = RoutePresentation.quickNavigateInfo("/about", "about.tsx", apiLabel = "navigate")
        assertTrue(q.contains("navigate"))
        assertTrue(q.contains("/about"))
        assertTrue(q.contains("about.tsx"))
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
        )
        assertTrue(html.contains("&lt;b&gt;"))
        assertFalse(html.contains("/a/<b>"))
    }
}
