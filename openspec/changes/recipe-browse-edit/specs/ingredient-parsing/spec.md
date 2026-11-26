# Spec Delta

## MODIFIED Requirements

### Requirement: Kitchen-friendly quantity display
A quantity SHALL be shown in the form the kitchen uses for its unit. For metric units, it SHALL be a whole number or a decimal with at most two decimal places, without trailing zeros, in the decimal separator of the locale. For other units and counted items, it SHALL be a whole number, a fraction or a mixed number written with a fraction character when it is within 2 % of a multiple of 1/2, 1/3, 1/4 or 1/8, and such a decimal otherwise.

#### Scenario: Snapped fraction
- **WHEN** the quantity 0.33 is displayed for a counted item
- **THEN** it is shown as `⅓`

#### Scenario: Mixed number
- **WHEN** the quantity 9/4 is displayed for a counted item
- **THEN** it is shown as `2¼`

#### Scenario: Decimal in German
- **WHEN** the quantity 1.15 is displayed for the German locale
- **THEN** it is shown as `1,15`

#### Scenario: Metric unit
- **WHEN** the quantity 3/2 is displayed in litres for the German locale
- **THEN** it is shown as `1,5`

#### Scenario: Spoons and pieces
- **WHEN** `1 Zwiebel` and `1/2 TL Salz` are shown with factor 1.5 for the German locale
- **THEN** the texts are `1½ Zwiebel` and `¾ TL Salz`

#### Scenario: Every fraction character
- **WHEN** the quantities 1/2, 1/3, 2/3, 1/4, 3/4, 1/8, 3/8, 5/8 and 7/8 are displayed for a counted item
- **THEN** they are shown as `½`, `⅓`, `⅔`, `¼`, `¾`, `⅛`, `⅜`, `⅝` and `⅞`

#### Scenario: Range
- **WHEN** a range with minimum 2 and maximum 3 is displayed
- **THEN** both ends are shown with a dash between them
