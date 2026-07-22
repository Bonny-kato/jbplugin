package com.ipfsoftwares.jbplugin.route

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiManager

/**
 * Locates the route *definition* inside a physical route module so navigation
 * can land on `createFileRoute(...)` (or similar) instead of a random line.
 */
object RouteDefinitionLocator {

    private data class Pattern(val regex: Regex, val priority: Int)

    /**
     * Higher priority first. Prefer file-route factories used by TanStack Router/Start.
     * `createRoute` is lower priority (code-based / less common in file routes).
     */
    private val CALL_PATTERNS = listOf(
        Pattern(Regex("""\bcreateFileRoute\s*\("""), 0),
        Pattern(Regex("""\bcreateLazyFileRoute\s*\("""), 1),
        Pattern(Regex("""\bcreateRoute\s*\("""), 2),
    )

    /**
     * Optional: match a specific path argument on the factory call, e.g.
     * `createFileRoute("/_private/customers/")`.
     */
    private fun pathAwarePattern(path: String): Regex {
        val escaped = Regex.escape(path)
        return Regex(
            """\b(?:createFileRoute|createLazyFileRoute|createRoute)\s*\(\s*(['"`])$escaped\1""",
        )
    }

    private val FACTORY_WITH_PATH = Regex(
        """\b(createFileRoute|createLazyFileRoute|createRoute)\s*\(\s*(['"`])([^'"`]+)\2""",
    )

    /**
     * Character offset of the best definition call in [text], or null if none found.
     * When [preferredPath] is set, prefers a factory call whose string argument equals it,
     * a trailing-slash variant, or (for Link `to` vs route id) ends with the same suffix.
     */
    fun findDefinitionOffset(text: String, preferredPath: String? = null): Int? {
        if (text.isEmpty()) return null

        if (!preferredPath.isNullOrBlank()) {
            val candidates = pathVariants(preferredPath)
            for (path in candidates) {
                val m = pathAwarePattern(path).find(text)
                if (m != null) return m.range.first
            }

            // Link `to` is often a public fullPath ("/customers/") while createFileRoute
            // uses the file route id ("/_private/customers/"). Prefer the factory whose
            // path argument is the best suffix/normalized match.
            val fuzzy = findBestPathArgumentOffset(text, preferredPath)
            if (fuzzy != null) return fuzzy
        }

        var best: MatchResult? = null
        var bestPriority = Int.MAX_VALUE
        for (p in CALL_PATTERNS) {
            val m = p.regex.find(text) ?: continue
            if (p.priority < bestPriority ||
                (p.priority == bestPriority && (best == null || m.range.first < best.range.first))
            ) {
                best = m
                bestPriority = p.priority
            }
        }
        return best?.range?.first
    }

    private fun findBestPathArgumentOffset(text: String, preferredPath: String): Int? {
        val want = normalizePathKey(preferredPath) ?: return null
        if (want.isEmpty()) return null

        var bestOffset: Int? = null
        var bestScore = Int.MIN_VALUE
        for (m in FACTORY_WITH_PATH.findAll(text)) {
            val arg = m.groupValues[3]
            val got = normalizePathKey(arg) ?: continue
            val score = when {
                got == want -> 1000
                got.endsWith(want) && (got.length == want.length || got[got.length - want.length - 1] == '/') ->
                    500 + want.length
                want.endsWith(got) && (want.length == got.length || want[want.length - got.length - 1] == '/') ->
                    400 + got.length
                else -> continue
            }
            val factoryBonus = when (m.groupValues[1]) {
                "createFileRoute" -> 30
                "createLazyFileRoute" -> 20
                else -> 0
            }
            val total = score + factoryBonus
            if (total > bestScore || (total == bestScore && (bestOffset == null || m.range.first < bestOffset))) {
                bestScore = total
                bestOffset = m.range.first
            }
        }
        return bestOffset
    }

    private fun normalizePathKey(path: String): String? {
        val t = path.trim()
        if (t.isEmpty()) return null
        if (t == "/") return "/"
        return t.trimEnd('/')
    }

    /**
     * PSI element to use as a Go-to-Definition / gutter target.
     * Falls back to the file element when no factory call is found.
     */
    fun findDefinitionElement(
        project: Project,
        file: VirtualFile,
        preferredPath: String? = null,
    ): PsiElement? {
        val psiFile = PsiManager.getInstance(project).findFile(file) ?: return null
        val text = psiFile.text ?: return psiFile
        val offset = findDefinitionOffset(text, preferredPath) ?: return psiFile
        // Prefer the identifier leaf (createFileRoute) so the caret sits on the call name
        return psiFile.findElementAt(offset.coerceIn(0, (text.length - 1).coerceAtLeast(0)))
            ?: psiFile
    }

    /**
     * Offset suitable for [com.intellij.openapi.fileEditor.OpenFileDescriptor], or 0.
     */
    fun findDefinitionOffset(project: Project, file: VirtualFile, preferredPath: String? = null): Int {
        val text = try {
            String(file.contentsToByteArray(), file.charset)
        } catch (_: Exception) {
            return 0
        }
        return findDefinitionOffset(text, preferredPath) ?: 0
    }

    private fun pathVariants(path: String): List<String> {
        val t = path.trim()
        if (t.isEmpty()) return emptyList()
        if (t == "/") return listOf("/")
        return buildList {
            add(t)
            if (t.endsWith('/')) add(t.trimEnd('/'))
            else add("$t/")
        }.distinct()
    }
}
