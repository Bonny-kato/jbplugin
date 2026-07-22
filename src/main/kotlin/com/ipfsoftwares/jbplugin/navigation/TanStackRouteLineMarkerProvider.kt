package com.ipfsoftwares.jbplugin.navigation

import com.intellij.codeInsight.daemon.RelatedItemLineMarkerInfo
import com.intellij.codeInsight.daemon.RelatedItemLineMarkerProvider
import com.intellij.codeInsight.navigation.NavigationGutterIconBuilder
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiManager
import com.ipfsoftwares.jbplugin.TanStackIcons
import com.ipfsoftwares.jbplugin.route.RouteIndexService

/**
 * Single gutter icon per TanStack `to` path that resolves to a route file.
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
        val target = PsiManager.getInstance(project).findFile(vf) ?: return

        val builder = NavigationGutterIconBuilder.create(TanStackIcons.Route)
            .setTargets(listOf(target))
            .setTooltipText("Go to TanStack route: ${link.to} → ${vf.name}")
            .setPopupTitle("TanStack route")

        result.add(builder.createLineMarkerInfo(anchor.gutterLeaf))
    }
}
