package io.github.tycho991.kombu.data.recipes

import androidx.room.Room
import io.github.tycho991.kombu.cookbookclient.RemoteRecipe
import io.github.tycho991.kombu.data.recipes.db.KombuDatabase
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.robolectric.RuntimeEnvironment

/** An in-memory recipe database for Robolectric tests. */
internal fun inMemoryDatabase(): KombuDatabase =
    Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), KombuDatabase::class.java).build()

/** An invented recipe with an unknown field and a varying field type, as real recipes have them. */
internal fun recipe(name: String, id: String? = null, image: String? = null): RemoteRecipe = RemoteRecipe(
    buildJsonObject {
        put("name", name)
        put("recipeCategory", "Soups")
        put("keywords", "quick,cheap")
        put("nutrition", JsonArray(emptyList()))
        put("video", buildJsonObject { put("@type", "VideoObject") })
        put("image", image.orEmpty())
        id?.let { put("id", it) }
    },
)

internal fun RemoteRecipe.renamed(name: String): RemoteRecipe = with("name", JsonPrimitive(name))

/** A synced recipe as the sync stores it. */
internal fun synced(name: String, remoteId: String, image: String? = null): LocalRecipe = LocalRecipe(
    remoteId = remoteId,
    base = recipe(name, remoteId, image),
    baseModified = "2024-01-01T10:00:00+0100",
    state = RecipeState.SYNCED,
    imageState = if (image == null) ImageState.NONE else ImageState.PRESENT,
)
