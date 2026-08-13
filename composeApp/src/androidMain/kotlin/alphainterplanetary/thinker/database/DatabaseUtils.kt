package alphainterplanetary.thinker.database

import alphainterplanetary.thinker.database.AppDatabase
import androidx.room.RoomDatabaseConstructor

actual object AppDatabaseConstructor : RoomDatabaseConstructor<AppDatabase> {
  override fun initialize(): AppDatabase = throw NotImplementedError()
}
