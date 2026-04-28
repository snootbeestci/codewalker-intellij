# Codewalker IntelliJ Plugin — Briefing

This document is the source of truth for architectural decisions in this repo.
Read it in full before making any changes. If something you need to do
contradicts this document, stop and ask rather than proceeding.

For decisions about the backend (proto contract, session model, forge
extensibility, LLM provider), refer to the backend briefing:
https://github.com/snootbeestci/codewalker/blob/main/codewalker-briefing.md

---

## What this is

An IntelliJ IDEA / PhpStorm plugin that provides a Codewalker tool window.
It connects to the Codewalker backend over gRPC and allows developers to:
- Walk through a pull request diff hunk by hunk with AI narration (v1)
- Walk through a file step by step with AI narration (planned)

Because the plugin is built on the JetBrains Platform, it works in any
JetBrains IDE — PhpStorm, GoLand, WebStorm, PyCharm, IntelliJ IDEA etc.

---

## Target platform

- Minimum build: 251 (IntelliJ IDEA / PhpStorm 2025.1)
- Language: Kotlin
- Build system: Gradle (Kotlin DSL)

---

## Proto stubs

The gRPC API is defined in the backend repo. Versioned Kotlin stubs are
served via JitPack and declared as a dependency in `build.gradle.kts`:

```kotlin
maven { url = uri("https://jitpack.io") }

implementation("com.github.snootbeestci:codewalker-proto:{tag}")
```

The version string must use the full Git tag (e.g. `v0.1.0`), not a plain
semver, because JitPack keys builds on the tag name. No credentials are
needed — JitPack serves public repos without authentication.
Never copy the `.proto` file into this repo.

---

## Connection and version compatibility

- On IDE startup, the plugin calls `GetVersion` against the configured backend address
- If unreachable: silent, show disconnected state in tool window
- If `proto_major` matches `SUPPORTED_PROTO_MAJOR` (currently 1): connected
- If `proto_major` does not match: show incompatibility warning, do not attempt sessions
- `SUPPORTED_PROTO_MAJOR` is defined in `CodewalkerClient` — update it when
  adopting a new major proto version

---

## Settings

Stored via `PersistentStateComponent` in `codewalker.xml`:
- `backendAddress` — default `localhost:50051`
- `experienceLevel` — default `EXPERIENCE_LEVEL_MID`

GitHub token is stored in the IDE `PasswordSafe` (not in XML):
- Credential key: `Codewalker.GitHubToken`
- Optional — absence means public repo mode

Settings page registered under Settings → Tools → Codewalker.

---

## Tool window

Registered as a right-side tool window. Four states managed via `CardLayout`:

| State | Trigger |
|---|---|
| DISCONNECTED | Backend unreachable or proto incompatible |
| IDLE | Connected, no active session |
| LOADING | `OpenReviewSession` in progress |
| SESSION | Session open, narration active |

### Session body layout

Inside the SESSION card, the body is split horizontally:

- **West**: step list (grouped by file path)
- **Centre**: a vertical column of
  - `SummaryTable` — fixed two-column key/value list rendering the eight
    `StepSummary` fields. Always visible. Empty/unset fields render as `—`
    so the layout never collapses.
  - `CollapsibleSection` wrapping the streamed narration `JTextPane`.
    Collapsed by default — clicking the header toggles visibility.

`SessionPanel.onStepComplete` calls `summaryTable.update(complete.summary)`
when `complete.hasSummary()` is true, otherwise passes `null` (which clears
the table). `SessionPanel.clearNarration` clears both the narration pane
and the summary table.

---

## Session model

- `ReviewSessionController` owns all active session state (session ID, steps, current step, glossary)
- `NavigationController` handles `Navigate` RPC calls and streams tokens to the UI
- `RephraseController` handles `Rephrase` RPC calls
- `GlossaryController` manages the term map and `ExpandTerm` RPC calls
- All gRPC calls use Kotlin coroutines — never block the UI thread
- UI updates always dispatched via `withContext(Dispatchers.Main)`

---

## Token resolution order

When opening a review session the plugin resolves a forge token as follows:
1. Token from IDE `PasswordSafe` if present
2. Empty string — public repo mode

`gh` CLI token resolution is handled server-side, not by the plugin.

---

## Future direction

- **Walkthrough sessions** — explain a file in the open project step by step
- **URI scheme handler** — register `codewalker://` so a browser extension can
  deep-link directly into the plugin with a pre-filled URL
- **Browser extension** — adds "Open in Codewalker" button to GitHub PR pages,
  generates a `codewalker://review?url=...` link

---

## Development rules

### Kotlin
- Use coroutines for all gRPC calls — never `runBlocking` on the UI thread
- Collect gRPC flows with `.collect {}` — always handle token, complete, and error variants
- UI updates must be dispatched via `withContext(Dispatchers.Main)`
- Prefer `JBTextField`, `JBPasswordField` etc. from `com.intellij.ui.components`
  over raw Swing equivalents — but verify the class is available on the plugin
  compilation classpath before using it. `com.intellij.ui.ComboBox` is a known
  example that is absent in IntelliJ 2025.1; use `javax.swing.JComboBox` instead.
- Editor integration uses `FileEditorManager.openTextEditor()` to open files
  and `editor.markupModel.addRangeHighlighter()` for transient highlights.
  Always call `RangeHighlighter.dispose()` to clean up — never let highlights
  accumulate across sessions.
- Index lookups (`FilenameIndex`, `ProjectFileIndex`, PSI traversal etc.)
  require a read action — wrap them in `ReadAction.compute { ... }` when
  called from the EDT or a coroutine, otherwise IntelliJ throws
  "Read access is allowed from inside read-action only".

### Build
- `./gradlew runIde` — launches a sandboxed IDE with the plugin installed
- `./gradlew buildPlugin` — produces a distributable `.zip`
- `./gradlew check` — runs tests and verifications
- Always run `./gradlew check` before submitting a PR
- All non-trivial logic must ship with unit tests in the same PR — not as a follow-up
- Use JUnit 5 (`org.junit.jupiter.api`) for new tests
- Test files mirror the package structure under `src/test/kotlin/`

### Briefing and README
- If your change introduces a new architectural decision, append it here
- If your change affects how a developer installs or runs the plugin, update README.md
- Do not create separate PRs for documentation that belongs with a feature
