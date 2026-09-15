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

/**
 * Questions keep a denormalized [projectId] to serve the project-centric flat
 * list/order queries directly, but ownership is strict Project -> Round ->
 * Question: the composite key ([projectId], [roundId]) enforces that a
 * question's round belongs to the same project the question claims to belong
 * to, and that cascades Project -> Round -> Question on delete.
 */
@Entity(
  tableName = "questions",
  foreignKeys = [
    ForeignKey(
      entity = RoundEntity::class,
      parentColumns = ["projectId", "id"],
      childColumns = ["projectId", "roundId"],
      onDelete = ForeignKey.CASCADE
    ),
  ],
  indices = [Index("projectId", "roundId")]
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
  indices = [Index("projectId"), Index(value = ["projectId", "id"], unique = true)]
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

/** App-wide settings persisted as simple key/value rows. */
@Entity(tableName = "settings")
data class SettingsEntity(
  @PrimaryKey val key: String,
  val value: String,
)

data class ProjectWithQuestions(
  @Embedded val project: ProjectEntity,
  @Relation(
    parentColumns = ["id"],
    entityColumns = ["projectId"]
  )
  val questions: List<QuestionEntity>,
)