package com.ipfsoftwares.jbplugin.navigation

import com.intellij.codeInsight.daemon.RelatedItemLineMarkerInfo
import com.intellij.codeInsight.daemon.RelatedItemLineMarkerProvider
import com.intellij.codeInsight.navigation.NavigationGutterIconBuilder
import com.intellij.icons.AllIcons
import com.intellij.psi.PsiElement
import com.ipfsoftwares.jbplugin.route.RouteDefinitionLocator
import com.ipfsoftwares.jbplugin.route.RouteIndexService

/**
 * Single gutter icon per TanStack `to` path that resolves to the route definition
 * (`createFileRoute` call). Uses the platform world icon (not the plugin logo).
 */
class TanStackRouteLineMarkerProvider : RelatedItemLineMarkerProvider() {

    override fun collectNavigationMarkers(
        element: PsiElement,
        result: MutableCollection<in RelatedItemLineMarkerInfo<*>>,
    ) {
        val anchor = TanStackRouteAnchor.findUniqueGutterAnchor(element) ?: return
        val link = anchor.link

        val project = element.project
        val baseHint = TanStackLinkContext.findBaseRouteHint(element, link.from)
        val vf = RouteIndexService.getInstance(project)
            .resolveLink(link.to, baseHint, element.containingFile?.virtualFile)
            ?: return
        val target = RouteDefinitionLocator.findDefinitionElement(project, vf, preferredPath = link.to)
            ?: return

        val builder = NavigationGutterIconBuilder.create(AllIcons.Nodes.PpWeb)
            .setTargets(listOf(target))
            .setTooltipText("Go to TanStack route: ${link.to} → ${vf.name}")
            .setPopupTitle("TanStack route")

        result.add(builder.createLineMarkerInfo(anchor.gutterLeaf))
    }
}
