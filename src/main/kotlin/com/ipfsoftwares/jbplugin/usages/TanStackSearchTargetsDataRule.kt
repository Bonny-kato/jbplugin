package com.ipfsoftwares.jbplugin.usages

import com.intellij.find.actions.FindUsagesAction
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataSink
import com.intellij.openapi.actionSystem.DataSnapshot
import com.intellij.openapi.actionSystem.UiDataRule
import com.intellij.find.usages.api.SearchTarget

/**
 * Supplies TanStack route [SearchTarget]s for Alt+F7 when the caret is on a route definition.
 *
 * WebStorm 2026+ prefers the modern SEARCH_TARGETS path (TypeScript symbols → routeTree.gen.ts).
 * Providing our target here lets Find Usages open real `Link`/`navigate` call sites instead.
 */
class TanStackSearchTargetsDataRule : UiDataRule {

    override fun uiDataSnapshot(sink: DataSink, snapshot: DataSnapshot) {
        sink.lazyValue(FindUsagesAction.SEARCH_TARGETS) { data ->
            val editor = data.get(CommonDataKeys.EDITOR) ?: return@lazyValue null
            val file = data.get(CommonDataKeys.PSI_FILE) ?: return@lazyValue null
            val offset = editor.caretModel.offset
            val el = file.findElementAt(offset)
                ?: file.findElementAt((offset - 1).coerceAtLeast(0))
                ?: return@lazyValue null

            val symbol = TanStackRouteSymbol.fromElement(el) ?: return@lazyValue null
            @Suppress("UNCHECKED_CAST")
            listOf(symbol) as Collection<SearchTarget>
        }
    }
}
