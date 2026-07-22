package com.ipfsoftwares.jbplugin.navigation

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.xml.XmlAttribute
import com.intellij.psi.xml.XmlAttributeValue

/**
 * Detects TanStack `to` / `from` string targets in JS/TS/TSX.
 *
 * Supports:
 * - `to="/absolute"`
 * - `to={"/absolute"}` / `to={'/absolute'}`
 * - `to: '/absolute'` in object literals
 * - relative targets: `.`, `..`, `../edit`, `edit`
 */
object TanStackLinkContext {

    private val QUOTED = Regex("""^(['"`])(.*)\1$""", RegexOption.DOT_MATCHES_ALL)
    private val CREATE_FILE_ROUTE =
        Regex("""createFileRoute\s*\(\s*(['"`])([^'"`]+)\1""")

    data class LinkTarget(
        val to: String,
        val from: String? = null,
    )

    /**
     * If [element] sits on a TanStack `to` value, returns the path and optional sibling `from`.
     */
    fun findLinkTargetAt(element: PsiElement?): LinkTarget? {
        if (element == null) return null
        val to = findRoutePathAt(element) ?: return null
        val from = findSiblingFromValue(element)
        return LinkTarget(to = to, from = from)
    }

    /**
     * If [element] (or an ancestor leaf cluster) is a string value of a `to` prop/attribute,
     * returns the unquoted route path (absolute or relative).
     */
    fun findRoutePathAt(element: PsiElement?): String? {
        if (element == null) return null

        // 1) Innermost quoted literal first (handles to={"/x"} when caret is on the string)
        var current: PsiElement? = element
        var depth = 0
        while (current != null && depth < 16) {
            val literalPath = coerceRoutePath(current.text)
            if (literalPath != null && isToValueElement(current)) {
                return literalPath
            }
            current = current.parent
            depth++
        }

        // 2) JSX/XML attribute fallback (to="/x" or to={"/x"})
        findFromXml(element)?.let { return it }

        return null
    }

    /**
     * Best-effort base route for relative `to` resolution:
     * 1) explicit `from` on the same link/navigate call
     * 2) `createFileRoute('...')` in the containing file
     */
    fun findBaseRouteHint(element: PsiElement, explicitFrom: String?): String? {
        if (!explicitFrom.isNullOrBlank()) return coerceRoutePath(explicitFrom) ?: explicitFrom.trim()
        return findCreateFileRouteId(element.containingFile)
    }

    fun findCreateFileRouteId(file: PsiFile?): String? {
        if (file == null) return null
        val text = file.text ?: return null
        return CREATE_FILE_ROUTE.find(text)?.groupValues?.get(2)
    }

    private fun findFromXml(element: PsiElement): String? {
        val attrValue = PsiTreeUtil.getParentOfType(element, XmlAttributeValue::class.java, false)
        val attr = attrValue?.parent as? XmlAttribute
            ?: PsiTreeUtil.getParentOfType(element, XmlAttribute::class.java, false)
            ?: return null
        if (attr.name != "to") return null
        val raw = attrValue?.value ?: attr.value ?: return null
        return coerceRoutePath(raw)
    }

    private fun findSiblingFromValue(element: PsiElement): String? {
        // Walk up looking for a parent text/scope that contains both to and from.
        var scope: PsiElement? = element
        repeat(14) {
            if (scope == null) return null
            val fromInScope = extractNamedStringProp(scope!!.text, "from")
            if (fromInScope != null) {
                // Ensure this scope is related to a `to` (same call / tag props)
                val toInScope = extractNamedStringProp(scope!!.text, "to")
                if (toInScope != null) return fromInScope
            }
            scope = scope!!.parent
        }
        return null
    }

    /**
     * Extract a simple string prop from a small PSI text chunk:
     * `from: '/x'`, `from="/x"`, `from={"/x"}`.
     */
    fun extractNamedStringProp(text: String?, propName: String): String? {
        if (text.isNullOrBlank()) return null
        // Keep this bounded — only use on relatively small PSI nodes.
        if (text.length > 4000) return null
        val patterns = listOf(
            Regex("""\b$propName\s*[:=]\s*\{?\s*(['"`])([^'"`]+)\1\s*\}?"""),
            Regex("""\b$propName\s*=\s*\{?\s*(['"`])([^'"`]+)\1\s*\}?"""),
        )
        for (re in patterns) {
            val m = re.find(text) ?: continue
            val value = m.groupValues[2]
            if (isPlausibleRoutePath(value)) return value
        }
        return null
    }

    /**
     * Normalize raw attribute / literal text into a route path string.
     * Handles: `/x`, `'/x'`, `"/x"`, `{ '/x' }`, `{"/x"}`.
     */
    fun coerceRoutePath(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        var s = raw.trim()
        // Unwrap a single JSX expression layer: {"/x"} or { '/x' }
        if (s.startsWith("{") && s.endsWith("}") && s.length >= 2) {
            s = s.substring(1, s.length - 1).trim()
        }
        extractQuoted(s)?.let { s = it }
        // Strip accidental trailing JSX/expression leftovers
        s = s.trim().trimEnd('}', ' ', '\t')
        return s.takeIf { isPlausibleRoutePath(it) }
    }

    /**
     * Heuristic: the element is the value side of a `to` property/attribute.
     */
    private fun isToValueElement(literalLike: PsiElement): Boolean {
        val literalPath = coerceRoutePath(literalLike.text) ?: return false
        // Never treat the identifier `to` itself as the value
        if (literalPath == "to" && extractQuoted(literalLike.text) == null) return false

        var p = literalLike.parent ?: return false
        repeat(10) {
            // XmlAttribute: name is first meaningful child
            if (p is XmlAttribute && p.name == "to") {
                return true
            }

            val first = p.firstChild
            if (first != null) {
                val name = first.text.trim('"', '\'', '`', ' ', '{', '}')
                if (name == "to" && literalLike.text != first.text) {
                    return true
                }
            }

            val trimmed = p.text.trim()
            if (trimmed.startsWith("to=") ||
                trimmed.startsWith("to =") ||
                trimmed.startsWith("to:") ||
                trimmed.startsWith("to :") ||
                trimmed.startsWith("\"to\"") ||
                trimmed.startsWith("'to'") ||
                trimmed.startsWith("{to") // rare
            ) {
                if (PsiTreeUtil.isAncestor(p, literalLike, false) && coerceRoutePath(literalLike.text) != null) {
                    // Avoid matching the whole file / huge blocks
                    if (p.textLength < 2500) return true
                }
            }

            if (propertyNamedTo(p) && PsiTreeUtil.isAncestor(p, literalLike, true)) {
                return true
            }

            // JSX attribute value container whose parent attribute is `to`
            val attr = p as? XmlAttribute
                ?: (p.parent as? XmlAttribute)
            if (attr?.name == "to" && PsiTreeUtil.isAncestor(attr, literalLike, false)) {
                return true
            }

            p = p.parent ?: return false
        }
        return false
    }

    private fun propertyNamedTo(element: PsiElement): Boolean {
        val children = element.children
        if (children.isEmpty()) return false
        val nameText = children.firstOrNull()?.text?.trim('"', '\'', '`', ' ') ?: return false
        return nameText == "to"
    }

    fun extractQuoted(text: String?): String? {
        if (text.isNullOrBlank()) return null
        val t = text.trim()
        val m = QUOTED.matchEntire(t) ?: return null
        val body = m.groupValues[2]
        // Skip template literals with substitutions
        if (m.groupValues[1] == "`" && body.contains("\${")) return null
        return body
    }

    fun isPlausibleRoutePath(path: String): Boolean {
        if (path.isEmpty()) return false
        if (path.startsWith("http://") || path.startsWith("https://") || path.startsWith("mailto:")) {
            return false
        }
        if (path.contains(' ') || path.contains('\n') || path.contains('\r')) return false
        // JSX/TS leftovers mean we failed to coerce
        if (path.startsWith("{") || path.endsWith("}")) return false
        if (path.startsWith("<") || path.contains("=>")) return false

        // Absolute TanStack paths
        if (path.startsWith("/")) return true

        // Relative targets used by TanStack Router
        if (path == "." || path == "..") return true
        if (path.startsWith("./") || path.startsWith("../")) return true

        // Bare relative segment(s): "edit", "search/nida"
        if (path.startsWith("http")) return false
        return path.all { it.isLetterOrDigit() || it == '/' || it == '$' || it == '_' || it == '-' || it == '.' }
            && path.any { it.isLetterOrDigit() || it == '$' || it == '.' }
    }

    fun isRelativeRoutePath(path: String): Boolean {
        if (path.isEmpty()) return false
        if (path.startsWith("/")) return false
        return isPlausibleRoutePath(path)
    }
}
