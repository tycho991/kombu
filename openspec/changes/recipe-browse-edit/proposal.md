# Proposal

## Why

Since `recipe-sync`, Kombu holds a complete offline copy of the household's recipes, but no screen shows a recipe or changes one. This change adds the screens that make the copy useful: list, search and filter, the recipe view and the editor. It also adds the conflict view that the sync needs before local edits can happen safely (D9, D10, R2). It is the fifth of the seven v1 changes and covers acceptance criterion A3 end to end. It also covers the reading part of A2: every recipe can be read in flight mode. Cooking mode (`cooking-mode`) and import (`recipe-share-import`) build on the view and the editor.

Four product questions are decided for this change:

- **Start screen:** the recipe list becomes the start screen. The sync status moves into a compact bar above the list; its details and "sync now" open from there.
- **Category in the editor:** the user picks one of the categories that already occur in the local copy, or types a new one. Kombu has no built-in category list, so it fits any Cookbook (D18, R4).
- **Servings:** the recipe view already scales servings with the ingredient parser. Scaling is a view and never changes the stored recipe. Lines the parser does not understand stay as written, with a subtle hint (O5b).
- **Conflicts:** the conflict view shows the fields that differ in both versions, and the user keeps one whole version (D10). Whoever wants a mix keeps one version and edits it afterwards.

## What Changes

- **Recipe list (new start screen):**
  - every local recipe with thumbnail, name and category, sorted by name or by last change;
  - markers for recipes with a pending change, a conflict or a refused upload;
  - search over name, category, keywords and ingredients, ignoring case and accents;
  - filters by category and by keywords, and a filter for recipes that need attention;
  - an empty state before the first sync and when nothing matches.
- **Sync status bar** above the list: last sync, pending changes, conflicts and the current problem. Tapping it opens the details that the start screen shows today, with "sync now" and "sign in again". The account screen opens from the top bar.
- **Recipe view:**
  - image, name, category, keywords, description, servings, preparation, cooking and total time, ingredients, steps and the source link;
  - a servings control that scales the ingredient lines;
  - banners for a pending change, a conflict or a refused upload, each with the action that resolves it;
  - edit and delete.
- **Editor** for new and existing recipes:
  - name (required, unique among the local recipes), description, category, keywords, servings, the three times, ingredients and steps;
  - only the fields the user changed are written, so every other field of the recipe stays as it was;
  - a warning before unsaved changes are discarded;
  - a check whether the sync changed the same fields while the editor was open, so that no server change is overwritten unseen.
- **Delete** with confirmation; the deletion is uploaded by the next sync.
- **Conflict view** for the three conflict kinds:
  - changed in both places;
  - changed here and deleted on the server;
  - deleted here and changed on the server.
  
  It names what each choice keeps and what it discards, and asks for confirmation.
- **Pure-Kotlin support:**
  - `cookbook-client` gets typed reading and writing of the recipe fields Kombu shows, and a field comparison of two versions;
  - `duration-parser` gets ISO 8601 durations such as `PT1H5M`;
  - the fake Cookbook server from the sync tests moves into its own test-support module, so that the app's end-to-end test can use it.
- **End-to-end conflict test (A3):** edit in the editor while offline, change the same recipe on the fake server, sync, resolve the conflict in the conflict view, sync again, and check the server. All of it runs on the JVM.

Out of scope:

- Cooking mode: step-by-step view, timers, keeping the screen on, ticking off ingredients (`cooking-mode`). Only scaling is part of this change, as a view.
- Import from the share sheet and from URLs (`recipe-share-import`, O12).
- Uploading images or changing the image. The Cookbook API cannot receive image files (see `recipe-sync`). The editor does not show the `image` field and leaves it unchanged.
- Editing nutrition, tools, the source URL and other schema.org fields. They stay unchanged and are shown where useful.
- Merging two conflict versions field by field.
- Renaming or merging categories and keywords across recipes, and a built-in category list.
- Open scaling rules (O30): notes in parentheses and ingredients that do not scale linearly. Scaling follows the parser: every measured quantity scales linearly; notes stay as written.
- Tablet or two-pane layouts; the screens are built for phones and work on larger screens without a special layout.

## Capabilities

### New Capabilities

- `recipe-browsing`: finding and reading recipes. It covers the recipe list as the start screen, search, filters and sorting, the sync status bar, the recipe view with images and scaled servings, and reading without network.
- `recipe-editing`: changing recipes in the app. It covers creating, editing and deleting recipes, validation, unsaved changes, protection against overwriting concurrent sync updates, refused uploads, and the conflict view with the user's choice.

### Modified Capabilities

None. `recipe-sync` already specifies offline editing, the upload queue, conflicts and their resolution, and the sync status; this change adds the screens for them without changing their behaviour. "Start screen" in `nextcloud-account` now means the recipe list, which needs no requirement change.

## Impact

- **New dependencies,** each pinned to the newest stable release that is at least two weeks old when the task starts. None of them depends on Google Play Services.
  - Jetpack Navigation 3 (`androidx.navigation3:navigation3-runtime`, `navigation3-ui`) and `androidx.lifecycle:lifecycle-viewmodel-navigation3`, Apache-2.0, for the back stack and per-screen view models;
  - Coil 3 (`io.coil-kt.coil3:coil-compose`), Apache-2.0, to load the stored image files. Its network module is not added.

  Apache-2.0 is compatible with GPL-3.0-or-later. Icons are vector drawables from Material Symbols (Apache-2.0) in the app's resources, so no icon library is added.
- **Modules:**
  - `cookbook-client`: new public API for recipe fields and the field comparison;
  - `duration-parser`: first code;
  - `data`: search, filters, sorting, and the lists of categories and keywords;
  - `app`: navigation and four new screens; the start screen becomes the list;
  - `sync`: its tests use the moved fake server.
- **New test-support module** (pure Kotlin) with the fake Cookbook server, used only by tests.
- **Fixed decisions and rules** this change relies on:
  - offline-first and full offline editing (D8, D9);
  - conflicts shown to the user, never overwritten (D10, R2);
  - scaling as a view (D7);
  - no built-in instance or personal data (D18, R4);
  - all text, units and number and duration formats from resources and the locale (R5);
  - unparsed ingredient lines shown as written (O5b).
- **Quality gates** stay as they are: coverage of 90 % lines and 80 % branches in the pure modules, 80/70 in `data` and `sync`, 70 % lines in `app`; zero detekt and lint findings; warnings as errors. None is loosened.
- **Docs:** README (finding, reading and editing recipes; what the editor changes and what it leaves alone) and CONTRIBUTING (the test-support module).
