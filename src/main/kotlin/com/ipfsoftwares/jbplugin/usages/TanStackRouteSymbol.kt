package com.ipfsoftwares.jbplugin.usages

import com.intellij.find.usages.api.SearchTarget
import com.intellij.find.usages.api.UsageHandler
import com.intellij.icons.AllIcons
import com.intellij.model.Pointer
import com.intellij.model.Symbol
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.backend.presentation.TargetPresentation
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiManager
import com.intellij.psi.SmartPointerManager
import com.ipfsoftwares.jbplugin.JbPluginBundle
import com.ipfsoftwares.jbplugin.route.RouteDefinitionLocator

/**
 * Modern Find Usages symbol/target for a TanStack physical route.
 * Implements both [Symbol] and [SearchTarget] so Alt+F7 can bind to link call sites
 * instead of TypeScript's `routeTree.gen.ts` import graph.
 */
class TanStackRouteSymbol(
    val project: Project,
    val routeFile: VirtualFile,
    val paths: List<String>,
) : Symbol, SearchTarget {

    val preferredPath: String =
        com.ipfsoftwares.jbplugin.route.PhysicalRoute.pickPreferredPath(paths)
            .ifBlank { paths.firstOrNull() ?: routeFile.name }

    override fun createPointer(): Pointer<TanStackRouteSymbol> {
        val project = this.project
        val path = routeFile.path
        val pathsCopy = paths.toList()
        return Pointer {
            val vf = com.intellij.openapi.vfs.LocalFileSystem.getInstance().findFileByPath(path)
                ?: return@Pointer null
            TanStackRouteSymbol(project, vf, pathsCopy)
        }
    }

    override fun presentation(): TargetPresentation {
        return TargetPresentation
            .builder(JbPluginBundle.message("usages.target.presentable", preferredPath))
            .icon(AllIcons.Nodes.PpWeb)
            .locationText(routeFile.name)
            .presentation()
    }

    override val usageHandler: UsageHandler =
        UsageHandler.createEmptyUsageHandler(preferredPath)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is TanStackRouteSymbol) return false
        return routeFile.path == other.routeFile.path && paths == other.paths
    }

    override fun hashCode(): Int = 31 * routeFile.path.hashCode() + paths.hashCode()

    fun definitionElement(): PsiElement? =
        RouteDefinitionLocator.findDefinitionElement(project, routeFile, preferredPath)
            ?: PsiManager.getInstance(project).findFile(routeFile)

    companion object {
        /**
         * Build a symbol only for a strict route-definition surface in the editor.
         * Never from arbitrary PSI / whole files — that made Cmd-hover underline the
         * entire route module and stole Alt+F7 from normal variables.
         */
        fun fromElement(element: PsiElement): TanStackRouteSymbol? {
            if (element is com.intellij.psi.PsiFile) return null
            val paths = RouteFindUsagesSupport.pathsForDefinitionSurface(element) ?: return null
            val vf = element.containingFile?.virtualFile ?: return null
            return TanStackRouteSymbol(element.project, vf, paths)
        }
    }
}

/**
 * Declaration of [TanStackRouteSymbol] on a tight route-definition PSI element.
 * Range is the element itself only (identifier / path literal) — never a whole file.
 */
class TanStackRouteSymbolDeclaration(
    private val element: PsiElement,
    private val symbol: TanStackRouteSymbol,
) : com.intellij.model.psi.PsiSymbolDeclaration {

    private val pointer = SmartPointerManager.getInstance(element.project)
        .createSmartPsiElementPointer(element)

    override fun getDeclaringElement(): PsiElement =
        pointer.element ?: element

    override fun getRangeInDeclaringElement(): TextRange {
        val el = getDeclaringElement()
        // Keep the hyperlink / highlight on the small surface only.
        val len = el.textLength.coerceAtLeast(0).coerceAtMost(200)
        return TextRange(0, len)
    }

    override fun getSymbol(): Symbol = symbol
}
