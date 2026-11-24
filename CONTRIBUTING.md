# Contributing to Kombu

Kombu aims for high code quality from the first line. The build enforces it: every quality gate below runs in one command, locally and in CI, and a change is only done when all gates are green.

## Before you start

- Kombu is developed spec-first with [OpenSpec](https://github.com/Fission-AI/OpenSpec). Every larger feature starts as a change under [`openspec/changes/`](openspec/changes/) (propose, apply, archive). Read [`openspec/config.yaml`](openspec/config.yaml) for the product context, fixed decisions and design rules R1–R7.
- Code, comments, commit messages, docs and specs are written in English.
- Never commit anything personal: no server URLs, account names, credentials, store names or private recipes.
- New dependencies need a license compatible with GPL-3.0-or-later and must work without Google Play Services.

## Toolchain

- JDK 21 (pinned in [`mise.toml`](mise.toml); `mise install` sets it up)
- Android SDK command-line tools with the platform from `compileSdk` in [`gradle/libs.versions.toml`](gradle/libs.versions.toml)

The [README](README.md#building) describes the setup.

## Commands

| Command | What it does |
|---|---|
| `./gradlew build` | Compiles everything, runs all tests and **all quality gates**. This is the check command; CI runs the same. |
| `./gradlew detektFormat` | Fixes formatting findings that can be corrected automatically (run per module, e.g. `./gradlew :app:detektFormat`, or `./gradlew detektFormat` for all). |
| `./gradlew :dokkaGenerate` | Generates the HTML API documentation of the pure Kotlin modules into `build/dokka/html/`. |
| `./gradlew :app:assembleRelease` | Builds the release APK, signed if `keystore.properties` exists (see the README). |

Reports are written to each module's `build/reports/` directory (`tests/`, `kover/`, `detekt/`, `lint-results-*.html`).

## Quality gates

| Gate | Rule | Enforced by |
|---|---|---|
| Unit tests | All tests pass. | Gradle `test` |
| Coverage | Per module, generated code excluded: pure modules and build-tooling modules ≥ 90 % lines and ≥ 80 % branches; `data` and `sync` ≥ 80 % lines and ≥ 70 % branches; `app` ≥ 70 % lines. | Kover (`koverVerify`) |
| Static analysis | Zero findings, no baseline. | detekt |
| Formatting | One standard (ktlint rules), checked in every module and in the build scripts. | detekt with ktlint rules |
| Suppressions | Every `@Suppress` needs a comment on the same or the preceding line that explains why. | Kombu's detekt rule `UnjustifiedSuppression` |
| Warnings | Kotlin compiler warnings and Android Lint warnings fail the build. | `allWarningsAsErrors`, Lint `warningsAsErrors` |
| Public API | The pure modules use explicit API mode, and every public declaration has KDoc. | Kotlin compiler, detekt |
| UI text | No user-visible text in code: strings come from resources, English in `values/`, German in `values-de/`, and every string is translated. | Kombu's Lint check `HardcodedComposeText`, Lint `MissingTranslation` |
| Module boundaries | Dependencies only run `app → sync → data`, and any module → the pure modules. The pure modules have no Android dependency. | Convention plugins in `build-logic` |
| No Play Services | No artifact from `com.google.android.gms` or `com.google.firebase`, not even transitively. | `checkForbiddenDependencies` |

The gates are specified in the `code-quality`, `build-system` and `localization` specs. Loosening one (a lower threshold, a disabled rule) is a change to those specs and must be proposed as such, never done silently.

## Writing tests

- Coverage is a floor, not the goal. Test behaviour with real assertions; a test that only executes code without checking its result does not count as a test in review.
- Parsers and API clients live in the pure Kotlin modules and are tested on the JVM with JUnit Jupiter, using fixtures.
- Compose UI is tested on the JVM with Robolectric (`app/src/test/`). Check texts per language with `@Config(qualifiers = "de")`.
- Behaviour that needs a real device or emulator is named in the change's design as part of its test strategy.

## Suppressing a finding

Fix the finding if you can. If a suppression is the right call, put the reason directly above it:

```kotlin
// The value is defined by the Cookbook API and documented there.
@Suppress("MagicNumber")
private const val DEFAULT_PAGE_SIZE = 50
```

## Lint checks that are switched off

A few Android Lint checks are disabled on purpose, see `AndroidLintConfiguration.kt` in `build-logic`:

- Checks that report newer versions of libraries, the Android Gradle Plugin or the SDK (`GradleDependency`, `AndroidGradlePluginVersion`, `NewerVersionAvailable`, `OldTargetApi`). They depend on the date of the build, not on the code. Version updates are deliberate changes.
- `ObsoleteLintCustomCheck`. It reports lint checks bundled in third-party libraries that were built against an older Lint API. It is about other projects' code, not Kombu's.
