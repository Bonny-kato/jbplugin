package com.ipfsoftwares.jbplugin.usages

import com.intellij.find.findUsages.FindUsagesHandler
import com.intellij.find.findUsages.FindUsagesHandlerFactory
import com.intellij.find.findUsages.FindUsagesOptions
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.psi.PsiReference
import com.intellij.usageView.UsageInfo
import com.intellij.util.Processor
import com.ipfsoftwares.jbplugin.navigation.TanStackApiContext
import com.ipfsoftwares.jbplugin.route.RouteDefinitionLocator
import com.ipfsoftwares.jbplugin.route.RouteIndexService
import com.ipfsoftwares.jbplugin.route.RouteInfoResolver
import com.ipfsoftwares.jbplugin.route.RouteUsageFinder

/**
 * Find Usages (Alt+F7) for TanStack route modules and `createFileRoute('...')` paths.
 * Lists `Link` / `navigate` / `redirect` / `linkOptions` / `Navigate` / `MatchRoute` call sites.
 *
 * Registered with `order="first"` so it wins over TypeScript's default symbol usages
 * (which typically only show imports in `routeTree.gen.ts`).
 */
class TanStackRouteFindUsagesHandlerFactory : FindUsagesHandlerFactory() {

    override fun canFindUsages(element: PsiElement): Boolean =
        RouteFindUsagesSupport.pathsFor(element) != null

    override fun createFindUsagesHandler(
        element: PsiElement,
        forHighlightUsages: Boolean,
    ): FindUsagesHandler? {
        val paths = RouteFindUsagesSupport.pathsFor(element) ?: return null
        val anchor = RouteFindUsagesSupport.preferredAnchor(element) ?: element
        return TanStackRouteFindUsagesHandler(anchor, paths)
    }
}

internal object RouteFindUsagesSupport {

    private val FACTORY_NAMES = setOf("createFileRoute", "createLazyFileRoute", "createRoute")

    /**
     * Path keys to search when Find Usages is invoked on [element], or null if we should not handle it.
     */
    fun pathsFor(element: PsiElement): List<String>? {
        val project = element.project
        val index = RouteIndexService.getInstance(project).getIndex()
        if (index.isEmpty()) return null

        // 1) Entire route module file
        if (element is PsiFile) {
            val vf = element.virtualFile ?: return null
            val paths = RouteInfoResolver.pathsForRouteFile(project, vf)
            return paths.takeIf { it.isNotEmpty() }
        }

        val vf = element.containingFile?.virtualFile ?: return null
        val filePaths = RouteInfoResolver.pathsForRouteFile(project, vf)
        val fileText = element.containingFile?.text

        // 2) Definition-ish caret positions inside a known physical route module
        if (filePaths.isNotEmpty() && fileText != null && isRouteDefinitionElement(element, fileText)) {
            return filePaths
        }

        // 3) Inside createFileRoute / lazy factory (even if file not yet indexed as physical)
        if (fileText != null && TanStackApiContext.isInsideRouteFactoryCall(fileText, element.textOffset)) {
            if (filePaths.isNotEmpty()) return filePaths
            val literal = TanStackApiContext.findCreateFileRoutePathAt(element)
            if (!literal.isNullOrBlank()) {
                return expandWithIndexVariants(project, pathVariants(literal), vf)
            }
        }

        // 4) Explicit factory path literal
        val factoryPath = TanStackApiContext.findCreateFileRoutePathAt(element)
        if (!factoryPath.isNullOrBlank()) {
            if (filePaths.isNotEmpty()) return filePaths
            return expandWithIndexVariants(project, pathVariants(factoryPath), vf)
        }

        // 5) Factory name leaf
        if (isRouteFactoryNameLeaf(element)) {
            if (filePaths.isNotEmpty()) return filePaths
        }

        // 6) Route export binding: `export const Route = createFileRoute(...)`
        if (filePaths.isNotEmpty() && fileText != null && isRouteExportBinding(element, fileText)) {
            return filePaths
        }

        return null
    }

    /**
     * Prefer the `createFileRoute` call element as the Find Usages anchor when possible.
     */
    fun preferredAnchor(element: PsiElement): PsiElement? {
        val project = element.project
        val vf = when (element) {
            is PsiFile -> element.virtualFile
            else -> element.containingFile?.virtualFile
        } ?: return element

        val paths = RouteInfoResolver.pathsForRouteFile(project, vf)
        val preferredPath = paths.firstOrNull()
        return RouteDefinitionLocator.findDefinitionElement(project, vf, preferredPath) ?: element
    }

    fun pathVariants(path: String): List<String> {
        val t = path.trim()
        if (t.isEmpty()) return emptyList()
        if (t == "/") return listOf("/")
        return buildList {
            add(t)
            if (t.endsWith('/')) add(t.trimEnd('/')) else add("$t/")
        }.distinct()
    }

    /**
     * True when caret is on a route definition surface (factory, path, or Route export),
     * not on arbitrary code inside the route component body.
     */
    fun isRouteDefinitionElement(element: PsiElement, fileText: String): Boolean {
        val offset = element.textOffset
        if (offset < 0) return false

        if (TanStackApiContext.isInsideRouteFactoryCall(fileText, offset)) return true
        if (TanStackApiContext.findCreateFileRoutePathAt(element) != null) return true
        if (isRouteFactoryNameLeaf(element)) return true
        if (isRouteExportBinding(element, fileText)) return true

        // PSI leaf may be a quote; parent may be the string / call
        var p: PsiElement? = element.parent
        repeat(4) {
            if (p == null || p is PsiFile) return@repeat
            if (TanStackApiContext.findCreateFileRoutePathAt(p) != null) return true
            if (isRouteExportBinding(p, fileText)) return true
            p = p!!.parent
        }
        return false
    }

    /**
     * Detect `Route` (or any name) in `const Route = createFileRoute(...)` / `export const Route = ...`.
     */
    fun isRouteExportBinding(element: PsiElement, fileText: String): Boolean {
        val name = element.text?.trim() ?: return false
        return isRouteExportBindingAt(name, element.textOffset, fileText)
    }

    fun isRouteExportBindingAt(name: String, offset: Int, fileText: String): Boolean {
        if (name.isEmpty() || name.length > 80) return false
        if (!name.matches(IDENTIFIER)) return false
        // Don't steal Find Usages for common non-route identifiers
        if (name in NON_ROUTE_BINDINGS) return false

        val windowStart = (offset - 60).coerceAtLeast(0)
        val windowEnd = (offset + name.length + 160).coerceAtMost(fileText.length)
        if (windowStart >= windowEnd) return false
        val window = fileText.substring(windowStart, windowEnd)

        // name = createFileRoute(  OR  name=createLazyFileRoute(
        val assign = Regex(
            """\b${Regex.escape(name)}\s*=\s*(?:createFileRoute|createLazyFileRoute|createRoute)\s*\(""",
        )
        return assign.containsMatchIn(window)
    }

    /** Pure text helper for tests / offset checks without full PSI. */
    fun isRouteDefinitionOffset(fileText: String, offset: Int): Boolean {
        if (offset < 0 || offset > fileText.length) return false
        if (TanStackApiContext.isInsideRouteFactoryCall(fileText, offset)) return true
        // export binding name near createFileRoute assignment
        val name = readIdentifierAt(fileText, offset) ?: return false
        return isRouteExportBindingAt(name, offset, fileText)
    }

    private fun readIdentifierAt(fileText: String, offset: Int): String? {
        if (offset >= fileText.length) return null
        var start = offset
        while (start > 0 && isIdentChar(fileText[start - 1])) start--
        var end = offset
        while (end < fileText.length && isIdentChar(fileText[end])) end++
        if (start >= end) return null
        return fileText.substring(start, end)
    }

    private fun isIdentChar(c: Char): Boolean =
        c.isLetterOrDigit() || c == '_' || c == '$'

    private fun isRouteFactoryNameLeaf(element: PsiElement): Boolean {
        val text = element.text?.trim() ?: return false
        if (text !in FACTORY_NAMES) return false
        val fileText = element.containingFile?.text ?: return false
        val offset = element.textOffset
        val afterEnd = (offset + text.length).coerceAtMost(fileText.length)
        val after = fileText.substring(afterEnd, minOf(fileText.length, afterEnd + 48))
        if (after.trimStart().startsWith("(")) return true
        return TanStackApiContext.isInsideRouteFactoryCall(fileText, offset)
    }

    private fun expandWithIndexVariants(
        project: com.intellij.openapi.project.Project,
        seeds: List<String>,
        contextFile: VirtualFile?,
    ): List<String> {
        if (seeds.isEmpty()) return emptyList()
        val index = RouteIndexService.getInstance(project).getIndex()
        val out = linkedSetOf<String>()
        out.addAll(seeds)
        for (seed in seeds) {
            val vf = index.resolve(seed)
                ?: index.resolveLink(seed, null)
                ?: continue
            out.addAll(RouteInfoResolver.pathsForRouteFile(project, vf))
        }
        return out.toList()
    }

    private val IDENTIFIER = Regex("""[A-Za-z_$][\w$]*""")

    private val NON_ROUTE_BINDINGS = setOf(
        "component", "loader", "beforeLoad", "pendingComponent", "errorComponent",
        "notFoundComponent", "validateSearch", "headers", "ssr", "gcTime", "staleTime",
        "params", "search", "deps", "context", "onEnter", "onLeave", "onCatch",
        "pendingMs", "pendingMinMs", "wrapInSuspense", "remountDeps", "preload",
        "caseSensitive", "content", "children",
    )
}

class TanStackRouteFindUsagesHandler(
    element: PsiElement,
    private val routePaths: List<String>,
) : FindUsagesHandler(element) {

    override fun getPrimaryElements(): Array<PsiElement> = arrayOf(psiElement)

    override fun getSecondaryElements(): Array<PsiElement> = PsiElement.EMPTY_ARRAY

    override fun getFindUsagesOptions(dataContext: com.intellij.openapi.actionSystem.DataContext?): FindUsagesOptions {
        val options = super.getFindUsagesOptions(dataContext)
        // We only contribute structured route-link usages; skip noisy text search.
        options.isSearchForTextOccurrences = false
        return options
    }

    override fun processElementUsages(
        element: PsiElement,
        processor: Processor<in UsageInfo>,
        options: FindUsagesOptions,
    ): Boolean {
        val project = element.project
        val indicator = ProgressManager.getInstance().progressIndicator
        val usages = RouteUsageFinder.findUsages(project, routePaths, indicator)

        for (usage in usages) {
            indicator?.checkCanceled()
            val info = ReadAction.compute<UsageInfo?, RuntimeException> {
                toUsageInfo(project, usage.file, usage.offset, usage.toValue)
            } ?: continue
            if (!processor.process(info)) return false
        }
        return true
    }

    override fun findReferencesToHighlight(
        target: PsiElement,
        searchScope: com.intellij.psi.search.SearchScope,
    ): Collection<PsiReference> {
        // Highlighting uses processElementUsages path; no extra PSI refs required.
        return emptyList()
    }

    private fun toUsageInfo(
        project: com.intellij.openapi.project.Project,
        file: VirtualFile,
        offset: Int,
        toValue: String,
    ): UsageInfo? {
        val psiFile = PsiManager.getInstance(project).findFile(file) ?: return null
        val safeOffset = offset.coerceIn(0, (psiFile.textLength - 1).coerceAtLeast(0))
        val el = psiFile.findElementAt(safeOffset) ?: psiFile
        // Prefer the string leaf that contains the path
        var host: PsiElement = el
        var p: PsiElement? = el
        repeat(6) {
            if (p == null) return@repeat
            if (p!!.text?.contains(toValue) == true && p!!.textLength < 400) {
                host = p!!
            }
            p = p!!.parent
        }
        return UsageInfo(host)
    }
}
