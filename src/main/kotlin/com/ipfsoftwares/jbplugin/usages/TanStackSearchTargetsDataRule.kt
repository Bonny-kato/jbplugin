package com.ipfsoftwares.jbplugin.usages

import com.intellij.find.actions.FindUsagesAction
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataSink
import com.intellij.openapi.actionSystem.DataSnapshot
import com.intellij.openapi.actionSystem.UiDataRule
import com.intellij.find.usages.api.SearchTarget
import com.intellij.usages.UsageTarget
import com.intellij.usages.UsageView

/**
 * Supplies a single TanStack route [SearchTarget] for Alt+F7 on route definitions.
 *
 * WebStorm 2026 merges SEARCH_TARGETS **and** USAGE_TARGETS into the "Find Usages Of"
 * chooser. We therefore:
 * 1. Publish one [TanStackRouteSymbol] via SEARCH_TARGETS
 * 2. Publish an **empty** USAGE_TARGETS array for the same caret so the platform does not
 *    also add our legacy UsageTarget / TypeScript PSI targets (duplicate rows that look
 *    like usages, then resolve to "Nothing found").
 */
class TanStackSearchTargetsDataRule : UiDataRule {

    override fun uiDataSnapshot(sink: DataSink, snapshot: DataSnapshot) {
        sink.lazyValue(FindUsagesAction.SEARCH_TARGETS) { data ->
            val symbol = resolveSymbol(data) ?: return@lazyValue null
            @Suppress("UNCHECKED_CAST")
            listOf(symbol) as Collection<SearchTarget>
        }

        // Empty (not null): skips both USAGE_TARGETS entries and the reference-fallback path.
        sink.lazyValue(UsageView.USAGE_TARGETS_KEY) { data ->
            if (resolveSymbol(data) == null) return@lazyValue null
            emptyArray<UsageTarget>()
        }
    }

    private fun resolveSymbol(data: com.intellij.openapi.actionSystem.DataMap): TanStackRouteSymbol? {
        val editor = data.get(CommonDataKeys.EDITOR) ?: return null
        val file = data.get(CommonDataKeys.PSI_FILE) ?: return null
        val offset = editor.caretModel.offset
        val el = file.findElementAt(offset)
            ?: file.findElementAt((offset - 1).coerceAtLeast(0))
            ?: return null
        return TanStackRouteSymbol.fromElement(el)
    }
}
