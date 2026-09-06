package alphainterplanetary.thinker.database

import alphainterplanetary.thinker.di.AndroidPlatformContext
import alphainterplanetary.thinker.di.PlatformContext
import androidx.room.Room
import androidx.room.RoomDatabase

private const val DATABASE_NAME = "alphathinker.db"

actual fun provideDatabaseBuilder(context: PlatformContext): RoomDatabase.Builder<AppDatabase> {
  val androidContext = (context as AndroidPlatformContext).context
  val dbFile = androidContext.getDatabasePath(DATABASE_NAME)
  return Room.databaseBuilder<AppDatabase>(
    context = androidContext,
    name = dbFile.absolutePath,
  )
}

actual fun provideStorage(context: PlatformContext): Storage {
  return RoomStorage(getRoomDatabase(provideDatabaseBuilder(context)))
}