# Proposal

## Why

Kombu is offline-first (D8): every recipe, including its image, must be readable and cookable without network, and recipes can be created, changed and deleted offline (D9). That needs a local copy of the household's Cookbook and a sync that never overwrites a change silently (D10, R2). Sync is the hardest part of v1, so it comes before the screens that build on it: browsing and editing, cooking mode and import. This is the fourth of the seven v1 changes. It covers acceptance criteria A2 and A3 of v1 on the data side.

Two open questions are decided for this change:

- **O9:** the household server runs Cookbook 0.11.12. Kombu supports Cookbook 0.10.3 or newer. From 0.10.3 on, recipe stubs carry a stable string `id`; the `/api/v1` routes exist since 0.9.15.
- **O11:** Kombu finds server changes through `dateModified` in the recipe list. Before every upload it compares a content hash of the server's recipe with the hash of the version it last synced. Recipes without `dateModified` (2 of 64 in the household's collection) are always compared by hash.

The Cookbook API shapes the design:

- It has no "changed since" filter.
- It has no ETag or `If-Match`, so the server cannot reject a stale update by itself.
- It cannot upload image files; an image can only be set as a URL in the recipe's `image` field.
- Its version route `/apps/cookbook/api/version` has been deprecated since Cookbook 0.11.5 in favour of the OCS capabilities.

## What Changes

- **`cookbook-client`** gets the recipe API:
  - list recipe stubs;
  - get, create, update and delete a recipe;
  - download a recipe image (full size and thumbnail).
  
  Recipes are handled as raw JSON objects, so fields that Kombu does not know survive a round trip. The real collection uses about 40 different schema.org fields, some with varying types. A canonical content hash makes two versions of a recipe comparable. The version check gains a fallback to the OCS capabilities and reports whether the server's Cookbook is supported (0.10.3 or newer).
- **`data`** gets a Room database:
  - a local copy of every recipe: the server JSON last synced, its hash and `dateModified`, and the local edit if there is one;
  - a sync state per recipe: synced, created, changed or deleted locally, or in conflict;
  - images in app-private storage that the system cannot evict, excluded from backups like the credentials.
  
  It also gets a recipe repository with local create, edit and delete for the later editor, and conflict resolution: keep mine or take the server's.
- **`sync`** gets the sync engine and its WorkManager scheduling:
  - **Pull:** new and changed server recipes are downloaded with their images. Recipes deleted on the server are removed locally if they have no local change.
  - **Push:** every local change is uploaded only after the server copy has been checked against the last synced version. A difference becomes a conflict that keeps both versions; nothing is overwritten.
  - **Scheduling:** sync runs on app start, after a local change, on request and periodically while a network is available. Only one sync runs at a time.
  - **Errors:** network failures are retried later. An unsupported Cookbook version or rejected credentials stop the sync with a reason the app can show.
- **`app`**: the start screen shows the sync status: number of recipes, last successful sync, pending local changes, conflicts, the current error and a "sync now" action. When the server rejects the credentials, the user can sign in again to the same account without losing local data.
- **Login** rejects a Cookbook older than 0.10.3 with a clear message.
- **Logout** says how many local changes are not yet uploaded before the user confirms. It then deletes the local recipe copy, including images.

Out of scope:

- Recipe list, search, filters, recipe view, editor and the conflict view. These belong to `recipe-browse-edit`, which builds on this change's repository and conflict API. Until then no screen creates local changes, so no conflicts can arise in practice.
- Uploading images. The Cookbook API has no image upload. Images are download-only; a local edit can only change the `image` URL field.
- Merging the two versions of a conflict field by field. The user picks one (D10).
- More than one account per installation.
- Categories and keywords as separate server objects (renaming a category). Kombu reads them only from the recipes.
- Cookbook versions older than 0.10.3.
- Push notifications or server-side change events. Cookbook has none.

## Capabilities

### New Capabilities

- `recipe-sync`: the local recipe copy and its sync with Nextcloud Cookbook. It covers:
  - initial download and incremental updates, including images;
  - offline create, edit and delete with an upload queue;
  - conflict detection before every upload and conflict resolution by the user;
  - sync triggers, scheduling and error handling;
  - the sync status shown to the user.

### Modified Capabilities

- `nextcloud-account`:
  - "Account check after login" also rejects a Cookbook older than 0.10.3.
  - "Logout revokes the app password" warns about unsynced local changes and deletes the local recipe copy.
  - A new requirement lets the user sign in again to the same account after the server rejected the credentials, keeping local data.

## Impact

- **Fixed decisions used:**
  - offline-first with Room and background sync;
  - full offline editing;
  - conflict detection and user choice;
  - no own backend, recipes in the Cookbook REST API;
  - the server URL is always user-configurable;
  - no Google Play Services.
- **Design rules:**
  - R2: no change is lost silently. This covers conflicts, server-side deletions of locally changed recipes, logout with pending changes and rejected credentials.
  - R4: no server URL, account or recipe in the repository. Test data uses invented recipes, and recorded API answers are anonymised.
  - R5: all new texts come from string resources in English and German.
- **Modules:**
  - `cookbook-client` (pure) gets the recipe API;
  - `data` gets Room and the image store;
  - `sync` gets its first code;
  - `app` gets the status display and the re-login path.
  
  The module dependency direction `app` → `sync` → `data` → pure modules stays as specified.
- **New dependencies.** All are Apache-2.0, which is compatible with GPL-3.0-or-later, and none needs Google Play Services. The `build-system` check for `com.google.android.gms` and `com.google.firebase` stays in force.
  - AndroidX Room (runtime, KTX, compiler) and its testing artifact;
  - KSP (Kotlin Symbol Processing), the build-time code generator Room needs;
  - AndroidX WorkManager (`work-runtime-ktx`; not the optional GCM artifact) and `work-testing` for tests.
  
  Versions follow the existing rule: the newest stable release published at least two weeks before it is adopted.
- **Quality gates:** unchanged. `cookbook-client` keeps the thresholds for pure modules; `data` and `sync` keep 80 % lines and 70 % branches; `app` keeps 70 % lines. Room-generated code is excluded from coverage like other generated code.
- **Server side:**
  - The first sync downloads every recipe once, with full image and thumbnail for each recipe that has an image (39 of 64 in the household's collection).
  - Later syncs fetch the recipe list. They fetch full recipes only when `dateModified` changed or is missing.
  - Kombu writes to the server only for local changes the user made.
