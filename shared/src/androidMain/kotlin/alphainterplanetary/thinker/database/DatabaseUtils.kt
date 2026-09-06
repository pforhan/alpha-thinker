package alphainterplanetary.thinker.database

import alphainterplanetary.thinker.di.AndroidPlatformContext
import alphainterplanetary.thinker.di.PlatformContext
import androidx.room3.Room
import androidx.room3.RoomDatabase
import androidx.sqlite.driver.bundled.BundledSQLiteDriver

private const val DATABASE_NAME = "alphathinker.db"

actual fun provideDatabaseBuilder(context: PlatformContext): RoomDatabase.Builder<AppDatabase> {
  val androidContext = (context as AndroidPlatformContext).context
  val dbFile = androidContext.getDatabasePath(DATABASE_NAME)
  return Room.databaseBuilder<AppDatabase>(
    name = dbFile.absolutePath,
    factory = { AppDatabaseConstructor.initialize() }
  ).setDriver(BundledSQLiteDriver())
}

actual fun provideStorage(context: PlatformContext): Storage {
  return RoomStorage(getRoomDatabase(provideDatabaseBuilder(context)))
}