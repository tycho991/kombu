package io.github.tycho991.kombu.data.recipes

import androidx.room.testing.MigrationTestHelper
import io.github.tycho991.kombu.data.recipes.db.KombuDatabase
import io.github.tycho991.kombu.data.recipes.db.RECIPE_DATABASE_NAME
import io.github.tycho991.kombu.data.recipes.db.toEntity
import io.github.tycho991.kombu.data.recipes.db.toModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class RecipeDatabaseTest {
    private val database = inMemoryDatabase()
    private val recipes = database.recipeDao()

    @get:Rule
    val migrations =
        MigrationTestHelper(
            androidx.test.platform.app.InstrumentationRegistry.getInstrumentation(),
            KombuDatabase::class.java,
        )

    @After
    fun close() {
        database.close()
    }

    private val conflict =
        synced("Stew", "3").copy(
            local = recipe("Stew", "3").renamed("Local stew"),
            state = RecipeState.CONFLICT,
            conflict = ConflictKind.BOTH_CHANGED,
            server = recipe("Stew", "3").renamed("Server stew"),
            serverModified = "2024-02-01T10:00:00+0100",
        )

    private val everyState =
        listOf(
            synced("Soup", "1", image = "https://example.org/soup.jpg"),
            LocalRecipe(local = recipe("New"), state = RecipeState.CREATED, localChangeAt = 5),
            synced("Bread", "2").let { it.copy(local = it.base?.renamed("Rye bread"), state = RecipeState.CHANGED) },
            synced("Gone", "4").copy(state = RecipeState.DELETED, localChangeAt = 7),
            conflict,
            LocalRecipe(
                local = recipe("Taken"),
                state = RecipeState.CREATED,
                uploadError = UploadError.NAME_TAKEN,
                imageState = ImageState.MISSING,
            ),
        )

    @Test
    fun storesAndReadsEveryStateUnchanged() = runTest {
        everyState.forEach { recipe ->
            val localId = recipes.insert(recipe.toEntity())

            assertEquals(recipe.copy(localId = localId), recipes.byLocalId(localId)?.toModel())
        }
    }

    @Test
    fun updatesARow() = runTest {
        val localId = recipes.insert(synced("Soup", "1").toEntity())
        val changed = synced("Soup", "1").copy(localId = localId, local = recipe("Soup").renamed("Better soup"))

        recipes.update(changed.copy(state = RecipeState.CHANGED).toEntity())

        val stored = recipes.byLocalId(localId)
        assertEquals(RecipeState.CHANGED, stored?.state)
        assertEquals("Better soup", stored?.name)
        assertEquals(changed.baseHash, stored?.baseHash)
    }

    @Test
    fun extractsNameCategoryAndKeywordsFromTheShownVersion() = runTest {
        val localId = recipes.insert(conflict.toEntity())

        val stored = recipes.byLocalId(localId)
        assertEquals("Local stew", stored?.name)
        assertEquals("Soups", stored?.category)
        assertEquals("quick,cheap", stored?.keywords)
    }

    @Test
    fun countsRecipesPendingChangesAndConflicts() = runTest {
        everyState.forEach { recipes.insert(it.toEntity()) }

        assertEquals(5, recipes.countVisible().first())
        assertEquals(4, recipes.countPending().first())
        assertEquals(1, recipes.countConflicts().first())
    }

    @Test
    fun hidesRecipesDeletedHereFromTheListAndSortsByName() = runTest {
        everyState.forEach { recipes.insert(it.toEntity()) }

        val names = recipes.visible().first().map { it.name }

        assertEquals(listOf("Local stew", "New", "Rye bread", "Soup", "Taken"), names)
    }

    @Test
    fun deletesRows() = runTest {
        val first = recipes.insert(synced("Soup", "1").toEntity())
        recipes.insert(synced("Bread", "2").toEntity())

        recipes.delete(first)
        assertNull(recipes.byLocalId(first))
        recipes.deleteAll()
        assertEquals(emptyList<Any>(), recipes.all())
    }

    @Test
    fun matchesTheExportedSchema() {
        val name = "schema-test.db"
        migrations.createDatabase(name, 1).close()

        migrations.runMigrationsAndValidate(name, 1, true).close()
        RuntimeEnvironment.getApplication().deleteDatabase(name)
        assertEquals("recipes.db", RECIPE_DATABASE_NAME)
    }
}
