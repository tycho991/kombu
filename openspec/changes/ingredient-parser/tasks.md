# Tasks

## 1. Module setup and attribution

- [ ] 1.1 Add `ingredient-parser/NOTICE` with the upstream MIT license text, copyright line and ported version (`@magrinj/parse-ingredients` 1.0.0). Update the module's `Module.md`. Mention the notice in the README license section. Update the ingredient-parser line of the v1 scope in `openspec/config.yaml` to name the chosen library. Verify that `./gradlew :ingredient-parser:check` runs and that `checkForbiddenDependencies` passes.

## 2. Exact quantities

- [ ] 2.1 Implement `Rational` (design D2): reduction, sign, `plus`, `times`, `div`, comparison, overflow guard with `Math.multiplyExact`, and exact parsing of integers, decimals with point or comma, ASCII fractions, mixed numbers and Unicode fractions, with the digit limits. Verify with table tests, including 1/3 × 3 = 1, `0.125` = 1/8, `1,5` = 3/2, and too many digits being rejected.
- [ ] 2.2 Implement `Amount` with `scaledBy` (positive factors only; a factor of zero or below throws) and `format(locale)` (design D6): whole numbers, snapping to 1/2, 1/3, 1/4 and 1/8 within 2 %, mixed numbers, decimals with at most two places and the locale separator, and ranges with an en dash. Verify with tests for every scenario of the spec requirements "Scaling quantities" and "Kitchen-friendly quantity display", in German and English locales.

## 3. Units and vocabularies

- [ ] 3.1 Implement `IngredientUnit`, the key normalisation (design D3) and the German and English vocabularies (design D4), with upstream words, the added kitchen units, and the spike spellings. Do not include size words. Verify with tests:
  - every German kitchen unit from the spec;
  - umlaut and ASCII forms;
  - case-insensitivity, plus the exact `T`/`t` rule;
  - a trailing period and the plural marker;
  - the conflict test that no normalised key maps to two units.

## 4. Parsing

- [ ] 4.1 Implement `IngredientParser.parse` and the `IngredientLine` result types (design D1, D5):
  - quantity only at the start, with approximation prefixes;
  - ranges with `-`, `–`, `bis` and `to`, with or without spaces;
  - glued units and longest unit match;
  - stripping of English articles;
  - notes from parentheses and from the first comma, with plural markers kept;
  - headings;
  - unmeasured lines;
  - `quantityRange`.

  Verify with tests for every scenario of the spec requirements from "Every line yields a result" to "Section headings". Include empty, blank and odd input, and check that nothing throws.
- [ ] 4.2 Implement `IngredientLine.scaledBy` and `Measured.displayText(factor, locale)`, which replace only the quantity text. Verify with tests for the spec requirement "Scaled line text", plus `ca.` prefixes, glued units and ranges being kept.
- [ ] 4.3 Port the upstream parse tests from `src/__tests__/index.test.ts` (design D7). Adapt tests that check behaviour Kombu changes on purpose, and give each adapted test a comment with the reason. Add the source header comment to the ported files. Verify that all ported tests pass.

## 5. Real-line fixtures

- [ ] 5.1 Select about 120 lines from the spike export in `private/spike/export.json` (design D8):
  - cover every form: units in both languages, glued units, ranges, fractions, decimal comma, plural markers, notes, headings, unmeasured lines and broken fragments;
  - remove duplicates;
  - drop lines with person names, store names or URLs.

  Write `src/test/resources/fixtures/ingredient-lines.tsv` with hand-written expected values. Verify that the file has at least 100 lines, and that a search finds no URL, no recipe name from the export and no store name.
- [ ] 5.2 Add the parameterized fixture test with `@CsvFileSource`. Fix parser or vocabulary gaps it reveals; each fix gets a focused unit test of its own. Verify that every fixture passes.

## 6. Quality gates and integration

- [ ] 6.1 Write KDoc for every public declaration. Verify that `./gradlew :ingredient-parser:check` passes with Kover at 90 % lines and 80 % branches or higher, detekt and ktlint with zero findings, and no compiler warnings, and that the module appears in the aggregated Dokka site.
- [ ] 6.2 Run `./gradlew build` and `openspec validate ingredient-parser --strict`, push, and verify that CI is green.

## Workflow follow-up

- Review the implementation.
- Archive the change with `/openspec-archive-change`, so that `ingredient-parsing` becomes a main spec.
