package alphainterplanetary.thinker.database

import androidx.room3.Dao
import androidx.room3.Query
import androidx.room3.Transaction
import androidx.room3.Upsert

@Dao
interface ProjectDao {
  @Upsert
  suspend fun upsertProject(project: ProjectEntity): Long

  @Transaction
  @Query("SELECT * FROM projects WHERE id = :id")
  suspend fun getProjectWithQuestions(id: String): ProjectWithQuestions?

  @Transaction
  @Query("SELECT * FROM projects")
  suspend fun getAllProjectsWithQuestions(): List<ProjectWithQuestions>

  @Query("DELETE FROM projects WHERE id = :id")
  suspend fun deleteProject(id: String)

  @Query("DELETE FROM projects")
  suspend fun deleteAllProjects()

  @Query("UPDATE projects SET updatedAt = :updatedAt WHERE id = :projectId")
  suspend fun updateProjectUpdatedAt(projectId: String, updatedAt: Long)
}
