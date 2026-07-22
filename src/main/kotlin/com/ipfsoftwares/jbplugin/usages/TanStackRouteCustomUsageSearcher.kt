package com.ipfsoftwares.jbplugin.usages

import com.intellij.find.findUsages.CustomUsageSearcher
import com.intellij.find.findUsages.FindUsagesOptions
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.progress.ProgressManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiManager
import com.intellij.usages.Usage
import com.intellij.usages.UsageInfo2UsageAdapter
import com.intellij.usageView.UsageInfo
import com.intellij.util.Processor
import com.ipfsoftwares.jbplugin.route.RouteUsageFinder

/**
 * When the IDE still uses TypeScript's Find Usages handler (e.g. on `export const Route`),
 * also inject real TanStack `to=` link call sites into the results.
 */
class TanStackRouteCustomUsageSearcher : CustomUsageSearcher() {

    override fun processElementUsages(
        element: PsiElement,
        processor: Processor<in Usage>,
        options: FindUsagesOptions,
    ) {
        val paths = ReadAction.compute<List<String>?, RuntimeException> {
            RouteFindUsagesSupport.pathsFor(element)
        } ?: return

        val project = element.project
        val indicator = ProgressManager.getInstance().progressIndicator
        val usages = RouteUsageFinder.findUsages(project, paths, indicator)

        for (usage in usages) {
            indicator?.checkCanceled()
            val info = ReadAction.compute<UsageInfo?, RuntimeException> {
                val psiFile = PsiManager.getInstance(project).findFile(usage.file) ?: return@compute null
                val safeOffset = usage.offset.coerceIn(0, (psiFile.textLength - 1).coerceAtLeast(0))
                val el = psiFile.findElementAt(safeOffset) ?: psiFile
                var host: PsiElement = el
                var p: PsiElement? = el
                repeat(6) {
                    if (p == null) return@repeat
                    if (p!!.text?.contains(usage.toValue) == true && p!!.textLength < 400) {
                        host = p!!
                    }
                    p = p!!.parent
                }
                UsageInfo(host)
            } ?: continue

            if (!processor.process(UsageInfo2UsageAdapter(info))) return
        }
    }
}
