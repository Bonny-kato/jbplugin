package com.ipfsoftwares.jbplugin.navigation

import com.intellij.codeInsight.navigation.actions.GotoDeclarationHandler
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.ipfsoftwares.jbplugin.route.RouteDefinitionLocator
import com.ipfsoftwares.jbplugin.route.RouteIndexService

/**
 * Cmd/Ctrl-click handler for TanStack `to` route paths → physical route definition.
 *
 * Lands on `createFileRoute(...)` (or lazy/code-based factory) inside the route module,
 * not a random line at the top of the file. TypeScript may still add its own type-map
 * entries in the popup; pick the route `.tsx` or use the gutter icon.
 */
class TanStackGotoDeclarationHandler : GotoDeclarationHandler {

    override fun getGotoDeclarationTargets(
        sourceElement: PsiElement?,
        offset: Int,
        editor: Editor?,
    ): Array<PsiElement>? {
        if (sourceElement == null) return null

        // Prefer resolving from the canonical host so we don't emit multiple identical targets
        // when the caret sits on nested leaves of the same `to` expression.
        val host = TanStackRouteAnchor.findCanonicalHost(sourceElement) ?: sourceElement
        val project = sourceElement.project
        val target = resolveFromElement(project, host)
            ?: resolveFromElement(project, sourceElement)
            ?: return null
        // Single unique target (file + offset) — TS may still add its own type entries separately.
        return arrayOf(target)
    }

    override fun getActionText(context: DataContext): String = "Go to TanStack Route"

    companion object {
        fun resolveFromElement(project: Project, sourceElement: PsiElement): PsiElement? {
            val link = TanStackLinkContext.findLinkTargetAt(sourceElement) ?: return null
            val baseHint = TanStackLinkContext.findBaseRouteHint(sourceElement, link.from)
            return resolveRoute(project, link.to, baseHint, sourceElement)
        }

        fun resolveRoute(
            project: Project,
            path: String,
            baseRouteHint: String?,
            sourceElement: PsiElement,
        ): PsiElement? {
            val vf = RouteIndexService.getInstance(project)
                .resolveLink(path, baseRouteHint, sourceElement.containingFile?.virtualFile)
                ?: return null
            // Prefer the factory call site so caret lands on createFileRoute(...).
            return RouteDefinitionLocator.findDefinitionElement(project, vf, preferredPath = path)
        }
    }
}
