package com.ipfsoftwares.jbplugin.navigation

import com.intellij.openapi.util.TextRange
import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReference
import com.intellij.psi.PsiReferenceContributor
import com.intellij.psi.PsiReferenceProvider
import com.intellij.psi.PsiReferenceRegistrar
import com.intellij.psi.xml.XmlAttributeValue
import com.intellij.util.ProcessingContext

/**
 * Registers references on string literals / JSX attribute values used as TanStack `to` targets.
 */
class TanStackLinkReferenceContributor : PsiReferenceContributor() {

    override fun registerReferenceProviders(registrar: PsiReferenceRegistrar) {
        val provider = TanStackLinkReferenceProvider()

        // JSX/TSX: <Link to="/about" /> and to={"/about"}
        registrar.registerReferenceProvider(
            PlatformPatterns.psiElement(XmlAttributeValue::class.java),
            provider,
            PsiReferenceRegistrar.HIGHER_PRIORITY,
        )

        // Broad fallback for JS/TS string tokens (object props: to: '/about')
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
        // Avoid attaching to huge file roots
        if (element is com.intellij.psi.PsiFile) return PsiReference.EMPTY_ARRAY

        val text = element.text ?: return PsiReference.EMPTY_ARRAY
        val routePath = TanStackLinkContext.findRoutePathAt(element)
            ?: return PsiReference.EMPTY_ARRAY

        // Must be able to locate the path text inside this element (literal or attr value)
        val startInElement = findPathOffsetInText(text, routePath) ?: return PsiReference.EMPTY_ARRAY
        val range = TextRange(startInElement, startInElement + routePath.length)
        if (range.startOffset < 0 || range.endOffset > text.length) {
            return PsiReference.EMPTY_ARRAY
        }

        val baseHint = TanStackLinkContext.findBaseRouteHint(
            element,
            TanStackLinkContext.findLinkTargetAt(element)?.from,
        )

        return arrayOf(TanStackRouteReference(element, range, routePath, baseHint))
    }

    private fun findPathOffsetInText(text: String, routePath: String): Int? {
        // Direct occurrence (most common for plain string literals)
        val direct = text.indexOf(routePath)
        if (direct >= 0) {
            // Prefer occurrence inside quotes when possible
            val quotedDouble = text.indexOf("\"$routePath\"")
            if (quotedDouble >= 0) return quotedDouble + 1
            val quotedSingle = text.indexOf("'$routePath'")
            if (quotedSingle >= 0) return quotedSingle + 1
            val quotedTick = text.indexOf("`$routePath`")
            if (quotedTick >= 0) return quotedTick + 1
            return direct
        }
        return null
    }
}
