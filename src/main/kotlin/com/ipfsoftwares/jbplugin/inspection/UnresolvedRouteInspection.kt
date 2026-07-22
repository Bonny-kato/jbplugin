package com.ipfsoftwares.jbplugin.inspection

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiFile
import com.ipfsoftwares.jbplugin.JbPluginBundle
import com.ipfsoftwares.jbplugin.navigation.TanStackApiContext
import com.ipfsoftwares.jbplugin.navigation.TanStackLinkContext
import com.ipfsoftwares.jbplugin.navigation.TanStackRouteAnchor
import com.ipfsoftwares.jbplugin.route.RouteIndex
import com.ipfsoftwares.jbplugin.route.RouteIndexService
import com.ipfsoftwares.jbplugin.route.RouteInfoResolver

/**
 * Warns when a TanStack `to` path does not resolve via `routeTree.gen.ts`.
 */
class UnresolvedRouteInspection : LocalInspectionTool() {

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        // One problem per unique text range even if multiple language inspections run.
        val reportedRanges = hashSetOf<Pair<String, IntRange>>()

        return object : PsiElementVisitor() {
            override fun visitElement(element: PsiElement) {
                if (element is PsiFile) return

                // Only the canonical host — never nested leaves/quotes/braces of the same `to`.
                val host = TanStackRouteAnchor.findCanonicalHost(element) ?: return
                if (host != element) return
                if (!TanStackRouteAnchor.isReferenceHost(element)) return

                val link = TanStackLinkContext.findLinkTargetAt(element) ?: return
                val project = element.project
                val index = RouteIndexService.getInstance(project).getIndex()
                if (index.isEmpty()) return

                val api = TanStackApiContext.findContext(element)
                if (api?.kind == TanStackApiContext.Kind.CREATE_FILE_ROUTE) return

                val path = link.to
                val baseHint = TanStackLinkContext.findBaseRouteHint(element, link.from)
                if (RouteIndex.isRelativePath(path) && baseHint.isNullOrBlank()) {
                    return
                }

                val resolved = RouteInfoResolver.isResolved(
                    project,
                    path,
                    baseHint,
                    element.containingFile?.virtualFile,
                )
                if (resolved != false) return

                val filePath = element.containingFile?.virtualFile?.path ?: element.containingFile?.name.orEmpty()
                val range = element.textRange
                val key = filePath to (range.startOffset until range.endOffset)
                if (!reportedRanges.add(key)) return

                val message = JbPluginBundle.message("inspection.unresolved.route", path)
                holder.registerProblem(element, message)
            }
        }
    }
}
