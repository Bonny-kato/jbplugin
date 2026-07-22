package com.ipfsoftwares.jbplugin.route

import com.intellij.openapi.vfs.VirtualFile

/**
 * One row in the Routes tool window: a unique physical route module
 * with its preferred navigable `to` path.
 */
data class PhysicalRoute(
    /** Preferred Link `to` / fullPath for display and navigation. */
    val path: String,
    /** Physical route module file. */
    val file: VirtualFile,
    /** Other path keys from the gen tree that resolve to the same file. */
    val alternatePaths: List<String> = emptyList(),
) {
    val allPaths: List<String>
        get() = (listOf(path) + alternatePaths).distinct()

    fun matchesFilter(query: String): Boolean {
        if (query.isBlank()) return true
        val q = query.trim().lowercase()
        if (path.lowercase().contains(q)) return true
        if (file.name.lowercase().contains(q)) return true
        if (file.path.lowercase().contains(q)) return true
        return alternatePaths.any { it.lowercase().contains(q) }
    }

    companion object {
        /**
         * Choose a single display path for a set of keys that point at one file.
         * Prefers navigable absolute paths without a trailing slash (except `/`).
         */
        fun pickPreferredPath(paths: Collection<String>): String {
            val absolute = paths.filter { it.startsWith("/") }
            val pool = absolute.ifEmpty { paths.toList() }.ifEmpty { return "" }

            fun score(p: String): Int {
                var s = 0
                // Prefer real URL-like paths over internal ids (pathless layouts often contain `_`)
                if (p.contains("/_") || p.startsWith("/_")) s += 100
                if (p != "/" && p.endsWith('/')) s += 10
                // Prefer shorter public paths
                s += p.length
                return s
            }

            return pool.minWith(compareBy<String> { score(it) }.thenBy { it })
        }
    }
}
