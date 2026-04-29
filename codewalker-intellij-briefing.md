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
- `knownHosts` — list of hosts that have a token in `PasswordSafe`. Mirrored
  from the credential store because `PasswordSafe` does not expose
  enumeration. Treat the credential store as authoritative for whether a
  token exists; `knownHosts` is only used to populate the settings table.

Forge tokens are stored in the IDE `PasswordSafe`, one credential per host.
`TokenStore` is the only entry point for reading or writing tokens — no
controller or panel reads `PasswordSafe` directly.

- `TokenStore` is registered as an application-level service
  (`@Service(Service.Level.APP)`); production code accesses it via
  `TokenStore.getInstance()`. Tests construct instances directly with
  custom adapters.
- Credential key: `Codewalker.ForgeToken.<host>` where `<host>` is the
  canonical form returned by `HostNormalizer.normalize` (lowercase, no
  scheme, no trailing slash, port preserved when non-default)
- Tokens are optional — absence means public repo mode for that host
- `gh` CLI tokens are imported on demand via the settings UI; the plugin
  never reads `gh` ambient credentials at request time

Settings page registered under Settings → Tools → Codewalker.

### Legacy credential migration

A `LegacyTokenMigration` `ProjectActivity` runs on first project open after
upgrade. If the pre-multi-host `Codewalker.GitHubToken` credential exists,
it is copied to `Codewalker.ForgeToken.github.com` (unless that per-host
key is already set, in which case the user's explicit configuration wins)
and the legacy credential is cleared. The migration is idempotent and
safe to run repeatedly.

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

The plugin parses the canonical host from the review URL via
`HostNormalizer.fromUrl(url)`, then resolves a forge token as follows:
1. Per-host token from IDE `PasswordSafe` (`Codewalker.ForgeToken.<host>`)
2. Empty string — public repo mode

Server-side token resolution has been removed from the backend; the plugin
is the sole source of truth for which token to send. `forge_token` on the
RPC is opaque to the server: empty means unauthenticated, non-empty is used
verbatim.

The `gh` CLI is **not** consulted at request time. Users import a token
explicitly from the settings page via the "Import from gh CLI" toolbar
action, which runs `gh auth token --hostname <host>` once and stores the
result in `PasswordSafe`. This avoids silently leaking whichever credential
the user happens to be logged into via `gh`.

### Host string contract

All host strings must use the canonical form: bare hostname, lowercase, no
scheme, no trailing slash, no trailing dot, port only when non-default. The
plugin normalises before keying the `TokenStore` and before sending
host-bearing RPC fields, so the keying matches what the server expects on
the wire.

The canonicalisation rules are mirrored exactly between
`HostNormalizer.normalize` here and `forge.NormalizeHost` in the server
repo. Any change to either must be paired with the same change on the
other side and the test suites updated to drift-detect.

URL parsing for the review URL field uses `HostNormalizer.fromUrlResult`,
which returns a sealed result type distinguishing empty input, parse
failure, and a successfully extracted canonical host. Empty and
parse-failure cases are surfaced to the user before the request is made;
they do not silently downgrade to unauthenticated mode.

### 403 / SSO error rendering

`ReviewErrorFormatter.format` converts a server-side error into a
`FormattedError(message, isAuthFailure)`. Callers use `isAuthFailure`
directly to decide whether to surface a "Configure tokens" affordance,
rather than string-matching the message. SSO-marked 403 responses still
get the `Authorization required:` prefix on the message; the marker
phrases are listed in the formatter source.

For `PERMISSION_DENIED`, the server includes the forge's response body in
the status description (truncated to ~500 chars). When the body contains
specific SSO markers — `SAML enforcement`, `SSO authorization` (or
`authorisation`), `single sign-on`, `must have admin rights`,
`configure SSO` — the formatter prepends `Authorization required:` so
the user knows the token is valid but needs SSO authorisation rather
than replacement. The markers are phrase-level, not bare acronyms, to
avoid false positives against bodies that mention `associated`,
`cross-origin`, or other words containing the letters `sso`.

---

## Project entry point

The plugin's idle screen lists open pull requests for the current project's
GitHub remote, rather than asking the user to paste a URL. The remote is
detected via Git4Idea (`GitRepositoryManager.getInstance(project)`),
parsed by `GitHubRemoteResolver` into a canonical (host, owner, repo)
triple, and the host is normalised via `HostNormalizer.normalize` before
either keying into `TokenStore` or being sent on the wire.

PRs are fetched via the backend's `ListPullRequests` RPC. The
`forge_token` is resolved client-side via `TokenStore.getInstance().get(host)` —
empty for hosts the user has not signed into, which downgrades to
unauthenticated public access for that fetch.

Refresh is manual via the refresh button — no automatic polling.

Hosts containing "github" are accepted (covers github.com and any GitHub
Enterprise instance). Other hosts (gitlab, bitbucket, gitea) are
explicitly rejected with an informational message; they are out of scope
until the corresponding ForgeHandler exists on the backend.

The plugin declares a `Git4Idea` dependency in `plugin.xml` so the
JetBrains Git integration is available at runtime in any host IDE.

URL parsing for git remotes is centralised in
`GitHubRemoteResolver.parseRemoteUrlResult`, which returns a sealed
`RemoteParseResult` distinguishing `Ok`, `NonGitHub(host)`,
`Unparseable`, and `Empty`. The idle panel uses this to render distinct
messages for each failure mode. There is no separate host-extraction
path elsewhere in the plugin — host parsing lives in one place.

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
- UI panels that own a `CoroutineScope` must implement `Disposable` and
  cancel the scope in `dispose()`. Owning containers register children
  via `Disposer.register(parent, child)` so cancellation cascades
  automatically — manual `child.dispose()` calls in a parent's
  `dispose()` are a code smell.

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
