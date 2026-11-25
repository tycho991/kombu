package io.github.tycho991.kombu.sync

import io.github.tycho991.kombu.data.recipes.ConflictKind
import io.github.tycho991.kombu.data.recipes.LocalRecipe
import io.github.tycho991.kombu.data.recipes.RecipeState
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/**
 * Task 5.4: every local state against every server situation (design D15). For a recipe created here, the
 * server situation applies to another recipe, because the created one has no server counterpart yet.
 */
@RunWith(Parameterized::class)
class StateTableTest(private val local: LocalState, private val situation: ServerSituation) : SyncTestBase() {
    enum class LocalState { SYNCED, CREATED, CHANGED, DELETED, CONFLICT }

    enum class ServerSituation { UNCHANGED, CHANGED, DELETED, CHANGED_WITHOUT_DATE }

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0} x {1}")
        fun cases(): List<Array<Any>> = LocalState.entries.flatMap { local ->
            ServerSituation.entries.map { arrayOf(local, it) }
        }
    }

    private lateinit var id: String
    private var row: LocalRecipe? = null

    private val name get() = server.stored[id]?.get("name")?.toString()?.trim('"')

    @Test
    fun endsInTheExpectedState() = runTest {
        id = server.add(FakeCookbookServer.recipe("Soup"), dated = situation != ServerSituation.CHANGED_WITHOUT_DATE)
        sync()
        val start = checkNotNull(store.byRemoteId(id))
        val created = prepareLocal(start)
        applyServer()

        sync()

        val createdId = created?.localId
        row = if (createdId != null) store.all.firstOrNull { it.localId == createdId } else store.byRemoteId(id)
        verify()
    }

    private suspend fun prepareLocal(start: LocalRecipe): LocalRecipe? = when (local) {
        LocalState.SYNCED -> null

        LocalState.CREATED -> store.create(remote("Bread"))

        LocalState.CHANGED -> store.edit(start.localId) { it.renamed("My soup") }

        LocalState.DELETED -> {
            store.delete(start.localId)
            null
        }

        LocalState.CONFLICT -> {
            store.edit(start.localId) { it.renamed("My soup") }
            server.edit(id) { it.renamed("Their soup") }
            sync()
            check(store.byRemoteId(id)?.state == RecipeState.CONFLICT)
            null
        }
    }

    private fun applyServer() {
        when (situation) {
            ServerSituation.UNCHANGED -> Unit

            ServerSituation.CHANGED, ServerSituation.CHANGED_WITHOUT_DATE -> server.edit(
                id,
            ) { it.renamed("Server soup") }

            ServerSituation.DELETED -> server.delete(id)
        }
    }

    private val serverChanged get() = situation == ServerSituation.CHANGED ||
        situation == ServerSituation.CHANGED_WITHOUT_DATE

    private fun verify() {
        when (local) {
            LocalState.SYNCED -> verifySynced()

            LocalState.CREATED -> {
                assertEquals(RecipeState.SYNCED, row?.state)
                assertSameContent(checkNotNull(row?.remoteId), row)
            }

            LocalState.CHANGED -> verifyChanged()

            LocalState.DELETED -> verifyDeleted()

            LocalState.CONFLICT -> verifyConflict()
        }
    }

    private fun verifySynced() {
        if (situation == ServerSituation.DELETED) {
            assertNull(row)
        } else {
            assertEquals(RecipeState.SYNCED, row?.state)
            assertSameContent(id, row)
        }
    }

    private fun verifyChanged() {
        when {
            situation == ServerSituation.DELETED -> assertEquals(ConflictKind.DELETED_ON_SERVER, row?.conflict)

            serverChanged -> {
                assertEquals(ConflictKind.BOTH_CHANGED, row?.conflict)
                assertEquals("Server soup", name)
                assertEquals("My soup", row?.local?.name)
            }

            else -> {
                assertEquals(RecipeState.SYNCED, row?.state)
                assertEquals("My soup", name)
            }
        }
    }

    private fun verifyDeleted() {
        if (serverChanged) {
            assertEquals(ConflictKind.CHANGED_ON_SERVER_DELETED_HERE, row?.conflict)
            assertEquals("Server soup", name)
        } else {
            assertNull(row)
            assertTrue(id !in server.stored)
        }
    }

    private fun verifyConflict() {
        assertEquals("My soup", row?.local?.name)
        when (situation) {
            ServerSituation.DELETED -> {
                assertEquals(ConflictKind.DELETED_ON_SERVER, row?.conflict)
                assertNull(row?.server)
            }

            ServerSituation.UNCHANGED -> assertEquals("Their soup", row?.server?.name)

            else -> assertEquals("Server soup", row?.server?.name)
        }
        if (situation != ServerSituation.DELETED) assertEquals(ConflictKind.BOTH_CHANGED, row?.conflict)
    }
}
