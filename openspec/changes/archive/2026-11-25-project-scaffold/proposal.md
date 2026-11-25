# Proposal

## Why

Kombu has a project context and a plan for v1, but no code yet. Every v1 change (login, ingredient parser, sync, browsing and editing, cooking mode, share import) needs a buildable Android project with an agreed module layout, localization set up from the start (R5), and an automated check on every push. Setting this up once, before any feature work, keeps later changes small and means the module boundaries and rules are enforced from the first line of code, not added afterwards.

## What Changes

- Add a Gradle multi-module Android project (Gradle wrapper, version catalog, shared build logic) with application ID and base package `io.github.tycho991.kombu`.
- Add the v1 modules from the proposed layout as empty skeletons with their dependency edges wired:
  - pure Kotlin/JVM: `ingredient-parser`, `duration-parser`, `recipe-import`, `cookbook-client`
  - Android: `data`, `sync`, `app`
- Set `minSdk` to 29 (Android 10). This decision was made during planning; the project context does not record it yet.
- Add a minimal Compose app shell: one launchable screen with the app name and a placeholder text, all taken from string resources, and a placeholder launcher icon of its own (R6).
- Set up localization: English in `values/`, German in `values-de/`, a generated locale config so Android 13+ offers a per-app language choice, missing translations treated as build errors, and a lint check that flags hard-coded user-visible text in Compose code (R5).
- Add a release build that produces an APK signed with a keystore kept outside the repository. Signing data is read from a git-ignored `keystore.properties` (R4).
- Add a build check that fails if any runtime dependency comes from Google Play Services or Firebase.
- Add quality gates enforced by the build:
  - per-module coverage thresholds: pure modules 90 % lines and 80 % branches, `data`/`sync` 80 % and 70 %, `app` 70 % lines
  - static analysis with zero findings and no baseline
  - one formatting standard with an auto-fix command
  - Kotlin compiler and Android Lint warnings as errors
  - explicit API mode and mandatory KDoc for public API in the pure modules
- Add generated HTML API documentation and contributor documentation (`CONTRIBUTING.md`) describing every gate and command.
- Add a JVM-run Compose UI test of the first screen in English and German.
- Add a GitHub Actions workflow that runs the full check command on every push and pull request and publishes the test, lint, static-analysis and coverage reports and the API docs.
- Make the quality gates binding for all later changes through the rules in `openspec/config.yaml`.
- Pin the local toolchain (JDK) in `mise.toml` and document how to build in the README.

### Out of scope

- Any feature behavior: login, networking, Room schema, sync, recipe screens, cooking mode, parsers, import. Each comes in its own change.
- `caldav-client` and all CalDAV features. They follow after v1.
- Signing release APKs in CI, publishing releases, and store setup (F-Droid, Google Play). CI builds and checks only.
- Final app icon, theme and branding beyond a neutral placeholder.
- Reproducible-build verification for F-Droid. Choices that would block it, such as the dependency metadata block in the APK, are avoided, but this change does not verify it.
- Dependency update automation (Renovate, Dependabot).

## Capabilities

### New Capabilities

- `build-system`: How Kombu is built. Covers module layout and allowed dependency directions, the SDK levels, the ban on Google Play Services and Firebase, and the signed release APK.
- `localization`: How user-visible text reaches the UI. Covers English as default, German as first translation, translation completeness, per-app language selection, and the ban on hard-coded UI text.
- `continuous-integration`: What is checked automatically on every push and pull request.
- `code-quality`: The quality gates every change must pass. Covers coverage thresholds, static analysis, formatting, warnings as errors, documented public API and generated API documentation.

### Modified Capabilities

None. There are no existing specs.

## Impact

- **New files:** Gradle build files and wrapper, `gradle/libs.versions.toml`, shared build logic, the seven module directories, a lint-rules module, detekt configuration, `.editorconfig`, `.github/workflows/`, Android resources (`values/`, `values-de/`), `CONTRIBUTING.md`, and README changes.
- **Changed files:** `openspec/config.yaml` gets project-wide rules for quality gates, KDoc and test strategy, plus minSdk 29 under the fixed decisions.
- **Fixed decisions relied on:** Android only with Kotlin + Compose; application ID `io.github.tycho991.kombu`; GPL-3.0-or-later; distribution as an APK without Google Play Services; the proposed module layout.
- **Design rules relied on:** R4 (no keystore, passwords or personal data in the repo), R5 (no UI text in code, en/de resources), R6 (own placeholder icon and name).
- **New dependencies.** All of them are Apache-2.0, which is compatible with GPL-3.0-or-later, and none needs Google Play Services:
  - Android Gradle Plugin, Kotlin Gradle plugin, Gradle wrapper
  - Jetpack Compose (BOM, Material 3), AndroidX Activity Compose, AndroidX Core
  - Android Lint API and Lint test infrastructure (for the custom lint check; build and test time only)
  - Kover (coverage), detekt (static analysis) and Dokka (API docs), all Gradle plugins used at build time only
  - Compose UI test (test only)
- **ktlint** (MIT), used through detekt's formatting rules at build time only. MIT is compatible with GPL-3.0-or-later.
- **Test-only dependencies:** JUnit 5 (EPL-2.0) for the pure Kotlin modules; JUnit 4 (EPL-1.0), which the Lint test infrastructure and Robolectric require; and Robolectric (MIT) for JVM-run Compose UI tests. None of them is packaged into the APK, so their licenses do not affect what Kombu distributes.
- **Developer setup:** Building needs a JDK (pinned via mise) and the Android SDK command-line tools. Neither is set up on the current development machine yet.
