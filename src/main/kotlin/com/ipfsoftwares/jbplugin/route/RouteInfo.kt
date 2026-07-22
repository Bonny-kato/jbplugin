package com.ipfsoftwares.jbplugin.route

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile

/**
 * Resolved metadata for a TanStack route path — used by docs, completion, and inspections.
 */
data class RouteInfo(
    val requestedPath: String,
    val resolvedPath: String,
    val file: VirtualFile,
    val alternatePaths: List<String> = emptyList(),
    val sourceTree: VirtualFile? = null,
) {
    val fileName: String get() = file.name
    val relativeHint: String
        get() = file.parent?.name?.let { "$it/${file.name}" } ?: file.name
}

object RouteInfoResolver {

    /**
     * Resolve a link `to` (absolute or relative) to [RouteInfo], or null if unknown.
     */
    fun resolveLink(
        project: Project,
        to: String,
        baseRouteHint: String? = null,
        contextFile: VirtualFile? = null,
    ): RouteInfo? {
        val service = RouteIndexService.getInstance(project)
        val index = service.getIndex()
        if (index.isEmpty()) return null

        val vf = service.resolveLink(to, baseRouteHint, contextFile) ?: return null
        val physical = index.physicalRoutes().firstOrNull { it.file == vf }
        val resolvedPath = physical?.path ?: run {
            val joined = if (RouteIndex.isRelativePath(to.trim())) {
                val base = index.fullPathFor(baseRouteHint) ?: baseRouteHint
                if (base != null) RouteIndex.joinRoutePaths(base, to.trim()) else to
            } else {
                to
            }
            normalizeForDisplay(joined)
        }
        val tree = index.sourceTrees.minByOrNull { distanceScore(contextFile ?: vf, it) }
        return RouteInfo(
            requestedPath = to,
            resolvedPath = resolvedPath,
            file = vf,
            alternatePaths = physical?.alternatePaths.orEmpty(),
            sourceTree = tree,
        )
    }

    /**
     * Whether [to] can be resolved with the current index.
     * Relative paths without a base are treated as *not inspectable* (return null),
     * distinct from unresolved (false).
     */
    fun isResolved(
        project: Project,
        to: String,
        baseRouteHint: String? = null,
        contextFile: VirtualFile? = null,
    ): Boolean? {
        val trimmed = to.trim()
        if (trimmed.isEmpty()) return null
        val index = RouteIndexService.getInstance(project).getIndex()
        if (index.isEmpty()) return null

        if (RouteIndex.isRelativePath(trimmed)) {
            val base = baseRouteHint?.takeIf { it.isNotBlank() }
            if (base == null) return null // can't judge
        }
        return resolveLink(project, trimmed, baseRouteHint, contextFile) != null
    }

    fun completionCandidates(project: Project, contextFile: VirtualFile? = null): List<RouteInfo> {
        val index = RouteIndexService.getInstance(project).getIndex()
        if (index.isEmpty()) return emptyList()
        return index.physicalRoutes().map { route ->
            RouteInfo(
                requestedPath = route.path,
                resolvedPath = route.path,
                file = route.file,
                alternatePaths = route.alternatePaths,
                sourceTree = index.sourceTrees.minByOrNull { distanceScore(contextFile ?: route.file, it) },
            )
        }
    }

    fun pathsForRouteFile(project: Project, file: VirtualFile): List<String> {
        val index = RouteIndexService.getInstance(project).getIndex()
        val physical = index.physicalRoutes().firstOrNull { it.file == file } ?: return emptyList()
        return physical.allPaths
    }

    private fun normalizeForDisplay(path: String): String {
        val n = normalizeRoutePath(path)
        return if (n.length > 1 && n.endsWith('/')) n.trimEnd('/') else n
    }

    private fun distanceScore(from: VirtualFile, to: VirtualFile): Int {
        val a = from.path
        val b = to.path
        val max = minOf(a.length, b.length)
        var i = 0
        while (i < max && a[i] == b[i]) i++
        return (a.length - i) + (b.length - i)
    }
}

/**
 * Pure helpers for docs / completion presentation (unit-testable).
 */
object RoutePresentation {

    fun lookupTypeText(info: RouteInfo): String = info.fileName

    fun lookupTypeText(fileName: String): String = fileName

    fun lookupTailText(info: RouteInfo): String {
        val parent = info.file.parent?.name ?: return ""
        return lookupTailText(parent)
    }

    fun lookupTailText(parentName: String?): String {
        if (parentName.isNullOrBlank()) return ""
        return "  $parentName/"
    }

    fun documentationHtml(
        info: RouteInfo,
        apiLabel: String? = null,
        usageCount: Int? = null,
    ): String = documentationHtml(
        resolvedPath = info.resolvedPath,
        requestedPath = info.requestedPath,
        fileHint = info.relativeHint,
        fullPath = info.file.path,
        alternatePaths = info.alternatePaths,
        sourceTreeName = info.sourceTree?.name,
        apiLabel = apiLabel,
        usageCount = usageCount,
    )

    fun documentationHtml(
        resolvedPath: String,
        requestedPath: String = resolvedPath,
        fileHint: String,
        fullPath: String,
        alternatePaths: List<String> = emptyList(),
        sourceTreeName: String? = null,
        apiLabel: String? = null,
        usageCount: Int? = null,
    ): String {
        val path = escape(resolvedPath)
        val file = escape(fileHint)
        val full = escape(fullPath)
        val api = apiLabel?.let { escape(it) }
        val alts = alternatePaths.filter { it != resolvedPath }.take(6)

        return buildString {
            append("<div class='definition'>")
            append("<b>TanStack route</b>")
            if (api != null) append(" <span style='color:gray'>(via ").append(api).append(")</span>")
            append("</div>")
            append("<table class='sections'>")
            row("Path", "<code>$path</code>")
            if (requestedPath != resolvedPath) {
                row("Requested", "<code>${escape(requestedPath)}</code>")
            }
            row("File", "<code>$file</code>")
            row("Full path", "<code>$full</code>")
            if (alts.isNotEmpty()) {
                row(
                    "Also known as",
                    alts.joinToString("<br/>") { "<code>${escape(it)}</code>" },
                )
            }
            if (usageCount != null) {
                row("Usages", usageCount.toString())
            }
            if (!sourceTreeName.isNullOrBlank()) {
                row("Route tree", "<code>${escape(sourceTreeName)}</code>")
            }
            append("</table>")
        }
    }

    fun quickNavigateInfo(info: RouteInfo, apiLabel: String? = null): String =
        quickNavigateInfo(info.resolvedPath, info.relativeHint, apiLabel)

    fun quickNavigateInfo(resolvedPath: String, fileHint: String, apiLabel: String? = null): String {
        val via = apiLabel?.let { " ($it)" }.orEmpty()
        return "TanStack route$via: $resolvedPath → $fileHint"
    }

    private fun StringBuilder.row(title: String, value: String) {
        append("<tr><td valign='top' class='section'><p>")
        append(escape(title))
        append("</p></td><td valign='top'>")
        append(value)
        append("</td></tr>")
    }

    private fun escape(s: String): String =
        s.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
}
