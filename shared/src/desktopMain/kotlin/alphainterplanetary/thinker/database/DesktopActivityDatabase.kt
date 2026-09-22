package alphainterplanetary.thinker.database

import alphainterplanetary.thinker.di.PlatformContext
import androidx.room3.Room
import androidx.room3.RoomDatabase
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import java.io.File

private const val ACTIVITY_DATABASE_NAME = "alphathinker-activity.db"

private fun activityDbFile(): File {
  val dir = File(System.getProperty("user.home"), ".alphathinker")
  dir.mkdirs()
  return File(dir, ACTIVITY_DATABASE_NAME)
}

actual fun provideActivityDatabaseBuilder(
  context: PlatformContext,
): RoomDatabase.Builder<ActivityDatabase> {
  return Room.databaseBuilder<ActivityDatabase>(
    name = activityDbFile().absolutePath,
    factory = { ActivityDatabaseConstructor.initialize() }
  ).setDriver(BundledSQLiteDriver())
}