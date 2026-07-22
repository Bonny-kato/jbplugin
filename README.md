# TanStack (WebStorm plugin)

WebStorm support for **TanStack Router / Start**, focused on **Go to Definition** on typed route links.

## Features (v1.1)

- **Go to Definition / Cmd-click** on route paths in:
  - `<Link to="/customers/$id" />`
  - `<ButtonLink to={"/customers/residents/search"} />` (JSX expression form)
  - `navigate({ to: '/about' })`
  - `redirect({ to: '/login' })`
  - relative targets: `to=".."`, `to="../edit"`, `to="."` (uses `from` or `createFileRoute` in the file)
- Resolves targets from generated **`routeTree.gen.ts`** (path → physical route module)
- **Single gutter icon** per resolvable `to` (no duplicate markers on braces/quotes)
- **Plugin + tool window icon**
- **TanStack Routes** tool window:
  - one row per **physical route file** (not every path key/segment)
  - total count + filter
  - **usages** panel for the selected route (`Link` / `navigate` / `redirect` call sites)

## How it works

```text
Link to="/customers/$customerId"
        │
        ▼
routeTree.gen.ts   (FileRoutesByTo / FileRoutesByPath + imports)
        │
        ▼
src/routes/.../$customerId.tsx
```

Relative example:

```text
// in a route file with createFileRoute('/posts/$postId')
navigate({ to: '..' })  →  parent fullPath → route module
```

## Install / update

1. Build: `./gradlew buildPlugin`
2. WebStorm → **Settings → Plugins → ⚙️ → Install Plugin from Disk…**
3. Pick `build/distributions/jbplugin-1.1.2.zip`
4. **Restart** the IDE (required after replace/upgrade)

### Verify it loaded

- Look for the **TanStack Routes** tool window on the right (custom icon)
- Open a file with `to={"/..."}` — **one** gutter icon should appear on the path
- Cmd/Ctrl-click the path: you should get the **route module file** (TypeScript may still list `routeTree.gen.ts` type entries — pick the `.tsx` route file, or use the gutter icon)
- In the tool window: filter routes, double-click a route to open the file, select a route to list usages

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
build/distributions/jbplugin-1.1.2.zip
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
- Usage scan is text-based (`to: '...'` / `to="..."` / `to={'...'}`); dynamic or indirect wrappers may be missed

## Roadmap

- IDE **Find Usages** action from a route module
- Completion polish for `to` values
- Unresolved route inspection
- Prefer / filter TypeScript type-map targets when a route file resolves
