# Codewalker for IntelliJ

IntelliJ IDEA / PhpStorm plugin for [Codewalker](https://github.com/snootbeestci/codewalker).

Explains code and pull requests step by step with AI narration from Claude.
Walk through a pull request hunk by hunk, with narration explaining what
changed and why.

## Requirements

- IntelliJ IDEA 2025.1 or later (also works in PhpStorm, GoLand, WebStorm etc.)
- [Codewalker backend](https://github.com/snootbeestci/codewalker) running locally

## Quickstart

**1. Start the backend**

```bash
git clone https://github.com/snootbeestci/codewalker
cd codewalker
export ANTHROPIC_API_KEY=sk-ant-...
docker-compose -f deploy/docker-compose.yml up
```

**2. Install the plugin**

Download the latest `.zip` from [Releases](https://github.com/snootbeestci/codewalker-intellij/releases),
then in IntelliJ: Settings → Plugins → ⚙️ → Install Plugin from Disk.

**3. Configure**

Settings → Tools → Codewalker. The default backend address (`localhost:50051`)
works if you're running the backend locally.

**4. Review a PR**

Open the Codewalker tool window (right side panel), paste a GitHub PR URL,
and click Open Review.

## Development

```bash
./gradlew runIde       # launch a sandboxed IDE with the plugin installed
./gradlew buildPlugin  # produce a distributable .zip
./gradlew check        # run tests and verifications
```

## Contributing

Two prompts are available to streamline AI-assisted development:

- [Design session prompt](docs/DESIGN_PROMPT.md) — for planning features
  and reviewing PRs with Claude
- [Claude Code prompt](docs/CLAUDE_CODE_PROMPT.md) — paste this before an
  issue body when starting a Claude Code implementation session

The [briefing document](codewalker-intellij-briefing.md) is the source of
truth for architectural decisions. Read it before making any structural
changes.

## Licence

TBD
