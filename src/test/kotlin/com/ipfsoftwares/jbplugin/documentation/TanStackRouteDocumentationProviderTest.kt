package com.ipfsoftwares.jbplugin.documentation

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.ipfsoftwares.jbplugin.route.RouteIndexService

class TanStackRouteDocumentationProviderTest : BasePlatformTestCase() {

    override fun setUp() {
        super.setUp()
        myFixture.addFileToProject("src/routes/__root.tsx", "export const Route = {}\n")
        myFixture.addFileToProject("src/routes/about.tsx", """
            import { createFileRoute } from '@tanstack/react-router'
            
            export const Route = createFileRoute('/about')({
              component: AboutComponent
            })
            
            export function AboutComponent({ changeValue }: { changeValue: () => void }) {
               return <div onClick={() => changeValue()}>About</div>
            }
        """.trimIndent())
        
        myFixture.addFileToProject("src/routeTree.gen.ts", """
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
        """.trimIndent())
        val service = RouteIndexService.getInstance(project)
        val index = service.rebuildSynchronously()
        // println("Index paths: ${index.allPaths()}")
    }

    fun testNoDocumentationOnArbitraryProp() {
        myFixture.configureByText("test.tsx", """
            import { AboutComponent } from './routes/about'
            
            export function Test() {
              return <AboutComponent changeValue={() => {}} />
            }
        """.trimIndent())
        
        val offset = myFixture.editor.caretModel.offset // not set yet
        val element = myFixture.file.findElementAt(myFixture.file.text.indexOf("changeValue"))!!
        
        val provider = TanStackRouteDocumentationProvider()
        // Simulate what the IDE does: it resolves the reference first
        val resolved = element.reference?.resolve() ?: element
        
        val doc = provider.generateHoverDoc(resolved, element)
        
        assertNull("Documentation should NOT be shown for 'changeValue' prop", doc)
    }

    fun testDocumentationOnRouteFactory() {
        myFixture.configureByFile("src/routes/about.tsx")
        val offset = myFixture.file.text.indexOf("createFileRoute('/about')")
        val element = myFixture.file.findElementAt(offset)!!
        
        val provider = TanStackRouteDocumentationProvider()
        val doc = provider.generateHoverDoc(element, element)
        
        assertNotNull("Documentation SHOULD be shown for 'createFileRoute'", doc)
        assertTrue("Doc should contain route path /about", doc!!.contains("/about"))
    }

    fun testDocumentationOnRouteExport() {
        myFixture.configureByFile("src/routes/about.tsx")
        val offset = myFixture.file.text.indexOf("Route =")
        val element = myFixture.file.findElementAt(offset)!!
        
        val provider = TanStackRouteDocumentationProvider()
        val doc = provider.generateHoverDoc(element, element)
        
        assertNotNull("Documentation SHOULD be shown for 'Route' export. Element text: '${element.text}'", doc)
        assertTrue("Doc should contain route path /about", doc!!.contains("/about"))
    }

    fun testDocumentationOnLinkTo() {
        myFixture.configureByText("test_link.tsx", """
            import { Link } from '@tanstack/react-router'
            export const T = () => <Link to="/about" />
        """.trimIndent())
        val element = myFixture.file.findElementAt(myFixture.file.text.indexOf("/about"))!!
        
        val provider = TanStackRouteDocumentationProvider()
        val doc = provider.generateHoverDoc(element, element)
        
        assertNotNull("Documentation SHOULD be shown for Link 'to' path", doc)
        assertTrue("Doc should contain route path /about", doc!!.contains("/about"))
    }
}
