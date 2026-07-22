package com.ipfsoftwares.jbplugin.route

import com.intellij.testFramework.fixtures.BasePlatformTestCase

class RouteIndexServiceTest : BasePlatformTestCase() {

    fun testIndexesRouteTreeAndResolvesPaths() {
        myFixture.addFileToProject("src/routes/__root.tsx", "export const Route = {}\n")
        myFixture.addFileToProject("src/routes/index.tsx", "export const Route = {}\n")
        myFixture.addFileToProject("src/routes/about.tsx", "export const Route = {}\n")
        myFixture.addFileToProject("src/routes/posts/\$postId.tsx", "export const Route = {}\n")
        myFixture.addFileToProject("src/routes/posts/\$postId.edit.tsx", "export const Route = {}\n")
        myFixture.addFileToProject(
            "src/routeTree.gen.ts",
            """
            import { Route as rootRouteImport } from './routes/__root'
            import { Route as IndexRouteImport } from './routes/index'
            import { Route as AboutRouteImport } from './routes/about'
            import { Route as PostsPostIdRouteImport } from './routes/posts/${'$'}postId'
            import { Route as PostsPostIdEditRouteImport } from './routes/posts/${'$'}postId.edit'

            const IndexRoute = IndexRouteImport.update({ id: '/', path: '/' } as any)
            const AboutRoute = AboutRouteImport.update({ id: '/about', path: '/about' } as any)
            const PostsPostIdRoute = PostsPostIdRouteImport.update({
              id: '/posts/${'$'}postId',
              path: '/posts/${'$'}postId',
            } as any)
            const PostsPostIdEditRoute = PostsPostIdEditRouteImport.update({
              id: '/posts/${'$'}postId/edit',
              path: '/edit',
            } as any)

            export interface FileRoutesByTo {
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
            """.trimIndent(),
        )

        val service = RouteIndexService.getInstance(project)
        service.invalidate()
        val index = service.getIndex()

        assertFalse("index should not be empty, keys=${index.pathToFile.keys}", index.isEmpty())
        assertNotNull(service.resolve("/about"))
        assertTrue(service.resolve("/about")!!.path.endsWith("about.tsx"))
        assertNotNull(service.resolve("/posts/${'$'}postId"))
        assertTrue(service.resolve("/posts/${'$'}postId")!!.name.contains("postId"))
        assertNotNull(service.resolve("/"))

        // Relative: from posts/$postId route id → ../edit-ish path using join against fullPath
        val relativeEdit = service.resolveLink(
            to = "edit",
            baseRouteHint = "/posts/${'$'}postId",
        )
        assertNotNull("relative edit should resolve", relativeEdit)
        assertTrue(
            "expected edit route file, got ${relativeEdit?.path}",
            relativeEdit!!.name.contains("edit"),
        )

        val parent = service.resolveLink(
            to = "..",
            baseRouteHint = "/posts/${'$'}postId/edit",
        )
        assertNotNull(parent)
        assertTrue(parent!!.name.contains("postId"))
    }
}
