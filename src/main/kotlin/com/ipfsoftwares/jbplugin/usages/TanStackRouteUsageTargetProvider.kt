package com.ipfsoftwares.jbplugin.usages

import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.usages.UsageTarget
import com.intellij.usages.UsageTargetProvider

/**
 * Intentionally returns no targets.
 *
 * Alt+F7 is owned by the modern SEARCH_TARGETS path ([TanStackSearchTargetsDataRule] +
 * [TanStackRouteSymbol] + [TanStackRouteUsageSearcher]). Contributing USAGE_TARGETS here
 * made WebStorm show **two** identical "TanStack route" chooser rows; picking either then
 * disagreed with the Routes tool window / showed "Nothing found".
 *
 * Legacy [TanStackRouteFindUsagesHandlerFactory] still covers PSI Find Usages handlers.
 */
class TanStackRouteUsageTargetProvider : UsageTargetProvider {

    override fun getTargets(editor: Editor, file: PsiFile): Array<UsageTarget>? = null

    override fun getTargets(psiElement: PsiElement): Array<UsageTarget>? = null
}
