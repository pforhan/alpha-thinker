package alphainterplanetary.thinker.database

import alphainterplanetary.thinker.di.PlatformContext
import androidx.room3.Room
import androidx.room3.RoomDatabase
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSUserDomainMask

private const val ACTIVITY_DATABASE_NAME = "alphathinker-activity.db"

private fun activityDbPath(): String {
  val dir = NSFileManager.defaultManager.URLsForDirectory(
    NSDocumentDirectory,
    NSUserDomainMask,
  ).first() as? platform.Foundation.NSURL ?: error("Could not resolve Documents directory")
  return dir.path + "/$ACTIVITY_DATABASE_NAME"
}

actual fun provideActivityDatabaseBuilder(
  context: PlatformContext,
): RoomDatabase.Builder<ActivityDatabase> {
  return Room.databaseBuilder<ActivityDatabase>(
    name = activityDbPath(),
    factory = { ActivityDatabaseConstructor.initialize() },
  ).setDriver(BundledSQLiteDriver())
}