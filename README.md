# TanStack (WebStorm plugin)

WebStorm support for **TanStack Router / Start**, focused on **Go to Definition** on typed route links.

## Features

- **Go to Definition / Cmd-click** on route paths in:
  - `<Link to="/customers/$id" />`
  - `<ButtonLink to={"/customers/residents/search"} />` (JSX expression form)
  - `navigate({ to: '/about' })`
  - `redirect({ to: '/login' })`
  - relative targets: `to=".."`, `to="../edit"`, `to="."` (uses `from` or `createFileRoute` in the file)
- Resolves targets from generated **`routeTree.gen.ts`** (path → physical route module)
- **Gutter icon** on resolvable `to` literals (direct jump to the route file)
- **TanStack Routes** tool window (right side) listing indexed paths

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
3. Pick `build/distributions/jbplugin-1.0.1.zip`
4. **Restart** the IDE (required after replace/upgrade)

### Verify it loaded

- Look for the **TanStack Routes** tool window on the right
- Open a file with `to={"/..."}` — a gutter icon should appear on the path
- Cmd/Ctrl-click the path: you should get the **route module file** (possibly alongside TypeScript’s `routeTree.gen.ts` type entries — pick the `.tsx` route file, or use the gutter icon)

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
build/distributions/jbplugin-1.0.1.zip
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

## Roadmap

- Find Usages from a route module back to `to="..."` call sites
- Completion polish for `to` values
- Prefer / filter TypeScript type-map targets when a route file resolves
