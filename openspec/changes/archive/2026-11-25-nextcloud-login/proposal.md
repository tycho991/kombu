# Proposal

## Why

Kombu cannot do anything for a household until it can reach the household's Nextcloud: recipes, sync and every later feature need an authenticated connection. This is the second of the seven v1 changes and the first one with user-facing behaviour beyond the start screen. It covers acceptance criterion A1 of v1: login to a freely entered Nextcloud URL, with no instance named in the repository.

The login method is decided (open question O2): Nextcloud Login Flow v2. The user signs in on the Nextcloud web page in the browser, and Nextcloud hands Kombu an app password. Kombu never sees the account password, and two-factor authentication and single sign-on work without extra code.

## What Changes

- New pure Kotlin module `nextcloud-auth`. It contains:
  - URL normalisation for what the user types (scheme, trailing slashes, sub-path installs);
  - the server check through `status.php`;
  - the Login Flow v2 client (start, poll);
  - an authenticated check of the account (OCS user endpoint);
  - revoking the app password on logout.
- `cookbook-client` gets its first code: a check that the Cookbook app answers for the signed-in account. The check reads the Cookbook API version without enforcing a minimum, because O9 is still open.
- `data` gets an encrypted credential store. It keeps the app password encrypted with a key in the Android Keystore, keeps the server URL and login name, and excludes all of it from backups and device transfer.
- `app` gets a login screen and an account section:
  - The login screen asks for the server address, opens the Nextcloud login page in the browser, waits for the result, and shows clear errors.
  - The account section shows the signed-in server and user and offers logout.
  - Without stored credentials the app opens on the login screen; with them it opens on the start screen.
- The app requests the `INTERNET` permission. Cleartext HTTP stays blocked: only HTTPS servers are supported.
- The `build-system` spec lists `nextcloud-auth` among the pure Kotlin modules.

Out of scope:

- Manual entry of an app password. O2 decided against it.
- More than one account per installation. Each household member signs in on their own device (D2).
- Plain HTTP servers, self-signed certificates and user-installed CAs.
- Reading, syncing or editing recipes. That is `recipe-sync` and later changes.
- Handling an app password that is revoked on the server while Kombu is signed in. No request runs in the background yet; `recipe-sync` will react to "unauthorized" answers.
- A minimum Cookbook version (O9).
- Rules for pending offline changes on logout. There are none yet; `recipe-sync` must extend logout so that no unsynced change is lost (R2).

## Capabilities

### New Capabilities

- `nextcloud-account`: connecting Kombu to a user's Nextcloud account. It covers the server address, Login Flow v2, the Cookbook availability check, secure local storage of the credentials, and logout with revocation of the app password.

### Modified Capabilities

- `build-system`: the requirement "Pure Kotlin modules stay Android-free" also names the new module `nextcloud-auth`.

## Impact

- **Fixed decisions used:**
  - Android only, Kotlin and Compose;
  - no own backend, all data in the user's Nextcloud;
  - every member has their own account (D2);
  - the server URL is always user-configurable;
  - no Google Play Services.
- **Design rules:**
  - R2: logout never removes data silently. There is no synced data yet, so the rule is recorded for `recipe-sync`.
  - R4: no server URL, account or credential in the repository. Tests use `MockWebServer` and example domains.
  - R5: all new texts come from string resources in English and German.
  - R6: the login screen is Kombu's own design.
- **Modules:**
  - new `nextcloud-auth` (pure);
  - first code in `cookbook-client`, `data` and `app`;
  - `build-logic` gets the new module's layer and the Kotlin serialization plugin.
- **New dependencies.** Each one is checked for license and freedom from Play Services before it is added. All are Apache-2.0, which is compatible with GPL-3.0-or-later, and none needs Google Play Services.
  - OkHttp, plus its MockWebServer for tests;
  - kotlinx.serialization (JSON);
  - kotlinx.coroutines, including the test artifact;
  - AndroidX DataStore (Preferences);
  - AndroidX Browser (Custom Tabs, which works with any browser that supports them, for example Firefox);
  - AndroidX Lifecycle ViewModel and runtime for Compose.
- **Quality gates:** unchanged. The new module follows the thresholds for pure modules (90 % lines, 80 % branches). `data` and `app` keep theirs.
- **Server side:** each login creates one app password, which appears under the device name "Kombu" in the user's Nextcloud security settings. Logout removes it again.
