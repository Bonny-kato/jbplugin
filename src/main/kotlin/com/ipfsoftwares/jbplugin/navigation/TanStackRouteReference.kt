package com.ipfsoftwares.jbplugin.navigation

import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReferenceBase
import com.ipfsoftwares.jbplugin.route.RouteDefinitionLocator
import com.ipfsoftwares.jbplugin.route.RouteIndexService

/**
 * Reference from a TanStack `to` string literal to the route definition
 * (`createFileRoute` / lazy factory call inside the route module).
 */
class TanStackRouteReference(
    element: PsiElement,
    rangeInElement: TextRange,
    private val routePath: String,
    private val baseRouteHint: String?,
) : PsiReferenceBase<PsiElement>(element, rangeInElement, false) {

    override fun resolve(): PsiElement? {
        val project = element.project
        val vf = RouteIndexService.getInstance(project)
            .resolveLink(routePath, baseRouteHint, element.containingFile?.virtualFile)
            ?: return null
        return RouteDefinitionLocator.findDefinitionElement(project, vf, preferredPath = routePath)
    }

    override fun getVariants(): Array<Any> {
        val index = RouteIndexService.getInstance(element.project).getIndex()
        return index.allPaths().toTypedArray()
    }

    override fun getCanonicalText(): String = routePath
}
