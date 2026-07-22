package com.ipfsoftwares.jbplugin.completion

import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionProvider
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.completion.CompletionType
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.icons.AllIcons
import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.util.ProcessingContext
import com.ipfsoftwares.jbplugin.navigation.TanStackApiContext
import com.ipfsoftwares.jbplugin.navigation.TanStackLinkContext
import com.ipfsoftwares.jbplugin.navigation.TanStackRouteAnchor
import com.ipfsoftwares.jbplugin.route.RouteInfo
import com.ipfsoftwares.jbplugin.route.RouteInfoResolver
import com.ipfsoftwares.jbplugin.route.RoutePresentation

/**
 * Completion for TanStack `to` values with path + file name hints.
 */
class TanStackRouteCompletionContributor : CompletionContributor() {

    init {
        extend(
            CompletionType.BASIC,
            PlatformPatterns.psiElement(),
            object : CompletionProvider<CompletionParameters>() {
                override fun addCompletions(
                    parameters: CompletionParameters,
                    context: ProcessingContext,
                    result: CompletionResultSet,
                ) {
                    val position = parameters.position
                    if (!isToCompletionPlace(position)) return

                    val project = position.project
                    val candidates = RouteInfoResolver.completionCandidates(
                        project,
                        position.containingFile?.virtualFile,
                    )
                    if (candidates.isEmpty()) return

                    val prefix = extractTypedPrefix(position, parameters)
                    val sink = if (prefix.isNotEmpty()) result.withPrefixMatcher(prefix) else result

                    for (info in candidates) {
                        sink.addElement(lookupElement(info))
                        // Also offer alternate path keys (e.g. trailing slash / internal id) lightly
                        for (alt in info.alternatePaths.take(2)) {
                            if (alt == info.resolvedPath) continue
                            if (!alt.startsWith("/")) continue
                            sink.addElement(
                                lookupElement(
                                    info.copy(requestedPath = alt, resolvedPath = alt),
                                ),
                            )
                        }
                    }
                }
            },
        )
    }

    companion object {
        fun lookupElement(info: RouteInfo): LookupElementBuilder {
            return LookupElementBuilder.create(info.resolvedPath)
                .withPresentableText(info.resolvedPath)
                .withIcon(AllIcons.Nodes.PpWeb)
                .withTypeText(RoutePresentation.lookupTypeText(info), true)
                .withTailText(RoutePresentation.lookupTailText(info), true)
                .withCaseSensitivity(true)
        }

        fun isToCompletionPlace(element: PsiElement): Boolean {
            if (element is PsiFile) return false

            // Existing canonical host
            if (TanStackRouteAnchor.findCanonicalHost(element) != null) return true
            if (TanStackLinkContext.findRoutePathAt(element) != null) return true

            // Incomplete string while typing after to: / to=
            var scope: PsiElement? = element
            repeat(12) {
                if (scope == null || scope is PsiFile) return false
                val text = scope!!.text
                if (text != null && text.length < 2000) {
                    if (text.contains(Regex("""\bto\s*[:=]"""))) {
                        // Avoid createFileRoute path completion here (different feature)
                        if (TanStackApiContext.findCreateFileRoutePathAt(element) != null &&
                            !text.contains(Regex("""\bto\s*[:=]"""))
                        ) {
                            return false
                        }
                        return true
                    }
                    // JSX attribute name `to` being filled
                    if (text.startsWith("to=") || text.startsWith("to =")) return true
                }
                scope = scope!!.parent
            }
            return false
        }

        fun extractTypedPrefix(position: PsiElement, parameters: CompletionParameters): String {
            // Inside a string literal the dummy identifier may be appended — strip it
            val raw = TanStackLinkContext.coerceRoutePath(position.text)
                ?: TanStackLinkContext.extractQuoted(position.text)
                ?: position.text.trim().trim('"', '\'', '`')
            val cleaned = raw
                .replace("IntellijIdeaRulezzz", "")
                .replace("intellijIdeaRulezzz", "")
                .trim()
            // Prefer prefix up to caret within the element when possible
            val offsetInElement = parameters.offset - position.textOffset
            if (offsetInElement in 1..position.textLength) {
                val partial = position.text.substring(0, offsetInElement)
                    .trimStart('"', '\'', '`', '{', ' ')
                    .replace("IntellijIdeaRulezzz", "")
                if (partial.isNotEmpty() && !partial.contains('\n')) {
                    return partial
                }
            }
            return cleaned.takeWhile { it != '$' || true }.let {
                // keep $param segments for matching
                it
            }
        }
    }
}
