package alphainterplanetary.thinker.database

import alphainterplanetary.thinker.di.AndroidPlatformContext
import alphainterplanetary.thinker.di.PlatformContext
import androidx.room3.Room
import androidx.room3.RoomDatabase
import androidx.sqlite.driver.bundled.BundledSQLiteDriver

private const val ACTIVITY_DATABASE_NAME = "alphathinker-activity.db"

actual fun provideActivityDatabaseBuilder(
  context: PlatformContext,
): RoomDatabase.Builder<ActivityDatabase> {
  val androidContext = (context as AndroidPlatformContext).context
  val dbFile = androidContext.getDatabasePath(ACTIVITY_DATABASE_NAME)
  return Room.databaseBuilder<ActivityDatabase>(
    name = dbFile.absolutePath,
    factory = { ActivityDatabaseConstructor.initialize() }
  ).setDriver(BundledSQLiteDriver())
}