# Design

## Context

The repository holds only the license, README, agent instructions and OpenSpec setup; there is no Gradle project yet (see proposal.md, Why). The requirements are in `specs/build-system`, `specs/localization` and `specs/continuous-integration`.

Constraints that shape the approach:

- The development machine has no JDK and no Android SDK yet. `mise` manages tools per project; the global mise configuration is managed by Ansible and must not be edited from here, so tools are pinned in the project's `mise.toml`.
- `.gitignore` already excludes `keystore.properties`, `*.jks`, `*.keystore` and `local.properties`.
- Nothing in this change talks to a server. Offline behaviour and server failure modes therefore do not apply; they start with `nextcloud-login` and `recipe-sync`.

## Goals / Non-Goals

**Goals:**

- One place for shared build configuration, so later modules are a few lines each.
- Rules from the specs (module boundaries, no Play Services, no hard-coded UI text, complete translations) enforced by the build itself, not by review.
- Toolchain versions pinned so that every machine and CI build with the same versions.
- Quality gates (coverage, static analysis, formatting, warnings, KDoc) set to their final strictness from the first commit. Loosening them later is easy, while tightening them on existing code is expensive.

**Non-Goals:**

- Choosing libraries for later changes: Room, WorkManager, HTTP client, image loading. Each feature change adds its own dependencies.
- Gradle TestKit tests for the build logic itself. See Risks.
- Mutation testing and screenshot tests. They can follow once there is real logic or UI to protect.

## Decisions

### D1 Shared build logic as convention plugins in an included build

A `build-logic/` included build provides convention plugins, for example `kombu.jvm.library`, `kombu.android.library`, `kombu.android.application` and `kombu.android.compose`. Each plugin sets the SDK levels, the JVM target, lint options and test setup in one place.

- *Alternative: `buildSrc`.* It works, but any change to it invalidates the whole build cache, and it is on the way out in Gradle's own guidance.
- *Alternative: repeating config in each module.* It is simple for seven modules, but drifts quickly and makes the rule checks easy to forget in new modules.

### D2 Versions in a version catalog, chosen at implementation time

All plugin and library versions live in `gradle/libs.versions.toml`. At implementation time, take the newest stable release of the Gradle wrapper, Android Gradle Plugin, Kotlin and Compose BOM that was published at least two weeks earlier. `compileSdk` and `targetSdk` use the newest stable API level that this AGP supports, and `minSdk` is 29.

If the chosen AGP has built-in Kotlin support (AGP 9 and later), Android modules use it and do not apply the separate Kotlin Android plugin.

- *Alternative: fixing exact versions in this design.* They would be outdated by the time the change is applied, and the specs do not depend on them.

### D3 JDK 21 toolchain, bytecode target 17

Gradle runs on JDK 21, pinned in `mise.toml` and set up the same way in CI. All Kotlin and Java code compiles to JVM target 17, including the pure modules, so that Android modules can consume them without desugaring concerns.

- *Alternative: target 21 for the pure modules.* It gains nothing for parsers and risks incompatibility when Android modules consume them.

### D4 Module boundaries enforced by plugin choice plus an edge check

Pure modules apply the Kotlin JVM plugin, so Android classes are not on their classpath at all. A dependency on an Android library fails to resolve, because it is an AAR without a JVM variant.

On top of that, the convention plugins declare each module's layer: `pure`, `data`, `sync` or `app`. At configuration time they check every `project(...)` dependency against the allowed edges from the spec and fail with a message naming both modules.

- *Alternative: a third-party plugin such as dependency-analysis or module-graph-assert.* It is another dependency for a check that takes about 30 lines.

### D5 Play Services and Firebase ban as a resolution check in `app`

A task in the application convention plugin resolves the release and debug runtime classpaths. It fails if any component's group starts with `com.google.android.gms` or `com.google.firebase`, naming the dependency and the path that pulled it in. The task is wired into `check` and runs before assembly, so `build` and CI catch it.

- *Alternative: a global exclude rule.* It would hide the problem: a library that needs Play Services would then crash at runtime instead of failing the build.

### D6 Localization through AGP resource features and Android Lint

- Strings live in `app/src/main/res/values/strings.xml` (English) and `values-de/strings.xml` (German). `res/resources.properties` sets `unqualifiedResLocale=en`, and `androidResources.generateLocaleConfig = true` lets AGP generate the locale config. This gives the per-app language setting on Android 13+ without AppCompat.
- Lint severity for `MissingTranslation` is error, and `abortOnError` stays on.
- Debug builds override `app_name` in `src/debug/res/values/` with a non-translatable "Kombu Debug", so the two installs can be told apart.

- *Alternative: AppCompat's per-app language API for Android 10–12.* It adds a dependency and an in-app language picker the specs do not ask for. Android 10–12 follow the system language.

### D7 Custom lint check for hard-coded Compose text

Built-in Android Lint (`HardcodedText`) covers only XML layouts. A new JVM module `lint-rules` uses the Lint API to report a string literal or string template when it is passed to one of these:

- the `text` parameter of Compose `Text` and `BasicText`
- any parameter named `contentDescription`, `label`, `placeholder` or `title` of a function from an `androidx.compose` package

It reports with error severity. The convention plugins add it to every Android module through `lintChecks(project(":lint-rules"))`. The detector is unit-tested with the Lint test infrastructure.

- *Alternative: a custom detekt rule.* detekt is part of the build anyway (D13), but its default run has no full type resolution. Android Lint resolves which `Text` function is called, so it can tell Compose calls from others reliably.
- *Alternative: no enforcement.* R5 would then depend on review only, which is exactly what the specs want to avoid.

### D8 Release signing from a git-ignored properties file

The application plugin reads `keystore.properties` from the repository root if it exists. It contains `storeFile` (an absolute path outside the repository), `storePassword`, `keyAlias` and `keyPassword`.

- If the file is present, the release variant is signed with v2 and v3 signature schemes. v1 is not needed because minSdk is 29.
- If the file is absent, the release variant stays unsigned and the build logs a warning that says so.
- `dependenciesInfo { includeInApk = false; includeInBundle = false }` removes the dependency metadata block.

The README documents how to create a keystore with `keytool` and where to keep it.

- *Alternative: environment variables.* They are useful for CI signing later and can be added then. For a single developer, a file is easier.

### D9 Minimal app shell without AppCompat

`MainActivity` extends `ComponentActivity`, enables edge-to-edge and shows a Material 3 `Scaffold` with the app name and a placeholder text from resources. Dynamic color is used on Android 12+, with a neutral fallback scheme below.

The launcher icon is an adaptive vector icon with a monochrome layer for themed icons. It shows a simple, original shape, so nothing is taken from other apps (R6).

### D10 CI on GitHub Actions

One workflow `.github/workflows/ci.yml`, triggered by `push` and `pull_request`, with `permissions: contents: read`. Steps:

1. Checkout.
2. Gradle wrapper validation.
3. `setup-java` with Temurin 21.
4. `setup-gradle` for caching.
5. `./gradlew build`, which covers assemble and `check`: unit tests, lint, the edge check, the Play Services check, coverage verification, detekt and the formatting check.
6. Generate the API docs with Dokka.
7. Upload the coverage reports and the API docs with `upload-artifact` on every run that reaches this step, and the test, lint and detekt reports on failure.

All actions are pinned to full commit SHAs, with the version noted in a comment. The Android SDK preinstalled on GitHub's Ubuntu runners is used, and AGP fetches a missing platform automatically.

### D11 Test frameworks

- Pure Kotlin modules use JUnit 5 (Jupiter) through the JVM convention plugin, ready for the fixture-based parser tests in later changes.
- `lint-rules` uses the Lint test infrastructure, which runs on JUnit 4.
- Android modules run unit tests on the JVM with JUnit 4. Compose UI tests in `app` use the Compose UI test library on Robolectric (MIT), so they need no emulator and run in CI. The first-screen test renders the screen once with the `en` and once with the `de` qualifier and asserts the texts from resources.

All of these are test-scope only and never packaged into the APK.

### D12 Coverage with Kover

Kover (JetBrains, Apache-2.0) is applied by the convention plugins to every module, with the per-module thresholds from `specs/code-quality`. The build-tooling modules `lint-rules` and `detekt-rules` are pure Kotlin modules too and use the same 90/80 threshold. `koverVerify` is wired into `check`, and HTML and XML reports are produced per module.

Classes generated by the Compose compiler, `BuildConfig`, `R` and `ComposableSingletons` are excluded by filter. A module with no production code yet passes, because there is nothing to measure. The threshold takes effect with the first class.

- *Alternative: JaCoCo directly.* It works for both JVM and Android, but needs per-variant wiring in Android modules that Kover already does, and it handles Kotlin inline functions worse.

### D13 detekt with ktlint formatting rules

detekt (Apache-2.0) runs on all Kotlin sources and Gradle Kotlin scripts. Its ktlint-based formatting rule set (ktlint is MIT) is the single formatting standard.

- One `config/detekt/detekt.yml` builds on detekt's defaults and turns on, among others, the comment rules for undocumented public classes, functions and properties for the pure modules.
- There is no baseline file, and `maxIssues` is 0.
- A `detektFormat` task (auto-correct) is the format command.
- The detekt run fails on `@Suppress` without a preceding justification comment. If no built-in rule covers this, a small custom rule in a `detekt-rules` module does it, with its own unit tests.

`.editorconfig` mirrors the formatting settings for editors.

- *Alternative: Spotless with ktfmt or ktlint.* That is another plugin next to detekt with an overlapping job. One tool with one configuration is easier to keep consistent.

### D14 Compiler strictness

All Kotlin compilations set `allWarningsAsErrors = true`. Pure modules enable `explicitApi()`, which makes the compiler reject public declarations without an explicit visibility. Android Lint uses `warningsAsErrors = true` and `abortOnError = true`.

### D15 API documentation with Dokka

Dokka (Apache-2.0) is applied to the pure modules and aggregated at the root into one HTML site under `build/dokka/`. Android modules are left out, because their API is internal to the app. CI uploads the site per run (D10).

### D16 Contributor documentation

`CONTRIBUTING.md` lists:

- the toolchain
- the one check command (`./gradlew build`)
- the format command and the docs command
- every quality gate with its threshold
- the suppression rule
- the rule that every change must keep all gates green

The README links to it from its Building section.

## Risks / Trade-offs

- [The custom lint check only sees literals passed directly, for example not `val s = "x"; Text(s)`] → It catches the common case. The check's limits are documented in its issue explanation, and the rest stays a review point.
- [The lint check misses composables outside `androidx.compose` or with other parameter names] → Extend the parameter list when new UI patterns come up. Its unit tests make extension cheap.
- [No automated tests for the edge check and the Play Services check] → Tasks include manual negative checks: temporarily add a forbidden edge and a forbidden dependency, confirm the build fails, then revert. Gradle TestKit tests can follow if the build logic grows.
- [AGP, Kotlin and Compose versions move fast, and AGP 9 changed Kotlin integration] → Versions are chosen at implementation time (D2) and kept in one catalog.
- [CI depends on the Android SDK preinstalled on GitHub runners] → AGP downloads missing platforms itself. If runner images drop the SDK, add an explicit SDK setup step.
- [An empty pure module produces no artifact worth testing] → The modules stay empty until their feature change. The build still proves the boundaries and the test setup.
- [Coverage thresholds invite tests that execute code without checking it] → Coverage is a floor, not the goal. `CONTRIBUTING.md` asks for assertions on behaviour, and reviews check them. Mutation testing can be added later if this becomes a problem.
- [70 % for `app` may be hard to hold once the UI grows] → Robolectric UI tests keep UI code testable on the JVM. Changing a threshold requires a change to the `code-quality` spec, so it is a visible decision, not a silent drift.
- [Strict gates slow down early prototyping] → That is intended. Spikes that must not meet the gates happen outside the repository or on throwaway branches.
- [detekt, ktlint and Kotlin versions must match] → Their versions sit together in the version catalog and are updated together.

## Migration Plan

Greenfield; nothing to migrate. Rollback is reverting the change's commits.

`openspec/config.yaml` was updated during planning: minSdk 29 is listed under the fixed decisions, and the project-wide rules require the quality gates in every change.

## Open Questions

- Exact version numbers for Gradle, AGP, Kotlin, Compose and the target SDK. They are chosen at implementation time per D2 and do not affect the specs.
- Final icon and theme colors. They are placeholders here and belong to a later branding change.
