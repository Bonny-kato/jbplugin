# TanStack (WebStorm plugin)

WebStorm support for **TanStack Router / Start**, focused on typed route navigation and safety.

## Features (v1.2)

- **Go to Definition / Cmd-click** on route paths in:
  - `<Link to="/customers/$id" />`
  - `<Navigate to="/login" />`, `<MatchRoute to="..." />`
  - `*Link` wrappers (e.g. `<ButtonLink to={"/customers/residents/search"} />`)
  - `navigate({ to: '/about' })`, `redirect({ to: '/login' })`, `linkOptions({ to: '...' })`
  - relative targets: `to=".."`, `to="../edit"`, `to="."` (uses `from` or `createFileRoute` in the file)
- Lands on **`createFileRoute(...)`** (not a random line)
- **Unresolved route inspection** when `to` is missing from `routeTree.gen.ts`
- **Find Usages (Alt+F7)** from a route module or `createFileRoute('/path')`
- **`to` completion** with path + file name hint
- **Hover docs** on route paths (path, file, API form, alternates)
- Resolves targets from generated **`routeTree.gen.ts`**
- **Single gutter icon** per resolvable `to` (world icon; plugin logo is branding only)
- **TanStack Routes** tool window:
  - one row per **physical route file**
  - total count + filter + keyboard-friendly navigation
  - **usages** panel for the selected route

## How it works

```text
Link to="/customers/$customerId"
        │
        ▼
routeTree.gen.ts   (FileRoutesByTo / FileRoutesByPath + imports)
        │
        ▼
src/routes/.../$customerId.tsx   → createFileRoute('...')
```

## Install / update

1. Build: `./gradlew buildPlugin`
2. WebStorm → **Settings → Plugins → ⚙️ → Install Plugin from Disk…**
3. Pick `build/distributions/jbplugin-1.2.4.zip`
4. **Restart** the IDE (required after replace/upgrade)

### Verify it loaded

- **TanStack Routes** tool window on the right
- Unresolved `to="/nope"` shows a warning when the route index exists
- Alt+F7 on `createFileRoute('/…')` lists link call sites
- Hover a `to` path for route quick-doc
- Cmd/Ctrl-click lands on `createFileRoute(...)` in the route module

## Requirements

- JDK **21**
- WebStorm / IntelliJ Platform **2026.2+**
- A TanStack Router/Start app that has generated `routeTree.gen.ts`

## Build

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 21 2>/dev/null || echo /opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home)
./gradlew buildPlugin test
```

Packaged plugin:

```text
build/distributions/jbplugin-1.2.4.zip
```

## Run in a sandbox WebStorm

```bash
./gradlew runIde
```

## Tests

```bash
./gradlew test
```

## Limitations

- Dynamic `to={variable}` (non-literal) is not resolved
- Relative links outside a route file need an explicit `from: '...'`
- Custom `generatedRouteTree` filenames other than `routeTree.gen.ts` are not discovered yet
- TypeScript may still offer `routeTree.gen.ts` type declarations in the same popup; choose the route file or use the gutter icon
- Usage scan is text-based (`to: '...'` / `to="..."` / `to={'...'}`); heavily dynamic wrappers may be missed
- Find Usages (Alt+F7) on `createFileRoute` / `export const Route` uses a TanStack SearchTarget and lists `Link`/`navigate` call sites (not `routeTree.gen.ts` imports)
- Unresolved-route inspection and gutter navigation are de-duplicated (one warning / one target)
- Find Usages is optimized for route definitions, not every symbol inside a route component body

## Roadmap

- Tool window health (active gen file, stale/missing)
- New Route file template
- Related files (parent layout / children)
- Prefer / filter TypeScript type-map targets when a route file resolves
