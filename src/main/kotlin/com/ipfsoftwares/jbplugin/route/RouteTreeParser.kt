package com.ipfsoftwares.jbplugin.route

/**
 * Parses TanStack Router's generated `routeTree.gen.ts` text into
 * route path (`to` / fullPath / id) → relative module path mappings.
 *
 * The generator shape is stable enough to parse with regexes:
 * - `import { Route as XImport } from './routes/...'`
 * - `const X = XImport.update({...})`
 * - `export interface FileRoutesByTo { '/path': typeof X }`
 * - `interface FileRoutesByPath { '...': { id, fullPath, preLoaderRoute: typeof XImport } }`
 */
object RouteTreeParser {

    data class ParseResult(
        /** Keys used by Link `to`, fullPath, and route ids → relative import path */
        val pathToRelative: Map<String, String>,
        /** Any known key (id / to / fullPath) → preferred fullPath for relative resolution */
        val keyToFullPath: Map<String, String>,
    ) {
        companion object {
            val EMPTY = ParseResult(emptyMap(), emptyMap())
        }
    }

    private val importRegex =
        Regex("""import\s*\{\s*Route\s+as\s+(\w+)\s*\}\s*from\s*['"]([^'"]+)['"]""")

    private val constAssignRegex =
        Regex("""const\s+(\w+)\s*=\s*(\w+)""")

    private val fileRoutesByToRegex =
        Regex(
            """export\s+interface\s+FileRoutesByTo\s*\{(.*?)\}""",
            setOf(RegexOption.DOT_MATCHES_ALL),
        )

    private val fileRoutesByFullPathRegex =
        Regex(
            """export\s+interface\s+FileRoutesByFullPath\s*\{(.*?)\}""",
            setOf(RegexOption.DOT_MATCHES_ALL),
        )

    private val fileRoutesByIdRegex =
        Regex(
            """export\s+interface\s+FileRoutesById\s*\{(.*?)\}""",
            setOf(RegexOption.DOT_MATCHES_ALL),
        )

    private val interfaceEntryRegex =
        Regex("""['"]([^'"]+)['"]\s*:\s*typeof\s+(\w+)""")

    private val byPathEntryRegex =
        Regex(
            """['"]([^'"]+)['"]\s*:\s*\{(.*?)\n\s*\}""",
            setOf(RegexOption.DOT_MATCHES_ALL),
        )

    private val fullPathFieldRegex =
        Regex("""fullPath\s*:\s*['"]([^'"]+)['"]""")

    private val idFieldRegex =
        Regex("""id\s*:\s*['"]([^'"]+)['"]""")

    private val preLoaderRegex =
        Regex("""preLoaderRoute\s*:\s*typeof\s+(\w+)""")

    /**
     * Backwards-compatible: path/id → relative import path only.
     */
    fun parse(content: String): Map<String, String> = parseDetailed(content).pathToRelative

    fun parseDetailed(content: String): ParseResult {
        val imports = LinkedHashMap<String, String>()
        importRegex.findAll(content).forEach { m ->
            imports[m.groupValues[1]] = m.groupValues[2]
        }
        if (imports.isEmpty()) return ParseResult.EMPTY

        // const Name = OtherName...  (update / _addFileChildren / aliases)
        val symbolToImport = LinkedHashMap<String, String>()
        imports.keys.forEach { importName ->
            symbolToImport[importName] = importName
        }
        constAssignRegex.findAll(content).forEach { m ->
            val constName = m.groupValues[1]
            val rhs = m.groupValues[2]
            resolveToImport(rhs, symbolToImport, imports)?.let {
                symbolToImport[constName] = it
            }
            // Common generator pattern: FooRoute = FooRouteImport.update(...)
            if (rhs in imports) {
                symbolToImport[constName] = rhs
            }
        }

        // WithChildren aliases used in FileRoutesByTo
        symbolToImport.keys.toList().forEach { name ->
            if (name.endsWith("WithChildren")) {
                val base = name.removeSuffix("WithChildren")
                resolveToImport(base, symbolToImport, imports)?.let {
                    symbolToImport[name] = it
                }
            }
        }

        val pathToRelative = LinkedHashMap<String, String>()
        val keyToFullPath = LinkedHashMap<String, String>()

        fun putPath(path: String, importName: String, prefer: Boolean = false) {
            val relative = imports[importName] ?: resolveToImport(importName, symbolToImport, imports)?.let { imports[it] }
                ?: return
            if (path.isEmpty()) return
            if (prefer) {
                pathToRelative[path] = relative
            } else {
                pathToRelative.putIfAbsent(path, relative)
            }
            // Index-friendly variants
            if (path != "/" && path.endsWith('/')) {
                if (prefer) pathToRelative[path.trimEnd('/')] = relative
                else pathToRelative.putIfAbsent(path.trimEnd('/'), relative)
            } else if (path != "/" && !path.endsWith('/')) {
                if (prefer) pathToRelative["$path/"] = relative
                else pathToRelative.putIfAbsent("$path/", relative)
            }
        }

        fun rememberFullPath(key: String, fullPath: String) {
            if (key.isEmpty() || fullPath.isEmpty()) return
            keyToFullPath.putIfAbsent(key, fullPath)
            keyToFullPath.putIfAbsent(fullPath, fullPath)
            if (fullPath != "/" && fullPath.endsWith('/')) {
                keyToFullPath.putIfAbsent(fullPath.trimEnd('/'), fullPath)
            } else if (fullPath != "/" && !fullPath.endsWith('/')) {
                keyToFullPath.putIfAbsent("$fullPath/", fullPath)
            }
        }

        fun putFromInterfaceBody(body: String, prefer: Boolean) {
            interfaceEntryRegex.findAll(body).forEach { m ->
                val path = m.groupValues[1]
                val symbol = m.groupValues[2]
                val importName = resolveToImport(symbol, symbolToImport, imports) ?: return@forEach
                putPath(path, importName, prefer = prefer)
                // Interface keys that look like navigable paths are their own fullPaths
                if (path.startsWith("/")) {
                    rememberFullPath(path, path)
                }
            }
        }

        // Prefer FileRoutesByTo for Link `to` resolution (index routes win over layout fullPaths).
        fileRoutesByToRegex.find(content)?.groupValues?.get(1)?.let { putFromInterfaceBody(it, prefer = true) }
        fileRoutesByFullPathRegex.find(content)?.groupValues?.get(1)?.let { putFromInterfaceBody(it, prefer = false) }
        fileRoutesByIdRegex.find(content)?.groupValues?.get(1)?.let { putFromInterfaceBody(it, prefer = false) }

        // FileRoutesByPath module augmentation — ids + fullPath + file linkage
        val byPathIdx = content.indexOf("interface FileRoutesByPath")
        if (byPathIdx >= 0) {
            val slice = content.substring(byPathIdx)
            byPathEntryRegex.findAll(slice).forEach { m ->
                val mapKey = m.groupValues[1]
                val block = m.groupValues[2]
                val fullPath = fullPathFieldRegex.find(block)?.groupValues?.get(1)
                val id = idFieldRegex.find(block)?.groupValues?.get(1)
                val importName = preLoaderRegex.find(block)?.groupValues?.get(1) ?: return@forEach
                val resolvedImport = if (importName in imports) {
                    importName
                } else {
                    resolveToImport(importName, symbolToImport, imports)
                } ?: return@forEach

                if (fullPath != null) {
                    // Do not override ByTo winners for the same fullPath key
                    putPath(fullPath, resolvedImport, prefer = false)
                    rememberFullPath(fullPath, fullPath)
                    rememberFullPath(mapKey, fullPath)
                    if (id != null) rememberFullPath(id, fullPath)
                }
                if (id != null) {
                    putPath(id, resolvedImport, prefer = false)
                }
                putPath(mapKey, resolvedImport, prefer = false)
            }
        }

        return ParseResult(pathToRelative, keyToFullPath)
    }

    private fun resolveToImport(
        symbol: String,
        symbolToImport: Map<String, String>,
        imports: Map<String, String>,
    ): String? {
        symbolToImport[symbol]?.let { return it }
        if (symbol in imports) return symbol

        val strippedChildren = symbol.removeSuffix("WithChildren")
        if (strippedChildren != symbol) {
            symbolToImport[strippedChildren]?.let { return it }
            if (strippedChildren in imports) return strippedChildren
        }

        val withImport = symbol + "Import"
        if (withImport in imports) return withImport
        symbolToImport[withImport]?.let { return it }

        // FooRouteRoute -> FooRouteRouteImport already covered; try trimming Route suffix chains
        if (symbol.endsWith("Route") && (symbol + "Import") in imports) {
            return symbol + "Import"
        }

        // Last resort: longest import name that is a prefix of symbol + "Import" or equals stripped form
        val candidates = imports.keys.filter { importName ->
            symbol == importName ||
                symbol + "Import" == importName ||
                importName.removeSuffix("Import") == symbol ||
                importName.removeSuffix("Import") == strippedChildren
        }
        return candidates.maxByOrNull { it.length }
    }
}
