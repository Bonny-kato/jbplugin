package com.ipfsoftwares.jbplugin.usages

import com.intellij.find.usages.api.PsiUsage
import com.intellij.find.usages.api.Usage
import com.intellij.find.usages.api.UsageSearchParameters
import com.intellij.find.usages.api.UsageSearcher
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiManager
import com.intellij.util.CollectionQuery
import com.intellij.util.Query
import com.ipfsoftwares.jbplugin.route.RouteUsageFinder

/**
 * Collects real `to=` link/navigate/redirect call sites for [TanStackRouteSymbol].
 */
class TanStackRouteUsageSearcher : UsageSearcher {

    override fun collectSearchRequests(
        parameters: UsageSearchParameters,
    ): Collection<Query<out Usage>> {
        val target = parameters.target as? TanStackRouteSymbol ?: return emptyList()
        val project = parameters.project

        val usages = RouteUsageFinder.findUsages(project, target.paths)
        if (usages.isEmpty()) {
            return listOf(CollectionQuery(emptyList()))
        }

        val psiUsages = ReadAction.compute<List<Usage>, RuntimeException> {
            val out = ArrayList<Usage>(usages.size)
            val manager = PsiManager.getInstance(project)
            for (usage in usages) {
                val psiFile = manager.findFile(usage.file) ?: continue
                val start = usage.offset.coerceIn(0, psiFile.textLength)
                val end = (start + usage.toValue.length).coerceAtMost(psiFile.textLength)
                if (start >= end) continue
                out.add(PsiUsage.textUsage(psiFile, TextRange(start, end)))
            }
            out
        }

        return listOf(CollectionQuery(psiUsages))
    }
}
