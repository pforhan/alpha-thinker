package alphainterplanetary.thinker.database

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation

@Entity(tableName = "projects")
data class ProjectEntity(
  @PrimaryKey val id: String,
  val synopsis: String,
  val editableTitle: String,
  val createdAt: Long,
  val updatedAt: Long,
  val status: String,
)

@Entity(
  tableName = "questions",
  foreignKeys = [ForeignKey(
    entity = ProjectEntity::class,
    parentColumns = ["id"],
    childColumns = ["projectId"],
    onDelete = ForeignKey.CASCADE
  )],
  indices = [Index("projectId")]
)
data class QuestionEntity(
  @PrimaryKey val id: String,
  val projectId: String,
  val text: String,
  val contextId: String = "",
  val createdAt: Long,
  val sortOrder: Int = 0,
  val ignoredAt: Long? = null,
)

@Entity(
  tableName = "answers",
  foreignKeys = [ForeignKey(
    entity = QuestionEntity::class,
    parentColumns = ["id"],
    childColumns = ["questionId"],
    onDelete = ForeignKey.CASCADE
  )],
  indices = [Index("questionId")]
)
data class AnswerEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val questionId: String,
  val text: String,
  val answeredAt: Long? = null,
  val modifiedAt: Long? = null,
  val deletedAt: Long? = null,
)

data class ProjectWithQuestions(
  @Embedded val project: ProjectEntity,
  @Relation(
    parentColumn = "id",
    entityColumn = "projectId"
  )
  val questions: List<QuestionEntity>,
)
