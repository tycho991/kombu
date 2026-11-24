# Tasks

## 1. Toolchain

- [x] 1.1 Pin Temurin JDK 21 in `mise.toml` next to OpenSpec and verify `java -version` reports 21 inside the repository
- [x] 1.2 Install the Android SDK command-line tools outside the repository, accept the licenses, install platform-tools and the platform for the chosen `compileSdk`, and verify `sdkmanager --list_installed` shows them (developer machine setup, nothing committed)

## 2. Gradle skeleton and build logic

- [x] 2.1 Choose versions per design D2 (Gradle wrapper, AGP, Kotlin, Compose BOM, Kover, detekt, Dokka, Robolectric, target SDK; each released at least two weeks earlier), write `gradle/libs.versions.toml`, and verify the catalog parses with `./gradlew help`
- [x] 2.2 Add the Gradle wrapper, `settings.gradle.kts`, root `build.gradle.kts` and `gradle.properties` (no personal values), and verify `./gradlew help` succeeds from a fresh clone
- [x] 2.3 Create the `build-logic` included build with convention plugins `kombu.jvm.library`, `kombu.android.library`, `kombu.android.application` and `kombu.android.compose` (SDK levels with minSdk 29, JVM target 17 on JDK 21 toolchain, JUnit 5 for JVM modules), and verify a module applying each plugin configures without errors
- [x] 2.4 Add the module-layer edge check to the convention plugins (design D4) and verify it by temporarily adding `data → sync` and `pure → data` dependencies: the build must fail naming both modules; revert afterwards

## 3. Quality gates

- [x] 3.1 Set `allWarningsAsErrors` for all Kotlin compilations and `explicitApi()` for pure modules (design D14), and verify with a temporary unused variable and a temporary top-level declaration without visibility modifier in a pure module that the build fails; revert afterwards
- [x] 3.2 Apply detekt with its ktlint formatting rules to all modules and Gradle scripts with `config/detekt/detekt.yml`, `maxIssues: 0`, no baseline, a `detektFormat` task and a matching `.editorconfig` (design D13), and verify a temporarily misformatted file fails `./gradlew check` and is fixed by `./gradlew detektFormat`; revert afterwards
- [x] 3.3 Enforce justified suppressions (design D13): use a built-in rule if one exists, otherwise create the `detekt-rules` module with a rule that reports `@Suppress` without a preceding comment, including unit tests for suppression with and without a comment, and verify `./gradlew check` fails on a temporary unjustified `@Suppress`; revert afterwards
- [x] 3.4 Enable the undocumented-public-API rules for the pure modules and verify a temporary public function without KDoc in a pure module fails `./gradlew check` naming the declaration; revert afterwards
- [x] 3.5 Apply Kover to every module with the thresholds from `specs/code-quality` and the generated-code filters (design D12), wire `koverVerify` into `check`, and verify that a temporary untested class in a pure module fails with module, value and threshold, and passes once a test covers it; revert afterwards
- [x] 3.6 Apply Dokka to the pure modules with an aggregated root site (design D15), and verify `./gradlew dokkaGenerate` (or the aggregate task of the chosen Dokka version) writes HTML that shows a temporary documented declaration; revert afterwards

## 4. Modules

- [x] 4.1 Create the pure Kotlin modules `ingredient-parser`, `duration-parser`, `recipe-import` and `cookbook-client` with package `io.github.tycho991.kombu.<module>`, and verify `./gradlew :ingredient-parser:test` (and the others) runs on the JVM and that detekt, Kover and Dokka are applied to each
- [x] 4.2 Verify the Android-free rule by temporarily adding an AndroidX dependency to one pure module: the build must fail; revert afterwards
- [x] 4.3 Create the Android library modules `data` and `sync` with the allowed edges `sync → data` and both → pure modules, and verify `./gradlew :sync:check` succeeds

## 5. App shell and localization

- [x] 5.1 Create the `app` module with application ID `io.github.tycho991.kombu`, debug suffix `.debug`, `MainActivity` (ComponentActivity, edge-to-edge, Material 3 Scaffold with dynamic color on Android 12+), and verify `./gradlew :app:assembleDebug` produces an APK
- [x] 5.2 Add string resources for the app name, the first-screen title and the placeholder text in `values/strings.xml` (en) and `values-de/strings.xml` (de), plus the non-translatable "Kombu Debug" app name in `src/debug/res/values/`, and verify the launcher label and first screen use them
- [x] 5.3 Add `res/resources.properties` with `unqualifiedResLocale=en`, enable `generateLocaleConfig`, set Android Lint `warningsAsErrors` and `MissingTranslation` to error, and verify a string added only to `values/` makes `./gradlew :app:lint` fail; revert afterwards
- [ ] 5.4 Add an original adaptive vector launcher icon with a monochrome layer (R6) and verify it renders in the launcher on an emulator, including with themed icons enabled on Android 13+
- [x] 5.5 Add Robolectric Compose UI tests that render the first screen with the `en` and `de` qualifiers and assert the resource texts (design D11), and verify `./gradlew :app:testDebugUnitTest` passes and `app` meets its 70 % line threshold

## 6. Lint check for hard-coded UI text

- [x] 6.1 Create the JVM module `lint-rules` with a detector for string literals and templates passed to Compose `Text`/`BasicText` `text` and to `contentDescription`, `label`, `placeholder` and `title` parameters of `androidx.compose` functions (design D7), with error severity and an issue explanation that states its limits
- [x] 6.2 Add Lint unit tests covering literal text, string templates, literal content description, a resource-based text (no report) and a non-Compose function with a `title` parameter (no report), and verify `./gradlew :lint-rules:test` passes and the module meets the 90/80 coverage threshold
- [x] 6.3 Register the check in every Android module through the convention plugins and verify a temporary `Text("Hello")` in `app` makes `./gradlew build` fail with the file and line; revert afterwards

## 7. Release build and dependency rules

- [x] 7.1 Add release signing from the optional `keystore.properties` (v2+v3, no v1) with an "unsigned" warning when it is absent, and verify that `./gradlew :app:assembleRelease` succeeds in both cases and that `apksigner verify --print-certs` confirms the signature when it is present
- [x] 7.2 Disable the dependency metadata block (`dependenciesInfo`) and verify with `apksigner verify -v` or an APK signing-block dump that the release APK contains no dependency info block
- [x] 7.3 Add the Play Services/Firebase check (design D5), wire it into `check`, and verify by temporarily adding a `com.google.android.gms` dependency: the build must fail naming it; revert afterwards
- [x] 7.4 Verify that `git status` and `git ls-files` show no keystore, `keystore.properties` or other secret after a signed build (R4)

## 8. Continuous integration

- [x] 8.1 Add `.github/workflows/ci.yml` per design D10 (push and pull_request, `contents: read`, wrapper validation, Temurin 21, setup-gradle, `./gradlew build`, Dokka, coverage reports and API docs uploaded per run, test/lint/detekt reports uploaded on failure, all actions pinned to commit SHAs), and verify a push runs green on GitHub and offers coverage reports and API docs for download
- [x] 8.2 Verify the failure path by pushing a throwaway branch with a failing unit test and a detekt finding: the run must fail and offer the test and detekt reports for download; delete the branch afterwards

## 9. Documentation

- [x] 9.1 Add a "Building" section to the README (mise for the JDK, Android SDK setup, `./gradlew build`, creating a keystore with `keytool` and the `keystore.properties` format, kept outside git, link to `CONTRIBUTING.md`) and update the Status section; verify the documented commands work as written on this machine
- [x] 9.2 Add `CONTRIBUTING.md` per design D16 (toolchain, check/format/docs commands, every gate with its threshold, suppression rule, gates stay green in every change), and verify every command in it runs as written

## 10. Integration check

- [x] 10.1 From a fresh clone, run `./gradlew build` and verify it succeeds with no network credentials and no files outside the repository, and that coverage, detekt, lint and Dokka reports are produced
- [ ] 10.2 Install the debug and the signed release APK side by side on an Android 10 (API 29) emulator and on a current household device, and verify both start, show the first screen in the device language, and that Android 13+ offers English and German in the per-app language setting

## Workflow follow-up

- Review and merge the implementation.
- Archive the change with `/openspec-archive-change` so that the four capabilities become main specs.
