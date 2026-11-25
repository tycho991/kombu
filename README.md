# Kombu

Kombu is an Android household app built around [Nextcloud Cookbook](https://github.com/nextcloud/cookbook). It keeps your recipes where they already live, in your own Nextcloud, and adds what a household needs around them:

- **Recipes and cooking mode** – browse and edit your Cookbook recipes, fully offline, with a step-by-step cooking mode, timers, ingredient check-off and serving scaling.
- **Recipe import** – share a recipe as text from another app (for example an AI assistant) and review it in the editor before saving.
- **Shopping lists** *(planned)* – shared lists with categories, history and suggestions, stored as CalDAV tasks.
- **Meal plan** *(planned)* – plan what to cook when, stored in a shared CalDAV calendar.
- **Chores** *(planned)* – a needs-based chore planner per room, stored as CalDAV tasks.

Kombu has no server of its own. Everything is stored in your Nextcloud and shared with the people in your household through Nextcloud's own sharing, so other apps can keep reading the same data.

## Status

Early development. Kombu can sign in to a Nextcloud, keeps a complete offline copy of your Cookbook recipes and shows the sync status on its start screen. The recipe list, the recipe view and the editor come next.

Development is spec-driven with [OpenSpec](https://github.com/Fission-AI/OpenSpec). The project context lives in [`openspec/config.yaml`](openspec/config.yaml), and changes appear under [`openspec/changes/`](openspec/changes/).

The first milestone (v1) covers recipes and the cooking mode. Shopping lists, meal plan and chores follow afterwards.

## Requirements

- Android 10 (API level 29) or later. Google Play Services are not needed.
- A Nextcloud reachable over HTTPS with a certificate your device trusts. Plain HTTP and self-signed certificates are not supported.
- The Cookbook app installed and enabled in that Nextcloud (and Calendar/Tasks for the later features).
- A web browser on the device for the login.

## Signing in

1. Enter the address of your Nextcloud, for example `cloud.example.com` or `example.com/nextcloud`. Kombu adds `https://` itself.
2. Kombu checks the server and opens the Nextcloud login page in your browser. Sign in there, including two-factor authentication if your account uses it, and allow access.
3. Return to Kombu. It continues on its own once access is granted.

Kombu never sees your Nextcloud password. Nextcloud creates an app password for Kombu instead, which you find under the name "Kombu" in the security settings of your Nextcloud. Kombu stores it encrypted on the device and leaves it out of backups. Signing out in Kombu removes the app password from your Nextcloud again; if the server cannot be reached at that moment, you can remove it in the security settings yourself.

## Recipes on the device

After signing in, Kombu downloads every recipe of your Cookbook, including its image (full size and thumbnail), and keeps the copy up to date: when the app starts, shortly after a change in Kombu, when you tap "Sync now", and about every hour while a network is available. Reading a recipe never needs the network.

- **What syncs:** every recipe with all its fields, also the ones Kombu does not show yet. Kombu changes only the fields you edit and keeps every other field as it is.
- **Offline changes:** recipes created, changed or deleted in Kombu are stored at once and uploaded on the next sync.
- **Conflicts:** before Kombu uploads a change, it checks whether the recipe changed in your Nextcloud since the last sync. If it did, Kombu uploads nothing and keeps both versions until you decide. Kombu never overwrites a change silently.
- **Images are download-only.** The Cookbook API cannot receive image files, so an image you want for a recipe must be set in the Cookbook web interface (or as an image URL in the recipe).
- **Not synced:** categories and keywords as separate objects (renaming a category), and anything outside the Cookbook app.
- **Signing out** deletes the recipe copy on the device, after telling you how many changes are not uploaded yet. Your recipes stay in your Nextcloud.

If Nextcloud stops accepting Kombu's app password (for example because you removed it in the security settings), the start screen asks you to sign in again. Signing in again to the same account keeps all recipes and changes on the device.

Kombu supports Cookbook 0.10.3 or newer. The recipe copy is stored in the app's private storage and is left out of backups and device transfers; after a restore, Kombu downloads the recipes again.

## Building

You need a JDK 21 and the Android SDK. No IDE is required.

1. **JDK.** With [mise](https://mise.jdx.dev/), run `mise install` in the repository; it installs the pinned Temurin JDK 21 and the OpenSpec CLI from [`mise.toml`](mise.toml). Any other JDK 21 works as well.
2. **Android SDK.** Install the [command-line tools](https://developer.android.com/studio#command-line-tools-only) outside the repository, then accept the licenses and install the platform tools:

   ```sh
   sdkmanager --licenses
   sdkmanager "platform-tools" "platforms;android-37.0"
   ```

   Point the build to the SDK with the `ANDROID_HOME` environment variable or a `local.properties` file containing `sdk.dir=/path/to/sdk`. `local.properties` is git-ignored.
3. **Build and check.**

   ```sh
   ./gradlew build
   ```

   This compiles everything, runs all tests and all quality gates, and produces a debug APK under `app/build/outputs/apk/debug/`. [`CONTRIBUTING.md`](CONTRIBUTING.md) explains the gates and the other commands.

### Signed release APK

The release build is signed only if a `keystore.properties` file exists in the repository root. Without it, `./gradlew :app:assembleRelease` produces an unsigned APK and says so.

Create a keystore once and keep it **outside** the repository, together with a backup. An app can only be updated with the key it was first signed with.

```sh
keytool -genkeypair -keystore ~/keys/kombu-release.jks -storetype PKCS12 \
  -alias kombu -keyalg RSA -keysize 4096 -validity 10000
```

Then create `keystore.properties` in the repository root:

```properties
storeFile=/home/you/keys/kombu-release.jks
storePassword=...
keyAlias=kombu
keyPassword=...
```

`keystore.properties`, `*.jks` and `*.keystore` are git-ignored. Never commit them. The signed APK is written to `app/build/outputs/apk/release/app-release.apk`.

## Language

The app is developed in English with German as the first translation. Further translations are welcome later on.

## License

Kombu is free software, licensed under the [GNU General Public License v3.0 or later](LICENSE).

The ingredient parser contains a Kotlin port of [parse-ingredients](https://github.com/magrinj/parse-ingredients) by Jérémy Magrin, used under the MIT License; see [ingredient-parser/NOTICE](ingredient-parser/NOTICE).

Kombu is an independent project and is not affiliated with or endorsed by Nextcloud GmbH. Nextcloud is a registered trademark of Nextcloud GmbH.
