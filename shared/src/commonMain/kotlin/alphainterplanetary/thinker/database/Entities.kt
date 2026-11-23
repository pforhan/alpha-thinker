package alphainterplanetary.thinker.database

import alphainterplanetary.thinker.model.RoundOutcome
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
  val title: String,
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
  val answerState: String,
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
  val outcome: String = RoundOutcome.Pending.name,
  val outcomeDetail: String? = null,
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

/**
 * One user-created engine (a Remote connection).
 * Code-defined built-ins have no row here; they are described by the catalog.
 * See [alphainterplanetary.thinker.engine.config.EngineInstance].
 */
@Entity(tableName = "engine_instances")
data class EngineInstanceEntity(
  @PrimaryKey val id: String,
  val kind: String,
  val provider: String?,
  val label: String,
  val baseUrl: String,
  val apiKey: String,
  val model: String,
  val contextTokens: Int,
)

/**
 * One engine's selection/usage state, keyed by the ref's durable string. A row
 * exists only once an engine has been selected or used. See
 * [alphainterplanetary.thinker.engine.config.EngineSelectionState].
 */
@Entity(tableName = "engine_selection_state")
data class EngineSelectionStateEntity(
  @PrimaryKey val refKey: String,
  val selected: Boolean,
  val lastUsedAt: Long?,
)

/**
 * A persisted project export: metadata and canonical managed-file reference.
 * [configJson] is the serialized [alphainterplanetary.thinker.model.ExportConfig]
 * snapshot used to produce the export. Rendered content is stored in the managed
 * export file identified by [storageRef] (a storage-relative ref resolved at
 * runtime), not in this row.
 */
@Entity(
  tableName = "export_artifacts",
  foreignKeys = [ForeignKey(
    entity = ProjectEntity::class,
    parentColumns = ["id"],
    childColumns = ["projectId"],
    onDelete = ForeignKey.CASCADE,
  )],
  indices = [Index("projectId"), Index("createdAt")],
)
data class ExportArtifactEntity(
  @PrimaryKey val id: String,
  val projectId: String,
  val createdAt: Long,
  val filename: String,
  val format: String,
  val configJson: String,
  val status: String,
  val storageRef: String? = null,
  val error: String? = null,
  val completedAt: Long? = null,
)

data class ProjectWithQuestions(
  @Embedded val project: ProjectEntity,
  @Relation(
    parentColumns = ["id"],
    entityColumns = ["projectId"]
  )
  val questions: List<QuestionEntity>,
)

/**
 * Cached per-phase summary, keyed by project revision, phase, summary intent/kind,
 * and engine/model identity so expensive summarization can be reused across
 * export, compaction, and future background generation.
 *
 * [coveredContentHash] fingerprints the covered transcript/content; a cache hit
 * is valid only when it matches the current covered content. [coveredQuestionIdsJson]
 * records the question ids the summary stands in for (provenance / [SummarizedPhase]).
 */
@Entity(
  tableName = "phase_summary_cache",
  foreignKeys = [ForeignKey(
    entity = ProjectEntity::class,
    parentColumns = ["id"],
    childColumns = ["projectId"],
    onDelete = ForeignKey.CASCADE,
  )],
  indices = [
    Index("projectId"),
    Index(
      value = [
        "projectId",
        "projectRevisionEpochMs",
        "phaseKey",
        "intent",
        "engineIdentity",
      ],
    ),
  ],
)
data class PhaseSummaryCacheEntity(
  @PrimaryKey val id: String,
  val projectId: String,
  val projectRevisionEpochMs: Long,
  val phaseKey: String,
  val intent: String,
  val engineIdentity: String,
  val coveredContentHash: String,
  val coveredQuestionIdsJson: String,
  val summaryText: String,
  val createdAtEpochMs: Long,
)
