package alphainterplanetary.thinker.database

import alphainterplanetary.thinker.di.DesktopPlatformContext
import alphainterplanetary.thinker.di.PlatformContext
import androidx.room3.Room
import androidx.room3.RoomDatabase
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import java.io.File

private const val DATABASE_NAME = "alphathinker.db"

private fun dbFile(): File {
  val dir = File(System.getProperty("user.home"), ".alphathinker")
  dir.mkdirs()
  return File(dir, DATABASE_NAME)
}

actual fun provideDatabaseBuilder(context: PlatformContext): RoomDatabase.Builder<AppDatabase> {
  return Room.databaseBuilder<AppDatabase>(
    name = dbFile().absolutePath,
    factory = { AppDatabaseConstructor.initialize() }
  ).setDriver(BundledSQLiteDriver())
}

actual fun provideStorage(context: PlatformContext): Storage {
  return RoomStorage(getRoomDatabase(provideDatabaseBuilder(context)))
}
