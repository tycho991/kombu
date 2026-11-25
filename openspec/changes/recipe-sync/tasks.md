# Tasks

## 1. Build setup

- [x] 1.1 Add Room (`room-runtime`, `room-ktx`, `room-compiler`, `room-testing`), the KSP Gradle plugin, and WorkManager (`work-runtime-ktx`, `work-testing`) to `gradle/libs.versions.toml` (design D14). Each must be:
  - the newest stable release at least two weeks old;
  - Apache-2.0;
  - free of Google Play Services.

  KSP must match Kotlin 2.4.21. If no stable KSP does, stop and report instead of changing Kotlin. Record the chosen versions in the commit message. Verify that `./gradlew help` succeeds and that `checkForbiddenDependencies` passes.
- [x] 1.2 Wire KSP and Room into `data`, with schema export to `data/schemas/`. Add WorkManager to `sync`, and `sync`'s dependency on `cookbook-client`. Verify that `./gradlew :data:assembleDebug :sync:assembleDebug` succeeds and that a Room-generated `*_Impl` class does not appear in the `data` Kover report.

## 2. Recipe API (`cookbook-client`)

- [x] 2.1 Implement `RemoteRecipe` (a `JsonObject` wrapper with `id`, `name`, `dateModified`, `image`, `category`, `keywords` accessors and a `with` copy) and `RecipeStub` (design D2). Verify with JUnit 5 tests on fixtures of invented recipes that use the real field shapes:
  - `id` as a number and as a string;
  - `nutrition` as an object and as a list;
  - `author` as a string, an object and a list;
  - `dateModified` `null` and missing;
  - unknown fields kept unchanged by `with`.
- [x] 2.2 Implement `RecipeHash` (design D3), with canonical JSON and the excluded server fields as one constant. Verify with tests that the hash:
  - is independent of key order (also nested);
  - is equal across whitespace differences;
  - ignores the excluded fields;
  - changes when any other field changes, including inside arrays and when a type changes (for example `1` versus `"1"`);
  - matches known SHA-256 values for two fixtures.
- [x] 2.3 Implement the recipe endpoints (design D1, D5):
  - list stubs;
  - get;
  - create, returning the new id;
  - update;
  - delete;
  - image download with `Accept: image/jpeg` and `size=full|thumb`.

  Map `409` to `NameTaken` and `422` to `NameMissing` (new `NextcloudError` kinds), `406` to "no image", and `404` to `NotFound`. Verify with `MockWebServer` tests per endpoint for: success, `401`, `404`, `409`, `422`, `406`, `500`, broken JSON, connection loss, and the paths, methods and `Authorization` header sent.
- [ ] 2.4 Confirm the excluded-field list against the household server. Fetch two recipes twice, with a manual script or test that is not committed and uses the developer's own credentials, and compare the raw answers. Commit anonymised copies (invented names and texts, real field shapes) as fixtures, with a test that asserts equal hashes for each pair. Adjust the constant in 2.2 if needed. Verify that the fixtures contain no server URL, account or real recipe text (R4).
- [x] 2.5 Add the version fallback to the OCS capabilities and `CookbookVersion.isSupported` (design D9). Verify with `MockWebServer` tests:
  - `/api/version` answers;
  - `404` there, then capabilities with a version;
  - `404` there, then capabilities without the `cookbook` key, giving `CookbookMissing`;
  - the `OCS-APIRequest` header is sent.

  Verify with table tests for `isSupported`: 0.10.2, 0.10.3, 0.11.12, 1.0, 0.10, empty.
- [x] 2.6 Write KDoc for every new public declaration and update `cookbook-client/Module.md`. Verify that `./gradlew :cookbook-client:check` passes, with Kover at 90 % lines and 80 % branches or higher.

## 3. Login version check and logout (`data`, `app`)

- [x] 3.1 Add `NextcloudError.CookbookTooOld(version)`. Make the login account check reject unsupported versions and revoke the new app password (spec `nextcloud-account`, "Account check after login"). Add the error message in English and German, naming the installed version and 0.10.3. Verify with `AccountRepository` tests (version too old: nothing stored, revocation called) and a login screen test in both languages.

## 4. Local store (`data`)

- [x] 4.1 Create the Room database, version 1 (design D4): `recipes` and `sync_status` tables, DAOs, converters for the enums, the exported schema checked in, and no destructive fallback. Verify with Robolectric tests on an in-memory database:
  - insert, query and update per state;
  - the counts flow;
  - `DELETED` rows hidden from the recipe list query;
  - the schema test from `room-testing`.
- [x] 4.2 Implement `ImageStore` in `noBackupFilesDir` with temporary-file writes and rename (design D8). Verify with tests for:
  - a write then a read;
  - an interrupted write that leaves no partial file and keeps the old image;
  - deleting one recipe's images;
  - deleting all images.
- [x] 4.3 Implement `RecipeRepository` local operations (spec "Offline editing"): create, edit (on top of the base JSON), delete, the `localChangeSeq` bump, and the shown version. Verify with tests for:
  - edit of a `SYNCED` recipe gives `CHANGED`, with unknown fields kept;
  - delete of `CREATED` removes the row;
  - delete of `CHANGED` gives `DELETED`;
  - a repeated edit keeps one pending change.
- [x] 4.4 Implement conflict resolution, every row of the design D7 table, in one transaction each. Verify with one test per table cell, and with a test that "keep mine" uses the server version the user saw as base.
- [x] 4.5 Implement `LocalRecipeStore` for the engine on top of Room, plus `clearAll()` and the pending count. Extend `AccountRepository.logout()` to clear the recipes and images (design D13). Exclude the database from backups in `backup_rules.xml` and `data_extraction_rules.xml` (spec "Local recipe data stays on the device"). Verify with tests: logout leaves no rows and no image files. Verify with a test that parses both XML rule files for the database exclusion.
- [x] 4.6 Add `reauthenticate()` to `AccountRepository` (design D13). Verify with tests: the same server and login name replaces the credentials and clears `credentialsRejected`; a different login name stores nothing, revokes the new app password and returns `DifferentAccount`.
- [x] 4.7 Verify that `./gradlew :data:check` passes, with Kover at 80 % lines and 70 % branches or higher.

## 5. Sync engine (`sync`)

- [ ] 5.1 Implement an in-memory `LocalRecipeStore` test fake and a `MockWebServer` dispatcher that simulates a Cookbook server (recipes, stubs, images, error injection per request). Verify with a self-test that the dispatcher serves list, get, create, update, delete and images consistently.
- [ ] 5.2 Implement the check and pull steps (design D5). Verify with JVM tests for every scenario of the spec requirements:
  - "Supported Cookbook versions";
  - "Server changes are downloaded", including "Nothing changed": no full `GET` and no image request;
  - "Recipe images";
  - "Server deletions";
  - "Rejected credentials stop the sync".
- [ ] 5.3 Implement the push step, including the lost-create adoption (D6) and the same-content adoption (D11). Verify with JVM tests for every scenario of the spec requirements:
  - "Pending changes are uploaded";
  - "Check before upload";
  - "Conflicts keep both versions";
  - "Rejected uploads keep the local change";
  - "Unknown fields are kept", asserted on the request body.
- [ ] 5.4 Add the state-by-situation table test, plus interruption tests (design D15). The table covers the states `SYNCED`, `CREATED`, `CHANGED`, `DELETED` and `CONFLICT` against server unchanged, changed, deleted and missing `dateModified`. The interruption tests run one sync per request position, fail that request, then run a clean sync. Verify that every interrupted run loses no pending change, and that the clean run reaches the same end state as an uninterrupted run.
- [ ] 5.5 Implement the run lock and the re-run on `localChangeSeq` (design D5, D10). Verify with tests: two concurrent `run()` calls never overlap; a local change during a run is uploaded by exactly one extra push.

## 6. Scheduling and status (`sync`, `app`)

- [ ] 6.1 Implement `SyncWorker` with the result mapping, and `SyncScheduler` with the four triggers and their policies and constraints (design D10). Verify with Robolectric and `work-testing` tests for:
  - each result mapping;
  - unique names;
  - `KEEP` behaviour;
  - the network constraint on all triggers except manual;
  - the 1-hour period.
- [ ] 6.2 Wire the scheduler into `AppContainer`: schedule on app start, trigger after local changes, cancel on logout. Verify that `./gradlew :sync:check` passes, with Kover at 80 % lines and 70 % branches or higher.
- [ ] 6.3 Show the sync status on the start screen (spec "Sync status", design D12):
  - recipe count;
  - last successful sync in the device locale;
  - pending changes and conflicts;
  - running indicator;
  - the problem text for each `SyncProblem`;
  - a "sync now" button.

  Add all strings, including plurals, in English and German. Verify with Robolectric Compose tests in both languages for: the empty state, after a sync, running, and each problem type.
- [ ] 6.4 Add the "sign in again" path from the status to the login flow, with the server prefilled and the different-account message (spec `nextcloud-account`, "Sign in again to the same account"). Verify with view-model and screen tests for: same account; different account; cancelled.
- [ ] 6.5 Extend the logout confirmation with the pending-change count, as a plural resource in English and German (spec "Logout revokes the app password"). Verify with tests for: zero pending (unchanged text) and three pending; and that cancelling deletes nothing. Verify that `./gradlew :app:check` passes at 70 % lines or higher.

## 7. Documentation and verification

- [ ] 7.1 Update `README.md` (what syncs and what does not, including image upload) and `CONTRIBUTING.md` (Room schema files and how to update them). Verify that `./gradlew build` passes locally with zero detekt and lint findings and no new baseline. Verify that the aggregated Dokka site lists the new public API of `cookbook-client`.
- [ ] 7.2 Test on a device against the household server (design D15, manual). Check that:
  - the first sync shows the same recipe count as the Cookbook web interface;
  - the image files exist for the recipes with images;
  - an immediate second sync downloads no full recipe (checked with a temporary debug log that is not committed);
  - a recipe edited in the web interface arrives on the next sync;
  - flight mode shows the offline problem;
  - revoking the app password in the Nextcloud security settings shows "sign in again", and re-login keeps the recipes.

  Record the result in the commit message without server details.
- [ ] 7.3 Push and confirm that CI is green. Verify that `openspec validate recipe-sync --strict` passes.

## Workflow follow-up

- Archive the change after review; the archive creates the main spec `recipe-sync` and updates `nextcloud-account`.
- Update the status document.
- End-to-end conflict test (A3) with the editor in `recipe-browse-edit`.
