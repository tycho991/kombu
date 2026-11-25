# Spec Delta

## MODIFIED Requirements

### Requirement: Pure Kotlin modules stay Android-free
The modules `ingredient-parser`, `duration-parser`, `recipe-import`, `cookbook-client` and `nextcloud-auth` SHALL compile and run their tests on a plain JVM without the Android SDK classes. They SHALL NOT depend on any Android module of the project.

#### Scenario: Android dependency added to a pure module
- **WHEN** a pure Kotlin module declares a dependency on an Android library or an Android module of the project
- **THEN** the build fails

#### Scenario: Pure module tests on a JVM
- **WHEN** the unit tests of a pure Kotlin module are run
- **THEN** they run on the JVM without an Android device, emulator or Android framework stubs
