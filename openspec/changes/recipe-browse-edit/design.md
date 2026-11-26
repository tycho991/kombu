# Design

## Context

`recipe-sync` left these building blocks; see proposal.md for why this change comes now.

- **Local copy:** `RecipeRepository` in `data` holds the local copy.
  - Reading: `recipes()` (every row except local deletions) and `recipe(localId)` are flows of `LocalRecipe`. `imageFile(localId, size)` returns the stored image.
  - Writing: `create`, `edit(localId, change)`, `delete(localId)` and `resolve(seen, KEEP_MINE | TAKE_SERVER)`. Each call stores at once, marks the row pending and grows `localChangeSeq`; the app then schedules a sync.
- **`LocalRecipe`** holds up to three versions as raw JSON (`RemoteRecipe`):
  - `base`, the last synced one;
  - `local`, the user's edit;
  - `server`, the server version of a conflict.
  
  It also holds `state`, `conflict` (three kinds), `uploadError` and `imageState`. `shown` is `local ?: base ?: server`.
- **`RemoteRecipe`** has typed accessors only for `name`, `category`, `keywords`, `image` and `dateModified`. Everything else is raw JSON, so unknown fields survive a round trip (spec `recipe-sync`, "Unknown fields are kept").
- **App:** `KombuApp` switches between login, start screen (sync status), account screen and "sign in again" with plain state; there is no navigation library yet.
- **Other modules:** `ingredient-parser` is complete: `IngredientParser.parse(line)` gives `Measured`, `Unmeasured` or `Heading`, and `Measured.displayText(locale, factor)` scales exactly and keeps the rest of the line. `duration-parser` is an empty module.
- **Test fake:** `FakeCookbookServer`, a mockwebserver3 dispatcher that behaves like Cookbook, lives in `sync/src/test`.

## Goals / Non-Goals

**Goals:**

- Every screen reads only the local copy. The only network actions are the sync and opening a recipe's source link.
- Pure logic lives in pure Kotlin modules with JVM tests: field mapping, field comparison, durations, search and filtering. Screens and view models stay thin.
- Saving never overwrites something the user has not seen. This holds against the server (handled by the sync) and against a sync that runs while the editor is open (D5 here).

**Non-Goals:**

- No new database table or migration. Search runs in memory over the local copy (D7).
- No full-text index, no fuzzy search, no search over steps or the description.
- No typed recipe model that replaces the JSON. Typed values are read from the JSON and written back field by field (D3).

## Decisions

### D1 Navigation 3 for the signed-in screens

The signed-in part becomes a Navigation 3 `NavDisplay` over a back stack of `@Serializable` keys:

- `RecipeList` (the root);
- `RecipeDetail(localId)`;
- `RecipeEditor(localId?)`;
- `Conflict(localId)`;
- `SyncStatus`, `Account` and `SignInAgain`.

`rememberNavBackStack` saves the stack across configuration changes and process death. `lifecycle-viewmodel-navigation3` gives each entry its own `ViewModel` and `SavedStateHandle`. Login stays outside the back stack, as today: signing out replaces the whole signed-in part.

Alternatives considered:

- *Navigation Compose 2.x* would also work, but Navigation 3 is the Compose-first API and keeps the back stack as plain state that tests can inspect and set.
- *Extending the hand-written state switch* would mean writing back handling, saved state and per-screen view models ourselves for seven destinations.

### D2 Coil 3 for local images

List thumbnails and the detail image are loaded with Coil 3 (`coil-compose`) from the `File` that `imageFile` returns. The network module is not added, so Coil cannot fetch anything.

- `ImageStore` replaces a file in place when a sync downloads a new image. The memory cache key therefore includes the file's modification time, and the disk cache is off, because the files are already local.
- A missing file shows the placeholder. This happens when the image is not downloaded yet or was removed.

Alternative considered: decoding with `BitmapFactory` and a hand-written `LruCache`. It is less code to depend on, but more to get right: downsampling, lifecycle and cancellation during scrolling.

### D3 Typed recipe fields in `cookbook-client`

A new `RecipeFields` API in `cookbook-client` reads and writes the fields the screens use:

| Field | JSON key | Read from | Written as |
| --- | --- | --- | --- |
| description | `description` | string | string |
| category | `recipeCategory` | string | string, removed when empty |
| keywords | `keywords` | comma string or list | comma string without spaces (Cookbook's form) |
| servings | `recipeYield` | number, or a string starting with a number | number, removed when empty |
| times | `prepTime`, `cookTime`, `totalTime` | ISO 8601 string (D4) | ISO 8601 string, removed when empty |
| ingredients | `recipeIngredient` | list of strings, or a single string split into lines | list of strings |
| steps | `recipeInstructions` | string split into lines, list of strings, `HowToStep` objects (`text`) or `HowToSection` objects, flattened | list of strings |
| source | `url` | string, only `http` and `https` | not written |

- **Lenient reading:** a value of an unexpected type reads as absent. The view then leaves the field out, as "Recipe view" in spec `recipe-browsing` requires. Times that are not valid ISO 8601 are the exception: they are shown raw.
- **Writing:** a writer replaces exactly one key and leaves every other key and its form untouched. This is what makes "Only edited fields change" (spec `recipe-editing`) hold for steps stored as `HowToStep` objects.
- **Cleared values:** an empty category, servings or time removes the key instead of writing `""`. When reading, `""`, `PT0S` and an all-zero duration count as absent, so the view is right whatever form the server stores after its own normalisation.

The same module gets `RecipeDiff.between(a, b)`. It compares two versions per top-level key and ignores the server-managed keys that `RecipeHash` also ignores. Each value is compared through `RecipeHash.canonical`, so key order and number formatting do not count. The result is:

- the differing fields among those the conflict view shows (name, description, category, keywords, servings, the three times, ingredients and steps);
- the count of other differing keys.

Alternative considered: typed accessors in `data` or `app`. They would be Android-module code for pure logic, tested under Robolectric and held to lower coverage thresholds. They also could not be reused by `recipe-share-import`, which writes the same fields.

### D4 ISO 8601 durations in `duration-parser`

`duration-parser` gets `IsoDuration.parse(text): Duration?` and `IsoDuration.format(duration): String`.

- **Parse** accepts `P[nD][T[nH][nM][n[.n]S]]`, case-insensitive, with leading zeros (`PT01H05M00S`) and days (`P1DT2H`, converted to hours). It returns `null` for anything else, including negative and empty values.
- **Format** writes the shortest form in hours and minutes, for example `PT1H5M`, `PT45M` or `PT2H`.
- **Display** is the app's job. String resources with placeholders give `1 Std. 5 Min.` in German and `1 hr 5 min` in English (R5). Seconds are rounded to the nearest minute. A non-zero duration under 30 seconds shows as `< 1 min`.

The timer patterns in step text (`⏱ 10 min`) belong to `cooking-mode` and are not part of this change.

### D5 Editor: field-wise save with a compare step

The editor view model keeps two values in its `SavedStateHandle`, as serialised strings:

- `opened`, the shown version when the editor opened;
- the form state.

On save it builds the set of edited fields: every field whose form value differs from its value in `opened`. Only those fields are written, through the D3 writers. An editor saved without changes writes nothing, so the recipe is not marked as changed.

To detect a sync that ran while the editor was open, the repository gets a compare-and-set variant: `edit(localId, expected, change)`. In one transaction it loads the row and compares its shown version with `expected`. It then returns one of three results:

- `Edited`: the row is unchanged, or changed only in fields the user did not edit. The change is applied to the current version, so the sync's other changes are kept.
- `Collision(fields, current)`: the sync changed a field the user also edited. The editor names those fields and lets the user choose. "Save mine anyway" saves with `expected = current`. "Take the new values" copies `current`'s values for those fields into the form and lets the user check before saving again.
- `Gone`: the row was removed by a server deletion. The editor offers to save the form as a new recipe through `create`.

The check runs inside the repository transaction, so a sync cannot slip between the check and the write. A recipe in conflict is never opened in the editor (spec "Recipes in conflict are resolved first"); the detail screen routes to the conflict view instead.

**Validation** runs on save:

- the name is required and unique, compared trimmed and ignoring case against the shown names of all other visible rows;
- servings are an integer of at least 1;
- hours and minutes are non-negative integers, and minutes are below 60.

Errors are shown per field from resources. Keywords are split on commas, trimmed and de-duplicated ignoring case, with the first spelling kept.

**Ingredient lines** are edited in one multi-line text field: it is fast to type and paste into, and one line per ingredient matches how Cookbook stores them. **Steps** are separate fields with add, remove, move up and move down, because steps are longer and their order matters.

Alternative considered: writing the whole form back as a new JSON object. It is simpler, but it would rewrite fields the user never touched. That breaks "Only edited fields change" and turns every save into a potential conflict with an unrelated web edit.

### D6 Conflict view on top of `resolve`

The conflict view model observes `recipe(localId)` and shows the kind and the `RecipeDiff` between `local` and `server`. For deletion conflicts it shows the one version that exists.

- **Confirming a choice** calls `resolve(seen, choice)` with the row the user was looking at. `resolve` already refuses when the row changed meanwhile; the view then keeps the new state on screen and shows a message (spec "Choosing a conflict version").
- **The choices and their texts** per kind:

| Kind | Keep | Take |
| --- | --- | --- |
| changed in both places | "Keep my version" (discards the Nextcloud changes on the next sync) | "Use the Nextcloud version" (discards my changes) |
| changed here, deleted in Nextcloud | "Keep the recipe" (creates it again) | "Delete it here too" |
| deleted here, changed in Nextcloud | "Delete it anyway" | "Keep the Nextcloud version" |

Each choice maps to `KEEP_MINE` or `TAKE_SERVER` exactly as the D7 table of `recipe-sync` defines. No new resolution logic is added.

### D7 Search, filter and sort in memory, in `data`

A pure `RecipeQuery` in `data` turns the `recipes()` list and the list state into the visible list. The list state is search text, category, keywords, the attention filter and the sort order.

- **Normalisation:** text is decomposed (NFD) and combining marks are removed. Then `ß` becomes `ss` and everything is lower-cased with `Locale.ROOT`. The normalised name, category, keywords and ingredient lines of each recipe are computed once per emission of `recipes()`.
- **Matching:** each whitespace-separated search term must occur as a substring in at least one of those texts.
- **Sorting by name** uses a `Collator` for the app locale. Sorting by last change uses the later of `localChangeAt` and the parsed `dateModified` of the shown version; unparseable dates sort last.
- **Filter choices:** `categories()` and `keywords()` are derived flows with the distinct values of the visible rows, sorted with the same collator. The editor's suggestions use the same flows.

The chosen sort order is stored in a small DataStore in `data` (`BrowsePreferences`). Search text and filters live in the list's `SavedStateHandle` and are not kept across app restarts.

Alternative considered: Room queries with `LIKE` or FTS. They need extra columns and a migration, cannot ignore accents without precomputed columns, and gain nothing at household scale. The 66 recipes of the household are filtered in well under a frame. Even 2,000 recipes stay a few milliseconds, because the normalised texts are computed once per emission, not per keystroke.

### D8 Start screen layout and the sync status

The list screen has:

- a top bar with search, sort and the account menu;
- the sync status bar;
- the filter chips;
- the list itself;
- a button to create a recipe.

The status bar is a compact one-line form of today's start screen, fed by the existing `StartViewModel` state. Tapping it opens `SyncStatus`, which is today's start screen content: count, last sync, pending, conflicts, problem, "sync now" and "sign in again". When there are conflicts or refused uploads, the bar's count opens the list with the attention filter instead.

Empty states come from the same state:

- no recipes and no completed sync yet: "downloading" or the problem;
- a sync completed and the account has no recipes: "no recipes yet", with the create action;
- recipes exist but none matches: "nothing found", with "clear search and filters".

### D9 The fake Cookbook server moves into a test-support module

`FakeCookbookServer` and its test move from `sync/src/test` into a new pure-Kotlin module `test-cookbook-server`. Its only dependencies are mockwebserver3, kotlinx.serialization and `cookbook-client` (for `RecipeHash`).

- `sync` and `app` use it as `testImplementation` only.
- It applies the same JVM convention plugin as the other pure modules, so its coverage thresholds stay at 90 % lines and 80 % branches; its existing test is extended where needed.

Alternative considered: Android Gradle test fixtures in `sync`. Kotlin support for test fixtures in Android libraries is still behind a flag in AGP, and a plain JVM module is simpler for both consumers.

### D10 Offline behaviour and failure modes

None of the new screens talks to the server.

- **Saving, deleting and resolving** write to Room and then call `SyncControl.afterLocalChange()`, as the existing code path does. Without network the scheduled sync waits for a connection, and the list shows the pending marker until then.
- **The source link** opens in a Custom Tab (AndroidX Browser, already a dependency). Without network the browser shows its own error, and Kombu's state is untouched.
- **Sync failures** reach these screens only through the status bar and the per-recipe markers, as `recipe-sync` specifies.
- **Local failures:**
  - an image file that cannot be decoded shows the placeholder;
  - a row that disappears while its detail screen is open closes the screen and returns to the list;
  - a failed database write shows an error message and keeps the editor's form, so nothing typed is lost.

## Risks / Trade-offs

- **[Navigation 3 is younger than Navigation Compose 2]** → Only its stable artifacts are used, pinned per the version rule. The back stack is plain serialisable keys, so moving to another navigation library later touches only `KombuApp` and the keys.
- **[Cookbook might store a removed field differently, for example as `""` or `PT0H0M`]** → Reading treats empty and zero values as absent (D3). The next sync stores whatever form the server returns as the new base, so the hash comparison stays consistent. The deferred device test checks a cleared time against the household server.
- **[Recipes with `HowToSection` steps lose their section names in the view]** → None of the household's recipes uses sections. The steps keep their stored form unless the user edits them (D3). Showing section headings can follow with `cooking-mode`.
- **[Unique-name check only against the local copy]** → A recipe created in the web interface after the last sync can still collide. The server then refuses the upload with `409`, and the existing "Refused uploads" path shows it (spec `recipe-editing`).
- **[In-memory search reads every recipe's ingredients]** → It is fine at household scale (D7). A Room-backed search can replace `RecipeQuery` behind the same list state if collections grow to tens of thousands.
- **[Two new libraries increase the APK size]** → Coil without its network module and Navigation 3 add a few hundred kilobytes. That is acceptable for an app that already ships Compose and Room.

## Migration Plan

- No data migration; the database stays at version 1.
- After the update, the app opens on the recipe list instead of the status screen. The status stays one tap away.
- Rollback is installing the previous APK. Local changes made with the editor stay in the database, and the previous version's sync uploads them.

## Test strategy

- **Pure modules (JUnit 5 on the JVM, 90 % lines and 80 % branches):**
  - `RecipeFields` read and write every JSON shape in D3, including unexpected types and the round trip of untouched fields;
  - `RecipeDiff` covers ignored server keys, key order and number formatting;
  - `IsoDuration` parses and formats every accepted and rejected form;
  - `test-cookbook-server` keeps its fake-server tests.
- **`data` (Robolectric and Room, 80 % / 70 %):**
  - `RecipeQuery` for search terms, accents, `ß`, filters, the attention filter and both sort orders, with German and English collation;
  - `edit(localId, expected, change)` for `Edited`, `Collision` and `Gone`, including a concurrent sync write, plus the derived flows for categories and keywords and `BrowsePreferences`.
- **`app` (Robolectric Compose tests in English and German, 70 % lines):** every screen's states:
  - list: empty states, markers, search with no match, filters, the attention filter and sort;
  - view: all fields, a sparse recipe, scaling, the "not scaled" hint and the banners;
  - editor: validation per field, unsaved changes, collision and gone, process recreation through `SavedStateHandle`;
  - conflict view: three kinds, confirmation, changed meanwhile;
  - navigation between them.
  
  View models are tested with fake repositories where a real database adds nothing.
- **End-to-end on the JVM (A3), in `app`:**
  - A real Room database (Robolectric), `RoomRecipeStore`, `RecipeRepository`, `SyncEngine` and `CookbookClient` run against `test-cookbook-server`.
  - The editor and conflict view models drive the flow: sync, edit offline, server-side edit, sync, check the conflict, resolve both ways in two runs, sync again, and assert the server content and the local state.
  - It also covers an edit of a field the server changed while the editor was open.
- **Device (manual):** list, search, view with images and scaling, editor and a conflict against the household server on the paired phone. Recorded without server details. It may be deferred with the other device tests.
