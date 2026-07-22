package com.ipfsoftwares.jbplugin.usages

import com.intellij.find.usages.api.SearchTarget
import com.intellij.find.usages.symbol.SymbolSearchTargetFactory
import com.intellij.openapi.project.Project

/**
 * Maps [TanStackRouteSymbol] → Find Usages [SearchTarget] (the symbol itself).
 */
class TanStackRouteSymbolSearchTargetFactory : SymbolSearchTargetFactory<TanStackRouteSymbol> {

    override fun searchTarget(project: Project, symbol: TanStackRouteSymbol): SearchTarget = symbol
}
