package com.ipfsoftwares.jbplugin.route

/**
 * Pure, unit-testable facts derived from TanStack route paths/files for quick docs.
 */
object RouteDocFacts {

    private val paramSegment = Regex("""^\{?-?\$([A-Za-z0-9_]+)\}?$|^\$([A-Za-z0-9_]+)$|\$([A-Za-z0-9_]+)""")

    fun extractParams(path: String): List<String> {
        if (path.isBlank()) return emptyList()
        val out = LinkedHashSet<String>()
        for (segment in path.split('/')) {
            if (segment.isEmpty()) continue
            // $id, {$id}, {-$id}, foo.$bar style segments
            if ('$' !in segment) continue
            val matches = Regex("""\$([A-Za-z0-9_]+)""").findAll(segment)
            for (m in matches) {
                out.add("$" + m.groupValues[1])
            }
            if (matches.none()) {
                out.add(segment)
            }
        }
        return out.toList()
    }

    /**
     * Infer compact kind chips from path + file name + whether the route has children.
     */
    fun inferKinds(
        path: String,
        fileName: String,
        childCount: Int = 0,
        alternatePaths: List<String> = emptyList(),
    ): List<String> {
        val kinds = LinkedHashSet<String>()
        val segs = pathSegments(path)
        val allPaths = listOf(path) + alternatePaths
        val file = fileName.lowercase()

        if (path == "/" || segs.isEmpty() || segs.any { it == "__root" } || file.contains("__root")) {
            kinds.add("root")
        }
        if (segs.any { it == "index" } || file.startsWith("index.")) {
            kinds.add("index")
        }
        if (segs.any { isPathlessSegment(it) } || allPaths.any { p -> pathSegments(p).any { isPathlessSegment(it) } }) {
            kinds.add("pathless")
        }
        if (extractParams(path).isNotEmpty() || allPaths.any { extractParams(it).isNotEmpty() }) {
            kinds.add("param")
        }
        if (segs.any { it == "$" || it.startsWith("$$") || it == "_" || it.endsWith("_") } ||
            path.contains("/*") || segs.any { it == "$" }
        ) {
            // splat / catch-all heuristics
            if (segs.any { it == "$" || it.startsWith("$$") || it == "_splat" || it.endsWith("_") && it.contains("$") }) {
                kinds.add("splat")
            }
        }
        if (file.contains(".lazy.") || file.endsWith(".lazy.tsx") || file.endsWith(".lazy.ts")) {
            kinds.add("lazy")
        }
        if (childCount > 0 && "index" !in kinds) {
            kinds.add("layout")
        }
        if (kinds.none { it in setOf("root", "index", "layout", "pathless") }) {
            kinds.add("leaf")
        } else if (childCount == 0 && "layout" !in kinds && "root" !in kinds && "index" !in kinds) {
            // keep pathless/param without forcing leaf
        }

        // Prefer a stable display order
        val order = listOf("root", "layout", "pathless", "index", "leaf", "param", "splat", "lazy")
        return order.filter { it in kinds }
    }

    fun isPathlessSegment(segment: String): Boolean {
        if (segment.isEmpty()) return false
        if (segment.startsWith("(") && segment.endsWith(")")) return true
        if (segment.startsWith("_") && !segment.startsWith("_$") && segment != "__root") return true
        return false
    }

    fun pathSegments(path: String): List<String> =
        path.trim('/').split('/').filter { it.isNotEmpty() }

    /**
     * Walk up from [path] producing candidate parent paths (nearest first).
     */
    fun parentCandidates(path: String): List<String> {
        val segs = pathSegments(path)
        if (segs.isEmpty()) return emptyList()
        val out = ArrayList<String>(segs.size)
        for (len in segs.size - 1 downTo 0) {
            out += if (len == 0) "/" else "/" + segs.take(len).joinToString("/")
        }
        return out
    }

    /**
     * True if [child] is a descendant of [parent] in URL space.
     */
    fun isDescendant(parent: String, child: String): Boolean {
        val p = normalize(parent)
        val c = normalize(child)
        if (p == c) return false
        if (p == "/") return c.startsWith("/") && c != "/"
        val prefix = p.trimEnd('/') + "/"
        return c.startsWith(prefix) || (c + "/").startsWith(prefix)
    }

    /**
     * Direct child only (one extra segment after parent), ignoring empty.
     */
    fun isDirectChild(parent: String, child: String): Boolean {
        if (!isDescendant(parent, child)) return false
        val pSegs = pathSegments(parent)
        val cSegs = pathSegments(child)
        return cSegs.size == pSegs.size + 1
    }

    fun normalize(path: String): String {
        if (path.isBlank()) return "/"
        var p = path.trim()
        if (!p.startsWith("/")) p = "/$p"
        if (p.length > 1 && p.endsWith("/")) p = p.trimEnd('/')
        return p.ifEmpty { "/" }
    }

    /**
     * Prefer a public path without internal groups/underscores for the hero line.
     */
    fun publicPath(resolvedPath: String, alternatePaths: List<String>): String {
        val candidates = (listOf(resolvedPath) + alternatePaths)
            .map { normalize(it) }
            .distinct()
        return candidates.firstOrNull { p ->
            p.startsWith("/") &&
                pathSegments(p).none { isPathlessSegment(it) || it == "__root" }
        } ?: candidates.firstOrNull { it.startsWith("/") && !it.contains("/_") }
            ?: candidates.firstOrNull()
            ?: resolvedPath
    }

    /**
     * Route id / internal path when it differs from the public path.
     */
    fun routeId(
        publicPath: String,
        resolvedPath: String,
        alternatePaths: List<String>,
    ): String? {
        val pub = normalize(publicPath)
        val candidates = (listOf(resolvedPath) + alternatePaths)
            .map { normalize(it) }
            .filter { it != pub }
        // Prefer one that shows groups / underscores
        return candidates.firstOrNull { pathSegments(it).any { s -> isPathlessSegment(s) } || it.contains("/_") }
            ?: candidates.firstOrNull()
    }

    fun projectRelativePath(projectBasePath: String?, filePath: String, fallback: String): String {
        val base = projectBasePath?.trimEnd('/') ?: return fallback
        val path = filePath
        if (path == base) return fallback
        if (path.startsWith("$base/")) {
            return path.removePrefix("$base/")
        }
        return fallback
    }

    fun formatUsageLabel(count: Int): String = when (count) {
        0 -> "0 links"
        1 -> "1 link"
        else -> "$count links"
    }

    fun formatChildrenSummary(childPaths: List<String>, limit: Int = 3): String {
        if (childPaths.isEmpty()) return "—"
        if (childPaths.size <= limit) {
            return childPaths.joinToString(", ")
        }
        val head = childPaths.take(limit).joinToString(", ")
        val more = childPaths.size - limit
        return "$head +$more more"
    }

    fun formatKinds(kinds: List<String>): String = kinds.joinToString(" · ")

    fun usefulRequested(requested: String, publicPath: String): String? {
        val r = normalize(requested)
        val p = normalize(publicPath)
        if (r == p) return null
        // skip pure trailing-slash difference already normalized
        if (requested == publicPath) return null
        return requested
    }
}
