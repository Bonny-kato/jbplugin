package com.ipfsoftwares.jbplugin.navigation

import com.intellij.codeInsight.daemon.GutterIconNavigationHandler
import com.intellij.codeInsight.daemon.MergeableLineMarkerInfo
import com.intellij.codeInsight.daemon.RelatedItemLineMarkerInfo
import com.intellij.codeInsight.daemon.RelatedItemLineMarkerProvider
import com.intellij.icons.AllIcons
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiManager
import com.intellij.util.Function
import com.ipfsoftwares.jbplugin.route.RouteDefinitionLocator
import com.ipfsoftwares.jbplugin.route.RouteIndexService
import java.awt.event.MouseEvent
import javax.swing.Icon

/**
 * Single gutter icon per TanStack `to` path that navigates to the route definition
 * (`createFileRoute` call). Uses the platform world icon (not the plugin logo).
 *
 * Important: the provider must be registered **once** for language=JavaScript.
 * [com.intellij.codeInsight.daemon.impl.LineMarkersPass] collects providers via
 * `allForLanguageOrAny`, which walks every base dialect (TSX → TS → JS, etc.).
 * Per-dialect registrations produced multiple mergeable markers on the same leaf;
 * the IDE merged the icons but the click popup listed the route N times.
 */
class TanStackRouteLineMarkerProvider : RelatedItemLineMarkerProvider() {

    override fun collectNavigationMarkers(
        element: PsiElement,
        result: MutableCollection<in RelatedItemLineMarkerInfo<*>>,
    ) {
        // Platform requires line markers on leaf elements.
        if (element.firstChild != null) return

        val anchor = TanStackRouteAnchor.findUniqueGutterAnchor(element) ?: return
        if (element != anchor.gutterLeaf) return

        val link = anchor.link
        val project = element.project
        val containingFile = element.containingFile ?: return
        val vFile = containingFile.virtualFile
        val baseHint = TanStackLinkContext.findBaseRouteHint(element, link.from)

        val resolvedFile = RouteIndexService.getInstance(project)
            .resolveLink(link.to, baseHint, vFile)
            ?: return

        val api = TanStackApiContext.findContext(element)?.displayName
        val via = api?.let { " via $it" }.orEmpty()
        val path = link.to
        val fileName = resolvedFile.name
        val resolvedPath = resolvedFile.path
        val leaf = anchor.gutterLeaf
        val tooltip = "Go to TanStack route$via: $path → $fileName"

        val marker = TanStackRouteGutterMarker(
            element = leaf,
            tooltip = tooltip,
            routePath = path,
            resolvedFilePath = resolvedPath,
            project = project,
        )
        result.add(marker)
    }

    /**
     * Mergeable marker that always navigates to exactly one route definition.
     * Even if another provider instance somehow merges with us, [canMergeWith]
     * collapses duplicates and navigation never opens a multi-target chooser.
     */
    private class TanStackRouteGutterMarker(
        element: PsiElement,
        private val tooltip: String,
        private val routePath: String,
        private val resolvedFilePath: String,
        private val project: Project,
    ) : RelatedItemLineMarkerInfo<PsiElement>(
        element,
        element.textRange,
        ICON,
        Function { tooltip },
        GutterIconNavigationHandler { _: MouseEvent?, _: PsiElement? ->
            navigateToRoute(project, resolvedFilePath, routePath)
        },
        GutterIconRenderer.Alignment.LEFT,
        { emptyList() },
    ) {
        override fun canMergeWith(info: MergeableLineMarkerInfo<*>): Boolean {
            val other = info as? TanStackRouteGutterMarker ?: return false
            // Same leaf + same resolved route → treat as identical marker.
            return element?.textOffset == other.element?.textOffset &&
                resolvedFilePath == other.resolvedFilePath &&
                routePath == other.routePath
        }

        override fun getCommonIcon(infos: List<MergeableLineMarkerInfo<*>>): Icon = ICON

        override fun getCommonTooltip(
            infos: List<MergeableLineMarkerInfo<*>>,
        ): Function<in PsiElement, String> = Function { tooltip }
    }

    companion object {
        private val ICON: Icon = AllIcons.Nodes.PpWeb

        private fun navigateToRoute(project: Project, filePath: String, routePath: String) {
            if (project.isDisposed) return
            val vf: VirtualFile = LocalFileSystem.getInstance().findFileByPath(filePath)
                ?: return
            val target = RouteDefinitionLocator.findDefinitionElement(
                project,
                vf,
                preferredPath = routePath,
            )
            if (target != null && target.isValid) {
                val nav = target as? com.intellij.pom.Navigatable
                if (nav != null && nav.canNavigate()) {
                    nav.navigate(true)
                    return
                }
                val file = target.containingFile?.virtualFile ?: vf
                OpenFileDescriptor(project, file, target.textOffset).navigate(true)
                return
            }
            // Fallback: open file (prefer factory offset via locator already failed)
            val psi = PsiManager.getInstance(project).findFile(vf)
            if (psi is com.intellij.pom.Navigatable && psi.canNavigate()) {
                psi.navigate(true)
            } else {
                OpenFileDescriptor(project, vf).navigate(true)
            }
        }
    }
}
