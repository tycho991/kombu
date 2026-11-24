# Spec Delta

## Purpose

Defines what is checked automatically for every change pushed to Kombu's repository, so that a broken build, failing tests or rule violations are caught before they reach `main`.

## ADDED Requirements

### Requirement: Checks on push and pull request
Every push to any branch and every pull request SHALL trigger an automated run that builds the project and runs the full check command, including all checks of the build-system, localization and code-quality capabilities.

#### Scenario: Passing change
- **WHEN** a commit that builds, passes its tests and passes lint is pushed
- **THEN** the automated run succeeds and the commit is marked as passing

#### Scenario: Failing test
- **WHEN** a commit with a failing unit test is pushed
- **THEN** the automated run fails and the commit is marked as failing

#### Scenario: Rule violation
- **WHEN** a commit adds hard-coded UI text, a missing German translation or a Google Play Services dependency
- **THEN** the automated run fails

#### Scenario: Quality gate violation
- **WHEN** a commit lowers coverage below a threshold, adds a static-analysis finding or contains unformatted code
- **THEN** the automated run fails

### Requirement: Results are inspectable
A failed run SHALL make the test, lint and static-analysis reports available for download from the run.

#### Scenario: Lint failure report
- **WHEN** a run fails because of lint
- **THEN** the lint report of that run can be downloaded from the run page

### Requirement: Coverage and API documentation published per run
Every run that reaches the end of the check command SHALL make the coverage reports and the generated API documentation available for download from the run, whether or not the run succeeded.

#### Scenario: Successful run
- **WHEN** a run succeeds
- **THEN** the coverage reports and the HTML API documentation can be downloaded from the run page

### Requirement: No secrets needed
The automated run SHALL succeed without any repository secrets, and it SHALL NOT sign release APKs.

#### Scenario: Pull request from a fork
- **WHEN** a pull request is opened from a fork, where secrets are not available
- **THEN** the automated run executes all checks normally

### Requirement: Trusted build tooling
The automated run SHALL verify the Gradle wrapper before using it, and SHALL reference third-party workflow actions by an immutable commit identifier.

#### Scenario: Tampered wrapper
- **WHEN** the Gradle wrapper JAR in a commit does not match a known official release
- **THEN** the automated run fails before the build starts
