# Module ingredient-parser

Parses free-text ingredient lines such as `200 g Mehl` or `2 tbsp olive oil` into quantity, unit, name and notes, classifies lines without a quantity and section headings, scales quantities exactly and formats them in a kitchen-friendly way. German and English units are recognised together.

The parse logic is a Kotlin port of the MIT-licensed `@magrinj/parse-ingredients`, extended for German; see `NOTICE` in the module directory. Units are returned as language-independent identifiers; turning them into display text is the app's job.

This module is pure Kotlin/JVM and has no Android dependency.
