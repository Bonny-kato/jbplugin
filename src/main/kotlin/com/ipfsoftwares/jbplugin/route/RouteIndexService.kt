package com.ipfsoftwares.jbplugin.route

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.AsyncFileListener
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.events.VFileContentChangeEvent
import com.intellij.openapi.vfs.newvfs.events.VFileCopyEvent
import com.intellij.openapi.vfs.newvfs.events.VFileCreateEvent
import com.intellij.openapi.vfs.newvfs.events.VFileDeleteEvent
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.openapi.vfs.newvfs.events.VFileMoveEvent
import com.intellij.openapi.vfs.newvfs.events.VFilePropertyChangeEvent
import com.intellij.psi.search.FilenameIndex
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.util.Alarm
import com.intellij.util.concurrency.annotations.RequiresReadLock
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

/**
 * Project-level cache of TanStack route path → route module file,
 * built from every `routeTree.gen.ts` in the project.
 *
 * Keeps in sync with the generator by listening for VFS changes **after** they
 * are applied (not only in prepare), debouncing rebuilds, and notifying
 * [RouteIndexListener] subscribers (tool window, etc.).
 */
@Service(Service.Level.PROJECT)
class RouteIndexService(private val project: Project) {

    private val log = logger<RouteIndexService>()
    private val cached = AtomicReference(RouteIndex.EMPTY)

    /** Incremented on every invalidation; compared after rebuild to avoid clobbering newer dirtiness. */
    private val generation = AtomicInteger(0)

    @Volatile
    private var dirty = true

    private val rebuildAlarm = Alarm(Alarm.ThreadToUse.POOLED_THREAD, project)

    init {
        VirtualFileManager.getInstance().addAsyncFileListener(
            AsyncFileListener { events ->
                if (!events.any { isRelevant(it) }) {
                    return@AsyncFileListener null
                }
                // Important: mark dirty / rebuild only AFTER VFS applies the change.
                // Doing it in prepareChange races with content updates and can cache a stale tree.
                object : AsyncFileListener.ChangeApplier {
                    override fun afterVfsChange() {
                        scheduleInvalidate(reason = "vfs")
                    }
                }
            },
            project,
        )
    }

    fun getIndex(): RouteIndex {
        if (!dirty) return cached.get()
        return rebuildNow()
    }

    /**
     * Synchronously rebuild the index on the calling thread (inside a read action).
     * Does not fire [RouteIndexListener] — use [invalidate] / VFS-driven
     * [scheduleInvalidate] when UI subscribers should refresh.
     */
    fun rebuildSynchronously(): RouteIndex {
        dirty = true
        generation.incrementAndGet()
        RouteUsageFinder.clearCountCache()
        return rebuildNow()
    }

    /**
     * Mark dirty and rebuild soon, then notify [RouteIndexListener] (tool window).
     */
    fun invalidate() {
        scheduleInvalidate(reason = "manual", debounceMs = 0)
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

    /**
     * Mark index dirty, debounce rebuild + [RouteIndexListener] notification.
     */
    fun scheduleInvalidate(reason: String = "unspecified", debounceMs: Int = DEFAULT_DEBOUNCE_MS) {
        if (project.isDisposed) return

        dirty = true
        val gen = generation.incrementAndGet()
        RouteUsageFinder.clearCountCache()
        log.debug("TanStack route index invalidated ($reason), gen=$gen")

        val delay = debounceMs.coerceAtLeast(0)
        // Coalesce bursts from the generator (write + rename + format).
        rebuildAlarm.cancelAllRequests()
        rebuildAlarm.addRequest(
            {
                if (project.isDisposed) return@addRequest
                // Only the latest scheduled generation should rebuild + notify.
                if (generation.get() != gen) return@addRequest
                val built = rebuildNow()
                // If something invalidated during rebuild, stay dirty; a newer request owns notify.
                if (generation.get() != gen) return@addRequest
                notifyListeners()
                log.debug(
                    "TanStack route index rebuilt after $reason: ${built.pathToFile.size} paths, gen=$gen",
                )
            },
            delay,
        )
    }

    private fun rebuildNow(): RouteIndex {
        val genBefore = generation.get()
        val built = ReadAction.compute<RouteIndex, RuntimeException> { buildIndex() }
        cached.set(built)
        // If generation advanced mid-build, another invalidation happened — keep dirty.
        dirty = generation.get() != genBefore
        return built
    }

    private fun notifyListeners() {
        if (project.isDisposed) return
        val app = ApplicationManager.getApplication()
        val fire = Runnable {
            if (project.isDisposed) return@Runnable
            project.messageBus.syncPublisher(RouteIndexListener.TOPIC).routesChanged()
        }
        if (app.isDispatchThread) {
            fire.run()
        } else {
            app.invokeLater(fire, ModalityState.nonModal())
        }
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
            if (!tree.isValid) continue
            val text = readVirtualFileText(tree) ?: continue

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

    /**
     * Prefer VFS bytes for generated trees (external `tsr`/Vite writes).
     * Only fall back to an open document when it has unsaved editor edits.
     */
    private fun readVirtualFileText(file: VirtualFile): String? {
        val docManager = FileDocumentManager.getInstance()
        val doc = docManager.getCachedDocument(file)
        if (doc != null && docManager.isDocumentUnsaved(doc)) {
            return doc.text
        }
        return try {
            String(file.contentsToByteArray(), file.charset)
        } catch (e: Exception) {
            log.warn("Failed to read ${file.path}", e)
            null
        }
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
        // Content / structure changes under routes or routeTree.gen.ts
        when (event) {
            is VFileContentChangeEvent,
            is VFileCreateEvent,
            is VFileDeleteEvent,
            is VFileMoveEvent,
            is VFileCopyEvent,
            -> {
                if (pathLooksRelevant(event.path)) return true
                val file = event.file
                if (file != null && isRelevantFile(file)) return true
            }
            is VFilePropertyChangeEvent -> {
                // rename
                if (pathLooksRelevant(event.path) || pathLooksRelevant(event.oldPath)) return true
                val file = event.file
                if (file != null && isRelevantFile(file)) return true
            }
            else -> {
                if (pathLooksRelevant(event.path)) return true
                val file = event.file
                if (file != null && isRelevantFile(file)) return true
            }
        }
        return false
    }

    private fun pathLooksRelevant(path: String): Boolean {
        if (path.contains(ROUTE_TREE_FILENAME) || path.endsWith("routeTree.gen.ts")) return true
        // TanStack file routes live under .../routes/...
        if (path.contains("/routes/") || path.contains("\\routes\\")) return true
        return false
    }

    private fun isRelevantFile(file: VirtualFile): Boolean {
        if (isRouteTreeFile(file)) return true
        val path = file.path
        if (path.contains("/routes/") || path.contains("\\routes\\")) return true
        // Parent folder named routes (file created as child)
        var parent = file.parent
        var depth = 0
        while (parent != null && depth < 6) {
            if (parent.name == "routes") return true
            if (isRouteTreeFile(parent)) return true
            parent = parent.parent
            depth++
        }
        return false
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
        const val DEFAULT_DEBOUNCE_MS = 300

        fun getInstance(project: Project): RouteIndexService =
            project.getService(RouteIndexService::class.java)
    }
}
