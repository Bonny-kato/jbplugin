package com.ipfsoftwares.jbplugin.route

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class RouteTreeParserTest {

    private val sample = """
        import { Route as rootRouteImport } from './routes/__root'
        import { Route as IndexRouteImport } from './routes/index'
        import { Route as AboutRouteImport } from './routes/about'
        import { Route as PostsPostIdRouteImport } from './routes/posts/${'$'}postId'
        import { Route as PostsPostIdEditRouteImport } from './routes/posts/${'$'}postId.edit'

        const IndexRoute = IndexRouteImport.update({ id: '/', path: '/', getParentRoute: () => rootRouteImport } as any)
        const AboutRoute = AboutRouteImport.update({ id: '/about', path: '/about', getParentRoute: () => rootRouteImport } as any)
        const PostsPostIdRoute = PostsPostIdRouteImport.update({
          id: '/posts/${'$'}postId',
          path: '/posts/${'$'}postId',
          getParentRoute: () => rootRouteImport,
        } as any)
        const PostsPostIdEditRoute = PostsPostIdEditRouteImport.update({
          id: '/posts/${'$'}postId/edit',
          path: '/edit',
          getParentRoute: () => PostsPostIdRoute,
        } as any)

        export interface FileRoutesByFullPath {
          '/': typeof IndexRoute
          '/about': typeof AboutRoute
          '/posts/${'$'}postId': typeof PostsPostIdRoute
          '/posts/${'$'}postId/edit': typeof PostsPostIdEditRoute
        }
        export interface FileRoutesByTo {
          '/': typeof IndexRoute
          '/about': typeof AboutRoute
          '/posts/${'$'}postId': typeof PostsPostIdRoute
          '/posts/${'$'}postId/edit': typeof PostsPostIdEditRoute
        }
        export interface FileRoutesById {
          __root__: typeof rootRouteImport
          '/': typeof IndexRoute
          '/about': typeof AboutRoute
          '/posts/${'$'}postId': typeof PostsPostIdRoute
          '/posts/${'$'}postId/edit': typeof PostsPostIdEditRoute
        }

        declare module '@tanstack/react-router' {
          interface FileRoutesByPath {
            '/about': {
              id: '/about'
              path: '/about'
              fullPath: '/about'
              preLoaderRoute: typeof AboutRouteImport
              parentRoute: typeof rootRouteImport
            }
            '/posts/${'$'}postId': {
              id: '/posts/${'$'}postId'
              path: '/posts/${'$'}postId'
              fullPath: '/posts/${'$'}postId'
              preLoaderRoute: typeof PostsPostIdRouteImport
              parentRoute: typeof rootRouteImport
            }
            '/posts/${'$'}postId/edit': {
              id: '/posts/${'$'}postId/edit'
              path: '/edit'
              fullPath: '/posts/${'$'}postId/edit'
              preLoaderRoute: typeof PostsPostIdEditRouteImport
              parentRoute: typeof PostsPostIdRoute
            }
            '/': {
              id: '/'
              path: '/'
              fullPath: '/'
              preLoaderRoute: typeof IndexRouteImport
              parentRoute: typeof rootRouteImport
            }
          }
        }
    """.trimIndent()

    @Test
    fun parsesToAndFullPathMaps() {
        val map = RouteTreeParser.parse(sample)
        assertEquals("./routes/about", map["/about"])
        assertEquals("./routes/index", map["/"])
        assertEquals("./routes/posts/${'$'}postId", map["/posts/${'$'}postId"])
        assertEquals("./routes/posts/${'$'}postId.edit", map["/posts/${'$'}postId/edit"])
    }

    @Test
    fun parseDetailedTracksFullPathsForIds() {
        val detailed = RouteTreeParser.parseDetailed(sample)
        assertEquals("/about", detailed.keyToFullPath["/about"])
        assertEquals("/posts/${'$'}postId/edit", detailed.keyToFullPath["/posts/${'$'}postId/edit"])
        assertEquals("./routes/about", detailed.pathToRelative["/about"])
    }

    @Test
    fun prefersFileRoutesByToOverLayoutFullPath() {
        // Mirrors banca-99: same fullPath on layout + index; ByTo points at index.
        val content = """
            import { Route as LayoutImport } from './routes/search/route'
            import { Route as IndexImport } from './routes/search/index'

            const LayoutRoute = LayoutImport
            const IndexRoute = IndexImport
            const LayoutRouteWithChildren = LayoutRoute

            export interface FileRoutesByFullPath {
              '/customers/residents/search': typeof LayoutRouteWithChildren
              '/customers/residents/search/': typeof IndexRoute
            }
            export interface FileRoutesByTo {
              '/customers/residents/search': typeof IndexRoute
            }
            declare module '@tanstack/react-router' {
              interface FileRoutesByPath {
                '/_private/search': {
                  id: '/_private/search'
                  path: '/search'
                  fullPath: '/customers/residents/search'
                  preLoaderRoute: typeof LayoutImport
                  parentRoute: typeof LayoutImport
                }
                '/_private/search/': {
                  id: '/_private/search/'
                  path: '/'
                  fullPath: '/customers/residents/search/'
                  preLoaderRoute: typeof IndexImport
                  parentRoute: typeof LayoutImport
                }
              }
            }
        """.trimIndent()

        val map = RouteTreeParser.parse(content)
        assertEquals(
            "ByTo index should win for /customers/residents/search",
            "./routes/search/index",
            map["/customers/residents/search"],
        )
    }

    @Test
    fun parsesRealBancaTreeWhenPresent() {
        val candidates = listOf(
            File("/Users/bonny-kato/Documents/dev/iPFSoftwares/nmb/banca-99/src/routeTree.gen.ts"),
            File("../nmb/banca-99/src/routeTree.gen.ts"),
            File("../../nmb/banca-99/src/routeTree.gen.ts"),
        )
        val file = candidates.firstOrNull { it.isFile } ?: return
        val map = RouteTreeParser.parse(file.readText())
        assertTrue("expected routes in banca tree, got ${map.size}", map.isNotEmpty())
        assertNotNull(map["/customers/residents/search"] ?: map["/customers/residents/search/"])
        // Login `to` should resolve to a real module under routes
        val login = map["/login"] ?: map["/login/"]
        assertTrue("login mapped: $login", login != null && login.contains("login"))
        // Prefer ByTo target for residents search (index route file)
        val search = map["/customers/residents/search"]
        assertNotNull(search)
        assertTrue(
            "search should map to nmb-account index, got $search",
            search!!.contains("nmb-account") || search.contains("search"),
        )
    }

    @Test
    fun emptyWhenNoImports() {
        assertTrue(RouteTreeParser.parse("export const x = 1").isEmpty())
    }
}
