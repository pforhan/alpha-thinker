package alphainterplanetary.thinker.database

import androidx.room3.Dao
import androidx.room3.Query
import androidx.room3.Upsert

/**
 * Persistence for user-created engine instances. Rows are addressed by id, and
 * only the repository's own ordering matters, so reads are unordered.
 */
@Dao
interface EngineInstanceDao {
  @Query("SELECT * FROM engine_instances")
  suspend fun getAll(): List<EngineInstanceEntity>

  @Upsert
  suspend fun upsert(instance: EngineInstanceEntity)

  @Query("DELETE FROM engine_instances WHERE id = :id")
  suspend fun deleteById(id: String)
}
