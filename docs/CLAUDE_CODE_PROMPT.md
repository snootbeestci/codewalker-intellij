# Codewalker IntelliJ Plugin — Claude Code Session

Before starting any work, read `codewalker-intellij-briefing.md` in full.
It contains architectural decisions and coding rules that must be respected.
If something you need to do contradicts the briefing, stop and ask rather
than proceeding.

Also read `README.md` to understand the current state of the plugin from a
user perspective.

For backend decisions (proto contract, session model, forge extensibility),
also read:
https://github.com/snootbeestci/codewalker/blob/main/codewalker-briefing.md

## Branch and PR rules

- Always create a new branch for each issue. Name it
  `claude/issue-{number}-{short-description}`
- Always submit work as a PR — never commit directly to main
- The PR description must accurately reflect every commit in the PR. If you
  add commits after the initial description, update the description to match
- Before finalising the PR description, check it against all commits and
  flag any inconsistencies

## Briefing and README updates
editing this so there are more files in this diff

- If your change introduces a new architectural decision, pattern, or
  operational detail that a future Claude Code session would need to know,
  append it to `codewalker-intellij-briefing.md` as part of the same PR
- If your change affects how a developer installs, runs, or uses the plugin,
  update `README.md` as part of the same PR
- Do not create separate PRs for documentation updates that belong with
  a feature

## Before submitting the PR

1. `./gradlew check` passes
2. PR description matches all commits
3. `codewalker-intellij-briefing.md` updated if needed
4. `README.md` updated if needed

## The issue to address

[paste issue body here]
