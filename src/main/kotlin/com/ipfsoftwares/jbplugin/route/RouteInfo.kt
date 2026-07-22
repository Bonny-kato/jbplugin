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

    /**
     * Nearest ancestor path that exists in the index, with a short file hint.
     */
    fun parentRoute(project: Project, path: String, contextFile: VirtualFile? = null): Pair<String, String>? {
        val index = RouteIndexService.getInstance(project).getIndex()
        if (index.isEmpty()) return null
        for (candidate in RouteDocFacts.parentCandidates(path)) {
            val file = index.resolve(candidate) ?: continue
            val display = RouteDocFacts.normalize(candidate)
            val hint = file.parent?.name?.let { "$it/${file.name}" } ?: file.name
            return display to hint
        }
        return null
    }

    /**
     * Preferred paths of physical routes that are direct children of [path].
     */
    fun childRoutes(project: Project, path: String, contextFile: VirtualFile? = null): List<String> {
        val index = RouteIndexService.getInstance(project).getIndex()
        if (index.isEmpty()) return emptyList()
        val parent = RouteDocFacts.normalize(path)
        return index.physicalRoutes()
            .map { it.path }
            .filter { RouteDocFacts.isDirectChild(parent, it) }
            .distinct()
            .sorted()
    }

    fun projectRelativeFile(project: Project, file: VirtualFile): String {
        val fallback = file.parent?.name?.let { "$it/${file.name}" } ?: file.name
        return RouteDocFacts.projectRelativePath(project.basePath, file.path, fallback)
    }
}


/**
 * Snapshot of everything quick-doc / hover needs to render.
 */
data class RouteDocModel(
    val publicPath: String,
    val requestedPath: String? = null,
    val fileHint: String,
    val kinds: List<String> = emptyList(),
    val params: List<String> = emptyList(),
    val parentPath: String? = null,
    val parentFileHint: String? = null,
    val childPaths: List<String> = emptyList(),
    val usageCount: Int? = null,
    val routeId: String? = null,
    val apiLabel: String? = null,
    val sourceTreeName: String? = null,
    val fullFilePath: String? = null,
)

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

    fun modelFrom(
        info: RouteInfo,
        fileHint: String = info.relativeHint,
        parentPath: String? = null,
        parentFileHint: String? = null,
        childPaths: List<String> = emptyList(),
        usageCount: Int? = null,
        apiLabel: String? = null,
        fullFilePath: String? = null,
    ): RouteDocModel {
        val publicPath = RouteDocFacts.publicPath(info.resolvedPath, info.alternatePaths)
        val params = LinkedHashSet<String>()
        params += RouteDocFacts.extractParams(publicPath)
        params += RouteDocFacts.extractParams(info.resolvedPath)
        info.alternatePaths.forEach { params += RouteDocFacts.extractParams(it) }
        val kinds = RouteDocFacts.inferKinds(
            path = publicPath,
            fileName = info.fileName,
            childCount = childPaths.size,
            alternatePaths = info.alternatePaths + listOf(info.resolvedPath),
        )
        val routeId = RouteDocFacts.routeId(publicPath, info.resolvedPath, info.alternatePaths)
        val requested = RouteDocFacts.usefulRequested(info.requestedPath, publicPath)
        return RouteDocModel(
            publicPath = publicPath,
            requestedPath = requested,
            fileHint = fileHint,
            kinds = kinds,
            params = params.toList(),
            parentPath = parentPath,
            parentFileHint = parentFileHint,
            childPaths = childPaths,
            usageCount = usageCount,
            routeId = routeId,
            apiLabel = apiLabel,
            sourceTreeName = info.sourceTree?.name,
            fullFilePath = fullFilePath,
        )
    }

    fun documentationHtml(
        info: RouteInfo,
        apiLabel: String? = null,
        usageCount: Int? = null,
    ): String = documentationHtml(
        modelFrom(info, usageCount = usageCount, apiLabel = apiLabel),
        compact = false,
    )

    fun documentationHtml(
        resolvedPath: String,
        requestedPath: String = resolvedPath,
        fileHint: String,
        fullPath: String = "",
        alternatePaths: List<String> = emptyList(),
        sourceTreeName: String? = null,
        apiLabel: String? = null,
        usageCount: Int? = null,
        parentPath: String? = null,
        parentFileHint: String? = null,
        childPaths: List<String> = emptyList(),
        params: List<String>? = null,
        kinds: List<String>? = null,
        compact: Boolean = false,
    ): String {
        val publicPath = RouteDocFacts.publicPath(resolvedPath, alternatePaths)
        val model = RouteDocModel(
            publicPath = publicPath,
            requestedPath = RouteDocFacts.usefulRequested(requestedPath, publicPath),
            fileHint = fileHint,
            kinds = kinds ?: RouteDocFacts.inferKinds(
                publicPath,
                fileHint.substringAfterLast('/'),
                childPaths.size,
                alternatePaths + resolvedPath,
            ),
            params = params ?: run {
                val p = LinkedHashSet<String>()
                p += RouteDocFacts.extractParams(publicPath)
                p += RouteDocFacts.extractParams(resolvedPath)
                alternatePaths.forEach { p += RouteDocFacts.extractParams(it) }
                p.toList()
            },
            parentPath = parentPath,
            parentFileHint = parentFileHint,
            childPaths = childPaths,
            usageCount = usageCount,
            routeId = RouteDocFacts.routeId(publicPath, resolvedPath, alternatePaths),
            apiLabel = apiLabel,
            sourceTreeName = sourceTreeName,
            fullFilePath = fullPath.takeIf { it.isNotBlank() },
        )
        return documentationHtml(model, compact = compact)
    }

    fun documentationHtml(model: RouteDocModel, compact: Boolean = true): String {
        val path = escape(model.publicPath)
        val file = escape(model.fileHint)
        val api = model.apiLabel?.let { escape(it) }
        val kinds = model.kinds
        val usage = model.usageCount

        return buildString {
            // Header: brand · API
            append("<div class='definition'>")
            append("<b>TanStack route</b>")
            if (api != null) {
                append(" <span style='color:gray'>· ")
                append(api)
                append("</span>")
            }
            append("</div>")

            // Hero path
            append("<div style='margin-top:6px;margin-bottom:2px'>")
            append("<code style='font-size:1.1em'><b>")
            append(path)
            append("</b></code>")
            append("</div>")

            // File line
            append("<div style='color:gray;margin-bottom:8px'><code>")
            append(file)
            append("</code></div>")

            // Chips + usages
            if (kinds.isNotEmpty() || usage != null) {
                append("<div style='margin-bottom:10px'>")
                if (kinds.isNotEmpty()) {
                    append("<span style='color:#787878'>")
                    append(escape(RouteDocFacts.formatKinds(kinds)))
                    append("</span>")
                }
                if (usage != null) {
                    if (kinds.isNotEmpty()) append("&nbsp;&nbsp;&nbsp;")
                    val tone = if (usage == 0) "#987600" else "gray"
                    append("<span style='color:")
                    append(tone)
                    append("'>")
                    append(escape(RouteDocFacts.formatUsageLabel(usage)))
                    append("</span>")
                }
                append("</div>")
            }

            append("<table class='sections'>")
            if (model.parentPath != null) {
                val parentVal = buildString {
                    append("<code>")
                    append(escape(model.parentPath))
                    append("</code>")
                    if (!model.parentFileHint.isNullOrBlank()) {
                        append(" <span style='color:gray'>→ ")
                        append(escape(model.parentFileHint))
                        append("</span>")
                    }
                }
                row("Parent", parentVal)
            }
            if (model.params.isNotEmpty()) {
                row("Params", model.params.joinToString("&nbsp;&nbsp;") { "<code>${escape(it)}</code>" })
            }
            if (model.childPaths.isNotEmpty()) {
                val summary = RouteDocFacts.formatChildrenSummary(model.childPaths)
                row("Children", "<span>${model.childPaths.size}</span> <span style='color:gray'>(${escape(summary)})</span>")
            } else if (!compact && model.kinds.contains("layout")) {
                row("Children", "<span style='color:gray'>none</span>")
            }
            if (model.requestedPath != null) {
                row("Requested", "<code>${escape(model.requestedPath)}</code>")
            }
            if (model.routeId != null) {
                row("Id", "<code>${escape(model.routeId)}</code>")
            }
            if (!compact) {
                if (!model.fullFilePath.isNullOrBlank() && model.fullFilePath != model.fileHint) {
                    row("Full path", "<code>${escape(model.fullFilePath)}</code>")
                }
                if (!model.sourceTreeName.isNullOrBlank()) {
                    row("Route tree", "<code>${escape(model.sourceTreeName)}</code>")
                }
            }
            append("</table>")
        }
    }

    fun quickNavigateInfo(info: RouteInfo, apiLabel: String? = null): String =
        quickNavigateInfo(info.resolvedPath, info.relativeHint, apiLabel)

    fun quickNavigateInfo(
        resolvedPath: String,
        fileHint: String,
        apiLabel: String? = null,
        kinds: List<String> = emptyList(),
        usageCount: Int? = null,
    ): String {
        val via = apiLabel?.let { " · $it" }.orEmpty()
        val kindPart = if (kinds.isNotEmpty()) " · ${RouteDocFacts.formatKinds(kinds)}" else ""
        val usagePart = usageCount?.let { " · ${RouteDocFacts.formatUsageLabel(it)}" }.orEmpty()
        return "TanStack$via$kindPart: $resolvedPath → $fileHint$usagePart"
    }

    private fun StringBuilder.row(title: String, value: String) {
        append("<tr><td valign='top' class='section'><p>")
        append(escape(title))
        append("</p></td><td valign='top'><p style='margin:2px 0'>")
        append(value)
        append("</p></td></tr>")
    }

    private fun escape(s: String): String =
        s.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
}

