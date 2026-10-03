package alphainterplanetary.thinker.database

import androidx.room3.Dao
import androidx.room3.Query
import androidx.room3.Upsert

@Dao
interface SettingsDao {
  @Query("SELECT value FROM settings WHERE `key` = :key")
  suspend fun getValue(key: String): String?

  @Upsert
  suspend fun upsertValue(entry: SettingsEntity)
}
