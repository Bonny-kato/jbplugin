package com.ipfsoftwares.jbplugin.documentation

import com.intellij.lang.documentation.AbstractDocumentationProvider
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiManager
import com.ipfsoftwares.jbplugin.navigation.TanStackApiContext
import com.ipfsoftwares.jbplugin.navigation.TanStackLinkContext
import com.ipfsoftwares.jbplugin.navigation.TanStackRouteAnchor
import com.ipfsoftwares.jbplugin.route.RouteInfo
import com.ipfsoftwares.jbplugin.route.RouteInfoResolver
import com.ipfsoftwares.jbplugin.route.RoutePresentation

/**
 * Hover / quick-doc for TanStack `to` paths: path, file, alternates, API form.
 */
class TanStackRouteDocumentationProvider : AbstractDocumentationProvider() {

    override fun generateDoc(element: PsiElement?, originalElement: PsiElement?): String? {
        val info = resolveInfo(originalElement, element) ?: return null
        val api = (originalElement ?: element)?.let { TanStackApiContext.findContext(it)?.displayName }
        return RoutePresentation.documentationHtml(info, apiLabel = api)
    }

    override fun getQuickNavigateInfo(element: PsiElement?, originalElement: PsiElement?): String? {
        val info = resolveInfo(originalElement, element) ?: return null
        val api = (originalElement ?: element)?.let { TanStackApiContext.findContext(it)?.displayName }
        return RoutePresentation.quickNavigateInfo(info, apiLabel = api)
    }

    override fun generateHoverDoc(element: PsiElement, originalElement: PsiElement?): String? =
        generateDoc(element, originalElement)

    override fun getDocumentationElementForLookupItem(
        psiManager: PsiManager?,
        `object`: Any?,
        element: PsiElement?,
    ): PsiElement? = null

    override fun getDocumentationElementForLink(
        psiManager: PsiManager?,
        link: String?,
        context: PsiElement?,
    ): PsiElement? = null

    private fun resolveInfo(original: PsiElement?, resolved: PsiElement?): RouteInfo? {
        val seed = original ?: resolved ?: return null
        val host = TanStackRouteAnchor.findCanonicalHost(seed) ?: seed
        val link = TanStackLinkContext.findLinkTargetAt(host)
            ?: TanStackLinkContext.findLinkTargetAt(seed)
        if (link != null) {
            val base = TanStackLinkContext.findBaseRouteHint(host, link.from)
            return RouteInfoResolver.resolveLink(
                host.project,
                link.to,
                base,
                host.containingFile?.virtualFile,
            )
        }

        // Hover on createFileRoute('/path') argument — show the route module info
        val factoryPath = TanStackApiContext.findCreateFileRoutePathAt(seed)
        if (!factoryPath.isNullOrBlank()) {
            return RouteInfoResolver.resolveLink(
                seed.project,
                factoryPath,
                null,
                seed.containingFile?.virtualFile,
            )
        }

        // Hover when documentation target is the resolved definition element
        if (resolved != null) {
            val vf = resolved.containingFile?.virtualFile ?: return null
            val paths = RouteInfoResolver.pathsForRouteFile(resolved.project, vf)
            val preferred = paths.firstOrNull() ?: return null
            return RouteInfoResolver.resolveLink(
                resolved.project,
                preferred,
                null,
                vf,
            )
        }
        return null
    }
}
