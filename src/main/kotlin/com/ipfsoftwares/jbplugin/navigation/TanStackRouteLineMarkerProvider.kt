package com.ipfsoftwares.jbplugin.navigation

import com.intellij.codeInsight.daemon.RelatedItemLineMarkerInfo
import com.intellij.codeInsight.daemon.RelatedItemLineMarkerProvider
import com.intellij.codeInsight.navigation.NavigationGutterIconBuilder
import com.intellij.icons.AllIcons
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiManager
import com.intellij.psi.xml.XmlAttributeValue
import com.ipfsoftwares.jbplugin.route.RouteIndexService

/**
 * Gutter icon on TanStack `to` string literals that resolve to a route file.
 */
class TanStackRouteLineMarkerProvider : RelatedItemLineMarkerProvider() {

    override fun collectNavigationMarkers(
        element: PsiElement,
        result: MutableCollection<in RelatedItemLineMarkerInfo<*>>,
    ) {
        // Only anchor on leaf elements to avoid duplicate markers
        if (element.firstChild != null) return
        val parent = element.parent ?: return

        val link = TanStackLinkContext.findLinkTargetAt(parent)
            ?: TanStackLinkContext.findLinkTargetAt(element)
            ?: return

        // Deduplicate: only mark leaves that sit inside the path text / attr value
        val parentText = parent.text
        val looksLikeLiteral =
            TanStackLinkContext.coerceRoutePath(parentText) != null ||
                TanStackLinkContext.extractQuoted(parentText) != null ||
                TanStackLinkContext.coerceRoutePath(element.text) != null ||
                parent is XmlAttributeValue ||
                element.parent is XmlAttributeValue
        if (!looksLikeLiteral) return

        // Ensure this leaf is part of the path characters when possible
        if (link.to.isNotEmpty() &&
            !parentText.contains(link.to) &&
            !element.text.contains(link.to) &&
            parent !is XmlAttributeValue
        ) {
            return
        }

        val project = element.project
        val baseHint = TanStackLinkContext.findBaseRouteHint(element, link.from)
        val vf = RouteIndexService.getInstance(project)
            .resolveLink(link.to, baseHint, element.containingFile?.virtualFile)
            ?: return
        val target = PsiManager.getInstance(project).findFile(vf) ?: return

        val builder = NavigationGutterIconBuilder.create(AllIcons.Nodes.PpWeb)
            .setTargets(target)
            .setTooltipText("Go to TanStack route: ${link.to} → ${vf.name}")

        result.add(builder.createLineMarkerInfo(element))
    }
}
