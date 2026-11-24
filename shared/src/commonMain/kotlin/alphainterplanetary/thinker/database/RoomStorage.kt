package alphainterplanetary.thinker.database

import alphainterplanetary.thinker.engine.config.EngineInstance
import alphainterplanetary.thinker.engine.config.EngineRef
import alphainterplanetary.thinker.engine.config.EngineSelectionState
import alphainterplanetary.thinker.engine.core.CachedPhaseSummary
import alphainterplanetary.thinker.engine.core.PhaseSummaryIntent
import alphainterplanetary.thinker.model.ExportArtifact
import alphainterplanetary.thinker.model.Project
import alphainterplanetary.thinker.util.now
import androidx.room3.withWriteTransaction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import me.tatarka.inject.annotations.Inject

class RoomStorage @Inject constructor(private val database: AppDatabase) : Storage {
  override suspend fun saveProject(project: Project) {
    database.withWriteTransaction {
      database.projectDao().upsertProject(project.toEntity())

      project.rounds.forEach { round ->
        database.roundDao().upsertRound(round.toEntity())
      }

      project.questions.forEachIndexed { index, q ->
        database.questionDao().upsertQuestion(q.toEntity(project.id, index))
      }

      reconcileAggregateChildren(project)
    }
  }

  /**
   * Brings the questions, answers, and rounds tables back in line with the saved
   * aggregate: answers already stored (immutable history — see `Answer`) are left
   * untouched, only new versions are inserted, and any question/answer/round rows
   * not present in the aggregate are deleted so a projected question can never
   * orphan its rows.
   */
  private suspend fun reconcileAggregateChildren(project: Project) {
    val savedQuestionIds = project.questions.map { it.id }
    val savedAnswerIds = project.questions.flatMap { it.answers }.map { it.id }.toSet()

    val existingAnswerIds = database.answerDao()
      .getAnswersForQuestions(savedQuestionIds)
      .map { it.id }
      .toSet()

    project.questions
      .flatMap { it.answers }
      .filterNot { it.id in existingAnswerIds }
      .forEach { a ->
        database.answerDao().upsertAnswer(a.toEntity())
      }

    val existingQuestionIds = database.questionDao()
      .getQuestionsForProject(project.id)
      .map { it.id }
    val savedQuestionIdSet = savedQuestionIds.toSet()
    val orphanedQuestionIds = existingQuestionIds.filterNot { it in savedQuestionIdSet }
    if (orphanedQuestionIds.isNotEmpty()) {
      database.questionDao().deleteQuestionsByIds(orphanedQuestionIds)
    }

    val orphanedAnswerIds = existingAnswerIds.filterNot { it in savedAnswerIds }
    if (orphanedAnswerIds.isNotEmpty()) {
      database.answerDao().deleteAnswersByIds(orphanedAnswerIds)
    }

    val savedRoundIds = project.rounds.map { it.id }.toSet()
    val orphanedRoundIds = database.roundDao()
      .getRoundsForProject(project.id)
      .map { it.id }
      .filterNot { it in savedRoundIds }
    if (orphanedRoundIds.isNotEmpty()) {
      database.roundDao().deleteRoundsByIds(orphanedRoundIds)
    }
  }

  override suspend fun getProject(id: String): Project? {
    val data = database.projectDao().getProjectWithQuestions(id) ?: return null
    return data.toModel(
      database.answerDao().getAnswersForQuestions(data.questions.map { it.id }),
      database.roundDao().getRoundsForProject(id)
    )
  }

  override suspend fun getAllProjects(): List<Project> =
    database.projectDao().getAllProjectsWithQuestions().let { rows ->
      val answers = database.answerDao()
        .getAnswersForQuestions(rows.flatMap { row -> row.questions.map { it.id } })
      val roundsByProject = database.roundDao().getAllRounds().groupBy { it.projectId }
      return rows.map { row ->
        row.toModel(answers, roundsByProject[row.project.id].orEmpty())
      }
    }

  override suspend fun deleteProject(id: String) {
    database.projectDao().deleteProject(id)
  }

  override suspend fun deleteAllProjects() {
    database.projectDao().deleteAllProjects()
  }

  override suspend fun saveQuestionOrder(projectId: String, order: List<String>) {
    database.withWriteTransaction {
      database.questionDao().updateSortOrderForProject(projectId, order)
      database.projectDao().updateProjectUpdatedAt(projectId, now().toEpochMilliseconds())
    }
  }

  override suspend fun updateQuestionText(projectId: String, questionId: String, text: String) {
    val trimmed = text.trim()
    if (trimmed.isEmpty()) return
    database.withWriteTransaction {
      database.questionDao().updateQuestionText(questionId, projectId, trimmed)
      database.projectDao().updateProjectUpdatedAt(projectId, now().toEpochMilliseconds())
    }
  }

  override suspend fun getSetting(key: SettingsKey, default: String): String =
    database.settingsDao().getValue(key.storageKey) ?: default

  override suspend fun saveSetting(key: SettingsKey, value: String) {
    database.settingsDao().upsertValue(SettingsEntity(key = key.storageKey, value = value))
  }

  override suspend fun getEngineInstances(): List<EngineInstance> {
    val rows = database.engineInstanceDao().getAll()
    val instances = rows.mapNotNull { it.toModel() }
    // Drop rows whose kind no longer exists (a retired "on-device"/"downloaded"
    // row), so a stale schema can never surface again.
    val liveIds = instances.mapTo(mutableSetOf()) { it.id }
    rows.filterNot { it.id in liveIds }.forEach { database.engineInstanceDao().deleteById(it.id) }
    return instances
  }

  override suspend fun upsertEngineInstance(instance: EngineInstance) {
    database.engineInstanceDao().upsert(instance.toEntity())
  }

  override suspend fun deleteEngineInstance(id: String) {
    database.engineInstanceDao().deleteById(id)
  }

  override suspend fun getEngineSelectionStates(): List<EngineSelectionState> =
    database.engineSelectionStateDao().getAll().map { it.toModel() }

  override suspend fun upsertEngineSelectionState(state: EngineSelectionState) {
    database.engineSelectionStateDao().upsert(state.toEntity())
  }

  override suspend fun deleteEngineSelectionState(ref: EngineRef) {
    database.engineSelectionStateDao().deleteByKey(ref.storageKey)
  }

  override suspend fun setSelectedEngine(ref: EngineRef) {
    database.engineSelectionStateDao().setSelected(ref.storageKey)
  }

  override suspend fun saveExportArtifact(artifact: ExportArtifact) {
    database.exportArtifactDao().upsert(artifact.toEntity())
  }

  override suspend fun getExportArtifact(id: String): ExportArtifact? =
    database.exportArtifactDao().getById(id)?.toModel()

  override fun observeExportArtifacts(projectId: String): Flow<List<ExportArtifact>> =
    database.exportArtifactDao().observeForProject(projectId).map { rows -> rows.map { it.toModel() } }

  override suspend fun getExportArtifacts(projectId: String): List<ExportArtifact> =
    database.exportArtifactDao().getForProject(projectId).map { it.toModel() }

  override suspend fun deleteExportArtifact(id: String) {
    database.exportArtifactDao().deleteById(id)
  }

  override suspend fun saveCachedPhaseSummary(entry: CachedPhaseSummary) {
    // Keep at most one row per (revision, phase, intent, engine, content hash) by
    // replacing any existing match; a fresh id is fine for an immutable cache value.
    val existing = database.phaseSummaryCacheDao().getMatching(
      projectId = entry.projectId,
      projectRevisionEpochMs = entry.projectRevisionEpochMs,
      phaseKey = entry.phaseKey,
      intent = entry.intent.storageKey,
      engineIdentity = entry.engineIdentity,
      coveredContentHash = entry.coveredContentHash,
    )
    database.phaseSummaryCacheDao().upsert(entry.toEntity(id = existing?.id ?: alphainterplanetary.thinker.util.randomUUID()))
  }

  override suspend fun getCachedPhaseSummary(
    projectId: String,
    projectRevisionEpochMs: Long,
    phaseKey: String,
    intent: PhaseSummaryIntent,
    engineIdentity: String,
    coveredContentHash: String,
  ): CachedPhaseSummary? =
    database.phaseSummaryCacheDao().getMatching(
      projectId = projectId,
      projectRevisionEpochMs = projectRevisionEpochMs,
      phaseKey = phaseKey,
      intent = intent.storageKey,
      engineIdentity = engineIdentity,
      coveredContentHash = coveredContentHash,
    )?.toModel()

  override suspend fun getCachedPhaseSummariesForRevision(
    projectId: String,
    projectRevisionEpochMs: Long,
    intent: PhaseSummaryIntent,
    engineIdentity: String,
  ): List<CachedPhaseSummary> =
    database.phaseSummaryCacheDao().getForRevision(
      projectId = projectId,
      projectRevisionEpochMs = projectRevisionEpochMs,
      intent = intent.storageKey,
      engineIdentity = engineIdentity,
    ).mapNotNull { it.toModel() }

  override suspend fun deleteStalePhaseSummaryCache(projectId: String, currentRevisionEpochMs: Long) {
    database.phaseSummaryCacheDao().deleteStaleRevisions(projectId, currentRevisionEpochMs)
  }

  override suspend fun deletePhaseSummaryCacheForProject(projectId: String) {
    database.phaseSummaryCacheDao().deleteForProject(projectId)
  }
}
