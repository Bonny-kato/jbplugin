package com.ipfsoftwares.jbplugin.usages

import com.intellij.model.psi.PsiSymbolDeclaration
import com.intellij.model.psi.PsiSymbolDeclarationProvider
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile

/**
 * Declares a [TanStackRouteSymbol] only on tight TanStack route definition surfaces
 * (`createFileRoute` name, path arg, `export const Route = ...`).
 *
 * Must not declare on [PsiFile] or arbitrary body PSI — a file-wide declaration range
 * makes Cmd-hover underline the whole module and Alt+F7 offer the route for every symbol.
 */
class TanStackRouteSymbolDeclarationProvider : PsiSymbolDeclarationProvider {

    override fun getDeclarations(
        element: PsiElement,
        offsetInElement: Int,
    ): Collection<PsiSymbolDeclaration> {
        if (element is PsiFile) return emptyList()
        // Only the surface element itself — not large parents that contain the call.
        if (!RouteFindUsagesSupport.isStrictDefinitionSurface(element)) return emptyList()

        val symbol = TanStackRouteSymbol.fromElement(element) ?: return emptyList()
        return listOf(TanStackRouteSymbolDeclaration(element, symbol))
    }
}
