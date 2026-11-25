package io.github.tycho991.kombu.sync

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.RecordedRequest
import mockwebserver3.SocketEffect
import okio.Buffer

/**
 * A Cookbook server in memory, served through `MockWebServer`. It behaves like Cookbook 0.11 where it matters:
 * - it sets `dateModified` on every save, in a raw form in the recipe and a normalised form in the list;
 * - it adds `imageUrl` and `printImage` to every recipe it returns;
 * - it reads the id of an update from the body and answers `409` for a taken name, `422` for a missing one;
 * - it answers `406` for a missing image when only JPEG is accepted.
 *
 * Tests change it like a user of the web interface would ([add], [edit], [delete]) and inject failures.
 */
class FakeCookbookServer : Dispatcher() {
    private val recipes = linkedMapOf<String, JsonObject>()
    private val images = mutableMapOf<String, ByteArray>()
    private val withoutDate = mutableSetOf<String>()
    private var nextId = 100
    private var tick = 0
    private val failures = mutableListOf<Failure>()

    private class Failure(
        val matches: (RecordedRequest) -> Boolean,
        val response: MockResponse,
        var remaining: Int,
        val afterHandling: Boolean,
    )

    /** The bodies of every create and update, in order. */
    val bodies = mutableListOf<JsonObject>()

    /** Every request the server received, in order, as "METHOD path". */
    val log = mutableListOf<String>()

    /** The version the version route reports; `null` makes the route answer `404`. */
    var version: List<Int>? = listOf(0, 11, 12)

    /** The recipes as stored, without the fields the server adds when reading. */
    val stored: Map<String, JsonObject> get() = recipes

    /** Adds a recipe like the web interface would and returns its id. */
    fun add(recipe: JsonObject, image: Boolean = false, dated: Boolean = true): String {
        val id = (nextId++).toString()
        recipes[id] = stamp(JsonObject(recipe + ("id" to JsonPrimitive(id))))
        if (image) images[id] = jpeg(id)
        if (!dated) {
            withoutDate += id
            recipes[id] = JsonObject(recipes.getValue(id) + ("dateModified" to JsonNull))
        }
        return id
    }

    /** Changes a stored recipe like the web interface would. */
    fun edit(id: String, change: (JsonObject) -> JsonObject) {
        recipes[id] = stamp(change(recipes.getValue(id)))
    }

    /** Deletes a recipe like the web interface would. */
    fun delete(id: String) {
        recipes.remove(id)
        images.remove(id)
    }

    /** Gives the recipe a new image file. */
    fun setImage(id: String, bytes: ByteArray?) {
        if (bytes == null) images.remove(id) else images[id] = bytes
    }

    /** Answers the next [times] requests matching [matches] with [response] instead of serving them. */
    fun fail(
        response: MockResponse = MockResponse.Builder().code(500).build(),
        times: Int = Int.MAX_VALUE,
        matches: (RecordedRequest) -> Boolean,
    ) {
        failures += Failure(matches, response, times, afterHandling = false)
    }

    /** Drops the connection of every request matching [matches] before serving it. */
    fun drop(matches: (RecordedRequest) -> Boolean) {
        fail(DROPPED, matches = matches)
    }

    /** Serves the next request matching [matches], then drops the connection, so its answer is lost. */
    fun loseAnswer(matches: (RecordedRequest) -> Boolean) {
        failures += Failure(matches, DROPPED, 1, afterHandling = true)
    }

    /** Removes all injected failures. */
    fun heal() {
        failures.clear()
    }

    @Synchronized
    override fun dispatch(request: RecordedRequest): MockResponse {
        val path = request.url.encodedPath.removePrefix(PREFIX)
        log += "${request.method} $path"
        val failure = failures.firstOrNull { it.remaining > 0 && it.matches(request) }
        failure?.let { it.remaining-- }
        if (failure != null && !failure.afterHandling) return failure.response
        val answer = serve(request, path)
        return if (failure != null) failure.response else answer
    }

    private fun serve(request: RecordedRequest, path: String): MockResponse {
        val segments = path.split('/')
        return when {
            path == "index.php/apps/cookbook/api/version" -> versionAnswer()
            path == RECIPES && request.method == "GET" -> json(JsonArray(recipes.keys.map(::stub)))
            path == RECIPES && request.method == "POST" -> create(body(request))
            path.startsWith("$RECIPES/") && segments.size == RECIPE_SEGMENTS -> single(request, segments.last())
            path.startsWith("$RECIPES/") && segments.last() == "image" -> image(segments[segments.size - 2])
            else -> status(404)
        }
    }

    private fun single(request: RecordedRequest, id: String): MockResponse = when (request.method) {
        "GET" -> recipes[id]?.let { json(withReadFields(id, it)) } ?: status(404)

        "PUT" -> update(body(request))

        "DELETE" -> {
            delete(id)
            json(JsonPrimitive("Recipe $id deleted successfully"))
        }

        else -> status(405)
    }

    /** Cookbook adds an image link and the user's print setting to every recipe it returns. */
    private fun withReadFields(id: String, recipe: JsonObject): JsonObject = JsonObject(
        recipe + ("imageUrl" to JsonPrimitive("/image/$id?t=$tick")) + ("printImage" to JsonPrimitive(true)),
    )

    private fun create(recipe: JsonObject): MockResponse {
        val name = recipe.name
        return when {
            name.isNullOrBlank() -> status(422)

            // Like Cookbook, a body with an id updates that recipe instead.
            recipe["id"] != null -> update(recipe)

            recipes.values.any { it.name == name } -> status(409)

            else -> json(JsonPrimitive(add(JsonObject(recipe + ("dateCreated" to JsonPrimitive("c$tick")))).toInt()))
        }
    }

    private fun update(recipe: JsonObject): MockResponse {
        val id = recipe["id"]?.jsonPrimitive?.contentOrNull
        val name = recipe.name
        return when {
            id == null || id !in recipes -> status(500)

            name.isNullOrBlank() -> status(422)

            recipes.any { (other, stored) -> other != id && stored.name == name } -> status(409)

            else -> {
                // The server stores what it gets, without the fields it adds when reading.
                recipes[id] = stamp(JsonObject(recipe - "imageUrl" - "printImage"))
                json(JsonPrimitive(id.toInt()))
            }
        }
    }

    private fun image(id: String): MockResponse = images[id]?.let {
        MockResponse.Builder().addHeader("Content-Type", "image/jpeg").body(Buffer().write(it)).build()
    } ?: status(406)

    private fun versionAnswer(): MockResponse =
        version?.let { json(buildJsonObject { put("cookbook_version", JsonArray(it.map(::JsonPrimitive))) }) }
            ?: status(404)

    private fun stub(id: String): JsonObject {
        val recipe = recipes.getValue(id)
        val modified = recipe["dateModified"]?.jsonPrimitive?.contentOrNull
        return buildJsonObject {
            put("id", id)
            put("recipe_id", id.toInt())
            put("name", recipe.name)
            // The list normalises dates; the full recipe keeps the raw text.
            put("dateModified", modified?.replace(' ', 'T')?.plus("+0000"))
        }
    }

    private fun stamp(recipe: JsonObject): JsonObject {
        tick++
        val id = recipe["id"]?.jsonPrimitive?.contentOrNull
        val date: JsonElement = if (id in withoutDate) JsonNull else JsonPrimitive(rawDate(tick))
        return JsonObject(recipe + ("dateModified" to date))
    }

    private val JsonObject.name: String? get() = this["name"]?.jsonPrimitive?.contentOrNull

    private fun body(request: RecordedRequest): JsonObject =
        (Json.parseToJsonElement(request.body!!.utf8()) as JsonObject).also { bodies += it }

    private fun json(element: JsonElement): MockResponse = MockResponse.Builder().body(element.toString()).build()

    private fun status(code: Int): MockResponse = MockResponse.Builder().code(code).body("""{"msg":"$code"}""").build()

    companion object {
        const val PREFIX = "/nextcloud/"
        const val RECIPES = "index.php/apps/cookbook/api/v1/recipes"
        private const val RECIPE_SEGMENTS = 7
        private val DROPPED = MockResponse.Builder().onResponseStart(SocketEffect.CloseSocket()).build()

        private const val SECONDS = 60

        /** A date as Cookbook writes it into `recipe.json`: with a space and without time zone. */
        fun rawDate(tick: Int): String = "2024-01-01 10:%02d:%02d".format(tick / SECONDS, tick % SECONDS)

        /** A small fake JPEG, different per recipe. */
        fun jpeg(id: String): ByteArray = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte()) + id.toByteArray()

        /** An invented recipe with an unknown field of varying type. */
        fun recipe(name: String, image: String? = null): JsonObject = buildJsonObject {
            put("@type", "Recipe")
            put("name", name)
            put("recipeIngredient", JsonArray(listOf(JsonPrimitive("1 onion"))))
            put("nutrition", JsonArray(emptyList()))
            put("author", buildJsonObject { put("name", "Sam Example") })
            put("image", image.orEmpty())
        }
    }
}
