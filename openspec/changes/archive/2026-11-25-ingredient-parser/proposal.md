# Proposal

## Why

In Nextcloud Cookbook, ingredients are free text: `recipeIngredient` is a plain list of strings. Two later v1 features need those strings split into quantity, unit and name:

- the cooking mode, which scales servings;
- the import, which reads the "• qty unit name" lines of shared recipe cards.

The shopping list after v1 will reuse the same parser. This is the third of the seven v1 changes. Its module `ingredient-parser` already exists as an empty pure Kotlin module.

Open question O5 is decided:

- **O5a, the library to port.** Kombu ports `@magrinj/parse-ingredients` (MIT) and extends it. The other candidate, `recipe-ingredient-parser-v3`, supports only English and Italian, so it is out.
- **O5b, lines the parser does not understand.** They are shown verbatim, are not scaled, and get a subtle hint.

The decision rests on a spike over a real household collection: 64 recipes with 706 ingredient lines. The spike showed that the upstream German support does not work as it is:

- **Umlaut spellings never match.** The German unit table spells units with ASCII "oe", "ae" and "ue", while the input is only stripped of accents. "Teelöffel", "Esslöffel", "Stück" and "Päckchen" are therefore never recognised. That affects 38 lines.
- **Common units are missing.** Among them are "Bund", "Pck.", "Stängel", "Spritzer", "Knolle", "Handvoll", "Becher", "Schuss" and "Tütchen".
- **Real lines mix languages and styles:**
  - German and English units side by side;
  - units glued to the number, as in `400g` or `2-3EL`, in 82 lines;
  - a decimal comma;
  - plural markers such as `Prise(n)`;
  - section headings such as "Für die Füllung:";
  - 104 lines without any quantity.

None of the upstream tests covers German, so none of this would have been caught.

## What Changes

- **The parser.** `ingredient-parser` gets a Kotlin port of the upstream parse logic: quantity, range, Unicode and ASCII fractions, unit lookup and parenthesised notes. It also ports the upstream parse tests. The upstream MIT copyright notice is kept in the module. The port deviates from upstream on purpose:
  - Units are matched on a normalised form, so umlaut and ASCII spellings both work.
  - German and English units are recognised together, in one pass. The German table gains the missing kitchen units.
  - A quantity is read only at the start of the line. Upstream takes the first number anywhere, so "Saft von 3 Orangen" became quantity 3.
  - Size words ("klein", "groß", "small", "large") stay part of the name and are not units.
  - Units glued to the number, ranges without spaces, the decimal comma and plural markers in parentheses are understood.
  - Every line is classified as **measured**, **unmeasured** or **heading**. The original text is always kept, and the parser never throws.
  - The result carries a language-independent unit identifier instead of display text (R5). Turning a unit into text is the app's job, in a later change.
- **Scaling** as a pure function. It multiplies measured quantities exactly, using fractions instead of floating point, and leaves everything else unchanged. Scaling is a view; it never changes stored data.
- **Kitchen-friendly display of quantities.** Results snap to halves, thirds, quarters or eighths where close, and otherwise use a short decimal with the separator of the given locale.
- **Regression fixtures.** About 120 real ingredient lines from the spike, with their expected results, go into the module's test resources. The selection covers every observed form. It contains no recipe names, no store names and nothing personal (R4). The maintainer waived reviewing the selection beforehand.
- **`openspec/config.yaml`.** The v1 scope line about the ingredient parser names the chosen library.

Out of scope:

- How the app shows parsed lines, the "not converted" hint (O5b) and unit names in the UI. Cooking mode (`cooking-mode`) and the recipe view (`recipe-browse-edit`) build on this module.
- O30: whether a spoon amount in parentheses scales along, and ingredients that do not scale linearly, such as cooking water or salt for pasta water. Parenthesised notes stay verbatim and unscaled until O30 is decided.
- Combining ingredients across recipes and pretty-printing in the upstream style. The shopping list needs these after v1.
- Languages other than German and English. Lines in other languages still get their quantity, but no unit.
- Unit conversion, for example cups to millilitres.
- Parsing the shared recipe card as a whole. That is `recipe-share-import`, which will call this parser for its ingredient lines.

## Capabilities

### New Capabilities

- `ingredient-parsing`: turning a free-text ingredient line into quantity, unit, name and note, or classifying it as unmeasured or as a heading; scaling quantities; and kitchen-friendly display of quantities, for German and English lines.

### Modified Capabilities

None. `build-system` already lists `ingredient-parser` among the pure Kotlin modules.

## Impact

- **Fixed decisions used:**
  - Android only, Kotlin; the parser is pure Kotlin without Android dependencies;
  - v1 scope: the ingredient parser is a Kotlin port of an MIT-licensed JS parser with German support. This change picks the library and records it in `openspec/config.yaml`;
  - scaling is a view and never changes the stored recipe;
  - license GPL-3.0-or-later.
- **Design rules:**
  - **R4:** fixtures contain only single ingredient lines, never recipe names, URLs, people or stores.
  - **R5:** the parser returns unit identifiers, not display text. Number formatting uses the given locale. The recognition tables are per language. This change adds no user-visible text, so no string resources are needed.
  - **R7:** lines that cannot be parsed are never lost. They come back verbatim as unmeasured.
- **Modules:** first code in `ingredient-parser`. No other module changes.
- **Dependencies:** none. The port is plain Kotlin with the standard library.
- **License:** the ported code is MIT (Copyright (c) 2021 Jérémy Magrin), which is compatible with GPL-3.0-or-later. Its notice ships in the module and is mentioned in the README.
- **Quality gates:** unchanged. The module follows the thresholds for pure modules: 90 % lines, 80 % branches, explicit API and KDoc.
