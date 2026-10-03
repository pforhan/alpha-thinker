package alphainterplanetary.thinker.database

import androidx.room3.Dao
import androidx.room3.Query
import androidx.room3.Upsert

@Dao
interface RoundDao {
  @Upsert
  suspend fun upsertRound(round: RoundEntity): Long

  @Query("SELECT * FROM rounds WHERE projectId = :projectId ORDER BY roundNumber ASC")
  suspend fun getRoundsForProject(projectId: String): List<RoundEntity>

  @Query("SELECT * FROM rounds ORDER BY roundNumber ASC")
  suspend fun getAllRounds(): List<RoundEntity>

  @Query("DELETE FROM rounds WHERE id IN (:roundIds)")
  suspend fun deleteRoundsByIds(roundIds: List<String>)
}
