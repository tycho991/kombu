# Module ingredient-parser

Parses free-text ingredient lines such as `200 g Mehl` or `2 tbsp olive oil` into quantity, unit, name and notes, classifies lines without a quantity and section headings, scales quantities exactly and formats them in a kitchen-friendly way. German and English units are recognised together.

The parse logic is a Kotlin port of the MIT-licensed `@magrinj/parse-ingredients`, extended for German; see `NOTICE` in the module directory. Units are returned as language-independent identifiers; turning them into display text is the app's job.

Quantities are shown in the form the kitchen uses for their unit (`QuantityStyle.of`): metric units as decimals (`1,5 l`, `66,67 g`), every other unit and counted items as fractions with fraction characters where they fit (`1½ Zwiebeln`, `¾ TL`). `Measured.displayText` applies this when it scales a line; `Amount.format` takes the style as a parameter.

This module is pure Kotlin/JVM and has no Android dependency.
