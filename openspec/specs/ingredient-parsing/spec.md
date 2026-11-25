# ingredient-parsing Specification

## Purpose

Turns free-text recipe ingredient lines in German and English into quantity, unit, name and note, so that recipes can be scaled and displayed consistently, while every line that cannot be understood is kept exactly as written.

## Requirements

### Requirement: Every line yields a result
The parser SHALL accept any text and SHALL classify each line as measured, unmeasured or heading. It SHALL keep the original text of the line unchanged in every result and SHALL NOT fail or throw for any input.

#### Scenario: Empty or blank line
- **WHEN** the parser receives an empty string or only whitespace
- **THEN** it returns an unmeasured result whose original text is the input

#### Scenario: Unexpected characters
- **WHEN** the parser receives a line with emoji, control characters or a broken fragment such as `Scheiben geschnitten), 2 Sternanis, 1`
- **THEN** it returns a result without failing, and the original text is unchanged

### Requirement: Quantity at the start of the line
A line SHALL be measured when it starts with a quantity, optionally preceded by an approximation word such as `ca.`, `etwa` or `~`. A number later in the line SHALL NOT be taken as the quantity.

#### Scenario: Quantity at the start
- **WHEN** the parser receives `400 g Mehl`
- **THEN** the result is measured with quantity 400

#### Scenario: Approximation word
- **WHEN** the parser receives `ca. 400ml Rotwein`
- **THEN** the result is measured with quantity 400 and unit milliliter, and it records that the quantity is approximate

#### Scenario: Number inside the line
- **WHEN** the parser receives `Saft von 3 Orangen`
- **THEN** the result is unmeasured

### Requirement: Quantity notations
The parser SHALL read whole numbers, decimals with a point or a comma, ASCII fractions, mixed numbers and Unicode fractions as exact values.

#### Scenario: Decimal comma
- **WHEN** the parser receives `1,5 l Milch`
- **THEN** the quantity is exactly 3/2

#### Scenario: Decimal point
- **WHEN** the parser receives `0.125 teaspoon cayenne pepper`
- **THEN** the quantity is exactly 1/8

#### Scenario: Mixed number
- **WHEN** the parser receives `1 1/2 cups flour`
- **THEN** the quantity is exactly 3/2

#### Scenario: Unicode fraction
- **WHEN** the parser receives `1½ TL Salz` or `½ TL Salz`
- **THEN** the quantity is exactly 3/2 or 1/2

### Requirement: Quantity ranges
The parser SHALL read a range as a minimum and a maximum quantity. A range SHALL be recognised with a hyphen or en dash, with or without spaces, and with the words `bis` or `to`.

#### Scenario: Range without spaces
- **WHEN** the parser receives `2-3 Knoblauchzehen`
- **THEN** the result has minimum 2 and maximum 3

#### Scenario: Range with words
- **WHEN** the parser receives `2 bis 3 EL Öl` or `1 to 2 cups water`
- **THEN** the result has minimum 2 and maximum 3, or minimum 1 and maximum 2

#### Scenario: Range with glued unit
- **WHEN** the parser receives `2-3EL Orangensaft`
- **THEN** the result has minimum 2, maximum 3, unit tablespoon and name `Orangensaft`

### Requirement: German and English units together
The parser SHALL recognise German and English units in the same line set without a language setting. It SHALL return a language-independent unit identifier together with the unit text as written, never translated display text.

#### Scenario: German abbreviation
- **WHEN** the parser receives `2 EL Olivenöl`
- **THEN** the unit is tablespoon, the unit text is `EL` and the name is `Olivenöl`

#### Scenario: English code from a recipe card
- **WHEN** the parser receives `2 tbsp Olivenöl`
- **THEN** the unit is tablespoon and the name is `Olivenöl`

#### Scenario: English word
- **WHEN** the parser receives `1 teaspoon basil`
- **THEN** the unit is teaspoon and the name is `basil`

### Requirement: Unit spellings
Unit matching SHALL ignore case, except for single-letter units such as `T` (tablespoon) and `t` (teaspoon), and SHALL accept umlaut spellings and their ASCII forms (`ö` or `oe`), an optional trailing period, a plural marker in parentheses such as `(n)`, and a unit glued directly to the number.

#### Scenario: Umlaut spelling
- **WHEN** the parser receives `2 Teelöffel Zucker` or `2 Teeloeffel Zucker`
- **THEN** the unit is teaspoon in both cases

#### Scenario: Plural marker
- **WHEN** the parser receives `1 Prise(n) Salz`
- **THEN** the unit is pinch and the name is `Salz`

#### Scenario: Glued unit
- **WHEN** the parser receives `400g Mehl`
- **THEN** the quantity is 400, the unit is gram and the name is `Mehl`

#### Scenario: Abbreviation with period
- **WHEN** the parser receives `1 Pck. Vanillezucker`
- **THEN** the unit is packet and the name is `Vanillezucker`

### Requirement: German kitchen units
The German vocabulary SHALL include, beyond weights, volumes and spoons, at least: Prise, Bund, Päckchen and Pck., Stück, Stängel, Zweig, Zehe, Scheibe, Dose, Becher, Spritzer, Schuss, Knolle, Handvoll, Tütchen, Kugel, Blatt and Würfel.

#### Scenario: Bunch
- **WHEN** the parser receives `1 Bund Petersilie`
- **THEN** the unit is bunch and the name is `Petersilie`

#### Scenario: Handful
- **WHEN** the parser receives `1 Handvoll Rucola`
- **THEN** the unit is handful and the name is `Rucola`

### Requirement: Countable items without a unit
When a measured line has no known unit after the quantity, the rest of the line SHALL be the name. Size words such as `klein`, `groß`, `small` or `large` SHALL stay part of the name and SHALL NOT be units.

#### Scenario: Counted item
- **WHEN** the parser receives `2 Zwiebeln`
- **THEN** the result is measured with quantity 2, no unit and name `Zwiebeln`

#### Scenario: Size word
- **WHEN** the parser receives `2 kleine Zwiebeln`
- **THEN** the result has no unit and the name is `kleine Zwiebeln`

#### Scenario: Unknown language
- **WHEN** the parser receives `2 cuillères à soupe de sucre`
- **THEN** the result is measured with quantity 2 and no unit

### Requirement: Notes in parentheses and after a comma
Text in parentheses SHALL be returned as a note, separate from the name. Text after the first comma that follows the name SHALL also be returned as a note. A line can carry several notes, kept in their original order. A plural marker attached to a word, such as `(n)` or `(er)`, SHALL stay part of the word and SHALL NOT be a note.

#### Scenario: Plural marker in the name
- **WHEN** the parser receives `1 Ei(er)`
- **THEN** the name is `Ei(er)` and there is no note

#### Scenario: Parenthesised note
- **WHEN** the parser receives `1 Bund Basilikum (optional)`
- **THEN** the name is `Basilikum` and the note is `optional`

#### Scenario: Comma note
- **WHEN** the parser receives `0.125 teaspoon cayenne pepper, or to taste`
- **THEN** the name is `cayenne pepper` and the note is `or to taste`

### Requirement: Lines without a quantity
A line that does not start with a quantity SHALL be unmeasured. Its text SHALL be kept verbatim, so that the app can show it unchanged and mark it as not converted.

#### Scenario: Seasoning to taste
- **WHEN** the parser receives `Salz & Pfeffer`
- **THEN** the result is unmeasured with the original text `Salz & Pfeffer`

### Requirement: Section headings
A line without a quantity that ends with a colon SHALL be a heading. The heading text SHALL be returned without the colon.

#### Scenario: Heading
- **WHEN** the parser receives `Für die Füllung:`
- **THEN** the result is a heading with the text `Für die Füllung`

### Requirement: Scaling quantities
Scaling by a positive factor SHALL multiply the minimum and maximum quantity of measured lines exactly. It SHALL leave units, names, notes, unmeasured lines and headings unchanged. Scaling SHALL return new values and SHALL NOT change its input.

#### Scenario: Doubling
- **WHEN** `1/3 cup milk` is scaled by 2
- **THEN** the quantity is exactly 2/3 and the unit and name are unchanged

#### Scenario: Servings
- **WHEN** a line with quantity 3 is scaled from 4 servings to 6 servings
- **THEN** the quantity is exactly 9/2

#### Scenario: Unmeasured line
- **WHEN** `Salz` is scaled by any factor
- **THEN** the result equals the input

#### Scenario: Note stays unscaled
- **WHEN** `2 EL (30 g) Butter` is scaled by 2
- **THEN** the quantity is 4 and the note `30 g` is unchanged

#### Scenario: Invalid factor
- **WHEN** scaling is requested with a factor of zero or below
- **THEN** the request is rejected and no result is produced

### Requirement: Kitchen-friendly quantity display
A quantity SHALL be shown as a whole number, a fraction or a mixed number when it is within 2 % of a multiple of 1/2, 1/3, 1/4 or 1/8. Otherwise it SHALL be shown as a decimal with at most two decimal places, without trailing zeros, using the decimal separator of the given locale.

#### Scenario: Snapped fraction
- **WHEN** the quantity 0.33 is displayed
- **THEN** it is shown as `1/3`

#### Scenario: Mixed number
- **WHEN** the quantity 9/4 is displayed
- **THEN** it is shown as `2 1/4`

#### Scenario: Decimal in German
- **WHEN** the quantity 1.15 is displayed for the German locale
- **THEN** it is shown as `1,15`

#### Scenario: Range
- **WHEN** a range with minimum 2 and maximum 3 is displayed
- **THEN** both ends are shown with a dash between them

### Requirement: Scaled line text
For a measured line, the parser SHALL produce display text for a given factor by replacing only the quantity part of the original line with the displayed scaled quantity. The rest of the original text SHALL stay unchanged.

#### Scenario: Scaled German line
- **WHEN** `2 EL (30 g) Butter` is shown with factor 1.5 for the German locale
- **THEN** the text is `3 EL (30 g) Butter`

#### Scenario: Glued unit kept
- **WHEN** `400g Mehl` is shown with factor 1/2
- **THEN** the text is `200g Mehl`

### Requirement: Regression against real lines
The module SHALL include a fixture set of at least 100 real ingredient lines covering every notation above, each with its expected result. The fixtures SHALL NOT contain recipe names, URLs, people or store names. Every fixture SHALL pass.

#### Scenario: Fixture run
- **WHEN** the module tests run
- **THEN** every fixture line produces its expected classification, quantity, unit, name and note
