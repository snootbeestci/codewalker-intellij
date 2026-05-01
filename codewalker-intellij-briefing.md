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

Inside the SESSION card, the body is a horizontal `JBSplitter`:

- **First component**: file step list, scrollable, with a subtitle row
  showing the longest common directory prefix across all changed files.
  Per-file headers show the path relative to that prefix.
- **Second component**: a `BorderLayout` panel:
  - **NORTH** — `SummaryTable`. Cell values are read-only `JTextArea`s
    with `lineWrap = true` so long text wraps to multiple lines rather
    than scrolling or truncating.
  - **CENTER** — `CollapsibleSection` wrapping the streamed narration
    `JTextPane`. Collapsed by default; clicking the header expands it.
    The header sits directly below the summary fields. When expanded,
    the narration content grows downward into the available space.

`SessionPanel.onStepComplete` calls `summaryTable.update(complete.summary)`
when `complete.hasSummary()` is true, otherwise passes `null` (which clears
the table). `SessionPanel.clearNarration` clears the narration pane and
puts the summary table into its loading state (see below).

The summary table has two display states:

- **Loading** — a spinner with "Generating summary…" label, shown while
  the backend is generating the structured summary. Entered via
  `summaryTable.showLoading()`, which `SessionPanel.clearNarration()`
  calls at the start of every navigation.
- **Populated** — the eight-row table with risk/breaking/tests etc.,
  shown once `SummaryReady` (or `Complete` for older backends) provides
  the summary. Entered via `summaryTable.update(summary)`.

The loading state typically lasts 2–4 seconds. Without it, the user
sees `—` placeholders that read as "no data" rather than "loading",
making the panel feel unresponsive even when work is in progress.

`summaryTable.clear()` resets to empty placeholders without entering
the loading state — used for navigation cleanup and dispose. The
spinner is an `AsyncProcessIcon` whose animation lifecycle is driven
by component visibility via `CardLayout`, so no manual start/stop is
required.

The file/step list is built by joining `ReviewReady.forge_context.files`
(canonical file order, deterministic, aligned with backend Forward
navigation) with `ReviewReady.steps` (grouped by `hunk_span.file_path`,
sorted within each file by `hunk_span.new_start`). The plugin does not
rely on the wire order of `ReviewReady.steps` for display purposes —
that field's order is treated as unspecified, even though the current
backend emits it in Forward order as of v0.6.1. Steps whose
`hunk_span.file_path` is not present in `forge_context.files` are
dropped from the rendered list and logged as orphans.

`NavigationController.findNextStep` / `findPreviousStep` walk the same
display order rather than the wire order of `controller.steps`, so the
pre-fetch highlight always lands on the file the backend's Forward RPC
will actually open next.

The summary table colour-codes three signal fields:

- `breaking` — green (No), red (Yes)
- `risk` — green (Low), amber (Medium), red (High)
- `tests` — green (Added/Modified), amber (Missing)

Cell foreground colour is set per-field. The whole panel also carries a
header pill above the rows showing the worst severity across the three
signal fields: green ("Looks fine"), amber ("Worth a look"), or red
("Needs attention"). The pill is hidden entirely when no signal is
available (e.g. before the first step completes). `—` placeholder
values are treated as no signal and use the default foreground colour.

`confidence` is intentionally not colour-coded. It describes the LLM's
confidence in its own analysis, which is a meta-signal distinct from the
risk of the change itself; conflating them would mislead the reviewer.
Free-text fields (`what_changed`, `side_effects`, `reviewer_focus`,
`suggestion`) are also uncoloured because their values don't have
parseable severity.

Colour pairs use `JBColor` so they adapt between light and dark themes.

---

## Session model

- `ReviewSessionController` owns all active session state (session ID, steps, current step, glossary)
- `NavigationController` handles `Navigate` RPC calls and streams tokens to the UI
- `RephraseController` handles `Rephrase` RPC calls
- `GlossaryController` manages the term map and `ExpandTerm` RPC calls
- All gRPC calls use Kotlin coroutines — never block the UI thread
- UI updates always dispatched via `withContext(Dispatchers.Main)`

The navigation stream emits three event variants the plugin handles:

- `token` — narration text. Appended to the narration pane as it
  arrives.
- `summary_ready` — structured summary (risk, breaking, tests, etc.).
  Updates the summary table immediately, typically before narration
  finishes streaming. New as of backend v0.7.0.
- `complete` — terminal event. Updates breadcrumb, navigation buttons,
  step highlight, and (for backward compatibility) the summary table
  again. Clients running against pre-v0.7.0 backends rely on this for
  the summary, so the redundant update is intentional.

The summary table thus populates ~6 seconds earlier than narration
finishes, giving the reviewer risk/breaking/tests at-a-glance before
reading the full prose.

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

## Review session file display

When a review session starts, the plugin checks out the PR's head branch
in the user's working tree. All files opened during the session are real
`VirtualFile`s aligned with the diff — full IDE features (Find Usages,
run configs, navigation) work as normal.

Session-start flow (in `ReviewSessionController.openReview`):

1. The user clicks a PR row in the idle panel. The plugin has the PR's
   url, number, title, author, and `head_ref` from the
   `ListPullRequests` response.
2. Working-tree preparation runs **before** any gRPC call to
   `OpenReviewSession`:
   - If the working tree is dirty, show a Stash/Cancel modal
     (`DirtyTreeDialog`). Cancel aborts the session cleanly; Stash
     creates a tagged stash and proceeds.
   - Fetch from `origin` via a `GitLineHandler` invocation of
     `git fetch origin` (origin only — fork PRs are not supported in
     this version and surface a clear "branch not on origin" error).
   - Check out the branch via `GitBrancher.checkout(branch, false, repos)`
     — branch checkout, not detached HEAD.
3. Only then is `OpenReviewSession` called. The backend session is
   created only when the plugin is committed to running it — a cancel
   in step 2 produces no server-side state, so there is no orphan
   server session to leak until TTL eviction.
4. On `REVIEW_READY`, switch the tool window to the session card and
   start narration.

This ordering depends on `PullRequestSummary.head_ref`, added in
backend `v0.6.2`. If `head_ref` is empty (defensive — older backends
or edge cases), the working-tree prep is skipped and a log line
records the skip; the session falls back to opening without checkout.

Because the prepare step runs before the session ID exists, the stash
tag is generated locally from the head ref plus a timestamp
(`generateStashTag` in `ReviewSessionController`). The tag still flows
through `CodewalkerGitOps.stashMessage` so the `codewalker-` prefix is
preserved and `findCodewalkerStashes` continues to identify these
stashes for cleanup.

Stash entries are tagged `codewalker-<sessionTag>` so they can be
distinguished from user-created stashes. On every session start the
plugin scans for codewalker-tagged stashes via `git stash list` and
logs a warning (informational only) listing any leftovers from
sessions that did not clean up. Automatic restoration is tracked
separately.

Concurrent sessions are not supported in this version; the plugin
refuses a second `openReview` call while one is active via an
`AtomicBoolean` on the controller. This is a stopgap until proper
session lifecycle exists.

All git operations are wrapped in `CodewalkerGitOps`
(project-scoped). Operations are exposed as plain or `suspend`
functions over Git4Idea APIs: `GitRepositoryManager` and
`ChangeListManager` for dirty detection, `GitBrancher` for the
checkout (wrapped in `suspendCancellableCoroutine` so the suspend
function resumes when the AWT-later callback fires), and
`GitLineHandler` invoked through `Git.getInstance().runCommand` for
`stash push`, `stash list`, and `fetch origin`. Failures throw
`GitOperationException`, which the controller surfaces verbatim to
the user.

The legacy `EditorHighlighter` `FetchFileAtRef`-based fallback (head-ref
content fetched over gRPC and rendered in a `LightVirtualFile` when the
working tree was out of sync) is no longer the primary path — once the
PR branch is checked out, the working-tree copy will match by
construction. The highlighter retains the fetch-and-compare logic as a
safety net for projects without a git repository or when checkout was
skipped.

Highlighted regions in the editor are clickable. A left click on a
highlighted line opens a popup showing the unified diff for that hunk,
including removed lines that are not visible in the head-ref content
displayed in the editor. Clicking the same region again dismisses the
popup; clicking outside the highlight or outside the editor dismisses
it as well. Modified clicks (ctrl, shift, alt, meta) fall through to
the IDE's default handlers so existing gestures like Find Usages
continue to work.

The popup is non-focusable so the editor retains keyboard focus.

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
- Display order of repeated proto fields is the server's choice. Plugin
  code that needs a specific order constructs it locally from structured
  fields (file lists, line numbers) rather than depending on the wire
  order of unstructured `repeated` fields.
- Use Git4Idea APIs for all git operations. Don't shell out to `git`
  directly via `ProcessBuilder`/`Runtime.exec`. Operations needed today
  (dirty detection, stash save, branch fetch, branch checkout, stash
  list) are reachable via `GitRepositoryManager`, `ChangeListManager`,
  `GitBrancher`, and `GitLineHandler` invoked through
  `Git.getInstance().runCommand`. All git operations belong in
  `CodewalkerGitOps` rather than being scattered across controllers.

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
