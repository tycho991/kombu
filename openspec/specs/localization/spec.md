# localization Specification

## Purpose

Defines how user-visible text reaches Kombu's UI: English as the default language, German as the first translation, complete translations, per-app language choice and no user-visible text hard-coded in source code.

## Requirements

### Requirement: English default, German translation
Every user-visible string SHALL exist in the default (English) resources. Every such string SHALL also exist in the German resources unless it is explicitly marked as not translatable.

#### Scenario: German device
- **GIVEN** a device whose system language is German
- **WHEN** the app is opened
- **THEN** all text on the first screen is German

#### Scenario: Unsupported language
- **GIVEN** a device whose system language has no Kombu translation, for example French
- **WHEN** the app is opened
- **THEN** all text on the first screen is English

### Requirement: Translations are complete
The build SHALL fail when a translatable string exists in the default resources but is missing in the German resources.

#### Scenario: Missing German string
- **WHEN** a new translatable string is added only to the English resources
- **THEN** lint reports an error and the build fails

### Requirement: Per-app language selection
On Android 13 and later, the system's per-app language setting SHALL list Kombu with English and German as choices. The app SHALL show the selected language regardless of the system language.

#### Scenario: Per-app language overrides system language
- **GIVEN** a device on Android 13 or later with English as system language
- **WHEN** the user sets Kombu's app language to German in the system settings and opens the app
- **THEN** the app shows German text

#### Scenario: Older Android version
- **GIVEN** a device on Android 10 to 12
- **WHEN** the app is opened
- **THEN** the app follows the system language

### Requirement: No hard-coded UI text in source code
User-visible text in Compose UI code SHALL come from string resources. A string literal passed as visible text or as an accessibility description of a UI element SHALL be reported as a lint error and SHALL fail the build.

#### Scenario: Literal text in a composable
- **WHEN** source code passes a string literal as the text of a text element
- **THEN** lint reports an error naming the file and line, and the build fails

#### Scenario: Literal content description
- **WHEN** source code passes a string literal as a content description
- **THEN** lint reports an error and the build fails

#### Scenario: Text from resources
- **WHEN** source code passes text loaded from a string resource
- **THEN** lint reports nothing for it

### Requirement: Launcher label from resources
The app name shown in the launcher and in the system settings SHALL come from string resources.

#### Scenario: Launcher label
- **WHEN** the app is installed
- **THEN** the launcher shows the app name defined in the string resources for the device language
