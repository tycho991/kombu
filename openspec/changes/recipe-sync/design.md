# Design

## Context

Current state, as relevant here:

- `cookbook-client` (pure) has one function, `checkAvailable`, which reads `GET index.php/apps/cookbook/api/version` and returns `CookbookVersion`. Errors use `NextcloudResult`/`NextcloudError` from `nextcloud-auth`. HTTP goes through `OkHttpClient.fetch` there, and tests use `MockWebServer`.
- `data` (Android) holds the credential store (DataStore plus Keystore) and `AccountRepository` with login and logout. It has no database yet.
- `sync` (Android) is an empty module. It may depend on `data` and on pure modules.
- `app` has `AppContainer` (manual DI), the login screen, the account section and a placeholder start screen.
- Android modules test with JUnit 4 and Robolectric. Pure modules test with JUnit 5.
- Kover already excludes `*_Impl` and other generated classes.

Constraints from the API and from real data, gathered during this proposal:

- Cookbook API 0.1.3:
  - `GET /api/v1/recipes` returns stubs with `id` (string), `name`, `dateCreated`, and optionally `dateModified`.
  - `GET`/`PUT`/`DELETE /api/v1/recipes/{id}` and `POST /api/v1/recipes` handle single recipes. `POST` returns `409` if the name exists and `422` if the name is missing.
  - `GET /api/v1/recipes/{id}/image?size=full|thumb` returns a JPEG, or a fallback SVG when the recipe has no image. With `Accept: image/jpeg` it returns `406` instead.
  - There are no conditional requests and no "changed since" filter.
- The household's 64 `recipe.json` files have about 40 distinct top-level fields. Some fields come with different JSON types: `nutrition` as an object or a list, `id` as a number or a string, `author` as a string, an object or a list. `dateModified` is sometimes `null`.

See proposal.md for scope and `specs/recipe-sync/spec.md` for the behaviour.

## Goals / Non-Goals

**Goals:**

- The sync decision logic is plain Kotlin and fully testable on the JVM against `MockWebServer` and an in-memory fake of the local store, one test per spec scenario.
- No code path can lose a local change or overwrite a server change without a conflict record.
- Recipes round-trip byte-for-byte in meaning: Kombu edits known fields and never re-serialises a recipe through a typed model.

**Non-Goals:**

- No typed recipe model for display. `recipe-browse-edit` adds read accessors on top of the JSON when it needs them; this change only extracts what the list, status and sync need: name, category, keywords and image presence.
- No database migrations beyond version 1. Schema export is switched on now, so later changes can write tested migrations.

## Decisions

### D1 Module split

| Module | Gets |
|---|---|
| `cookbook-client` (pure) | Recipe endpoints, `RemoteRecipe`/`RecipeStub`, `RecipeHash`, the version fallback and `CookbookVersion.isSupported` |
| `data` | Room database, `RecipeRepository` (local edits, conflict resolution, status queries), `ImageStore`, logout cleanup |
| `sync` | `SyncEngine` (pull/push decisions), `SyncWorker`, `SyncScheduler`, the run lock |
| `app` | Sync status on the start screen, the re-login path, scheduler wiring in `AppContainer` |

`SyncEngine` talks to a `LocalRecipeStore` interface. `data` implements it with Room, and tests implement it in memory. That keeps the engine's tests on the plain JVM, without Robolectric, even though the `sync` module is Android.

The engine reads a recipe, talks to the server, then writes. The user may change the recipe in between, so every engine write is a compare-and-set: `replace(expected, updated)` and `remove(expected)` apply only if the stored row still equals what the engine read. Otherwise the engine skips the recipe; the `localChangeSeq` re-run (D5) or the next run handles it. Without this, a pull could move the base under a fresh local edit, and the next push would overwrite the server change silently.

Alternative considered: a new pure module `sync-core`. It would make the JVM-only rule enforceable by the build, but it adds a module, a layer entry and Gradle wiring for logic that tests already run without Android. It can be split off later if the engine grows.

### D2 Recipes as raw JSON

`RemoteRecipe` wraps a `kotlinx.serialization` `JsonObject`. It offers read accessors for the fields sync needs (`id`, `name`, `dateModified`, `image`) and a `with(field, value)` copy for edits. Local storage keeps the JSON text. A local edit is always the last synced JSON with the changed fields replaced, so unknown fields and their types survive (spec "Unknown fields are kept").

Alternative considered: a typed `@Serializable` recipe with `ignoreUnknownKeys`. It would silently drop the ~30 fields Kombu does not model and break on the varying types. Rejected.

### D3 Content hash

`RecipeHash.of(json)` is SHA-256, written as lower-case hex, over a canonical form of the recipe:

- object keys sorted by code point, recursively;
- arrays keep their order;
- no whitespace;
- strings escaped as in RFC 8259;
- numbers written as their JSON literal.

Before hashing, the top-level fields the server sets itself are removed: `id`, `dateModified`, `imageUrl`, `imagePlaceholderUrl` and `printImage`. The Cookbook source shows that `show()` adds `printImage` (a per-user app setting, not recipe content) and `imageUrl` to every recipe it returns, so a changed setting would otherwise look like a changed recipe. Task 2.4 confirms the list against the household server. `dateCreated` stays in the hash, because the server sets it only once.

A pure Kotlin canonicaliser is enough (`MessageDigest` is in the JVM). No extra library is needed.

Alternative considered: comparing `dateModified` alone. It would miss edits to recipes without that field, and edits made directly in `recipe.json` that do not update it. Comparing whole JSON strings would treat key order and whitespace as changes. Both were rejected (O11).

### D4 Local data model (Room, version 1)

Table `recipes`, one row per recipe:

- `localId` (primary key, auto-generated) — stable for the UI, exists before the server assigns an id;
- `remoteId` (unique, nullable) — `null` until a created recipe is uploaded;
- `baseJson`, `baseHash`, `baseModified` — the last synced server version, its hash and its raw `dateModified` (all `null` for a recipe created locally);
- `localJson` — the pending local version (`null` when synced);
- `state` — `SYNCED`, `CREATED`, `CHANGED`, `DELETED` or `CONFLICT`;
- `conflict` — `BOTH_CHANGED`, `DELETED_ON_SERVER` or `CHANGED_ON_SERVER_DELETED_HERE` (only in state `CONFLICT`);
- `serverJson`, `serverModified` — the server version in a conflict (`null` if deleted there) and its stub `dateModified`, so a later pull downloads it again only when it moved;
- `uploadError` — `NAME_TAKEN`, `NAME_MISSING` or `REJECTED`, or `null`;
- extracted columns for later list and filter screens: `name`, `category`, `keywords`;
- `imageState` — `NONE`, `PRESENT` or `MISSING`;
- `localChangeAt`.

Table `sync_status` has a single row: `lastSuccessAt`, `problem`, `problemDetail`, `credentialsRejected` and a `localChangeSeq` counter.

Enum columns use Room's built-in name mapping, so no type converters are needed. Counts (recipes, pending, conflicts) are `Flow` queries. The shown version of a recipe is `localJson ?: baseJson`. Rows in state `DELETED` are hidden from lists.

The schema is exported to `data/schemas/` and checked in. There is no `fallbackToDestructiveMigration`, because the database holds pending changes that a destructive migration would lose (R2).

Alternative considered: an operation log as queue (`create`, `update`, `delete` entries). The state-per-recipe model gives "one pending change per recipe" by construction (spec "Offline editing"). Created-then-deleted collapses to nothing, and there is no log to compact. Chosen.

### D5 Sync run

A run has three steps, in this order:

1. **Check.**
   - If `credentialsRejected` is set, the run ends at once without any request.
   - Otherwise it checks the version (D9). An unsupported or unknown version ends the run with that problem.
2. **Pull.** The run fetches the stub list once.
   - **Stub not known locally:** download the recipe and insert it as `SYNCED`, then fetch its images.
   - **Local row `SYNCED`, stub `dateModified` differs from `baseModified` or is missing:** download the recipe and hash it. `baseModified` always holds the *stub's* `dateModified`: the server normalises dates in the list (it adds the `T` separator and a time zone) but not in the full recipe, so only list values are comparable. After a push the stub value is not known yet; the next pull downloads the recipe once, finds an equal hash and stores the stub value.
     - Hash differs from `baseHash`: replace the base and mark the image for refresh.
     - Hash equal: only `baseModified` is updated.
   - **Local row in `CONFLICT`, server version may have moved:** download it and refresh `serverJson` (spec "Conflicts keep both versions").
   - **Local row with a `remoteId` that is not in the list:**
     - `SYNCED`: delete the row and its images.
     - `DELETED`: delete the row, since both sides agree.
     - `CHANGED`: conflict `DELETED_ON_SERVER`.
     - `CONFLICT`: set `serverJson` to `null`.
   - **Rows in `CREATED`, `CHANGED` or `DELETED` that are in the list:** pull leaves them to push.
3. **Push.** Every row in `CREATED`, `CHANGED` or `DELETED` is handled in `localChangeAt` order.
   - **`CREATED`:** `POST` with `localJson` without its `id` field (Cookbook treats a body with `id` as an update of that recipe; `PUT` likewise reads the id from the body, so the client sets it there).
     - Success: `GET` the new recipe and store it as base, with `remoteId` set and the row `SYNCED`.
     - `409`: see D6.
     - `422`: `NAME_MISSING`.
   - **`CHANGED`:** `GET` the server version first.
     - `404`: conflict `DELETED_ON_SERVER`.
     - Hash differs from `baseHash`: conflict `BOTH_CHANGED` with `serverJson`.
     - Hash equal: `PUT` `localJson`, then `GET` again and store that as base, so the server's normalisation and new `dateModified` become the base.
   - **`DELETED`:** `GET` first.
     - `404`: delete the row.
     - Hash equal: `DELETE`, then delete the row.
     - Hash differs: conflict `CHANGED_ON_SERVER_DELETED_HERE`.

Pull comes first, so server deletions and conflicts are known before anything is written. Push re-checks each recipe anyway, right before writing.

After the run, if `localChangeSeq` grew while it ran, the engine runs push once more (spec "Sync triggers": at most one further run).

### D6 Lost answers to a create

If a `POST` reached the server but its answer was lost, the retry gets `409`. Before marking `NAME_TAKEN`, the engine looks up the stub with the same name. If that recipe's hash equals the hash of the recipe Kombu sent (normalised like D3, and without `dateCreated`, which the server sets on every create), Kombu adopts it as its own upload. The pull of the next run usually stores that server recipe as a new synced row first; then the created row is the same recipe twice and is removed. Otherwise the row gets `NAME_TAKEN`. Without this check, every interrupted create would turn into a false error.

### D7 Conflict resolution

`RecipeRepository.resolve(localId, Choice)` writes in one transaction:

| Conflict | Keep mine | Take the server's |
|---|---|---|
| `BOTH_CHANGED` | base := `serverJson`, state `CHANGED` | base := `serverJson`, `localJson` := null, `SYNCED`, image refresh |
| `DELETED_ON_SERVER` | `remoteId`/base := null, state `CREATED` | delete row and images |
| `CHANGED_ON_SERVER_DELETED_HERE` | base := `serverJson`, state `DELETED` | base := `serverJson`, `localJson` := null, `SYNCED` |

"Keep mine" sets the base to the server version the user saw. If the server changes again before the next push, D5 produces a new conflict instead of overwriting it.

### D8 Images

Files live in `Context.noBackupFilesDir/recipe-images/<localId>/full.jpg` and `thumb.jpg`. Android never backs that directory up and never evicts it, unlike the cache directory.

Requests send `Accept: image/jpeg`. `406`, or a body that is not a JPEG, means "no image". A recipe whose `image` field is empty is not requested at all.

Downloads write to a temporary file and rename it into place. The row's `imageState` is set only after both files exist. A failed download sets `MISSING`, and the next pull retries every `MISSING` row.

Images for locally created recipes are not uploaded (proposal, out of scope).

### D9 Version check with fallback

`checkAvailable` first tries `GET index.php/apps/cookbook/api/version`.

- On `404` it tries `GET ocs/v2.php/cloud/capabilities?format=json` with `OCS-APIRequest: true` and reads `ocs.data.capabilities.cookbook.version`. Authenticated requests include the version there.
- If neither answers with a version, the result is `CookbookMissing`.

`CookbookVersion.isSupported` is true for 0.10.3 or newer. Comparison is numeric and part by part; missing parts count as 0. An empty version is not supported.

The login check (`AccountRepository`) returns a new `NextcloudError.CookbookTooOld(version)` in that case. The sync check uses the same function.

This keeps login and sync working when Cookbook removes the deprecated route. The fallback costs one extra request only on servers that have removed it.

### D10 Scheduling and the run lock

`SyncScheduler` (in `sync`) wraps WorkManager.

| Trigger | Work | Constraint |
|---|---|---|
| App start | unique one-off `recipe-sync`, policy `KEEP` | network connected |
| After a local change | the same unique work, 10 s initial delay, policy `KEEP` | network connected |
| "Sync now" | unique one-off `recipe-sync-manual`, policy `KEEP` | none: offline gives immediate feedback |
| Periodic | unique periodic `recipe-sync-periodic`, 1 h, policy `KEEP` | network connected |

All workers call `SyncEngine.run()` behind one process-wide `Mutex`, so two runs never overlap. The `localChangeSeq` re-run (D5) catches changes made during a run, so a `KEEP`-dropped trigger loses nothing.

| Outcome | Worker result |
|---|---|
| Network error, `5xx` | `Result.retry()` (WorkManager's exponential backoff, from 30 s) |
| `401` | `Result.failure()`, with `credentialsRejected` set |
| Unsupported version | `Result.failure()`, with the problem set |
| Success | `Result.success()`, `lastSuccessAt` set, problem cleared |

Running state comes from WorkManager's `WorkInfo` flow, not from a stored flag, so it cannot stick after the process is killed.

### D11 Transactions and failure modes

Each recipe's outcome in pull or push is written in one Room transaction, after its network calls completed. A failure between recipes leaves processed recipes updated and the rest untouched (spec "Interrupted and failed syncs").

There is one remaining window: a `PUT` or `DELETE` succeeds on the server, but the app dies before the transaction. The row then stays `CHANGED`. On the next run the `GET` hash no longer equals `baseHash`, which produces a `BOTH_CHANGED` conflict, with the server version equal to the local one.

The engine detects that case: if the server hash equals the hash of `localJson`, it adopts the server version as `SYNCED` instead of reporting a conflict. The same rule removes false conflicts when someone made the identical edit on both sides.

### D12 Status

`RecipeRepository.status(): Flow<SyncStatus>` combines four things:

- the counts;
- `lastSuccessAt`;
- `problem`;
- the running state from `SyncScheduler`. The app combines the scheduler flow, because `data` cannot see `sync`.

`SyncProblem` is a closed set: `OFFLINE`, `SERVER_ERROR(code)`, `CREDENTIALS_REJECTED`, `COOKBOOK_MISSING` and `COOKBOOK_TOO_OLD(version)`. The app maps each to a string resource (R5).

Times are shown with the device locale's formatting.

### D13 Logout and re-login

**Logout** in the app:

1. Read the pending count (`CREATED` + `CHANGED` + `DELETED` + `CONFLICT`) for the confirmation text, a plural resource.
2. Cancel all sync work.
3. Call `AccountRepository.logout()`. It now also calls `RecipeRepository.clearAll()`, which drops all rows and deletes the image directory.

**Re-login** applies when `credentialsRejected` is set. The status offers "sign in again", which opens the login flow with the server address prefilled.

- After Login Flow v2, `AccountRepository.reauthenticate()` compares the server and login name with the stored ones.
- On a match it replaces the credentials, clears `credentialsRejected` and triggers a sync.
- On a mismatch it revokes the new app password and returns `DifferentAccount`.

### D14 Dependencies

All the new dependencies are Apache-2.0 and free of Play Services. The existing forbidden-dependency check still guards against transitive ones.

- `androidx.room:room-runtime`, `room-ktx`, `room-compiler` (via KSP), and `room-testing` for schema tests;
- `com.google.devtools.ksp` Gradle plugin, matching the Kotlin version;
- `androidx.work:work-runtime-ktx`, and `work-testing` for tests. The optional `work-gcm` stays out.

Versions follow the scaffold rule (newest stable, at least two weeks old). Task 1.1 picks and records them.

### D15 Test strategy

- **`cookbook-client`**, on the JVM with JUnit 5 and `MockWebServer`:
  - every endpoint, with success, `401`, `404`, `409`, `422`, `406`, `5xx`, broken JSON and connection loss;
  - the version fallback;
  - `RecipeHash`: key-order independence, nested objects, number and string forms, excluded fields, and a fixture of invented recipes in the real field shapes (varying `nutrition`/`author` types, `null` `dateModified`);
  - `isSupported` boundaries.
  
  Threshold: 90 % lines, 80 % branches.
- **`sync` engine**, on the JVM with JUnit 4, without Robolectric: an in-memory `LocalRecipeStore` and `MockWebServer`.
  - One test per spec scenario of `recipe-sync`.
  - A table test over local state (`SYNCED`, `CREATED`, `CHANGED`, `DELETED`, `CONFLICT`) × server situation (unchanged, changed, deleted, missing `dateModified`).
  - D6 and D11 recovery cases.
  - A run interrupted after each request, checked for nothing lost.
- **`data`**, with Robolectric and an in-memory Room database:
  - DAO queries;
  - `RecipeRepository` edits and every row of the D7 table;
  - `clearAll`;
  - `ImageStore` atomic writes;
  - a schema-export test with `room-testing`.
- **`sync` worker**, with Robolectric and `work-testing`:
  - the result mapping (success, retry, failure);
  - the unique-work policies and constraints of `SyncScheduler`.
  
  Threshold for `data` and `sync`: 80 % lines, 70 % branches.
- **`app`**, with Robolectric Compose tests:
  - the status in English and German, per problem type;
  - the logout confirmation with a pending count;
  - the re-login path.
  
  Threshold: 70 % lines.
- **Manual, on a device against the household server (task 7.2):**
  - the first sync count equals the server count;
  - images are present;
  - a second sync downloads no recipe;
  - a recipe edited in the web interface is updated;
  - flight mode shows "offline".
  
  The conflict path (A3) is tested end to end once the editor exists (`recipe-browse-edit`). Here it is covered by engine tests.

## Risks / Trade-offs

- **[Race window between check and write]** Cookbook has no `If-Match`, so a web edit in the milliseconds between Kombu's `GET` and `PUT` is overwritten. → The window is one round trip. The `GET` happens right before the `PUT`. The risk is documented, and the server-side request remains open for a future Cookbook version.
- **[Server normalises uploaded JSON]** Cookbook may reformat fields on save, such as durations or `recipeYield`. → The base is always re-read after `PUT`/`POST` (D5), and the hash compares server versions only with server versions, never with Kombu's own serialisation. The exception is the D6/D11 adoption check, where a mismatch only yields a conflict, never a loss.
- **[Excluded-field list wrong]** If the server changes another field on every read, every sync would download everything, and every push would conflict. → Task 2.4 records two consecutive `GET`s of anonymised household recipes and asserts equal hashes. The list is a single constant.
- **[First sync size]** About 39 full images plus thumbnails on the first run. → It runs on any network the user is on. The data volume is small for a household collection, and a metered-network constraint can be added later without a spec change.
- **[Deprecated version route removed]** → D9 fallback.
- **[Large collections]** The stub list is fetched in full on every sync (no paging in the API). → Acceptable for household sizes. Full downloads happen only for changed recipes.
- **[Room and KSP versions tied to Kotlin 2.4.21]** → Task 1.1 checks compatibility before adoption. If no stable KSP matches, it stops and reports instead of downgrading Kotlin.

## Migration Plan

There is nothing to migrate: no recipe data exists on devices yet. On update from the previous build, the new database is created empty and the first sync fills it.

Rollback is installing the previous APK. Its version has no database, so the file is ignored, and Android removes it on uninstall.
