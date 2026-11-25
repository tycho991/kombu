# Design

## Context

`project-scaffold` left an app that shows a start screen and nothing else. `cookbook-client` and `data` are empty, and nothing talks to a network yet. The quality gates in the `code-quality` spec apply from the first line:

- coverage per module;
- detekt and Lint with zero findings;
- explicit API and KDoc in pure modules;
- no hard-coded UI text.

See proposal.md for the motivation and scope, and `specs/nextcloud-account/spec.md` for the required behaviour.

Nextcloud Login Flow v2, the protocol this change builds on:

1. `POST <server>/index.php/login/v2` returns a login URL and a poll token with a poll endpoint. The app password is named after the `User-Agent` of this request.
2. The app opens the login URL in a browser.
3. The app repeatedly sends `POST <poll endpoint>` with `token=<token>`. The answer is `404` until the user approves, then `200` with `server`, `loginName` and `appPassword`. The token expires after 20 minutes.

The other server endpoints used:

| Purpose | Request |
|---|---|
| Server check | `GET <server>/status.php` → JSON with `installed`, `maintenance`, `needsDbUpgrade`, `version` |
| Account check | `GET <server>/ocs/v2.php/cloud/user?format=json` with header `OCS-APIRequest: true` and Basic auth |
| Cookbook check | `GET <server>/index.php/apps/cookbook/api/v1/version` with Basic auth |
| Revoke on logout | `DELETE <server>/ocs/v2.php/core/apppassword` with header `OCS-APIRequest: true` and Basic auth |

## Goals / Non-Goals

**Goals:**

- A login that works against any HTTPS Nextcloud with the Cookbook app. It is tested on the JVM against simulated servers and checked once by hand against a real instance.
- One place that turns network failures into a small, closed set of error kinds, so the UI can explain each one in both languages.
- Credentials that the rest of the app reads through one interface. `recipe-sync` and the later CalDAV client reuse it unchanged.

**Non-Goals:**

- No navigation library. The app has three screens; `recipe-browse-edit` decides on navigation.
- No dependency-injection framework. A small hand-written container is enough at this size.
- No background work. Nothing runs without the login screen being open.

## Decisions

### D1 Generic Nextcloud code in a new pure module `nextcloud-auth`

Address normalisation, the server check, Login Flow v2, the account check and revocation are Nextcloud features, not Cookbook features. They go into the new pure module `nextcloud-auth`. The module also defines the `NextcloudCredentials` value type: server URL, login name and app password, with a `toString()` that hides the password.

`cookbook-client` depends on `nextcloud-auth` and adds only the Cookbook check. Its `Module.md` is updated accordingly. The later `caldav-client` will depend on `nextcloud-auth` the same way.

*Alternative:* put everything into `cookbook-client`, as its current `Module.md` suggests. Rejected, because the CalDAV client would then depend on the Cookbook client only for login.

### D2 OkHttp and kotlinx.serialization in the pure modules

HTTP runs on OkHttp 5. Suspending calls use `okhttp-coroutines`, and JSON is parsed with kotlinx.serialization using `ignoreUnknownKeys`. All of them are Apache-2.0, need no Android classes and work without Google Play Services.

Every module that uses kotlinx.serialization gets the Kotlin serialization compiler plugin through a new convention plugin `kombu.kotlin.serialization` in `build-logic`. Its version comes from the Kotlin version in the catalog.

Classes annotated with `@Serializable` are data transfer types without own logic. Their constructors and serializers are generated, so Kover excludes them like other generated code (spec `code-quality`, "Generated code excluded"); `CONTRIBUTING.md` states the rule.

The clients receive an `OkHttpClient` from outside, which keeps them testable with `MockWebServer`. The app builds one shared client with these settings:

- connect timeout 15 s, read timeout 30 s;
- `User-Agent: Kombu`;
- no logging interceptor.

*Alternatives:*

- Ktor client: more dependencies for the same result.
- `HttpURLConnection`: no coroutine support, more code of our own, and harder to test.

### D3 Address normalisation is a pure function with explicit outcomes

`ServerAddress.parse(input)` returns either a normalised `https` base URL or a reason why the input was rejected: empty, cleartext HTTP, or invalid. It applies these steps in order:

1. trim whitespace;
2. prepend `https://` when no scheme is given;
3. reject `http://`;
4. drop a query and a fragment;
5. strip trailing slashes;
6. strip a trailing `/index.php`, `/index.php/login`, `/login` or `/apps/...` part;
7. keep any sub-path before that.

Because the HTTPS rule lives here, the network clients can be tested against plain-HTTP `MockWebServer` instances without weakening it.

### D4 The address the user entered is the address Kombu stores

The poll result contains a `server` field. Behind a reverse proxy, Nextcloud sometimes reports this address with `http://` or with an internal host name. Kombu therefore keeps the address it already verified with `status.php` and uses only `loginName` and `appPassword` from the result.

The poll endpoint and the login URL must use `https` and the same host as the verified address. Otherwise the login aborts with a "not a Nextcloud" error, so that the poll token is never sent to a third party.

### D5 Polling owned by the ViewModel and restored after process death

The `LoginViewModel` runs the flow in `viewModelScope`:

- It polls every 2 s while waiting.
- It polls immediately when the screen resumes, which is the moment the user comes back from the browser.
- It stops after 20 minutes with a timeout.

Poll token, poll endpoint, login URL and verified address are kept in `SavedStateHandle`. If Android kills the process while the browser is in front, the restored ViewModel resumes polling instead of losing the login.

*Alternative:* a deep-link redirect back into the app. Login Flow v2 has none, and polling is how the official Nextcloud clients do it.

### D6 Browser through Custom Tabs, with a plain view intent as fallback

The login page opens in a Custom Tab (AndroidX Browser, Apache-2.0). Firefox and most other browsers support Custom Tabs; without one, the call falls back to a normal `ACTION_VIEW` intent. If no app can open the URL at all, the screen shows an error. Kombu never shows the page in its own WebView, because a WebView would let the app see the password.

### D7 Error kinds form a closed set

`nextcloud-auth` returns results instead of throwing across module borders. Its failure type is a sealed hierarchy:

| Error kind | Cause |
|---|---|
| `InvalidAddress` | the address did not parse (with the reason from D3) |
| `Unreachable` | unknown host, connection refused, timeout, no network |
| `CertificateRejected` | TLS handshake or peer verification failed |
| `NotNextcloud` | the `status.php` answer is not usable JSON, or the D4 host check failed |
| `Maintenance` | `maintenance` or `needsDbUpgrade` is true |
| `LoginExpired` | the flow ran past 20 minutes |
| `Unauthorized` | `401` from an authenticated request |
| `CookbookMissing` | the Cookbook check answered `404` or not with JSON |
| `ServerError` | any other unexpected status, with its code |

The app maps each kind to a string resource in English and German. Unknown or unexpected exceptions become `ServerError` or `Unreachable`, never a crash.

### D8 Credential store: Keystore AES-GCM key and DataStore

The `data` module stores the credentials as follows:

- A 256-bit AES key is created in the Android Keystore under the alias `kombu_credentials`. It is not exportable and needs no user authentication, so the app can start offline without prompts.
- The app password is encrypted with AES/GCM/NoPadding and a fresh IV for every write.
- Server URL, login name, ciphertext and IV are stored in a Preferences DataStore file `account`.

If decryption fails (`AEADBadTagException` or a missing key), the store deletes the entry and reports "signed out", as the spec requires.

The Keystore access sits behind a small `SecretKeySource` interface. On the JVM, tests use a software AES key. On the device, `AndroidKeystoreKeySource` is the only implementation.

*Alternatives:*

- EncryptedSharedPreferences (`androidx.security:security-crypto`): deprecated and no longer developed.
- Tink: much larger for a single value.
- Plain preferences: not acceptable.

### D9 Excluded from backup and device transfer

The `account` DataStore file is excluded in two places:

- `dataExtractionRules`, for Android 12+ cloud backup and device transfer;
- `fullBackupContent`, for Android 10 and 11.

A restored ciphertext could not be decrypted anyway, because Keystore keys are never backed up. Excluding the file avoids that dead state. `allowBackup` stays on, so that later changes can decide about other data.

### D10 Network security config written out explicitly

`res/xml/network_security_config.xml` sets `cleartextTrafficPermitted="false"` and trusts only the system CAs. This is already Android's default since API 28, but the explicit file documents the decision and makes a later change visible in review.

### D11 Hand-written app container and three screens

`KombuApplication` creates an `AppContainer` holding:

- the shared `OkHttpClient`;
- the `nextcloud-auth` client;
- the Cookbook client;
- the `AccountRepository`.

`MainActivity` collects the account state and shows a loading state, the login screen, or the start screen. The start screen gets an account action in its top bar that opens the account screen with logout. Which screen is shown follows the account state, and back from the account screen returns to the start screen.

### D12 Logout order

1. Read the credentials.
2. Delete them locally.
3. Try the revocation with a 10 s timeout.

Deleting first guarantees that a crash or a hanging request never leaves the user signed in against their will. A failed revocation only changes the notice that is shown (spec: "Logout offline").

`recipe-sync` will add a check for unsynced changes before step 2.

### Test strategy

| Module | On the JVM | Gate |
|---|---|---|
| `nextcloud-auth` | `ServerAddress` table tests for every rule in D3. `MockWebServer` tests for: the `status.php` variants; the login start; polling (404 then 200); the timeout with a virtual clock; the D4 host check; the account check with `401`; revocation; and the mapping of connection failures and TLS failures to error kinds (an `okhttp-tls` held certificate that the client does not trust). | 90 % lines, 80 % branches |
| `cookbook-client` | `MockWebServer` tests for "Cookbook answers", `404`, non-JSON and `401`. | 90 % / 80 % |
| `data` | Robolectric tests of the credential store with a software key: write and read, restart, wrong key (decryption failure → signed out), delete. A test that the password never appears in `toString()`. | 80 % / 70 % |
| `app` | `LoginViewModel` with fakes and `kotlinx-coroutines-test`: every state transition, process-death restore through `SavedStateHandle`, cancel, timeout. Robolectric Compose tests: login screen in `en` and `de` for each error kind, account screen and logout confirmation, and the entry point for both account states. | 70 % lines |

On an emulator or device, checked by hand:

- Keystore encryption with the real `AndroidKeystoreKeySource`, which Robolectric cannot provide. The check is to sign in, stop the app, start it again offline, and confirm the user is still signed in.
- The Custom Tab round trip.
- A full login and logout against a real Nextcloud with the Cookbook app.

The maintainer uses their own instance for this. No address ends up in the repository.

`AndroidKeystoreKeySource` is the one class that JVM tests cannot reach. It stays below 30 lines, so `data` keeps its 80 % threshold.

## Risks / Trade-offs

- [The app password is named from the `User-Agent`; some Nextcloud versions show the raw string] → `User-Agent: Kombu` is readable either way. The manual test checks the name in the security settings.
- [Servers behind proxies report a different `server` value] → D4 ignores that field and keeps the verified address.
- [Certificates from a private CA or self-signed certificates cannot be used] → Accepted for v1 (proposal, out of scope). Households with such servers cannot use Kombu yet. A later change can add user-CA trust as an explicit option.
- [Polling stops when the app process is killed and the user never comes back] → The token expires on the server after 20 minutes, and nothing is stored until approval.
- [The Keystore key can be lost, for example after some OS updates on some devices] → The store treats undecryptable data as signed out (spec). The user signs in again, and no other data depends on the credentials yet.
- [Cookbook API path or version endpoint differs between Cookbook versions] → Only "answers with JSON" is checked. The version is kept for later (O9).
