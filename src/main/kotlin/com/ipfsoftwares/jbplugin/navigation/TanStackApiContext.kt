package com.ipfsoftwares.jbplugin.navigation

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.xml.XmlAttribute
import com.intellij.psi.xml.XmlTag

/**
 * Detects whether a `to` value sits in a TanStack Router / Start API context,
 * and labels known call/component forms for docs and tooling.
 *
 * Covered patterns:
 * - `<Link to>`, `<Navigate to>`, `<MatchRoute to>`, `*Link` wrappers (e.g. ButtonLink)
 * - `navigate({ to })`, `redirect({ to })`, `linkOptions({ to })`
 * - generic object `to:` when clearly nested under those calls
 */
object TanStackApiContext {

    enum class Kind {
        LINK_JSX,
        NAVIGATE_JSX,
        MATCH_ROUTE_JSX,
        LINK_WRAPPER_JSX,
        NAVIGATE_CALL,
        REDIRECT_CALL,
        LINK_OPTIONS_CALL,
        TO_PROP,
        CREATE_FILE_ROUTE,
        UNKNOWN,
    }

    data class Context(
        val kind: Kind,
        /** Component or call name when known (`Link`, `navigate`, …). */
        val apiName: String?,
    ) {
        val displayName: String
            get() = apiName ?: when (kind) {
                Kind.LINK_JSX -> "Link"
                Kind.NAVIGATE_JSX -> "Navigate"
                Kind.MATCH_ROUTE_JSX -> "MatchRoute"
                Kind.LINK_WRAPPER_JSX -> "Link"
                Kind.NAVIGATE_CALL -> "navigate"
                Kind.REDIRECT_CALL -> "redirect"
                Kind.LINK_OPTIONS_CALL -> "linkOptions"
                Kind.TO_PROP -> "to"
                Kind.CREATE_FILE_ROUTE -> "createFileRoute"
                Kind.UNKNOWN -> "route"
            }
    }

    private val CALL_NAMES = mapOf(
        "navigate" to Kind.NAVIGATE_CALL,
        "redirect" to Kind.REDIRECT_CALL,
        "linkOptions" to Kind.LINK_OPTIONS_CALL,
    )

    private val JSX_EXACT = mapOf(
        "Link" to Kind.LINK_JSX,
        "Navigate" to Kind.NAVIGATE_JSX,
        "MatchRoute" to Kind.MATCH_ROUTE_JSX,
    )

    private val FACTORY_NAMES = setOf(
        "createFileRoute",
        "createLazyFileRoute",
        "createRoute",
    )

    private val CALL_NEARBY = Regex(
        """\b(navigate|redirect|linkOptions)\s*\(""",
    )

    private val FACTORY_NEARBY = Regex(
        """\b(createFileRoute|createLazyFileRoute|createRoute)\s*\(""",
    )

    /**
     * Best-effort API context for [element] sitting on / near a route path string.
     */
    fun findContext(element: PsiElement?): Context? {
        if (element == null) return null

        findJsxContext(element)?.let { return it }
        findCallContext(element)?.let { return it }
        findFactoryContext(element)?.let { return it }

        // Fall back: any `to` value we already classify as a link target
        if (TanStackLinkContext.findRoutePathAt(element) != null) {
            return Context(Kind.TO_PROP, "to")
        }
        return null
    }

    /**
     * True when this looks like a TanStack navigation `to` (JSX or call), not an arbitrary prop.
     * Used to reduce false positives for inspection/completion outside real router APIs.
     */
    fun isTanStackToContext(element: PsiElement?): Boolean {
        if (element == null) return false
        val ctx = findContext(element) ?: return false
        return when (ctx.kind) {
            Kind.CREATE_FILE_ROUTE -> false // path is the definition, not a link
            Kind.UNKNOWN -> false
            else -> true
        }
    }

    /**
     * If [element] is on the path argument of `createFileRoute('...')` (or lazy/code factory),
     * returns that path string.
     */
    fun findCreateFileRoutePathAt(element: PsiElement?): String? {
        if (element == null) return null
        var current: PsiElement? = element
        var depth = 0
        while (current != null && depth < 12 && current !is PsiFile) {
            val text = current.text ?: break
            if (text.length in 1..800) {
                val path = extractFactoryPath(text)
                if (path != null && textContainsOffsetOf(current, element)) {
                    // Prefer tight hosts that are essentially the path literal
                    val coerced = TanStackLinkContext.coerceRoutePath(current.text)
                    if (coerced == path || text.trim().let {
                            it == "\"$path\"" || it == "'$path'" || it == "`$path`" ||
                                it.startsWith("createFileRoute") ||
                                it.startsWith("createLazyFileRoute") ||
                                it.startsWith("createRoute")
                        }
                    ) {
                        return path
                    }
                }
            }
            current = current.parent
            depth++
        }

        // Broader: file-level scan near caret offset
        val file = element.containingFile ?: return null
        val fileText = file.text ?: return null
        val offset = element.textOffset
        return findFactoryPathNearOffset(fileText, offset)
    }

    fun isInsideRouteFactoryCall(fileText: String, offset: Int): Boolean {
        if (offset < 0 || offset > fileText.length) return false
        val windowStart = (offset - 250).coerceAtLeast(0)
        val windowEnd = (offset + 200).coerceAtMost(fileText.length)
        val window = fileText.substring(windowStart, windowEnd)

        // Full call with string path argument
        val withPath = Regex(
            """\b(createFileRoute|createLazyFileRoute|createRoute)\s*\(\s*(['"`])([^'"`]*)\2""",
        )
        for (m in withPath.findAll(window)) {
            val absStart = windowStart + m.range.first
            val absEnd = windowStart + m.range.last + 1
            if (offset in absStart..absEnd + 2) return true
        }

        // Caret on factory name (path may still be incomplete)
        val nameOnly = Regex("""\b(createFileRoute|createLazyFileRoute|createRoute)\b""")
        for (m in nameOnly.findAll(window)) {
            val absStart = windowStart + m.range.first
            val absEnd = windowStart + m.range.last + 1
            if (offset !in absStart until absEnd) continue
            val after = fileText.substring(absEnd, minOf(fileText.length, absEnd + 48))
            if (after.trimStart().startsWith("(")) return true
        }
        return false
    }

    private fun findJsxContext(element: PsiElement): Context? {
        val attr = PsiTreeUtil.getParentOfType(element, XmlAttribute::class.java, false)
            ?: return null
        if (attr.name != "to") return null
        val tag = attr.parent as? XmlTag ?: return null
        val name = tag.localName.ifBlank { tag.name }.substringAfterLast('.')
        JSX_EXACT[name]?.let { return Context(it, name) }
        if (name.endsWith("Link") && name.length > 4) {
            return Context(Kind.LINK_WRAPPER_JSX, name)
        }
        // Still a JSX `to` — common for typed custom links in TanStack apps
        return Context(Kind.TO_PROP, name.ifBlank { "to" })
    }

    private fun findCallContext(element: PsiElement): Context? {
        var scope: PsiElement? = element
        repeat(16) {
            if (scope == null || scope is PsiFile) return null
            val text = scope!!.text
            if (text != null && text.length < 2500) {
                val m = CALL_NEARBY.find(text)
                if (m != null) {
                    // Only accept if this scope also contains our `to` assignment
                    val toVal = TanStackLinkContext.extractNamedStringProp(text, "to")
                    if (toVal != null || text.contains(Regex("""\bto\s*:"""))) {
                        val name = m.groupValues[1]
                        val kind = CALL_NAMES[name] ?: Kind.TO_PROP
                        return Context(kind, name)
                    }
                }
            }
            scope = scope!!.parent
        }
        return null
    }

    private fun findFactoryContext(element: PsiElement): Context? {
        val path = findCreateFileRoutePathAt(element) ?: return null
        if (path.isEmpty()) return null
        return Context(Kind.CREATE_FILE_ROUTE, "createFileRoute")
    }

    private fun extractFactoryPath(text: String): String? {
        val m = Regex(
            """\b(?:createFileRoute|createLazyFileRoute|createRoute)\s*\(\s*(['"`])([^'"`]+)\1""",
        ).find(text) ?: return null
        return m.groupValues[2].takeIf { TanStackLinkContext.isPlausibleRoutePath(it) }
    }

    private fun findFactoryPathNearOffset(fileText: String, offset: Int): String? {
        if (!isInsideRouteFactoryCall(fileText, offset)) return null
        val windowStart = (offset - 250).coerceAtLeast(0)
        val windowEnd = (offset + 200).coerceAtMost(fileText.length)
        val window = fileText.substring(windowStart, windowEnd)
        val m = Regex(
            """\b(?:createFileRoute|createLazyFileRoute|createRoute)\s*\(\s*(['"`])([^'"`]+)\1""",
        ).find(window) ?: return null
        val path = m.groupValues[2]
        val pathStartInFile = windowStart + m.range.first + m.value.indexOf(path)
        val pathEnd = pathStartInFile + path.length
        if (offset in pathStartInFile..pathEnd || offset in m.range.first + windowStart..m.range.last + windowStart) {
            return path.takeIf { TanStackLinkContext.isPlausibleRoutePath(it) }
        }
        // Caret on factory name
        if (offset in (windowStart + m.range.first)..pathEnd) {
            return path.takeIf { TanStackLinkContext.isPlausibleRoutePath(it) }
        }
        return null
    }

    private fun textContainsOffsetOf(host: PsiElement, child: PsiElement): Boolean {
        val start = host.textOffset
        val end = start + host.textLength
        val c = child.textOffset
        return c in start until end || child == host
    }
}
