package alphainterplanetary.thinker.database

import alphainterplanetary.thinker.di.IosPlatformContext
import alphainterplanetary.thinker.di.PlatformContext
import androidx.room3.Room
import androidx.room3.RoomDatabase
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSUserDomainMask

private const val DATABASE_NAME = "alphathinker.db"

private fun dbPath(): String {
  val dir = NSFileManager.defaultManager.URLsForDirectory(
    NSDocumentDirectory,
    NSUserDomainMask,
  ).first() as? platform.Foundation.NSURL ?: error("Could not resolve Documents directory")
  return dir.path + "/$DATABASE_NAME"
}

actual fun provideDatabaseBuilder(context: PlatformContext): RoomDatabase.Builder<AppDatabase> {
  return Room.databaseBuilder<AppDatabase>(
    name = dbPath(),
    factory = { AppDatabaseConstructor.initialize() },
  ).setDriver(BundledSQLiteDriver())
}

actual fun provideStorage(context: PlatformContext): Storage {
  return RoomStorage(getRoomDatabase(provideDatabaseBuilder(context)))
}
