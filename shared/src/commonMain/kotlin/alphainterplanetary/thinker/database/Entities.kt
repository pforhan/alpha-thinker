package alphainterplanetary.thinker.database

import androidx.room3.Embedded
import androidx.room3.Entity
import androidx.room3.ForeignKey
import androidx.room3.Index
import androidx.room3.PrimaryKey
import androidx.room3.Relation

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
    parentColumns = ["id"],
    entityColumns = ["projectId"]
  )
  val questions: List<QuestionEntity>,
)