package com.ipfsoftwares.jbplugin.navigation

import com.intellij.codeInsight.daemon.RelatedItemLineMarkerInfo
import com.intellij.codeInsight.daemon.RelatedItemLineMarkerProvider
import com.intellij.codeInsight.navigation.NavigationGutterIconBuilder
import com.intellij.icons.AllIcons
import com.intellij.openapi.util.Key
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.ipfsoftwares.jbplugin.route.RouteDefinitionLocator
import com.ipfsoftwares.jbplugin.route.RouteIndexService

/**
 * Single gutter icon per TanStack `to` path that resolves to the route definition
 * (`createFileRoute` call). Uses the platform world icon (not the plugin logo).
 *
 * Dedupes across multi-language line-marker registrations (JS/TS/TSX) so the
 * navigation popup never lists the same route twice.
 */
class TanStackRouteLineMarkerProvider : RelatedItemLineMarkerProvider() {

    override fun collectNavigationMarkers(
        element: PsiElement,
        result: MutableCollection<in RelatedItemLineMarkerInfo<*>>,
    ) {
        val anchor = TanStackRouteAnchor.findUniqueGutterAnchor(element) ?: return
        val link = anchor.link

        val file = element.containingFile ?: return
        if (!claimGutterSlot(file, anchor.gutterLeaf.textOffset)) return

        val project = element.project
        val baseHint = TanStackLinkContext.findBaseRouteHint(element, link.from)
        val vf = RouteIndexService.getInstance(project)
            .resolveLink(link.to, baseHint, element.containingFile?.virtualFile)
            ?: return
        val target = RouteDefinitionLocator.findDefinitionElement(project, vf, preferredPath = link.to)
            ?: return

        // One navigation target identity (file + offset) — prevents duplicate popup rows
        // if the builder merges with another marker on the same leaf.
        val uniqueTargets = listOf(target).distinctBy {
            (it.containingFile?.virtualFile?.path ?: it.containingFile?.name.orEmpty()) to it.textOffset
        }

        val api = TanStackApiContext.findContext(element)?.displayName
        val via = api?.let { " via $it" }.orEmpty()
        val builder = NavigationGutterIconBuilder.create(AllIcons.Nodes.PpWeb)
            .setTargets(uniqueTargets)
            .setTooltipText("Go to TanStack route$via: ${link.to} → ${vf.name}")
            .setPopupTitle("TanStack route")

        result.add(builder.createLineMarkerInfo(anchor.gutterLeaf))
    }

    companion object {
        private data class GutterPass(val stamp: Long, val offsets: MutableSet<Int>)

        private val PASS_KEY = Key.create<GutterPass>("tanstack.gutter.pass")

        /**
         * Claim this caret offset for the current file modification stamp.
         * Returns false if another language provider already created a marker here.
         */
        private fun claimGutterSlot(file: PsiFile, offset: Int): Boolean {
            val stamp = file.modificationStamp
            var pass = file.getUserData(PASS_KEY)
            if (pass == null || pass.stamp != stamp) {
                pass = GutterPass(stamp, HashSet())
                file.putUserData(PASS_KEY, pass)
            }
            return pass.offsets.add(offset)
        }
    }
}
