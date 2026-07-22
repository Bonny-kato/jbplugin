package com.ipfsoftwares.jbplugin.navigation

import com.intellij.codeInsight.daemon.RelatedItemLineMarkerInfo
import com.intellij.codeInsight.daemon.RelatedItemLineMarkerProvider
import com.intellij.codeInsight.navigation.NavigationGutterIconBuilder
import com.intellij.icons.AllIcons
import com.intellij.openapi.util.NotNullLazyValue
import com.intellij.psi.PsiElement
import com.ipfsoftwares.jbplugin.route.RouteDefinitionLocator
import com.ipfsoftwares.jbplugin.route.RouteIndexService

/**
 * Single gutter icon per TanStack `to` path that resolves to the route definition
 * (`createFileRoute` call). Uses the platform world icon (not the plugin logo).
 *
 * Stability notes:
 * - Markers attach only to a stable leaf PSI element (platform requirement).
 * - Targets are resolved lazily so hover/click does not pin stale PSI.
 * - No cross-pass "claim" cache: those survived re-highlights with the same
 *   modification stamp and caused the icon to disappear after hover/click.
 * - Anchor logic guarantees one marker per `to` value even when multiple
 *   JS/TS dialects are registered.
 */
class TanStackRouteLineMarkerProvider : RelatedItemLineMarkerProvider() {

    override fun collectNavigationMarkers(
        element: PsiElement,
        result: MutableCollection<in RelatedItemLineMarkerInfo<*>>,
    ) {
        // Platform requires line markers on leaf elements; non-leaves are dropped
        // during daemon validation (looks like a "disappearing" gutter icon).
        if (element.firstChild != null) return

        val anchor = TanStackRouteAnchor.findUniqueGutterAnchor(element) ?: return
        if (element != anchor.gutterLeaf) return

        val link = anchor.link
        val project = element.project
        val containingFile = element.containingFile ?: return
        val vFile = containingFile.virtualFile
        val baseHint = TanStackLinkContext.findBaseRouteHint(element, link.from)

        // Only show the icon when the route resolves *now*. Navigation still
        // re-resolves lazily so the target stays valid after PSI refreshes.
        val resolvedFile = RouteIndexService.getInstance(project)
            .resolveLink(link.to, baseHint, vFile)
            ?: return

        val api = TanStackApiContext.findContext(element)?.displayName
        val via = api?.let { " via $it" }.orEmpty()
        val path = link.to
        val fileName = resolvedFile.name
        val resolvedPath = resolvedFile.path

        val builder = NavigationGutterIconBuilder.create(AllIcons.Nodes.PpWeb)
            .setTargets(
                NotNullLazyValue.createValue {
                    val vf = com.intellij.openapi.vfs.LocalFileSystem.getInstance()
                        .findFileByPath(resolvedPath)
                        ?: resolvedFile.takeIf { it.isValid }
                        ?: return@createValue emptyList()
                    val target = RouteDefinitionLocator.findDefinitionElement(
                        project,
                        vf,
                        preferredPath = path,
                    ) ?: return@createValue emptyList()
                    // Exactly one navigation target identity.
                    listOf(target)
                },
            )
            .setTooltipText("Go to TanStack route$via: $path → $fileName")
            .setPopupTitle("TanStack route")
            .setEmptyPopupText("Route file not found")

        result.add(builder.createLineMarkerInfo(anchor.gutterLeaf))
    }
}
