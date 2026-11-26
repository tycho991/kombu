# Module cookbook-client

Client for the Nextcloud Cookbook REST API of one account. It signs requests with the credentials from `nextcloud-auth` and returns every outcome as a `NextcloudResult`.

- `CookbookClient.checkAvailable` reads the Cookbook version from the app's version route, or from the Nextcloud capabilities when that route is gone. `CookbookVersion.isSupported` tells whether Kombu supports it (0.10.3 or newer).
- `listRecipes`, `getRecipe`, `createRecipe`, `updateRecipe` and `deleteRecipe` handle recipes; `downloadImage` fetches a recipe image (JPEG, PNG, WebP or GIF, recognised by its first bytes).
- `RemoteRecipe` keeps a recipe as raw schema.org `Recipe` JSON, so fields Kombu does not know survive a round trip. `RecipeStub` is one entry of the recipe list.
- `RecipeFields` reads the fields Kombu shows (name, description, category, keywords, servings, the three times, ingredients, steps and the source link) leniently into `RecipeValues`, and writes one `RecipeField` at a time without touching any other key. Cleared category, servings and times remove their key. Times are ISO 8601 durations; text that is not one is kept as `RecipeTime.Raw`.
- `RecipeDiff.between` compares two versions for the conflict view: the shown fields that differ, and the number of other differing keys.
- `RecipeHash` hashes a canonical form of a recipe without the fields the server sets itself, so two server versions can be compared.

Server behaviour the client relies on (Cookbook 0.11):

- An update reads the recipe id from the JSON body, not from the URL, so `updateRecipe` sets the `id` field. A create with an `id` field would update that recipe, so `createRecipe` removes it.
- The recipe list normalises `dateModified` (it adds the `T` separator and a time zone). Compare list dates only with list dates.
- The server adds `imageUrl` and the per-user `printImage` setting to every recipe it returns.

This module is pure Kotlin/JVM and has no Android dependency.
