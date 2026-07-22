package com.ipfsoftwares.jbplugin.usages

import com.intellij.find.findUsages.CustomUsageSearcher
import com.intellij.find.findUsages.FindUsagesOptions
import com.intellij.psi.PsiElement
import com.intellij.usages.Usage
import com.intellij.util.Processor

/**
 * No-op.
 *
 * Link-site discovery is owned by:
 * - [TanStackRouteUsageSearcher] (SearchTarget / Alt+F7)
 * - [TanStackRouteFindUsagesHandler] (legacy PSI handler)
 *
 * Running a third injector here double-counted the same `to=` sites and could disagree
 * with the Routes tool window.
 */
class TanStackRouteCustomUsageSearcher : CustomUsageSearcher() {

    override fun processElementUsages(
        element: PsiElement,
        processor: Processor<in Usage>,
        options: FindUsagesOptions,
    ) {
        // intentionally empty
    }
}
