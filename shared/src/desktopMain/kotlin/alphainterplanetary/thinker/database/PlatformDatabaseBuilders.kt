package alphainterplanetary.thinker.database

import alphainterplanetary.thinker.di.PlatformContext
import androidx.room3.Room
import androidx.room3.RoomDatabase


actual fun provideDatabaseBuilder(context: PlatformContext): RoomDatabase.Builder<AppDatabase> =
  Room.databaseBuilder<AppDatabase>(
    name = context.databaseFile(DATABASE_NAME),
    factory = { AppDatabaseConstructor.initialize() },
  ).setDriver(sqliteDriver())

actual fun provideActivityDatabaseBuilder(
  context: PlatformContext,
): RoomDatabase.Builder<ActivityDatabase> =
  Room.databaseBuilder<ActivityDatabase>(
    name = context.databaseFile(ACTIVITY_DATABASE_NAME),
    factory = { ActivityDatabaseConstructor.initialize() },
  ).setDriver(sqliteDriver())
