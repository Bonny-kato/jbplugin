package com.ipfsoftwares.jbplugin.documentation

import com.intellij.lang.documentation.AbstractDocumentationProvider
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.ipfsoftwares.jbplugin.navigation.TanStackApiContext
import com.ipfsoftwares.jbplugin.navigation.TanStackLinkContext
import com.ipfsoftwares.jbplugin.navigation.TanStackRouteAnchor
import com.ipfsoftwares.jbplugin.route.PhysicalRoute
import com.ipfsoftwares.jbplugin.route.RouteDocFacts
import com.ipfsoftwares.jbplugin.route.RouteDocModel
import com.ipfsoftwares.jbplugin.route.RouteInfo
import com.ipfsoftwares.jbplugin.route.RouteInfoResolver
import com.ipfsoftwares.jbplugin.route.RoutePresentation
import com.ipfsoftwares.jbplugin.route.RouteUsageFinder

/**
 * Quick documentation / Ctrl+hover for TanStack route `to` paths and definitions.
 *
 * Hover is compact (path, file, kind, usages, parent/params/children).
 * Full doc (Ctrl+Q) adds route id details, absolute path, and route tree source.
 */
class TanStackRouteDocumentationProvider : AbstractDocumentationProvider() {

    override fun generateHoverDoc(element: PsiElement, originalElement: PsiElement?): String? {
        return buildHtml(element, originalElement, compact = true)
    }

    override fun generateDoc(element: PsiElement?, originalElement: PsiElement?): String? {
        if (element == null) return null
        return buildHtml(element, originalElement, compact = false)
    }

    override fun getQuickNavigateInfo(element: PsiElement?, originalElement: PsiElement?): String? {
        val info = resolveInfo(originalElement, element) ?: return null
        val project = (originalElement ?: element)?.project ?: return null
        val api = originalElement?.let { TanStackApiContext.findContext(it)?.displayName }
            ?: element?.let { TanStackApiContext.findContext(it)?.displayName }
        val model = enrich(project, info, apiLabel = api, withUsages = false)
        return RoutePresentation.quickNavigateInfo(
            resolvedPath = model.publicPath,
            fileHint = model.fileHint,
            apiLabel = model.apiLabel,
            kinds = model.kinds,
            usageCount = null,
        )
    }

    private fun buildHtml(
        element: PsiElement,
        originalElement: PsiElement?,
        compact: Boolean,
    ): String? {
        val info = resolveInfo(originalElement, element) ?: return null
        val project = (originalElement ?: element).project
        val api = originalElement?.let { TanStackApiContext.findContext(it)?.displayName }
            ?: TanStackApiContext.findContext(element)?.displayName
        val model = enrich(project, info, apiLabel = api, withUsages = true)
        return RoutePresentation.documentationHtml(model, compact = compact)
    }

    private fun enrich(
        project: Project,
        info: RouteInfo,
        apiLabel: String?,
        withUsages: Boolean,
    ): RouteDocModel {
        val fileHint = RouteInfoResolver.projectRelativeFile(project, info.file)
        val publicPath = RouteDocFacts.publicPath(info.resolvedPath, info.alternatePaths)

        val parentPair = RouteInfoResolver.parentRoute(project, publicPath, info.file)
            ?: RouteInfoResolver.parentRoute(project, info.resolvedPath, info.file)

        val childPaths = RouteInfoResolver.childRoutes(project, publicPath, info.file).ifEmpty {
            RouteInfoResolver.childRoutes(project, info.resolvedPath, info.file)
        }

        val usageCount = if (withUsages) {
            val paths = (listOf(info.resolvedPath, info.requestedPath, publicPath) + info.alternatePaths)
                .filter { it.isNotBlank() }
                .distinct()
            RouteUsageFinder.countUsages(project, paths)
        } else {
            null
        }

        return RoutePresentation.modelFrom(
            info = info,
            fileHint = fileHint,
            parentPath = parentPair?.first,
            parentFileHint = parentPair?.second,
            childPaths = childPaths,
            usageCount = usageCount,
            apiLabel = apiLabel,
            fullFilePath = info.file.path,
        )
    }

    private fun resolveInfo(original: PsiElement?, resolved: PsiElement?): RouteInfo? {
        val seed = original ?: resolved ?: return null

        val host = TanStackRouteAnchor.findCanonicalHost(seed) ?: seed
        val link = TanStackLinkContext.findLinkTargetAt(host)
            ?: TanStackLinkContext.findLinkTargetAt(seed)
        if (link != null) {
            val base = TanStackLinkContext.findBaseRouteHint(seed, link.from)
            return RouteInfoResolver.resolveLink(
                seed.project,
                link.to,
                base,
                seed.containingFile?.virtualFile,
            )
        }

        val factoryPath = TanStackApiContext.findCreateFileRoutePathAt(seed)
        if (!factoryPath.isNullOrBlank()) {
            return RouteInfoResolver.resolveLink(
                seed.project,
                factoryPath,
                null,
                seed.containingFile?.virtualFile,
            )
        }

        if (resolved != null) {
            val vf = resolved.containingFile?.virtualFile ?: return null
            val paths = RouteInfoResolver.pathsForRouteFile(resolved.project, vf)
            if (paths.isEmpty()) return null
            val preferred = PhysicalRoute.pickPreferredPath(paths)
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
