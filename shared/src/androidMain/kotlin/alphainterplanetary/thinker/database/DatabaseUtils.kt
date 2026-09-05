package alphainterplanetary.thinker.database

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase

private const val DATABASE_NAME = "alphathinker.db"

@Volatile
private var appContext: Context? = null

fun initDatabase(context: Context) {
  appContext = context.applicationContext
}

actual fun provideDatabaseBuilder(): RoomDatabase.Builder<AppDatabase> {
  val context = requireNotNull(appContext) {
    "initDatabase(context) must be called before accessing the database"
  }
  val dbFile = context.getDatabasePath(DATABASE_NAME)
  return Room.databaseBuilder<AppDatabase>(
    context = context,
    name = dbFile.absolutePath,
  )
}