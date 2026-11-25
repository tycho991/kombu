# Design

## Context

`ingredient-parser` is an empty pure Kotlin module. It is already registered as PURE, so the gates for pure modules apply:

- explicit API mode and KDoc for public declarations;
- Kover at 90 % lines and 80 % branches;
- detekt and ktlint with zero findings.

JUnit 5 with parameterized tests is available, as in `nextcloud-auth`.

The upstream library `@magrinj/parse-ingredients` (MIT, TypeScript) is about 300 lines of logic. It works in these steps:

1. find a quantity, which may be a Unicode fraction, a range or a number;
2. convert fractions;
3. pull out the first parenthesised group as extra info;
4. look up the longest matching unit at the start of the rest;
5. strip an article ("of", "a", "ein" …);
6. return strings for the quantity, unit, symbol and name.

Its locale tables hold three things: recognition words, display translations and symbols. `combine` and `pretty` are separate functions.

Upstream behaviour that Kombu must not take over:

- The quantity is the first number anywhere in the line.
- The input is accent-stripped with NFD, but the German table spells units with "oe", "ae" and "ue". That is the umlaut bug from the spike.
- "small", "medium" and "large" are units.
- One locale is used at a time.
- Quantities are strings, and parsing them relies on `parseFloat`.

See proposal.md for the spike findings and `specs/ingredient-parsing/spec.md` for the required behaviour.

## Goals / Non-Goals

**Goals:**

- One parse function that never fails and always keeps the original text.
- Exact arithmetic, so that scaling and display never show `0.30000000000000004`.
- Recognition tables that are easy to extend per language and checked for conflicts by tests.

**Non-Goals:**

- No display names for units. The app maps the unit identifier to string resources when it first shows units (R5).
- No porting of `combine` or `pretty`.
- No natural-language understanding beyond the line shapes in the spec. When a line is unclear, the parser prefers "unmeasured" over guessing.

## Decisions

### D1 Public API: one parser, a closed result type

Package `io.github.tycho991.kombu.ingredientparser`. The public API is:

- `IngredientParser`, a stateless object with `parse(line: String): IngredientLine`.
- `sealed interface IngredientLine` with `original: String` and three cases:
  - `Measured`: `amount: Amount`, `approximate: Boolean`, `unit: IngredientUnit?`, `unitText: String?`, `name: String`, `notes: List<String>`, and `quantityRange: IntRange`, which gives the position of the quantity text in `original`;
  - `Unmeasured`;
  - `Heading`: `text: String`.
- `Amount(min: Rational, max: Rational)` with `isRange`, `scaledBy(factor: Rational)` and `format(locale: Locale): String`.
- `IngredientLine.scaledBy(factor)`, which scales measured lines and returns the others unchanged.
- `Measured.displayText(factor, locale)`, which returns the original line with only `quantityRange` replaced by the formatted scaled amount.
- `enum class IngredientUnit`, with one entry per unit kind: `GRAM`, `TABLESPOON`, `BUNCH`, `HANDFUL` and so on.

Keeping `quantityRange` instead of rebuilding the line from its parts preserves the user's wording: a glued `400g`, "ca.", plural forms and notes all stay as written. The price is a known flaw: "1 Zwiebel" scaled by 2 reads "2 Zwiebel". Fixing that would need a grammar per language.

*Alternative:* upstream's flat record with nullable strings. Rejected, because it cannot tell a heading from a line without quantity, and the app would have to parse the quantity strings again.

### D2 `Rational` for quantities

`Rational` is a small value class: numerator and denominator as `Long`, always reduced, denominator positive.

- **Parsing:** decimals are read exactly, so `0.125` becomes 1/8 and `1,5` becomes 3/2.
- **Scaling:** scaling multiplies rationals; servings 4 → 6 become the factor 6/4.
- **Overflow:** arithmetic uses `Math.multiplyExact`. The parser accepts at most 6 integer digits and 3 decimal digits. A longer number makes the line unmeasured, so overflow cannot happen with realistic factors.
- **Invalid factor:** a factor of zero or below throws `IllegalArgumentException`, as the spec requires. `parse` itself never throws.

*Alternatives:*

- `Double`: binary rounding errors break "1/3 × 3 = 1".
- `BigDecimal`: cannot represent 1/3 exactly.

### D3 Normalised matching fixes the umlaut bug

Units are matched on a normalised key. Both the table entries and the input go through the same function:

1. lower-case with `Locale.ROOT`;
2. map `ä`/`ö`/`ü` to `ae`/`oe`/`ue` and `ß` to `ss`;
3. strip the remaining accents with NFD;
4. drop a trailing period;
5. drop a plural marker of up to three letters in parentheses, as in `(n)` or `(en)`.

Because both sides are normalised the same way, `Teelöffel`, `Teeloeffel` and `TEELÖFFEL` all match.

Single-letter tokens are the exception and are compared exactly. `T` is tablespoon and `t` is teaspoon; lower-casing would merge them.

The unit token is the text up to the next space or opening parenthesis. It may also follow the quantity with no space in between (`400g`, `2-3EL`). Longest match wins, as upstream: `Esslöffel` beats `E…`. Multi-word units such as "measuring cup" are tried before single words.

### D4 Vocabularies per language, merged into one lookup

`GermanVocabulary` and `EnglishVocabulary` are internal objects, each mapping `IngredientUnit` to its recognition words.

- **German** keeps the upstream words, written as real German spellings (`Esslöffel`). It adds:
  - the kitchen units from the spec;
  - the spike spellings `El`, `Tl`, `mL`, `Pck.` and `Pkg.`.
- **English** keeps the upstream words and adds `handful`, `bunch` and `dash`.
- **Dropped:** the size words `small`/`medium`/`large` and `klein`/`mittel`/`groß`.

At first use both vocabularies are merged into one map from normalised key to unit. A unit test fails if one key maps to two different units. Then a new word cannot silently change an existing match.

Upstream's display translations and symbols are not ported (R5). The unit identifier is the only output; the app adds display names later.

The tables live in Kotlin and not in resource files. That gives compile-time checking, no I/O and no parsing at startup. R5 requires tables per language, which separate objects satisfy. The words are recognition vocabulary, not user-visible text.

### D5 Line classification order

1. Trim the line. An empty line is `Unmeasured`.
2. Read the quantity at position 0:
   - optional approximation prefix: `ca.`, `ca`, `etwa`, `circa`, `~`, `about`, `approx.`;
   - then a mixed number, ASCII fraction, decimal with `.` or `,`, integer, or Unicode fraction;
   - optionally followed by a range part: `-`, `–`, `bis` or `to`, then another quantity.
3. No quantity: a line ending in `:` is a `Heading` without the colon; anything else is `Unmeasured`.
4. Quantity found: read the unit (D3). An English article directly after the unit (`of`, `a`, `an`) is stripped from the name, as upstream does.
5. Notes:
   - every top-level parenthesised group in the rest becomes a note, unless it is a plural marker glued to a word (`Ei(er)`);
   - the text after the first comma that is outside parentheses becomes a further note;
   - the name is what remains, with spaces collapsed.

A decimal comma is only read as part of a number when a digit follows it directly. So `1,5 l` is 3/2, while `Salz, Pfeffer` never starts with a digit and is unmeasured anyway.

### D6 Kitchen-friendly formatting

`Amount.format(locale)` formats each end of the range:

1. **Whole numbers** are printed as is.
2. **Fractions:** for the denominators 2, 3, 4 and 8, in that order, the value is rounded to the nearest multiple. The first one within 2 % relative error is used, and printed as a fraction or mixed number (`1/3`, `2 1/4`). The order prefers the simplest fraction.
3. **Otherwise** the value is printed as a decimal with at most two places, without trailing zeros, using `DecimalFormatSymbols.getInstance(locale)`.

A range prints both ends joined by an en dash.

Unicode fraction glyphs are not used in the output, because small screens and some fonts render them badly. That is easy to change later in one place.

### D7 Port and attribution

The upstream parse logic is ported by hand into idiomatic Kotlin. It is not a line-by-line transliteration, but the structure and the quantity handling follow upstream. The upstream parse tests from `src/__tests__/index.test.ts` are ported as JUnit tests:

- Tests that check behaviour Kombu changes on purpose are adapted, and each one gets a comment giving the reason. Examples: size words as units; the "article" output; translations.
- Tests for `combine` and `pretty` are not ported.

`ingredient-parser/NOTICE` carries the upstream MIT license text and copyright line. The ported files have a header comment that names the source. The README's license section mentions it.

### D8 Real-line fixtures

`src/test/resources/fixtures/ingredient-lines.tsv` has one line per case. The columns are: original, kind, min, max, unit, name, notes (notes separated by `|`). A `@ParameterizedTest` with `@CsvFileSource` and a tab delimiter runs every line.

The selection comes from the spike export in the git-ignored `private/` folder:

- about 120 lines;
- every form from the spike, with several examples of the frequent ones;
- duplicates removed;
- only single lines, never recipe names;
- lines naming a person, a store or a URL are dropped (R4).

The expected values are written by hand from the spec, not generated by the parser, so the fixtures really test it. The maintainer waived reviewing the selection beforehand.

## Test strategy

Everything runs on the JVM. No device or emulator is needed, because there is no Android code.

| Area | Tests |
|---|---|
| `Rational` | reduce, sign, arithmetic, overflow guard, parse of decimals with point and comma |
| Quantity reading | table tests for every notation and range form, approximation prefixes, too many digits |
| Units | per-unit tests for both languages; umlaut and ASCII forms; case rules incl. `T`/`t`; trailing period; plural marker; glued units; the vocabulary conflict test |
| Classification | headings, unmeasured lines, numbers inside the line, broken fragments, empty and odd input (never throws, original kept) |
| Notes | parentheses, comma notes, several notes, plural marker vs. note |
| Scaling and display | exact results, factor validation, snapping thresholds, German and English locale, ranges, `displayText` keeps the rest of the line |
| Upstream | ported parse tests, adapted where Kombu deviates |
| Regression | the real-line fixture file |

Coverage is enforced by the existing Kover gate for pure modules: 90 % lines and 80 % branches.

## Risks / Trade-offs

- [A new vocabulary word could steal matches from names, for example "Kugel" in "Kugel Eis" vs. "Kugeln" as a name] → The unit must be the first token after the quantity, and the fixtures show such regressions at once.
- [English `c`/`c.` (cup) matches French "c. à soupe"] → Accepted: French is out of scope. The quantity is still right, and the scaled text keeps "c. à soupe" as written. Only the unit identifier is wrong.
- [Plural agreement in scaled text ("2 Zwiebel")] → Accepted for v1 (D1). Cooking mode can decide later whether to show the unit text or a display name.
- [Upstream changes after the port] → The port is a fork in Kotlin. Upstream has one release and few changes, so tracking it is not planned. NOTICE names the version that was ported.
- [Real lines in the public repo] → Only single ingredient lines, screened for names, stores and URLs (D8). They are no more personal than any cookbook line.

## Open Questions

- Whether cooking mode shows `unitText` or a localised unit name. This does not affect the parser, which provides both the identifier and the original text.
