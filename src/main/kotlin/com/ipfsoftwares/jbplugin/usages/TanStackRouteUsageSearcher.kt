package com.ipfsoftwares.jbplugin.usages

import com.intellij.find.usages.api.PsiUsage
import com.intellij.find.usages.api.Usage
import com.intellij.find.usages.api.UsageSearchParameters
import com.intellij.find.usages.api.UsageSearcher
import com.intellij.openapi.application.runReadActionBlocking
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiManager
import com.intellij.util.CollectionQuery
import com.intellij.util.Query
import com.ipfsoftwares.jbplugin.route.RouteUsageFinder

/**
 * Collects real `to=` link/navigate/redirect call sites for [TanStackRouteSymbol].
 * Uses the same [RouteUsageFinder] path set as the Routes tool window.
 */
class TanStackRouteUsageSearcher : UsageSearcher {

    override fun collectSearchRequests(
        parameters: UsageSearchParameters,
    ): Collection<Query<out Usage>> {
        val target = parameters.target as? TanStackRouteSymbol ?: return emptyList()
        val project = parameters.project
        val paths = target.paths
        if (paths.isEmpty()) return emptyList()

        // RouteUsageFinder already takes a read action; keep collection off EDT-sensitive paths.
        val usages = RouteUsageFinder.findUsages(project, paths)
        if (usages.isEmpty()) {
            return emptyList()
        }

        val psiUsages = runReadActionBlocking {
            val out = ArrayList<Usage>(usages.size)
            val manager = PsiManager.getInstance(project)
            val seen = HashSet<String>()
            for (usage in usages) {
                val psiFile = manager.findFile(usage.file) ?: continue
                val textLen = psiFile.textLength
                if (textLen <= 0) continue
                val start = usage.offset.coerceIn(0, textLen)
                val end = (start + usage.toValue.length).coerceAtMost(textLen)
                if (start >= end) continue
                val key = "${usage.file.path}:$start:$end"
                if (!seen.add(key)) continue
                // Non-declaration text usage so the usage view keeps the result.
                out.add(PsiUsage.textUsage(psiFile, TextRange(start, end)))
            }
            out
        }

        if (psiUsages.isEmpty()) return emptyList()
        return listOf(CollectionQuery(psiUsages))
    }
}
