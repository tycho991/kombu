package io.github.tycho991.kombu.data.recipes.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/** File name of the recipe database; the app's backup rules exclude it (spec "Local recipe data stays here"). */
const val RECIPE_DATABASE_NAME = "recipes.db"

/**
 * The local recipe copy (design D4). Version 1; the schema is exported to `data/schemas`. There is no destructive
 * migration fallback, because the database holds changes that are not on the server yet.
 */
@Database(entities = [RecipeEntity::class, SyncStatusEntity::class], version = 1, exportSchema = true)
abstract class KombuDatabase : RoomDatabase() {
    internal abstract fun recipeDao(): RecipeDao

    internal abstract fun syncStatusDao(): SyncStatusDao

    /** Opens the database file of the app. */
    companion object {
        /** Opens the recipe database in the app's database directory. */
        fun open(context: Context): KombuDatabase =
            Room.databaseBuilder(context.applicationContext, KombuDatabase::class.java, RECIPE_DATABASE_NAME).build()
    }
}
