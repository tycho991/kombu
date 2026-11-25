package io.github.tycho991.kombu.data.recipes.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import io.github.tycho991.kombu.data.recipes.ConflictKind
import io.github.tycho991.kombu.data.recipes.ImageState
import io.github.tycho991.kombu.data.recipes.RecipeState
import io.github.tycho991.kombu.data.recipes.UploadError

/** Row of the `recipes` table (design D4); JSON columns hold the recipe text exactly as it is sent and received. */
@Entity(
    tableName = "recipes",
    indices = [Index(value = ["remoteId"], unique = true), Index(value = ["state"])],
)
internal data class RecipeEntity(
    @PrimaryKey(autoGenerate = true) val localId: Long = 0,
    val remoteId: String?,
    val baseJson: String?,
    val baseHash: String?,
    val baseModified: String?,
    val localJson: String?,
    val state: RecipeState,
    val conflict: ConflictKind?,
    val serverJson: String?,
    val serverModified: String?,
    val uploadError: UploadError?,
    /** The shown name, extracted for lists and sorting; empty when the recipe has none. */
    val name: String,
    val category: String?,
    /** The shown keywords, comma-separated, for later filters. */
    val keywords: String,
    val imageState: ImageState,
    val localChangeAt: Long?,
)

/** The single row of the `sync_status` table. */
@Entity(tableName = "sync_status")
internal data class SyncStatusEntity(
    @PrimaryKey val id: Int = SINGLE_ROW,
    val lastSuccessAt: Long? = null,
    val problem: ProblemKind? = null,
    val problemDetail: String? = null,
    val credentialsRejected: Boolean = false,
    val localChangeSeq: Long = 0,
) {
    companion object {
        const val SINGLE_ROW = 0
    }
}

/** Stored form of [io.github.tycho991.kombu.data.recipes.SyncProblem]; the detail goes into its own column. */
internal enum class ProblemKind {
    OFFLINE,
    SERVER_ERROR,
    CREDENTIALS_REJECTED,
    COOKBOOK_MISSING,
    COOKBOOK_TOO_OLD,
}
