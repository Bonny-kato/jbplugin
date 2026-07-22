package com.ipfsoftwares.jbplugin.route

import com.intellij.openapi.vfs.VirtualFile

/**
 * Immutable lookup table from TanStack route `to` / fullPath / id values
 * to the physical route module file.
 */
data class RouteIndex(
    val pathToFile: Map<String, VirtualFile>,
    /** Optional route id / path key → canonical fullPath used for relative resolution. */
    val keyToFullPath: Map<String, String> = emptyMap(),
    val sourceTrees: List<VirtualFile> = emptyList(),
) {
    fun resolve(path: String): VirtualFile? {
        if (path.isEmpty()) return null
        pathToFile[path]?.let { return it }

        val normalized = normalizeRoutePath(path)
        pathToFile[normalized]?.let { return it }

        // Index routes are often stored with a trailing slash in fullPath maps.
        if (!normalized.endsWith('/')) {
            pathToFile["$normalized/"]?.let { return it }
        } else if (normalized.length > 1) {
            pathToFile[normalized.trimEnd('/')]?.let { return it }
        }

        return null
    }

    /**
     * Resolve a `to` value that may be absolute or relative.
     *
     * @param to path from Link/navigate
     * @param baseRouteHint explicit `from` or `createFileRoute` id/fullPath of the current file
     */
    fun resolveLink(to: String, baseRouteHint: String? = null): VirtualFile? {
        val trimmed = to.trim()
        if (trimmed.isEmpty()) return null

        if (!isRelativePath(trimmed)) {
            return resolve(trimmed)
        }

        val base = resolveBaseFullPath(baseRouteHint) ?: return null
        val absolute = joinRoutePaths(base, trimmed)
        return resolve(absolute)
    }

    fun fullPathFor(key: String?): String? {
        if (key.isNullOrBlank()) return null
        keyToFullPath[key]?.let { return it }
        keyToFullPath[normalizeRoutePath(key)]?.let { return it }
        // If the key itself looks like a navigable path present in the index, use it.
        if (resolve(key) != null) {
            return keyToFullPath[key]
                ?: keyToFullPath[normalizeRoutePath(key)]
                ?: normalizeRoutePath(key)
        }
        return null
    }

    private fun resolveBaseFullPath(baseRouteHint: String?): String? {
        if (baseRouteHint.isNullOrBlank()) return null
        val hint = normalizeRoutePath(baseRouteHint)
        keyToFullPath[baseRouteHint]?.let { return it }
        keyToFullPath[hint]?.let { return it }
        // Hint may already be a fullPath / to value
        if (pathToFile.containsKey(baseRouteHint) || pathToFile.containsKey(hint)) {
            return keyToFullPath[baseRouteHint]
                ?: keyToFullPath[hint]
                ?: hint
        }
        // Last resort: treat hint as a path-like base
        return hint.takeIf { it.startsWith("/") }
    }

    fun allPaths(): Collection<String> =
        pathToFile.keys.filter { it.startsWith("/") }.sorted()

    /**
     * Unique physical route modules (one row per file), with a preferred navigable path.
     * Intermediate path keys that share a file are collapsed into [PhysicalRoute.alternatePaths].
     */
    fun physicalRoutes(): List<PhysicalRoute> {
        if (pathToFile.isEmpty()) return emptyList()

        val byFileUrl = LinkedHashMap<String, MutableList<Pair<String, VirtualFile>>>()
        for ((path, file) in pathToFile) {
            // Only navigable absolute paths for tool window rows
            if (!path.startsWith("/")) continue
            byFileUrl.getOrPut(file.url) { mutableListOf() }.add(path to file)
        }

        return byFileUrl.values.mapNotNull { entries ->
            val file = entries.first().second
            val paths = entries.map { it.first }.distinct()
            val preferred = PhysicalRoute.pickPreferredPath(paths)
            if (preferred.isEmpty()) return@mapNotNull null
            PhysicalRoute(
                path = preferred,
                file = file,
                alternatePaths = paths.filter { it != preferred }.sorted(),
            )
        }.sortedBy { it.path }
    }

    fun isEmpty(): Boolean = pathToFile.isEmpty()

    companion object {
        val EMPTY = RouteIndex(emptyMap())

        fun isRelativePath(path: String): Boolean {
            if (path.isEmpty()) return false
            if (path.startsWith("/")) return false
            return true
        }

        /**
         * Join a base absolute route path with a relative TanStack `to` value.
         *
         * Examples:
         * - base `/customers/residents/search`, to `..` → `/customers/residents`
         * - base `/customers/residents`, to `./search` → `/customers/residents/search`
         * - base `/posts/$id`, to `.` → `/posts/$id`
         */
        fun joinRoutePaths(baseFullPath: String, relativeTo: String): String {
            val baseNorm = normalizeRoutePath(baseFullPath).ifEmpty { "/" }
            val rel = relativeTo.trim()
            if (rel.startsWith("/")) return normalizeRoutePath(rel)

            val parts = ArrayDeque<String>()
            if (baseNorm != "/") {
                baseNorm.trim('/').split('/').filter { it.isNotEmpty() }.forEach { parts.addLast(it) }
            }

            // Bare `.` keeps current path
            if (rel == ".") {
                return if (parts.isEmpty()) "/" else "/" + parts.joinToString("/")
            }

            val relParts = rel.split('/')
            for (segment in relParts) {
                when (segment) {
                    "", "." -> Unit
                    ".." -> if (parts.isNotEmpty()) parts.removeLast()
                    else -> parts.addLast(segment)
                }
            }

            return if (parts.isEmpty()) "/" else "/" + parts.joinToString("/")
        }
    }
}

fun normalizeRoutePath(path: String): String {
    var p = path.trim()
    if (p.isEmpty()) return p
    // Ignore query/hash if a user somehow put them in `to` (TanStack discourages this).
    val q = p.indexOf('?')
    if (q >= 0) p = p.substring(0, q)
    val h = p.indexOf('#')
    if (h >= 0) p = p.substring(0, h)
    if (p.length > 1 && p.endsWith('/')) {
        // Keep trailing slash variants as distinct keys in the map; normalize only for ops
        // that explicitly want a non-slash form. Here we preserve as-is except pure cleanup.
        return p
    }
    return p
}
