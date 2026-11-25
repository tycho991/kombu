# Tasks

## 1. Build setup

- [x] 1.1 Add the new libraries to `gradle/libs.versions.toml`. Each must be a stable release at least two weeks old, and its license (Apache-2.0) and freedom from Google Play Services must be confirmed:
  - OkHttp with `okhttp-coroutines`, `mockwebserver3` and `okhttp-tls`;
  - kotlinx.serialization JSON;
  - kotlinx.coroutines core and test;
  - AndroidX DataStore Preferences;
  - AndroidX Browser;
  - AndroidX Lifecycle ViewModel Compose and runtime Compose.

  Verify that `./gradlew help` succeeds and that `checkForbiddenDependencies` passes.
- [x] 1.2 Add the convention plugin `kombu.kotlin.serialization` to `build-logic` (design D2). Verify that a module applying it compiles a `@Serializable` class.
- [x] 1.3 Create the pure module `nextcloud-auth` with `Module.md`, register it as PURE in `ModuleLayer.kt`, and include it in `settings.gradle.kts` and the Dokka aggregation. Update the `cookbook-client` `Module.md` (design D1). Verify that `./gradlew :nextcloud-auth:check` runs, and that the aggregated Dokka site lists the module.

## 2. Nextcloud client (`nextcloud-auth`)

- [x] 2.1 Implement `NextcloudCredentials` with a `toString()` that hides the password. Implement `ServerAddress.parse` with the rules from design D3. Add table tests for:
  - scheme added;
  - `http://` rejected;
  - trailing slashes;
  - sub-path;
  - `/index.php`, `/index.php/login`, `/login` and `/apps/...` suffixes;
  - query and fragment;
  - invalid input and empty input.

  Verify the tests pass.
- [x] 2.2 Implement the error kinds from design D7 and the mapping of connection and TLS failures to them. Verify with `MockWebServer` tests: a closed port gives `Unreachable`, and an untrusted `okhttp-tls` certificate gives `CertificateRejected`.
- [x] 2.3 Implement the server check through `status.php`. Verify with `MockWebServer` tests for: installed; `maintenance`; `needsDbUpgrade`; not JSON; JSON without `installed`; and `500`.
- [x] 2.4 Implement the Login Flow v2 start and polling:
  - poll interval, immediate poll on request, and the 20-minute limit using an injectable clock;
  - the host check from design D4.

  Verify with `MockWebServer` tests for: start; 404 then 200; expiry; a poll endpoint on another host; a login URL without `https`; and that the stored address is the verified one, not the `server` field.
- [x] 2.5 Implement the account check (OCS user endpoint) and the revocation of the app password. Verify with `MockWebServer` tests: the `OCS-APIRequest` header and Basic auth are sent, `401` gives `Unauthorized`, and revocation succeeds and fails as expected.
- [x] 2.6 Write KDoc for every public declaration. Verify that `./gradlew :nextcloud-auth:check` passes, with Kover at 90 % lines and 80 % branches or higher.

## 3. Cookbook check (`cookbook-client`)

- [x] 3.1 Implement the Cookbook availability check on `/index.php/apps/cookbook/api/v1/version`. It returns the reported version or `CookbookMissing`, `Unauthorized` or `ServerError`. Verify with `MockWebServer` tests for a JSON answer, `404`, an HTML answer and `401`.
- [x] 3.2 Write KDoc for every public declaration. Verify that `./gradlew :cookbook-client:check` passes with the pure-module coverage thresholds.

## 4. Credential store (`data`)

- [x] 4.1 Implement `SecretKeySource` with `AndroidKeystoreKeySource`, and the AES-GCM credential store on Preferences DataStore (design D8). The store offers read as a flow, save and delete, and treats data it cannot decrypt as signed out. Verify with Robolectric tests using a software key: round trip; reading again after a new store instance; a wrong key, which deletes the entry and returns signed out; delete; and a check that the stored file does not contain the password in plain text.
- [x] 4.2 Implement `AccountRepository` for the login sequence and for logout:
  - login: server check, start, poll, account check, Cookbook check, store;
  - Cookbook missing: no storing, and a revocation attempt;
  - logout in the order of design D12.

  Verify with tests using fake clients for every path in the spec requirements "Account check after login" and "Logout revokes the app password".
- [x] 4.3 Exclude the `account` DataStore file from backup and device transfer with `dataExtractionRules` and `fullBackupContent` (design D9). Verify that Lint passes and that the merged manifest of the app references both rule files.
- [x] 4.4 Verify that `./gradlew :data:check` passes with 80 % lines and 70 % branches or higher.

## 5. App: login, account and entry point

- [x] 5.1 Add the `INTERNET` permission, `network_security_config.xml` (design D10), `KombuApplication` and the `AppContainer` with the shared `OkHttpClient` (design D2, D11). Verify that the merged manifest contains the permission and the config, and that the existing tests still pass.
- [x] 5.2 Implement `LoginViewModel` with the polling and `SavedStateHandle` restore from design D5. Verify with `kotlinx-coroutines-test` tests for:
  - address error;
  - each server-check error;
  - waiting, then success;
  - cancel;
  - timeout;
  - Cookbook missing;
  - restore after process death, which keeps polling with the saved token.
- [x] 5.3 Implement the login screen: address field, sign-in button, waiting state with "open login page again" and "cancel", and one message for each error kind. Open the login page through a Custom Tab with a fallback (design D6). Add all texts to `values/` and `values-de/`. Verify with Robolectric Compose tests that each state and error message is shown in English and German, and that the Lint check `HardcodedComposeText` passes.
- [x] 5.4 Implement the entry point (loading, then the login screen or the start screen), the account action on the start screen, and the account screen with server, login name, logout confirmation and the offline-logout notice. Add the texts in English and German. Verify with Robolectric tests for both account states, a cancelled logout, an online logout, and an offline logout with the notice.
- [x] 5.5 Verify that `./gradlew build` passes with `app` at 70 % lines or higher, and that detekt, Lint and the compiler report no warnings.

## 6. Documentation and integration check

- [x] 6.1 Update the README: the first steps for signing in, the requirements (HTTPS, the Cookbook app on the server), and the note that each login creates an app password named "Kombu". Update CONTRIBUTING.md if commands changed. Verify that the README contains no server address other than example domains.
- [ ] 6.2 On an emulator with a debug build against a real Nextcloud with the Cookbook app, verify all of the following and record the result in this task. The maintainer provides the instance, and no address is committed.
  - Login through the browser.
  - The app password named "Kombu" in the security settings.
  - A restart offline keeps the user signed in (Keystore).
  - Logout removes the app password on the server.
  - A wrong address and an address without Cookbook show their errors in English and in German.
  - Partly done on an API 36 emulator without a real instance: the debug build starts on the login screen; `http://…` is rejected with the HTTPS message, `example.com` reports that no Nextcloud was found, and an unresolvable host reports that the server cannot be reached; no crash. Open: everything that needs a real Nextcloud with the Cookbook app.
- [x] 6.3 Verify that CI is green on the pushed branch, and run `openspec validate nextcloud-login --strict`.

## Workflow follow-up

- Review and merge the implementation.
- Archive the change with `/openspec-archive-change`, so that `nextcloud-account` becomes a main spec and the `build-system` change is merged.
