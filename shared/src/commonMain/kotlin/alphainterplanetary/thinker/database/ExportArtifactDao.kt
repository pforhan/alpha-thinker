package alphainterplanetary.thinker.database

import androidx.room3.Dao
import androidx.room3.Query
import androidx.room3.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface ExportArtifactDao {
  @Upsert
  suspend fun upsert(artifact: ExportArtifactEntity)

  @Query("SELECT * FROM export_artifacts WHERE id = :id")
  suspend fun getById(id: String): ExportArtifactEntity?

  @Query("SELECT * FROM export_artifacts WHERE projectId = :projectId ORDER BY createdAt DESC")
  fun observeForProject(projectId: String): Flow<List<ExportArtifactEntity>>

  @Query("SELECT * FROM export_artifacts WHERE projectId = :projectId ORDER BY createdAt DESC")
  suspend fun getForProject(projectId: String): List<ExportArtifactEntity>

  @Query("DELETE FROM export_artifacts WHERE id = :id")
  suspend fun deleteById(id: String)

  @Query("DELETE FROM export_artifacts WHERE projectId = :projectId")
  suspend fun deleteForProject(projectId: String)
}
