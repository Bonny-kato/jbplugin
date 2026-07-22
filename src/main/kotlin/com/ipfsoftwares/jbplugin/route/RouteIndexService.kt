package com.ipfsoftwares.jbplugin.route

import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.AsyncFileListener
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.psi.search.FilenameIndex
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.util.concurrency.annotations.RequiresReadLock
import java.util.concurrent.atomic.AtomicReference

/**
 * Project-level cache of TanStack route path → route module file,
 * built from every `routeTree.gen.ts` in the project.
 */
@Service(Service.Level.PROJECT)
class RouteIndexService(private val project: Project) {

    private val log = logger<RouteIndexService>()
    private val cached = AtomicReference(RouteIndex.EMPTY)

    @Volatile
    private var dirty = true

    init {
        VirtualFileManager.getInstance().addAsyncFileListener(
            AsyncFileListener { events ->
                if (events.any { isRelevant(it) }) {
                    dirty = true
                }
                null
            },
            project,
        )
    }

    fun getIndex(): RouteIndex {
        if (!dirty) return cached.get()
        val built = ReadAction.compute<RouteIndex, RuntimeException> { buildIndex() }
        cached.set(built)
        dirty = false
        return built
    }

    fun resolve(path: String, contextFile: VirtualFile? = null): VirtualFile? {
        val index = getIndex()
        val direct = index.resolve(path) ?: return null
        return pickClosest(index, path, direct, contextFile)
    }

    /**
     * Resolve absolute or relative `to`, using optional base hint (`from` / createFileRoute id).
     */
    fun resolveLink(
        to: String,
        baseRouteHint: String? = null,
        contextFile: VirtualFile? = null,
    ): VirtualFile? {
        val index = getIndex()
        val direct = index.resolveLink(to, baseRouteHint) ?: return null
        val lookupKey = if (RouteIndex.isRelativePath(to.trim())) {
            val base = index.fullPathFor(baseRouteHint) ?: baseRouteHint
            if (base != null) RouteIndex.joinRoutePaths(base, to.trim()) else to
        } else {
            to
        }
        return pickClosest(index, lookupKey, direct, contextFile)
    }

    fun invalidate() {
        dirty = true
    }

    private fun pickClosest(
        index: RouteIndex,
        path: String,
        direct: VirtualFile,
        contextFile: VirtualFile?,
    ): VirtualFile {
        if (contextFile == null || index.sourceTrees.size <= 1) return direct

        val normalized = normalizeRoutePath(path)
        val variants = setOf(
            path,
            normalized,
            normalized.trimEnd('/'),
            if (normalized.endsWith('/')) normalized else "$normalized/",
        )
        val candidates = index.pathToFile
            .filterKeys { it in variants }
            .values
            .distinct()
        if (candidates.size <= 1) return direct

        return candidates.minByOrNull { distanceScore(contextFile, it) } ?: direct
    }

    @RequiresReadLock
    private fun buildIndex(): RouteIndex {
        if (project.isDisposed) return RouteIndex.EMPTY

        val scope = GlobalSearchScope.projectScope(project)
        val trees = FilenameIndex
            .getVirtualFilesByName(ROUTE_TREE_FILENAME, scope)
            .filter { !it.path.contains("/node_modules/") && !it.path.contains("/.git/") }

        if (trees.isEmpty()) {
            log.debug("No $ROUTE_TREE_FILENAME found in project ${project.name}")
            return RouteIndex.EMPTY
        }

        val pathToFile = LinkedHashMap<String, VirtualFile>()
        val keyToFullPath = LinkedHashMap<String, String>()
        for (tree in trees) {
            val text = try {
                String(tree.contentsToByteArray(), tree.charset)
            } catch (e: Exception) {
                log.warn("Failed to read ${tree.path}", e)
                continue
            }

            val parsed = RouteTreeParser.parseDetailed(text)
            val baseDir = tree.parent ?: continue
            for ((routePath, relativeImport) in parsed.pathToRelative) {
                val resolved = resolveImportToFile(baseDir, relativeImport) ?: continue
                pathToFile.putIfAbsent(routePath, resolved)
            }
            parsed.keyToFullPath.forEach { (k, v) ->
                keyToFullPath.putIfAbsent(k, v)
            }
        }

        log.info(
            "TanStack route index: ${pathToFile.size} paths from ${trees.size} route tree(s) in ${project.name}",
        )
        return RouteIndex(pathToFile, keyToFullPath, trees.toList())
    }

    private fun resolveImportToFile(baseDir: VirtualFile, relativeImport: String): VirtualFile? {
        var rel = relativeImport
        if (rel.startsWith("./")) rel = rel.substring(2)

        val direct = baseDir.findFileByRelativePath(rel)
        if (direct != null && !direct.isDirectory) return direct

        val extensions = listOf(".tsx", ".ts", ".jsx", ".js")
        for (ext in extensions) {
            val withExt = baseDir.findFileByRelativePath(rel + ext)
            if (withExt != null && !withExt.isDirectory) return withExt
        }

        // Directory import → index / route file
        val dir = baseDir.findFileByRelativePath(rel)
        if (dir != null && dir.isDirectory) {
            for (ext in extensions) {
                dir.findChild("route$ext")?.let { return it }
                dir.findChild("index$ext")?.let { return it }
            }
        }
        return null
    }

    private fun isRelevant(event: VFileEvent): Boolean {
        val file = event.file ?: return event.path.contains(ROUTE_TREE_FILENAME) || event.path.contains("/routes/")
        return isRouteTreeFile(file) || file.path.contains("/routes/")
    }

    private fun isRouteTreeFile(file: VirtualFile): Boolean =
        file.name == ROUTE_TREE_FILENAME || file.name.endsWith("routeTree.gen.ts")

    private fun distanceScore(from: VirtualFile, to: VirtualFile): Int {
        val a = from.path
        val b = to.path
        val max = minOf(a.length, b.length)
        var i = 0
        while (i < max && a[i] == b[i]) i++
        return (a.length - i) + (b.length - i)
    }

    companion object {
        const val ROUTE_TREE_FILENAME = "routeTree.gen.ts"

        fun getInstance(project: Project): RouteIndexService =
            project.getService(RouteIndexService::class.java)
    }
}
