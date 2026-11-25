package io.github.tycho991.kombu.data.recipes.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/** Queries on the `recipes` table. */
@Dao
internal interface RecipeDao {
    @Insert
    suspend fun insert(recipe: RecipeEntity): Long

    @Update
    suspend fun update(recipe: RecipeEntity)

    @Query("DELETE FROM recipes WHERE localId = :localId")
    suspend fun delete(localId: Long)

    @Query("DELETE FROM recipes")
    suspend fun deleteAll()

    @Query("SELECT * FROM recipes WHERE localId = :localId")
    suspend fun byLocalId(localId: Long): RecipeEntity?

    @Query("SELECT * FROM recipes WHERE localId = :localId")
    fun observe(localId: Long): Flow<RecipeEntity?>

    @Query("SELECT * FROM recipes ORDER BY localId")
    suspend fun all(): List<RecipeEntity>

    /** Every recipe the app lists: all but the ones deleted here, by name. */
    @Query("SELECT * FROM recipes WHERE state != 'DELETED' ORDER BY name COLLATE NOCASE, localId")
    fun visible(): Flow<List<RecipeEntity>>

    @Query("SELECT COUNT(*) FROM recipes WHERE state != 'DELETED'")
    fun countVisible(): Flow<Int>

    @Query("SELECT COUNT(*) FROM recipes WHERE state IN ('CREATED', 'CHANGED', 'DELETED')")
    fun countPending(): Flow<Int>

    @Query("SELECT COUNT(*) FROM recipes WHERE state = 'CONFLICT'")
    fun countConflicts(): Flow<Int>
}

/** Advances the local change counter, creating the status row if needed; call it inside the change's transaction. */
internal suspend fun SyncStatusDao.bumpLocalChangeSeq() {
    if (incrementLocalChangeSeq() == 0) put(SyncStatusEntity(localChangeSeq = 1))
}

/** Queries on the single-row `sync_status` table. */
@Dao
internal interface SyncStatusDao {
    @Query("SELECT * FROM sync_status WHERE id = 0")
    suspend fun get(): SyncStatusEntity?

    @Query("SELECT * FROM sync_status WHERE id = 0")
    fun observe(): Flow<SyncStatusEntity?>

    @Upsert
    suspend fun put(status: SyncStatusEntity)

    /** Advances the local change counter; call it inside the transaction of the change (design D5). */
    @Query("UPDATE sync_status SET localChangeSeq = localChangeSeq + 1 WHERE id = 0")
    suspend fun incrementLocalChangeSeq(): Int

    @Query("DELETE FROM sync_status")
    suspend fun deleteAll()
}
