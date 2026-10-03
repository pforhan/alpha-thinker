package alphainterplanetary.thinker.database

import alphainterplanetary.thinker.di.PlatformContext
import androidx.room3.RoomDatabase

internal const val DATABASE_NAME = "alphathinker.db"

/**
 * The app database's Room builder. Only the [Room.databaseBuilder] call itself
 * is per-target; where the file lives ([PlatformContext.databaseFile]) and the
 * driver ([sqliteDriver]) are the target's two answers, both shared.
 */
expect fun provideDatabaseBuilder(context: PlatformContext): RoomDatabase.Builder<AppDatabase>