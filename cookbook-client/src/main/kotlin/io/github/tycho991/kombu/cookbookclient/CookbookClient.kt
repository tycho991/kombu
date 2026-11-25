package io.github.tycho991.kombu.cookbookclient

import io.github.tycho991.kombu.nextcloudauth.NextcloudCredentials
import io.github.tycho991.kombu.nextcloudauth.NextcloudError
import io.github.tycho991.kombu.nextcloudauth.NextcloudResult
import io.github.tycho991.kombu.nextcloudauth.fetch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response

/** The two image sizes the Cookbook server stores for a recipe. */
public enum class ImageSize(internal val query: String) {
    /** The image as uploaded, scaled down by the server. */
    FULL("full"),

    /** A small square thumbnail for lists. */
    THUMB("thumb"),
}

/**
 * Client for the Nextcloud Cookbook REST API of one account.
 *
 * Every call returns a [NextcloudResult]; rejected credentials give [NextcloudError.Unauthorized], a missing recipe
 * [NextcloudError.NotFound], and an unexpected status or unreadable body [NextcloudError.ServerError].
 *
 * @param httpClient the HTTP client to use; the caller configures timeouts and the user agent.
 */
public class CookbookClient(private val httpClient: OkHttpClient) {
    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Checks that the Cookbook app answers for the account in [credentials] and returns the version it reports.
     *
     * It asks the app's version route first. When that route is missing (Cookbook deprecated it in 0.11.5), it
     * reads the version from the Nextcloud capabilities. When neither reports a version, the result is
     * [NextcloudError.CookbookMissing]. Whether the version is supported is up to the caller, see
     * [CookbookVersion.isSupported].
     */
    public suspend fun checkAvailable(credentials: NextcloudCredentials): NextcloudResult<CookbookVersion> {
        // The version route has no /v1 prefix, unlike the other API routes.
        val direct = httpClient.fetch(credentials.get(credentials.server.resolve(VERSION_ROUTE)), ::readVersion)
        if (direct !is NextcloudResult.Success || direct.value != null) return direct.withoutNull()
        val capabilities =
            Request
                .Builder()
                .url(credentials.server.resolve("ocs/v2.php/cloud/capabilities").withQuery("format", "json"))
                .header("Authorization", credentials.authorizationHeader())
                .header("Accept", "application/json")
                .header("OCS-APIRequest", "true")
                .get()
                .build()
        return httpClient.fetch(capabilities, ::readCapabilities).withoutNull()
    }

    /** Lists all recipes of the account as [RecipeStub]s. */
    public suspend fun listRecipes(credentials: NextcloudCredentials): NextcloudResult<List<RecipeStub>> =
        httpClient.fetch(credentials.get(credentials.server.resolve(RECIPES))) { response ->
            response.readBody { parseStubs(parseElement(it)) }
        }

    /** Downloads the recipe with [id] in full. */
    public suspend fun getRecipe(credentials: NextcloudCredentials, id: String): NextcloudResult<RemoteRecipe> =
        httpClient.fetch(credentials.get(credentials.recipeUrl(id))) { response ->
            response.readBody { body -> parseObject(body)?.let(::RemoteRecipe) }
        }

    /**
     * Creates [recipe] on the server and returns the id the server assigned. Any `id` field in [recipe] is left
     * out, because the server would treat the request as an update of that recipe. A name that another recipe
     * already has gives [NextcloudError.NameTaken], a missing name [NextcloudError.NameMissing].
     */
    public suspend fun createRecipe(credentials: NextcloudCredentials, recipe: RemoteRecipe): NextcloudResult<String> =
        httpClient.fetch(
            credentials.send("POST", credentials.server.resolve(RECIPES), recipe.without("id")),
        ) { response ->
            response.readBody { body -> (parseElement(body) as? JsonPrimitive)?.takeIf(::isId)?.content }
        }

    /**
     * Replaces the recipe with [id] by [recipe]. The server reads the recipe id from the body, not from the URL,
     * so the `id` field is set to [id]. Renaming to a name another recipe has gives [NextcloudError.NameTaken].
     */
    public suspend fun updateRecipe(
        credentials: NextcloudCredentials,
        id: String,
        recipe: RemoteRecipe,
    ): NextcloudResult<Unit> {
        val body = if (recipe.id == id) recipe else recipe.with("id", JsonPrimitive(id))
        return httpClient.fetch(credentials.send("PUT", credentials.recipeUrl(id), body)) { response ->
            response.readBody { }
        }
    }

    /** Deletes the recipe with [id] on the server. */
    public suspend fun deleteRecipe(credentials: NextcloudCredentials, id: String): NextcloudResult<Unit> {
        val request =
            Request
                .Builder()
                .url(credentials.recipeUrl(id))
                .header("Authorization", credentials.authorizationHeader())
                .delete()
                .build()
        return httpClient.fetch(request) { response -> response.readBody { } }
    }

    /**
     * Downloads the image of the recipe with [id] in [size] as JPEG bytes. Returns a success with `null` when the
     * recipe has no image: the server then answers `406` to a request that accepts only JPEG.
     */
    public suspend fun downloadImage(
        credentials: NextcloudCredentials,
        id: String,
        size: ImageSize,
    ): NextcloudResult<ByteArray?> {
        val request =
            Request
                .Builder()
                .url(credentials.recipeUrl(id, "image").withQuery("size", size.query))
                .header("Authorization", credentials.authorizationHeader())
                .header("Accept", "image/jpeg")
                .get()
                .build()
        return httpClient.fetch(request) { response ->
            if (response.code == HTTP_NOT_ACCEPTABLE) {
                NextcloudResult.Success(null)
            } else {
                response.readBytes { bytes -> bytes.takeIf(::isJpeg) }
            }
        }
    }

    private fun readVersion(response: Response): NextcloudResult<CookbookVersion?> = when {
        response.code == HTTP_NOT_FOUND -> NextcloudResult.Success(null)

        // An HTML page instead of JSON means that Nextcloud answered, but not the Cookbook app.
        else -> response.readBody { body -> decodeVersion(body) ?: NO_VERSION }
    }

    private fun readCapabilities(response: Response): NextcloudResult<CookbookVersion?> =
        if (response.code == HTTP_NOT_FOUND) {
            NextcloudResult.Success(NO_VERSION)
        } else {
            response.readBody { body ->
                val cookbook = (parseObject(body)?.get("ocs") as? JsonObject)?.path("data", "capabilities", "cookbook")
                versionOf(cookbook?.get("version")) ?: NO_VERSION
            }
        }

    private fun decodeVersion(body: String): CookbookVersion? = try {
        CookbookVersion(json.decodeFromString<VersionAnswer>(body).cookbookVersion).takeIf { it.parts.isNotEmpty() }
    } catch (ignored: SerializationException) {
        null
    } catch (ignored: IllegalArgumentException) {
        // JSON of another shape; treated like an HTML page.
        null
    }

    private fun NextcloudResult<CookbookVersion?>.withoutNull(): NextcloudResult<CookbookVersion> = when (this) {
        is NextcloudResult.Failure -> this

        is NextcloudResult.Success -> value?.takeUnless { it === NO_VERSION }
            ?.let { NextcloudResult.Success(it) }
            ?: NextcloudResult.Failure(NextcloudError.CookbookMissing)
    }

    private companion object {
        const val RECIPES = "index.php/apps/cookbook/api/v1/recipes"
        const val VERSION_ROUTE = "index.php/apps/cookbook/api/version"
        const val HTTP_UNAUTHORIZED = 401
        const val HTTP_NOT_FOUND = 404
        const val HTTP_NOT_ACCEPTABLE = 406
        const val HTTP_CONFLICT = 409
        const val HTTP_UNPROCESSABLE = 422
        const val JPEG_MAGIC_0: Byte = 0xFF.toByte()
        const val JPEG_MAGIC_1: Byte = 0xD8.toByte()

        /** Marks an answer that was readable but carried no version, so the capabilities are not asked again. */
        val NO_VERSION = CookbookVersion(emptyList())
        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

        /** The URL of the recipe with [id], with [extra] path segments; the id is encoded as one segment. */
        fun NextcloudCredentials.recipeUrl(id: String, vararg extra: String): HttpUrl = server
            .resolve(RECIPES)
            .newBuilder()
            .addPathSegment(id)
            .apply { extra.forEach(::addPathSegment) }
            .build()

        /** A recipe id the server returned: a JSON number or string, not a bare word from an HTML page. */
        fun isId(value: JsonPrimitive): Boolean =
            (value.isString || value.content.toLongOrNull() != null) && value.content.isNotBlank()

        fun isJpeg(bytes: ByteArray): Boolean = bytes.size > 2 && bytes[0] == JPEG_MAGIC_0 && bytes[1] == JPEG_MAGIC_1

        fun NextcloudCredentials.get(url: HttpUrl): Request = Request
            .Builder()
            .url(url)
            .header("Authorization", authorizationHeader())
            .header("Accept", "application/json")
            .get()
            .build()

        fun NextcloudCredentials.send(method: String, url: HttpUrl, recipe: RemoteRecipe): Request = Request
            .Builder()
            .url(url)
            .header("Authorization", authorizationHeader())
            .header("Accept", "application/json")
            .method(method, recipe.encode().toRequestBody(JSON_MEDIA_TYPE))
            .build()

        fun HttpUrl.withQuery(name: String, value: String): HttpUrl =
            newBuilder().addQueryParameter(name, value).build()

        fun JsonObject.path(vararg keys: String): JsonObject? =
            keys.fold(this as JsonObject?) { current, key -> current?.get(key) as? JsonObject }

        /** A version as a list of numbers (`[0, 11, 12]`) or as text (`"0.11.12"`). */
        fun versionOf(element: JsonElement?): CookbookVersion? {
            val texts =
                when (element) {
                    is JsonArray -> element.map { (it as? JsonPrimitive)?.content }
                    else -> element.textOrNull()?.split('.')
                }
            val parts = texts?.map { it?.toIntOrNull() }
            return parts?.takeIf { it.isNotEmpty() && null !in it }?.let { CookbookVersion(it.filterNotNull()) }
        }

        /** Maps the status of [this] answer; a success reads the body with [read], `null` meaning unreadable. */
        fun <T> Response.readBody(read: (String) -> T?): NextcloudResult<T> =
            status() ?: read(body.string())?.let { NextcloudResult.Success(it) }
                ?: NextcloudResult.Failure(NextcloudError.ServerError(code))

        fun <T> Response.readBytes(read: (ByteArray) -> T): NextcloudResult<T> =
            status() ?: NextcloudResult.Success(read(body.bytes()))

        /** The failure for an unsuccessful status, or `null` for a success. */
        fun Response.status(): NextcloudResult.Failure? = when {
            isSuccessful -> null
            code == HTTP_UNAUTHORIZED -> NextcloudResult.Failure(NextcloudError.Unauthorized)
            code == HTTP_NOT_FOUND -> NextcloudResult.Failure(NextcloudError.NotFound)
            code == HTTP_CONFLICT -> NextcloudResult.Failure(NextcloudError.NameTaken)
            code == HTTP_UNPROCESSABLE -> NextcloudResult.Failure(NextcloudError.NameMissing)
            else -> NextcloudResult.Failure(NextcloudError.ServerError(code))
        }
    }
}

/** The version the Cookbook app reports, as its numeric [parts] (for example `[0, 11, 12]`). */
public data class CookbookVersion(
    /** The numeric version parts. */
    val parts: List<Int>,
) {
    /**
     * Whether Kombu supports this version: 0.10.3 or newer (decision O9). Parts are compared as numbers, one by
     * one; missing parts count as 0. An empty version is not supported.
     */
    val isSupported: Boolean
        get() = parts.isNotEmpty() && compareParts(parts, MINIMUM.parts) >= 0

    override fun toString(): String = parts.joinToString(".")

    /** The oldest supported version. */
    public companion object {
        /** Cookbook 0.10.3: from this version on, recipe stubs carry a stable string id. */
        public val MINIMUM: CookbookVersion = CookbookVersion(listOf(0, 10, 3))

        private fun compareParts(left: List<Int>, right: List<Int>): Int {
            for (index in 0 until maxOf(left.size, right.size)) {
                val difference = left.getOrElse(index) { 0 }.compareTo(right.getOrElse(index) { 0 })
                if (difference != 0) return difference
            }
            return 0
        }
    }
}

@Serializable
internal data class VersionAnswer(
    @SerialName("cookbook_version")
    val cookbookVersion: List<Int> = emptyList(),
)
