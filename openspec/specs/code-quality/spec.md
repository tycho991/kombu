# code-quality Specification

## Purpose

Defines the quality gates every Kombu change must pass: enforced test coverage, static analysis with zero findings, consistent formatting, warnings treated as errors, and documented public APIs.

## Requirements

### Requirement: One command runs all checks
A single Gradle wrapper invocation SHALL run every quality gate of this capability together with compilation, unit tests and lint, and SHALL fail if any gate fails.

#### Scenario: All gates pass
- **WHEN** the developer runs the check command on a clean tree that meets all gates
- **THEN** the command succeeds

#### Scenario: One gate fails
- **WHEN** any single gate of this capability is violated
- **THEN** the check command fails and names the violated gate

### Requirement: Enforced test coverage
The build SHALL fail when unit-test coverage of a module falls below its threshold: pure Kotlin modules 90 % lines and 80 % branches, `data` and `sync` 80 % lines and 70 % branches, `app` 70 % lines. Generated code SHALL NOT count.

#### Scenario: Coverage below threshold
- **WHEN** code is added to a pure Kotlin module without tests, so its line coverage drops below 90 %
- **THEN** the check command fails and reports the module, the measured value and the threshold

#### Scenario: Generated code excluded
- **WHEN** coverage is measured for a module containing generated code, such as Compose compiler output or build config classes
- **THEN** the generated code is not part of the measured total

#### Scenario: Coverage report
- **WHEN** the check command has run
- **THEN** an HTML and an XML coverage report exist for every module

### Requirement: Static analysis with zero findings
Kotlin code SHALL pass static analysis with zero findings. The project SHALL NOT use a findings baseline. A suppression of a finding SHALL carry a comment on the same or preceding line that states the reason.

#### Scenario: New finding
- **WHEN** code with a static-analysis finding is added
- **THEN** the check command fails and reports the rule, file and line

#### Scenario: Suppression without reason
- **WHEN** a finding is suppressed without a justifying comment
- **THEN** the check command fails

### Requirement: Consistent formatting
Kotlin source and Gradle Kotlin scripts SHALL follow one formatting standard. The check command SHALL fail on formatting deviations, and a separate command SHALL fix them automatically.

#### Scenario: Unformatted code
- **WHEN** a file deviates from the formatting standard
- **THEN** the check command fails and names the file

#### Scenario: Automatic fix
- **WHEN** the developer runs the format command
- **THEN** all fixable deviations are corrected and the check command no longer reports them

### Requirement: Warnings are errors
Kotlin compiler warnings and Android Lint warnings SHALL fail the build.

#### Scenario: Compiler warning
- **WHEN** code that produces a Kotlin compiler warning, for example a call to a deprecated function, is compiled
- **THEN** the build fails

#### Scenario: Lint warning
- **WHEN** an Android module triggers an Android Lint warning
- **THEN** the build fails

### Requirement: Documented public API in pure modules
Every public declaration in the pure Kotlin modules SHALL declare its visibility explicitly and SHALL carry a KDoc comment.

#### Scenario: Implicit visibility
- **WHEN** a pure Kotlin module contains a top-level declaration without an explicit visibility modifier
- **THEN** the build fails

#### Scenario: Missing KDoc
- **WHEN** a public class or function in a pure Kotlin module has no KDoc comment
- **THEN** the check command fails and names the declaration

### Requirement: Generated API documentation
The build SHALL generate browsable HTML API documentation from the KDoc of all pure Kotlin modules.

#### Scenario: Documentation generated
- **WHEN** the developer runs the documentation command
- **THEN** HTML API documentation for every pure Kotlin module is written to the build directory

### Requirement: Contributor documentation
The repository SHALL contain contributor documentation that lists every quality gate, the coverage thresholds, and the commands to run all checks, fix formatting and generate API documentation.

#### Scenario: New contributor
- **WHEN** a contributor follows the contributor documentation on a fresh clone with the documented toolchain
- **THEN** the documented check command runs and succeeds
