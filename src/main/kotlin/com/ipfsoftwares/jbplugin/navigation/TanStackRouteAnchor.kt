package com.ipfsoftwares.jbplugin.navigation

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.xml.XmlAttribute
import com.intellij.psi.xml.XmlAttributeValue

/**
 * Picks a single canonical PSI host + gutter anchor for a TanStack `to` value
 * so references and line markers are not duplicated on `{`, quotes, and leaves.
 */
object TanStackRouteAnchor {

    data class Anchor(
        /** Element that owns the reference range (literal / attribute value). */
        val host: PsiElement,
        /** Leaf used as gutter line-marker target (stable content leaf of [host]). */
        val gutterLeaf: PsiElement,
        val link: TanStackLinkContext.LinkTarget,
    )

    /**
     * If [element] is the dedicated gutter leaf for a TanStack `to` value, return the anchor.
     * Returns null for every other PSI node of the same `to` (quotes, braces, parents).
     */
    fun findUniqueGutterAnchor(element: PsiElement): Anchor? {
        // Gutter markers must be registered on leaves only.
        if (element.firstChild != null) return null

        val host = findCanonicalHost(element) ?: return null
        val link = TanStackLinkContext.findLinkTargetAt(host)
            ?: TanStackLinkContext.findLinkTargetAt(element)
            ?: return null

        val leaf = preferredGutterLeaf(host)
        if (element != leaf) return null

        return Anchor(host = host, gutterLeaf = leaf, link = link)
    }

    /**
     * Canonical host for attaching a PsiReference (attribute value or quoted literal).
     */
    fun findCanonicalHost(element: PsiElement?): PsiElement? {
        if (element == null || element is PsiFile) return null

        // 1) JSX/XML: attribute value of `to=...`
        val attrValue = PsiTreeUtil.getParentOfType(element, XmlAttributeValue::class.java, false)
        if (attrValue != null) {
            val attr = attrValue.parent as? XmlAttribute
            if (attr?.name == "to") {
                val path = TanStackLinkContext.coerceRoutePath(attrValue.value)
                    ?: TanStackLinkContext.coerceRoutePath(attrValue.text)
                if (path != null) return attrValue
            }
        }

        // 2) Walk up looking for a small element whose entire text is the quoted path / JSX expr
        var current: PsiElement? = element
        var depth = 0
        var best: PsiElement? = null
        while (current != null && depth < 14 && current !is PsiFile) {
            val text = current.text
            if (text != null && text.length <= 500) {
                val coerced = TanStackLinkContext.coerceQuotedRoutePath(text)
                if (coerced != null && TanStackLinkContext.isPlausibleRoutePath(coerced)) {
                    // Must be a `to` value, not an arbitrary string
                    if (isElementToValue(current, coerced)) {
                        // Prefer the tightest (shortest) host that still contains the full path literal
                        if (best == null || current.textLength < best.textLength) {
                            best = current
                        }
                    }
                }
            }
            current = current.parent
            depth++
        }
        return best
    }

    /**
     * Whether this element is an appropriate reference host (not a giant parent).
     */
    fun isReferenceHost(element: PsiElement): Boolean {
        if (element is PsiFile) return false
        if (element is XmlAttributeValue) {
            val attr = element.parent as? XmlAttribute
            val raw = element.value.orEmpty().ifEmpty { element.text }
            return attr?.name == "to" &&
                TanStackLinkContext.coerceRoutePath(raw) != null
        }
        val text = element.text ?: return false
        if (text.length > 500) return false
        val path = TanStackLinkContext.coerceQuotedRoutePath(text) ?: return false
        // Host text should be essentially just the path (quoted or `{...}`)
        val stripped = text.trim()
        val isTight =
            stripped == "\"$path\"" ||
                stripped == "'$path'" ||
                stripped == "`$path`" ||
                stripped == "{\"$path\"}" ||
                stripped == "{'$path'}" ||
                stripped == "{`$path`}" ||
                stripped == "{ \"$path\" }" ||
                stripped == "{ '$path' }"
        if (!isTight) return false
        return isElementToValue(element, path)
    }

    /**
     * Prefer the leaf that carries path characters (not `{`, `}`, or quotes).
     * Falls back to the deepest-first leaf so we always have exactly one anchor.
     */
    fun preferredGutterLeaf(host: PsiElement): PsiElement {
        if (host.firstChild == null) return host

        val hostRange = host.textRange
        var leaf: PsiElement? = PsiTreeUtil.getDeepestFirst(host)
        val fallback = leaf ?: host
        while (leaf != null && hostRange.contains(leaf.textRange)) {
            if (leaf.firstChild == null) {
                val text = leaf.text
                if (!text.isNullOrEmpty() && text.any { isPathChar(it) }) {
                    return leaf
                }
            }
            val next = PsiTreeUtil.nextLeaf(leaf) ?: break
            if (!hostRange.contains(next.textRange)) break
            leaf = next
        }
        return fallback
    }

    private fun isPathChar(c: Char): Boolean =
        c.isLetterOrDigit() || c == '/' || c == '$' || c == '_' || c == '.' || c == '-' || c == '%'

    private fun isElementToValue(element: PsiElement, path: String): Boolean {
        val found = TanStackLinkContext.findRoutePathAt(element) ?: return false
        return found == path
    }
}
