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
  foreignKeys = [
    ForeignKey(
      entity = ProjectEntity::class,
      parentColumns = ["id"],
      childColumns = ["projectId"],
      onDelete = ForeignKey.CASCADE
    ),
    ForeignKey(
      entity = RoundEntity::class,
      parentColumns = ["id"],
      childColumns = ["roundId"],
      onDelete = ForeignKey.CASCADE
    ),
  ],
  indices = [Index("projectId"), Index("roundId")]
)
data class QuestionEntity(
  @PrimaryKey val id: String,
  val projectId: String,
  val text: String,
  val roundId: String,
  val createdAt: Long,
  val sortOrder: Int = 0,
  val ignoredAt: Long? = null,
  val answerId: String? = null,
  val draftText: String? = null,
  val draftUpdatedAt: Long? = null,
)

@Entity(
  tableName = "rounds",
  foreignKeys = [ForeignKey(
    entity = ProjectEntity::class,
    parentColumns = ["id"],
    childColumns = ["projectId"],
    onDelete = ForeignKey.CASCADE
  )],
  indices = [Index("projectId")]
)
data class RoundEntity(
  @PrimaryKey val id: String,
  val projectId: String,
  val phase: String,
  val roundNumber: Int,
  val origin: String,
  val startedAt: Long,
  val completedAt: Long? = null,
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
  @PrimaryKey val id: String = "",
  val questionId: String,
  val text: String,
  val createdAt: Long,
)

data class ProjectWithQuestions(
  @Embedded val project: ProjectEntity,
  @Relation(
    parentColumns = ["id"],
    entityColumns = ["projectId"]
  )
  val questions: List<QuestionEntity>,
)