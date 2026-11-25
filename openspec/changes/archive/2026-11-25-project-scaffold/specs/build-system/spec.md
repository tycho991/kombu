# Spec Delta

## Purpose

Defines how Kombu is built: the module layout and its dependency rules, the supported Android versions, the exclusion of Google Play Services, and how a signed release APK is produced.

## ADDED Requirements

### Requirement: Command-line build
The project SHALL build, run its unit tests and run lint from a fresh clone with a single Gradle wrapper invocation, using only a JDK and the Android SDK. It SHALL NOT need an IDE, network credentials or files outside the repository, except the optional signing configuration.

#### Scenario: Fresh clone builds
- **GIVEN** a fresh clone, a JDK and the Android SDK
- **WHEN** the developer runs the wrapper's build task
- **THEN** compilation, unit tests and lint run and the build succeeds
- **AND** a debug APK is produced

### Requirement: Application identity
The installed app SHALL use the application ID `io.github.tycho991.kombu`. Debug builds SHALL use a distinct application ID suffix so that they can be installed next to a release build.

#### Scenario: Release application ID
- **WHEN** a release APK is inspected
- **THEN** its application ID is `io.github.tycho991.kombu`

#### Scenario: Debug next to release
- **GIVEN** a release build is installed on a device
- **WHEN** a debug build is installed on the same device
- **THEN** both apps are installed side by side and neither replaces the other

### Requirement: Supported Android versions
The app SHALL install and start on Android 10 (API level 29) and every later version up to the target SDK. It SHALL NOT install on Android versions below API level 29.

#### Scenario: Oldest supported version
- **WHEN** the APK is installed on a device running Android 10
- **THEN** installation succeeds and the app starts to its first screen

#### Scenario: Unsupported version
- **WHEN** installation is attempted on a device running Android 9 or older
- **THEN** the system refuses the installation

### Requirement: Pure Kotlin modules stay Android-free
The modules `ingredient-parser`, `duration-parser`, `recipe-import` and `cookbook-client` SHALL compile and run their tests on a plain JVM without the Android SDK classes. They SHALL NOT depend on any Android module of the project.

#### Scenario: Android dependency added to a pure module
- **WHEN** a pure Kotlin module declares a dependency on an Android library or an Android module of the project
- **THEN** the build fails

#### Scenario: Pure module tests on a JVM
- **WHEN** the unit tests of a pure Kotlin module are run
- **THEN** they run on the JVM without an Android device, emulator or Android framework stubs

### Requirement: Module dependency direction
Modules SHALL depend on each other only in this direction: `app` → `sync` → `data`, and any Android module → the pure Kotlin modules. No module SHALL depend on `app`, and `data` SHALL NOT depend on `sync`.

#### Scenario: Reverse dependency
- **WHEN** a module declares a dependency against the allowed direction, for example `data` on `sync`
- **THEN** the build fails

### Requirement: No Google Play Services or Firebase
The app's runtime dependencies SHALL NOT include any artifact from Google Play Services (`com.google.android.gms`) or Firebase (`com.google.firebase`), including transitive ones.

#### Scenario: Forbidden dependency appears
- **WHEN** a direct or transitive runtime dependency from `com.google.android.gms` or `com.google.firebase` is added
- **THEN** the build fails and names the offending dependency

#### Scenario: Device without Google services
- **WHEN** the APK is installed on a device without Google Play Services
- **THEN** the app installs and starts normally

### Requirement: Signed release APK
The build SHALL produce a release APK signed with the APK Signature Scheme v2 or later when a signing configuration is provided outside version control. The keystore, its passwords and its location SHALL NOT be stored in the repository.

#### Scenario: Signing configuration present
- **GIVEN** a git-ignored signing configuration that points to a keystore outside the repository
- **WHEN** the developer builds the release variant
- **THEN** a signed release APK is produced that installs on a household device

#### Scenario: Signing configuration absent
- **GIVEN** no signing configuration
- **WHEN** the developer builds the release variant
- **THEN** the build still succeeds and produces an unsigned release APK
- **AND** the build output states that the APK is unsigned

#### Scenario: Secrets stay out of git
- **WHEN** the repository content is inspected
- **THEN** it contains no keystore file, keystore password or keystore path

### Requirement: No dependency metadata block in the APK
Release APKs SHALL NOT contain the encrypted dependency metadata block that the Android build tools add by default, so that the APK stays acceptable for F-Droid.

#### Scenario: Release APK inspected
- **WHEN** the signing block of a release APK is inspected
- **THEN** it contains no dependency metadata block
