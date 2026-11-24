package com.ipfsoftwares.jbplugin.navigation

import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReferenceBase
import com.ipfsoftwares.jbplugin.route.RouteDefinitionLocator
import com.ipfsoftwares.jbplugin.route.RouteIndexService

/**
 * Reference from a TanStack `to` string literal to the route definition
 * (`createFileRoute` / lazy factory call inside the route module).
 *
 * Soft reference: unresolved highlighting is owned by [com.ipfsoftwares.jbplugin.inspection.UnresolvedRouteInspection].
 */
class TanStackRouteReference(
    element: PsiElement,
    rangeInElement: TextRange,
    private val routePath: String,
    private val baseRouteHint: String?,
) : PsiReferenceBase<PsiElement>(element, rangeInElement, /* soft = */ true) {

    override fun resolve(): PsiElement? {
        val project = element.project
        val vf = RouteIndexService.getInstance(project)
            .resolveLink(routePath, baseRouteHint, element.containingFile?.virtualFile)
            ?: return null
        // Single definition element — avoids multi-target popups from repeated resolves.
        return RouteDefinitionLocator.findDefinitionElement(project, vf, preferredPath = routePath)
    }

    override fun isReferenceTo(element: PsiElement): Boolean {
        val resolved = resolve() ?: return false
        if (resolved == element) return true
        return resolved.containingFile == element.containingFile &&
            resolved.textOffset == element.textOffset
    }

    override fun getCanonicalText(): String = routePath
}
