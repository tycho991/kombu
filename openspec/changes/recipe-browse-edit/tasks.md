# Tasks

## 1. Build setup

- [x] 1.1 Add Navigation 3 (`navigation3-runtime`, `navigation3-ui`), `lifecycle-viewmodel-navigation3` and Coil 3 (`coil-compose`, without the network module) to `gradle/libs.versions.toml` and `app` (design D1, D2). Each must be:
  - the newest stable release at least two weeks old;
  - Apache-2.0;
  - free of Google Play Services;
  - compatible with Compose BOM 2026.09.00 and Kotlin 2.4.21.

  If no stable Navigation 3 release fits, stop and report instead of switching libraries. Record the chosen versions in the commit message. Verify that `./gradlew :app:assembleDebug` and `checkForbiddenDependencies` pass.
- [x] 1.2 Create the pure-Kotlin module `test-cookbook-server` and move `FakeCookbookServer` and `FakeCookbookServerTest` into it from `sync/src/test` (design D9). Switch `sync` to `testImplementation(project(":test-cookbook-server"))`. Verify that all `sync` tests pass unchanged, and that the new module passes its Kover thresholds (90 % lines, 80 % branches) and detekt. Update CONTRIBUTING.md's module list.

## 2. Recipe fields and durations (pure modules)

- [x] 2.1 Implement `IsoDuration.parse` and `IsoDuration.format` in `duration-parser`, with KDoc (design D4). Verify with JUnit 5 tests:
  - `PT1H5M`, `PT45M`, `PT2H`, `PT01H05M00S`, `P1DT2H`, `pt30m`, `PT1.5S`;
  - rejected: empty, `P`, `PT`, `1H`, `-PT5M`, `PT5X`;
  - formatting of 0, 45 min, 1 h 5 min and 26 h;
  - parse-format round trips.
- [x] 2.2 Implement `RecipeFields` in `cookbook-client`: typed reading and single-key writing of description, category, keywords, servings, the three times, ingredients, steps and source URL, with KDoc (design D3). Verify with tests per row of the D3 table:
  - every accepted input shape, including `HowToStep` and `HowToSection` steps, `recipeYield` as `4` and `"4 Portionen"`, and keywords as a string and as a list;
  - unexpected types read as absent;
  - empty and zero values read as absent;
  - cleared values remove their key;
  - writing one field leaves every other key identical, compared as canonical JSON;
  - a non-`http(s)` source URL reads as absent.
- [x] 2.3 Implement `RecipeDiff.between` in `cookbook-client`, with KDoc (design D3). Verify with tests that it:
  - reports each shown field that differs;
  - counts other differing keys;
  - ignores the server-managed keys, key order and `1` versus `1.0`;
  - reports nothing for equal recipes.

  Update `cookbook-client/Module.md` and `duration-parser/Module.md`. Verify that both modules pass their thresholds and that the Dokka site lists the new API.

## 3. Data layer (`data`)

- [x] 3.1 Implement `RecipeQuery`, the list state and the derived flows `categories()` and `keywords()` (design D7). Verify with JVM tests for:
  - search over name, category, keywords and ingredients;
  - several terms;
  - case, accents and `ß`;
  - category filter, keywords combined with AND, and the attention filter (conflicts and refused uploads);
  - name order with German and English collation (`Äpfel` among the `A` names);
  - last-change order from `localChangeAt` and `dateModified`, with unparseable dates last;
  - distinct, sorted categories and keywords that update when a recipe changes.
- [x] 3.2 Add `RecipeRepository.edit(localId, expected, change)` with the results `Edited`, `Collision(fields, current)` and `Gone` (design D5), and the unique-name check against the other visible rows. Verify with Robolectric and Room tests:
  - unchanged row;
  - row changed by a sync in another field, where the edit keeps the sync's change;
  - row changed in the same field;
  - row deleted;
  - a sync write racing the transaction;
  - the name check ignores case and spaces and excludes the recipe itself;
  - `localChangeSeq` grows exactly once per successful edit.
- [x] 3.3 Add `BrowsePreferences` (the sort order in a DataStore, design D7). Verify with a test that the order survives a new instance and defaults to name. Verify that `./gradlew :data:check` passes at 80 % lines and 70 % branches.

## 4. Navigation and start screen (`app`)

- [x] 4.1 Replace the signed-in state switch in `KombuApp` with a Navigation 3 `NavDisplay` and the keys from design D1. Move today's start screen to `SyncStatus` unchanged; the account screen and "sign in again" become entries. Verify with Robolectric tests:
  - after login the list is the root;
  - back from each entry returns to its parent;
  - back from the list leaves the app;
  - the back stack survives activity recreation;
  - signing out clears the stack and shows the login screen.

  The existing sign-in-again, account and sync-status tests must pass after their move.
- [x] 4.2 Build the recipe list screen (design D2, D8):
  - Coil thumbnails with the placeholder, keyed by the file's modification time;
  - the pending, conflict and needs-attention markers;
  - search, filter chips for category and keywords, the attention filter and the sort menu;
  - the create button;
  - the three empty states.

  Add the strings, the plurals and the icon drawables in `values/` and `values-de/`. Verify with Compose tests in English and German for: each empty state; markers; a search with and without a match; "clear search and filters"; a category filter; two keywords; the attention filter; both sort orders; the order kept after recreation.
- [x] 4.3 Build the sync status bar above the list (spec "Sync status bar"). Tapping it opens `SyncStatus`; its conflict count opens the list with the attention filter. Verify with Compose tests in both languages for: synced; running; offline with two pending changes; conflicts; credentials rejected, including the path to "sign in again".

## 5. Recipe view (`app`)

- [x] 5.1 Build the recipe view (spec "Recipe view", "Recipe state in the view"):
  - the image, then every field from `RecipeFields`, with times formatted from `IsoDuration`;
  - ingredient headings;
  - the source link opened in a Custom Tab;
  - the pending, conflict and refused-upload banners with their actions;
  - edit and delete actions, where a recipe in conflict routes both to the conflict view;
  - closing when the row disappears.

  Add the strings in English and German. Verify with Compose tests in both languages for: a complete recipe; a sparse recipe with no empty sections; a raw unreadable time; each banner and its action; a row removed while open.
- [x] 5.2 Add the servings control and scaling with `IngredientParser` and `displayText(locale, factor)` (spec "Scaled servings", "Unscaled ingredient lines are marked"). Verify with view-model and Compose tests for:
  - 4 to 6 servings (`200 g Mehl` becomes `300 g Mehl`);
  - the hint on `Saft von 3 Orangen` only while scaled;
  - headings without the hint;
  - no control without servings;
  - the recipe not marked as changed after scaling;
  - the stored servings shown again on return.
- [x] 5.3 Add deletion with a confirmation that names the recipe (spec "Delete a recipe"). Verify with tests that confirming removes it from the list and schedules a sync, and that cancelling changes nothing.

## 6. Editor (`app`)

- [x] 6.1 Build the editor for new and existing recipes (spec "Create a recipe", "Edit a recipe", "Editable fields"):
  - all fields from the spec;
  - the category picker with the existing categories plus free entry;
  - keyword chips with suggestions and comma splitting;
  - hours and minutes for the times;
  - the ingredient text field;
  - the step list with add, remove, move up and move down.

  Add the strings in English and German. Verify with Compose tests in both languages that every field is shown and editable and that no image field is offered.
- [x] 6.2 Implement saving with the edited-field set and the D3 writers (design D5, spec "Only edited fields change"). Validate the name, servings and times (spec "Recipe name", "Servings and times"), and normalise keywords, steps and ingredient lines (spec "Keywords", "Ingredients and steps"). Verify with view-model tests for:
  - a name-only change that leaves the JSON otherwise identical, including a source URL, nutrition and `HowToStep` steps;
  - saving without changes writes nothing;
  - each validation error;
  - comma splitting and case-insensitive de-duplication of keywords;
  - dropped empty lines;
  - a moved step;
  - an unreadable time kept, and a cleared time removed;
  - a new recipe created as pending;
  - a fixed refused upload that clears the reason and schedules a sync.
- [x] 6.3 Add the unsaved-changes dialog and the saved form state (spec "Unsaved changes"). Verify with tests: back with changes asks and keeps editing when declined; back without changes leaves; the form survives view-model recreation from its `SavedStateHandle`.
- [x] 6.4 Handle `Collision` and `Gone` (spec "Sync updates during editing"). Verify with view-model tests: different fields are merged; the same field shows the dialog naming the field; both choices behave as in design D5; a recipe deleted meanwhile is saved as a new recipe on request.

## 7. Conflict view (`app`)

- [ ] 7.1 Build the conflict view for the three kinds with `RecipeDiff` (spec "Conflict view", design D6), showing both values per differing field and the count of other fields. Add the strings in English and German. Verify with Compose tests in both languages per kind.
- [ ] 7.2 Implement the choices, their confirmations and the "changed meanwhile" path on top of `resolve` (spec "Choosing a conflict version"). Verify with view-model tests per kind and choice, mapped as in the D6 table, and with a row updated by a sync between showing and confirming.

## 8. End to end and documentation

- [ ] 8.1 Write the A3 end-to-end test in `app` (design, Test strategy). It uses Room under Robolectric, the real repository and sync engine, and `test-cookbook-server`. Verify that the test passes for:
  - an offline edit uploaded;
  - an offline edit against a web edit, giving a conflict and then "keep mine" on the server;
  - the same, with "take Nextcloud's";
  - a sync during editing in another field and in the same field;
  - creating, renaming after `409`, and deleting.
- [ ] 8.2 Update the README (finding, reading, scaling, editing, conflicts; what the editor changes and leaves alone). Verify that `./gradlew build` passes locally with zero detekt and lint findings, no new baseline, and `app` at 70 % lines or higher.
- [ ] 8.3 Test on the paired phone against the household server (manual, design Test strategy):
  - list, search and filters;
  - the view with images and scaling;
  - creating, editing and deleting a recipe;
  - a conflict made with the Nextcloud web interface, resolved both ways;
  - a cleared time checked in the web interface.

  Record the result in the commit message without server details.
- [ ] 8.4 Push and confirm that CI is green. Verify that `openspec validate recipe-browse-edit --strict` passes.

## Workflow follow-up

- Archive the change after review; the archive creates the main specs `recipe-browsing` and `recipe-editing`.
- Update the status document.
- The deferred device tests of `nextcloud-login` (6.2) and `recipe-sync` (2.4, rest of 7.2) can run together with 8.3.
