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

    fun testRebuildPicksUpUpdatedRouteTree() {
        myFixture.addFileToProject("src/routes/__root.tsx", "export const Route = {}\n")
        myFixture.addFileToProject("src/routes/about.tsx", "export const Route = {}\n")
        myFixture.addFileToProject("src/routes/contact.tsx", "export const Route = {}\n")
        val tree = myFixture.addFileToProject(
            "src/routeTree.gen.ts",
            """
            import { Route as rootRouteImport } from './routes/__root'
            import { Route as AboutRouteImport } from './routes/about'
            const AboutRoute = AboutRouteImport.update({ id: '/about', path: '/about' } as any)
            export interface FileRoutesByTo {
              '/about': typeof AboutRoute
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
              }
            }
            """.trimIndent(),
        )

        val service = RouteIndexService.getInstance(project)
        service.rebuildSynchronously()
        assertNotNull(service.resolve("/about"))
        assertNull("contact not generated yet", service.resolve("/contact"))

        // Simulate TanStack generator rewriting routeTree.gen.ts
        com.intellij.openapi.application.WriteAction.run<RuntimeException> {
            val vf = tree.virtualFile
            val updated = """
            import { Route as rootRouteImport } from './routes/__root'
            import { Route as AboutRouteImport } from './routes/about'
            import { Route as ContactRouteImport } from './routes/contact'
            const AboutRoute = AboutRouteImport.update({ id: '/about', path: '/about' } as any)
            const ContactRoute = ContactRouteImport.update({ id: '/contact', path: '/contact' } as any)
            export interface FileRoutesByTo {
              '/about': typeof AboutRoute
              '/contact': typeof ContactRoute
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
                '/contact': {
                  id: '/contact'
                  path: '/contact'
                  fullPath: '/contact'
                  preLoaderRoute: typeof ContactRouteImport
                  parentRoute: typeof rootRouteImport
                }
              }
            }
            """.trimIndent()
            vf.setBinaryContent(updated.toByteArray(Charsets.UTF_8))
        }

        // Without waiting for async VFS notify: synchronous rebuild must see new path
        val rebuilt = service.rebuildSynchronously()
        assertFalse(rebuilt.isEmpty())
        assertNotNull("expected /contact after gen update", service.resolve("/contact"))
        assertTrue(service.resolve("/contact")!!.path.endsWith("contact.tsx"))
    }

    fun testInvalidateNotifiesListeners() {
        val service = RouteIndexService.getInstance(project)
        var notified = 0
        val connection = project.messageBus.connect(testRootDisposable)
        connection.subscribe(
            RouteIndexListener.TOPIC,
            RouteIndexListener { notified++ },
        )

        service.invalidate()
        // Debounce 0 on pooled alarm — pump events briefly
        val deadline = System.currentTimeMillis() + 3_000
        while (notified == 0 && System.currentTimeMillis() < deadline) {
            com.intellij.util.ui.UIUtil.dispatchAllInvocationEvents()
            Thread.sleep(20)
        }
        assertTrue("RouteIndexListener should fire after invalidate()", notified >= 1)
    }
}
