# CI Detective for Gitlab

A plugin for IntelliJ IDEA that provides static analysis and navigation for GitLab CI/CD pipeline configurations.

Working with multi-file GitLab CI/CD configurations can be painful — `include` chains are hard to follow, job templates defined in remote files are invisible to the IDE, and there's no way to quickly find all usages of a shared template. CI Detective solves this by bringing full navigation and analysis support directly into IntelliJ IDEA.

## Features

- **Navigate to includes** — `Ctrl+Click` on any `include: local`, `include: file`, `include: remote`, or `include: template` directive to open the target file. Remote files are downloaded and cached automatically.
- **Navigate to job templates** — `Ctrl+Click` on `extends` to jump to the job template definition, even if it lives in a remote included file.
- **YAML anchor navigation** — `Ctrl+Click` on `*alias` to jump to the `&anchor` definition across all included files.
- **Find usages** — `Alt+F7` on a job template, YAML anchor, or variable to find all references across the project and cached remote files.
- **Inline documentation** — hover over an `include` path or `extends` value to preview the contents of the referenced file (`Ctrl+Q`).
- **Inspections** — real-time error highlighting for missing local includes, undefined job templates, and undefined YAML anchors.
- **Background indexing** — remote files are downloaded and indexed in the background when you open a `.gitlab-ci.yml` file, so navigation is available without manual triggering.
- **Cache management** — remote files are cached locally with configurable TTL. Use **Tools → Refresh GitLab CI Include Cache** to force a reload.

## Supported include types

| Type | Example |
|---|---|
| `local` | `include: local: '/templates/base.yml'` |
| `file` (scalar) | `file: '/templates/base.yml'` |
| `file` (list) | `file: ['/a.yml', '/b.yml']` |
| `remote` | `include: remote: 'https://...'` |
| `template` | `include: template: 'Auto-DevOps.gitlab-ci.yml'` |

## Requirements

- IntelliJ IDEA 2022.3 or later (Community or Ultimate)
- YAML plugin (bundled with IntelliJ IDEA)

## Installation

### From JetBrains Marketplace
*Coming soon.*

### From disk
1. Download the latest `.zip` from [Releases](../../releases).
2. In IntelliJ IDEA, go to **Settings → Plugins → ⚙️ → Install Plugin from Disk**.
3. Select the downloaded `.zip` file and restart the IDE.

### Build from source
```bash
git clone https://github.com/alinaagnistova/ci_detective.git
cd ci_detective
./gradlew buildPlugin
```
The plugin archive will be at `build/distributions/ci_detective-1.0.0.zip`.

## Configuration

Go to **Settings → Tools → CI Detective for GitLab** to configure:

| Setting | Description |
|---|---|
| GitLab URL | Base URL of your GitLab instance (default: `https://gitlab.com`) |
| GitLab Token | Personal Access Token with `read_api` scope for accessing private repositories |
| GitHub Token | Personal Access Token for accessing GitHub raw files (optional) |
| Cache TTL | How long remote files are cached before re-fetching (in hours) |

Tokens are stored securely using the IntelliJ Platform PasswordSafe API (integrated with the system keychain).

## How it works

When you open a `.gitlab-ci.yml` file, CI Detective:

1. Parses the file and collects all `include` directives.
2. Downloads remote files (`include: file`, `include: remote`, `include: template`) in the background and stores them in `.idea/gitlab-ci-cache/`.
3. Recursively processes nested includes up to 10 levels deep, with cycle detection.
4. Makes all cached files available for navigation, inspections, find usages, and inline documentation.

Local files (`include: local`) are resolved directly through the VFS without any network requests.

## Project structure

```
src/main/kotlin/com/detective/
├── action/          # IDE actions (Go to Declaration, Refresh Cache)
├── annotation/      # Annotator for visual highlighting
├── background/      # Background indexing task and file listener
├── cache/           # Cache management with TTL and eviction
├── documentation/   # Inline documentation provider (Ctrl+Q)
├── findUsages/      # Find usages handlers for jobs, anchors, variables
├── inspection/      # Inspections for missing includes, jobs, anchors
├── messages/        # i18n bundle
├── navigation/      # Goto declaration handler
├── onboarding/      # First-run notification
├── reference/       # PSI reference contributors
├── remote/          # GitLab and GitHub API clients
├── settings/        # Plugin settings and configurable
└── util/            # Shared utilities and constants
```

## Known limitations

- Navigation to job templates defined in `include: file` lists requires the files to be cached first. Open the file and wait for background indexing to complete, then retry.
- The underline highlight for `include: file` list entries may not appear immediately after cache is cleared — it refreshes on the next file open.

## License

Copyright 2026 Alina Agnistova

Licensed under the Apache License, Version 2.0. See [LICENSE](LICENSE) for details.