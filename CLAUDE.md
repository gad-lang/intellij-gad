# CLAUDE.md

## Project
`intellij-gad` — the Gad Language plugin for the IntelliJ Platform (GoLand, IDEA,
…): file types and icons for `.gad` / `.gadt` / `.gadx`, TextMate highlighting,
run configurations, a DAP debugger bridge, Reformat Code via `gad fmt`, context
actions (Run, Gad Transpile, Gad Doc) and a live Gad Doc side panel.

## Tooling
- Built with the **IntelliJ Platform Gradle Plugin 2.x** (Kotlin, JDK 21).
- Use the `Makefile` (wraps `./gradlew`) — run `make help`: `make build` (the
  plugin `.zip`), `make compile`, `make test`, `make verify`, `make run` (sandbox IDE).
- Build against a locally-installed IDE with `-PlocalIdePath=/path/to/IDE`.
- The plugin shells out to the user-configured `gad` binary at runtime; it does
  not bundle it.

## Conventions
- Idiomatic Kotlin; `<extensions defaultExtensionNs="com.intellij">` (note: `Ns`,
  not `Namespace`). Keep `plugin.xml` extension points valid.
