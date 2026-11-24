package com.ipfsoftwares.jbplugin.route

import com.intellij.openapi.application.runReadActionBlocking
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.search.FilenameIndex
import com.intellij.psi.search.GlobalSearchScope
import com.ipfsoftwares.jbplugin.navigation.TanStackLinkContext
import java.util.concurrent.ConcurrentHashMap

/**
 * A single place in the project that navigates to a TanStack route via `to`.
 */
data class RouteUsage(
    val file: VirtualFile,
    val offset: Int,
    val lineNumber: Int,
    val columnNumber: Int,
    val toValue: String,
    val lineText: String,
) {
    val displayLabel: String
        get() {
            val preview = lineText.trim().take(120)
            return "${file.name}:${lineNumber}  $preview"
        }
}

/**
 * Finds `to` link/navigate/redirect call sites that target a given route path
 * (or any of its alternate path keys).
 */
object RouteUsageFinder {

    private data class CountCacheKey(val projectPath: String, val pathsKey: String)
    private val countCache = ConcurrentHashMap<CountCacheKey, Int>()

    /**
     * Cached usage count for hover docs. Cleared when the process ends; cheap path prefilter
     * keeps first scan reasonable for medium projects.
     */
    fun countUsages(project: Project, paths: Collection<String>): Int {
        val normalized = paths.map { it.trim() }.filter { it.isNotEmpty() }.distinct().sorted()
        if (normalized.isEmpty()) return 0
        val key = CountCacheKey(
            projectPath = project.locationHash,
            pathsKey = normalized.joinToString("\u0000"),
        )
        countCache[key]?.let { return it }
        val count = findUsages(project, normalized).size
        countCache[key] = count
        return count
    }

    fun clearCountCache() {
        countCache.clear()
    }


    private val CODE_EXTENSIONS = setOf("ts", "tsx", "js", "jsx", "mts", "cts", "mjs", "cjs")

    fun findUsages(
        project: Project,
        routePaths: Collection<String>,
        indicator: ProgressIndicator? = null,
    ): List<RouteUsage> {
        val targets = routePaths
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .flatMap { path ->
                buildList {
                    add(path)
                    if (path != "/" && path.endsWith('/')) add(path.trimEnd('/'))
                    else if (path != "/" && !path.endsWith('/')) add("$path/")
                }
            }
            .toSet()
        if (targets.isEmpty()) return emptyList()

        // Prefer exact absolute paths for search needles (skip relative tokens)
        val needles = targets.filter { it.startsWith("/") }.ifEmpty { targets.toList() }

        return runReadActionBlocking {
            scanProject(project, needles, targets, indicator)
        }
    }

    private fun scanProject(
        project: Project,
        needles: Collection<String>,
        targets: Set<String>,
        indicator: ProgressIndicator?,
    ): List<RouteUsage> {
        if (project.isDisposed) return emptyList()
        val scope = GlobalSearchScope.projectScope(project)
        val results = LinkedHashMap<String, RouteUsage>()

        val files = collectCodeFiles(project, scope)
        for ((index, vf) in files.withIndex()) {
            indicator?.checkCanceled()
            if (index % 20 == 0) {
                indicator?.fraction = (index.toDouble() / files.size.coerceAtLeast(1)).coerceIn(0.0, 1.0)
                indicator?.text = "Scanning ${vf.name}"
            }
            if (vf.path.contains("/node_modules/") || vf.path.contains("/.git/")) continue
            if (vf.name == RouteIndexService.ROUTE_TREE_FILENAME) continue
            if (vf.name.endsWith(".gen.ts")) continue

            val text = try {
                String(vf.contentsToByteArray(), vf.charset)
            } catch (_: Exception) {
                continue
            }

            // Cheap pre-filter: file must contain at least one needle
            if (needles.none { text.contains(it) }) continue

            findInText(vf, text, targets, results)
        }

        return results.values.sortedWith(
            compareBy<RouteUsage> { it.file.path }.thenBy { it.offset },
        )
    }

    private fun collectCodeFiles(project: Project, scope: GlobalSearchScope): List<VirtualFile> {
        val out = LinkedHashSet<VirtualFile>()
        for (ext in CODE_EXTENSIONS) {
            // Use FilenameIndex with a broad approach: get files by extension via file type is cleaner,
            // but extension walk through FilenameIndex needs exact names. Fall back to content search roots.
            FilenameIndex.getAllFilesByExt(project, ext, scope).forEach { out.add(it) }
        }
        return out.toList()
    }

    private fun findInText(
        vf: VirtualFile,
        text: String,
        targets: Set<String>,
        results: MutableMap<String, RouteUsage>,
    ) {
        // Match to: 'path' / to="path" / to={'path'} including relative that equals a target
        val pattern = Regex(
            """\bto\s*[:=]\s*\{?\s*(['"`])([^'"`]+)\1\s*\}?""",
        )
        for (match in pattern.findAll(text)) {
            val raw = match.groupValues[2]
            val path = TanStackLinkContext.coerceRoutePath(raw) ?: raw.trim()
            if (path !in targets) {
                // Also accept if absolute form after trivial normalize
                val norm = normalizeRoutePath(path)
                if (norm !in targets && path.trimEnd('/') !in targets && "$path/" !in targets) {
                    continue
                }
            }

            val offset = match.range.first
            // Prefer offset of the path string itself
            val pathOffset = text.indexOf(path, match.range.first).takeIf { it >= 0 } ?: offset
            val key = "${vf.path}:$pathOffset:$path"
            if (key in results) continue

            val (line, col) = offsetToLineCol(text, pathOffset)
            val lineText = lineTextAt(text, pathOffset)
            results[key] = RouteUsage(
                file = vf,
                offset = pathOffset,
                lineNumber = line,
                columnNumber = col,
                toValue = path,
                lineText = lineText,
            )
        }
    }

    private fun offsetToLineCol(text: String, offset: Int): Pair<Int, Int> {
        var line = 1
        var col = 1
        var i = 0
        val max = offset.coerceAtMost(text.length)
        while (i < max) {
            if (text[i] == '\n') {
                line++
                col = 1
            } else {
                col++
            }
            i++
        }
        return line to col
    }

    private fun lineTextAt(text: String, offset: Int): String {
        val start = text.lastIndexOf('\n', (offset - 1).coerceAtLeast(0)).let { if (it < 0) 0 else it + 1 }
        val end = text.indexOf('\n', offset).let { if (it < 0) text.length else it }
        return text.substring(start, end)
    }
}
