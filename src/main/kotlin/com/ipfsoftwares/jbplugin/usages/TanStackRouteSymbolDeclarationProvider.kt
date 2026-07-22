package com.ipfsoftwares.jbplugin.usages

import com.intellij.model.psi.PsiSymbolDeclaration
import com.intellij.model.psi.PsiSymbolDeclarationProvider
import com.intellij.psi.PsiElement

/**
 * Declares a [TanStackRouteSymbol] on TanStack route definition surfaces
 * (`createFileRoute`, path arg, `export const Route = ...`).
 */
class TanStackRouteSymbolDeclarationProvider : PsiSymbolDeclarationProvider {

    override fun getDeclarations(
        element: PsiElement,
        offsetInElement: Int,
    ): Collection<PsiSymbolDeclaration> {
        val symbol = TanStackRouteSymbol.fromElement(element) ?: return emptyList()
        return listOf(TanStackRouteSymbolDeclaration(element, symbol))
    }
}
