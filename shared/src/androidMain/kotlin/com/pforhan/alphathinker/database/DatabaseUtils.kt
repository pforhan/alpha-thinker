package com.pforhan.alphathinker.database

import androidx.room.RoomDatabaseConstructor

actual object AppDatabaseConstructor : RoomDatabaseConstructor<AppDatabase> {
  override fun initialize(): AppDatabase = throw NotImplementedError()
}
