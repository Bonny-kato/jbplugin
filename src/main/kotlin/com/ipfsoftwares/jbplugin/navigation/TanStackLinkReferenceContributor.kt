package com.ipfsoftwares.jbplugin.navigation

import com.intellij.openapi.util.TextRange
import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiReference
import com.intellij.psi.PsiReferenceContributor
import com.intellij.psi.PsiReferenceProvider
import com.intellij.psi.PsiReferenceRegistrar
import com.intellij.psi.xml.XmlAttributeValue
import com.intellij.util.ProcessingContext

/**
 * Registers a single reference on the canonical TanStack `to` host element.
 */
class TanStackLinkReferenceContributor : PsiReferenceContributor() {

    override fun registerReferenceProviders(registrar: PsiReferenceRegistrar) {
        val provider = TanStackLinkReferenceProvider()

        registrar.registerReferenceProvider(
            PlatformPatterns.psiElement(XmlAttributeValue::class.java),
            provider,
            PsiReferenceRegistrar.HIGHER_PRIORITY,
        )

        // JS/TS string literals and small expression hosts
        registrar.registerReferenceProvider(
            PlatformPatterns.psiElement(),
            provider,
            PsiReferenceRegistrar.HIGHER_PRIORITY,
        )
    }
}

class TanStackLinkReferenceProvider : PsiReferenceProvider() {

    override fun getReferencesByElement(
        element: PsiElement,
        context: ProcessingContext,
    ): Array<PsiReference> {
        if (element is PsiFile) return PsiReference.EMPTY_ARRAY
        if (!TanStackRouteAnchor.isReferenceHost(element)) return PsiReference.EMPTY_ARRAY

        val link = TanStackLinkContext.findLinkTargetAt(element) ?: return PsiReference.EMPTY_ARRAY
        val routePath = link.to
        val text = element.text ?: return PsiReference.EMPTY_ARRAY
        val startInElement = findPathOffsetInText(text, routePath) ?: return PsiReference.EMPTY_ARRAY
        val range = TextRange(startInElement, startInElement + routePath.length)
        if (range.startOffset < 0 || range.endOffset > text.length) {
            return PsiReference.EMPTY_ARRAY
        }

        val baseHint = TanStackLinkContext.findBaseRouteHint(element, link.from)
        return arrayOf(TanStackRouteReference(element, range, routePath, baseHint))
    }

    private fun findPathOffsetInText(text: String, routePath: String): Int? {
        val quotedDouble = text.indexOf("\"$routePath\"")
        if (quotedDouble >= 0) return quotedDouble + 1
        val quotedSingle = text.indexOf("'$routePath'")
        if (quotedSingle >= 0) return quotedSingle + 1
        val quotedTick = text.indexOf("`$routePath`")
        if (quotedTick >= 0) return quotedTick + 1
        val direct = text.indexOf(routePath)
        if (direct >= 0) return direct
        return null
    }
}
