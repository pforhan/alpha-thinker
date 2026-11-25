package alphainterplanetary.thinker.export

import alphainterplanetary.thinker.model.ANSWER_NEWEST_FIRST
import alphainterplanetary.thinker.model.Answer
import alphainterplanetary.thinker.model.AnswerState
import alphainterplanetary.thinker.model.Project
import alphainterplanetary.thinker.model.Question
import alphainterplanetary.thinker.model.Round
import alphainterplanetary.thinker.model.RoundOrigin
import alphainterplanetary.thinker.model.RoundOutcome
import alphainterplanetary.thinker.phases.Phase
import alphainterplanetary.thinker.util.now
import alphainterplanetary.thinker.util.randomUUID
import kotlin.time.Instant

/** Severity of an issue found while parsing/validating an interchange payload. */
enum class ImportIssueSeverity { Error, Warning }

/** A single parsing/validation/planning issue. */
data class ImportIssue(
  val severity: ImportIssueSeverity,
  val message: String,
  /** Project index in the envelope, when the issue is project-specific. */
  val projectIndex: Int? = null,
)

enum class ImportProjectAction {
  /** Create a distinct imported copy with remapped IDs (default). */
  ImportAsNew,
  /** Replace an existing local project with the same source project ID. */
  Overwrite,
  /** Do not import this project. */
  Skip,
}

data class ImportProjectStats(
  val rounds: Int,
  val questions: Int,
  val answers: Int,
)

/**
 * One project's analyzed import candidate. [action] is the planned/tweakable
 * choice for stage 2/3; defaults to [ImportProjectAction.ImportAsNew], or
 * [ImportProjectAction.Skip] when the candidate is not importable.
 */
data class ImportProjectCandidate(
  val index: Int,
  val snapshot: ExportSnapshot,
  val issues: List<ImportIssue> = emptyList(),
  /** True when the snapshot can be converted/imported (possibly with warnings). */
  val isImportable: Boolean,
  /** Local project with the same source ID, if any. */
  val existingProjectId: String? = null,
  val existingProjectTitle: String? = null,
  val defaultAction: ImportProjectAction,
  val action: ImportProjectAction = defaultAction,
  val stats: ImportProjectStats,
  /** Original phase keys from the payload that did not map to known phases. */
  val unknownPhaseKeys: List<String> = emptyList(),
)

/** Stage 1 result: parsed/validated envelope and per-project import plan. */
data class ImportPlan(
  val envelope: ProjectInterchangeEnvelope? = null,
  val envelopeIssues: List<ImportIssue> = emptyList(),
  val projects: List<ImportProjectCandidate> = emptyList(),
) {
  val isReadable: Boolean get() = envelope != null
  val hasErrors: Boolean get() =
    envelopeIssues.any { it.severity == ImportIssueSeverity.Error } ||
      projects.any { p -> p.issues.any { it.severity == ImportIssueSeverity.Error } }
}

data class ImportProjectResult(
  val index: Int,
  val sourceProjectId: String,
  val action: ImportProjectAction,
  val importedProjectId: String? = null,
  val error: String? = null,
)

data class ImportExecutionResult(
  val results: List<ImportProjectResult>,
)

/**
 * Parses, validates, and converts portable interchange payloads into [Project]s.
 *
 * Compatibility directives: prefer canonical snapshot fields/state; fall back to older
 * revision/legacy fields when reading older exports. Backward compatibility is crucial;
 * forward compatibility is not a priority right now (no old readers in the wild). Do not
 * use content sentinels - use null/defaults and schema/version signals.
 */
object ProjectInterchangeImport {

  /** Parses and validates a `.thinker` payload (gzip-compressed or plain JSON). */
  fun analyze(
    bytes: ByteArray,
    existingProjects: List<Project> = emptyList(),
  ): ImportPlan {
    val existingById = existingProjects.associateBy { it.id }
    val decodedBytes = if (isGzip(bytes)) {
      try {
        gzipDecompress(bytes)
      } catch (e: Exception) {
        return ImportPlan(
          envelopeIssues = listOf(
            ImportIssue(
              ImportIssueSeverity.Error,
              "File looks gzip-compressed but could not be decompressed: ${e.message ?: e}",
            ),
          ),
        )
      }
    } else {
      bytes
    }
    val text = try {
      decodedBytes.decodeToString()
    } catch (e: Exception) {
      return ImportPlan(
        envelopeIssues = listOf(
          ImportIssue(
            ImportIssueSeverity.Error,
            "Interchange payload is not valid UTF-8 JSON: ${e.message ?: e}",
          ),
        ),
      )
    }
    val envelope = try {
      ProjectInterchange.decode(text)
    } catch (e: Exception) {
      return ImportPlan(
        envelopeIssues = listOf(
          ImportIssue(
            ImportIssueSeverity.Error,
            "Interchange file could not be parsed: ${e.message ?: e}",
          ),
        ),
      )
    }

    val envelopeIssues = mutableListOf<ImportIssue>()
    if (envelope.schemaVersion > PROJECT_INTERCHANGE_SCHEMA_VERSION) {
      envelopeIssues += ImportIssue(
        ImportIssueSeverity.Error,
        "Unsupported interchange schema version ${envelope.schemaVersion} (supported up to $PROJECT_INTERCHANGE_SCHEMA_VERSION).",
      )
    } else if (envelope.schemaVersion < 1) {
      envelopeIssues += ImportIssue(
        ImportIssueSeverity.Error,
        "Invalid interchange schema version ${envelope.schemaVersion}.",
      )
    }
    if (envelope.projects.isEmpty()) {
      envelopeIssues += ImportIssue(
        ImportIssueSeverity.Error,
        "Interchange file contains no projects.",
      )
    }

    val projectCandidates = if (envelopeIssues.any { it.severity == ImportIssueSeverity.Error }) {
      emptyList()
    } else {
      envelope.projects.mapIndexed { index, project ->
        analyzeProject(index, project.snapshot, existingById[project.snapshot.projectId])
      }
    }

    return ImportPlan(
      envelope = envelope,
      envelopeIssues = envelopeIssues,
      projects = projectCandidates,
    )
  }

  fun analyzeProject(
    index: Int,
    snapshot: ExportSnapshot,
    existingProject: Project?,
  ): ImportProjectCandidate {
    val issues = mutableListOf<ImportIssue>()
    val unknownPhaseKeys = linkedSetOf<String>()

    if (snapshot.projectId.isBlank()) {
      issues += ImportIssue(ImportIssueSeverity.Error, "Project id is missing.", index)
    }
    if (snapshot.schemaVersion > EXPORT_SNAPSHOT_SCHEMA_VERSION) {
      issues += ImportIssue(
        ImportIssueSeverity.Error,
        "Unsupported project snapshot schema version ${snapshot.schemaVersion} (supported up to $EXPORT_SNAPSHOT_SCHEMA_VERSION).",
        index,
      )
    } else if (snapshot.schemaVersion < 1) {
      issues += ImportIssue(
        ImportIssueSeverity.Error,
        "Invalid project snapshot schema version ${snapshot.schemaVersion}.",
        index,
      )
    }

    // Structural/reference checks.
    val phaseKeys = snapshot.phases.map { it.phaseKey }
    if (phaseKeys.size != phaseKeys.toSet().size) {
      issues += ImportIssue(ImportIssueSeverity.Warning, "Duplicate phase keys found; later entries may be ambiguous.", index)
    }
    val questionIds = mutableSetOf<String>()
    val roundIds = mutableSetOf<String>()

    snapshot.rounds.forEach { round ->
      if (round.id.isBlank()) {
        issues += ImportIssue(ImportIssueSeverity.Error, "A round is missing an id.", index)
      } else if (!roundIds.add(round.id)) {
        issues += ImportIssue(ImportIssueSeverity.Error, "Duplicate round id '${round.id}'.", index)
      }
      if (round.phaseKey.isNotBlank() && Phase.fromKey(round.phaseKey) == null) {
        unknownPhaseKeys += round.phaseKey
      }
      if (round.roundNumber < 1) {
        issues += ImportIssue(ImportIssueSeverity.Error, "Round '${round.id}' has invalid round number ${round.roundNumber}.", index)
      }
      if (runCatching { RoundOrigin.valueOf(round.origin) }.isFailure) {
        issues += ImportIssue(ImportIssueSeverity.Warning, "Round '${round.id}' has unknown origin '${round.origin}'; it will be imported as Initial.", index)
      }
      if (runCatching { RoundOutcome.valueOf(round.outcome) }.isFailure) {
        issues += ImportIssue(ImportIssueSeverity.Warning, "Round '${round.id}' has unknown outcome '${round.outcome}'; it will be imported as Pending.", index)
      }
    }

    snapshot.phases.forEach { phase ->
      if (phase.phaseKey.isBlank()) {
        issues += ImportIssue(ImportIssueSeverity.Error, "A phase is missing a phase key.", index)
      } else if (Phase.fromKey(phase.phaseKey) == null) {
        unknownPhaseKeys += phase.phaseKey
      }
      phase.questions.forEach { question ->
        if (question.id.isBlank()) {
          issues += ImportIssue(ImportIssueSeverity.Error, "A question is missing an id.", index)
        } else if (!questionIds.add(question.id)) {
          issues += ImportIssue(ImportIssueSeverity.Error, "Duplicate question id '${question.id}'.", index)
        }
        if (question.roundId.isBlank()) {
          issues += ImportIssue(ImportIssueSeverity.Warning, "Question '${question.id}' is missing a round id.", index)
        }
        val answerIds = mutableSetOf<String>()
        question.answers.forEach { answer ->
          if (answer.id.isBlank()) {
            issues += ImportIssue(ImportIssueSeverity.Error, "Question '${question.id}' has an answer with a missing id.", index)
          } else if (!answerIds.add(answer.id)) {
            issues += ImportIssue(ImportIssueSeverity.Error, "Question '${question.id}' has duplicate answer id '${answer.id}'.", index)
          }
          if (answer.questionId.isNotBlank() && answer.questionId != question.id) {
            issues += ImportIssue(
              ImportIssueSeverity.Warning,
              "Answer '${answer.id}' references question '${answer.questionId}' but belongs to '${question.id}'; it will be reassociated.",
              index,
            )
          }
        }
        val selected = question.answerId
        if (selected != null && question.answers.none { it.id == selected }) {
          issues += ImportIssue(
            ImportIssueSeverity.Warning,
            "Question '${question.id}' references missing current answer '$selected'; it will be imported without a committed answer.",
            index,
          )
        }
      }
    }

    if (unknownPhaseKeys.isNotEmpty()) {
      issues += ImportIssue(
        ImportIssueSeverity.Warning,
        "Unknown phase key(s) ${unknownPhaseKeys.joinToString(", ") { "'$it'" }} will be imported as unassigned (not misattributed).",
        index,
      )
    }

    val fatal = issues.any { it.severity == ImportIssueSeverity.Error }
    val importable = !fatal
    val defaultAction = if (!importable) ImportProjectAction.Skip else ImportProjectAction.ImportAsNew

    val answerCount = snapshot.phases.sumOf { phase -> phase.questions.sumOf { it.answers.size } }
    val questionCount = snapshot.phases.sumOf { it.questions.size }

    return ImportProjectCandidate(
      index = index,
      snapshot = snapshot,
      issues = issues,
      isImportable = importable,
      existingProjectId = existingProject?.id,
      existingProjectTitle = existingProject?.title,
      defaultAction = defaultAction,
      action = defaultAction,
      stats = ImportProjectStats(
        rounds = snapshot.rounds.size,
        questions = questionCount,
        answers = answerCount,
      ),
      unknownPhaseKeys = unknownPhaseKeys.toList(),
    )
  }

  /**
   * Converts [snapshot] to a [Project] for import.
   *
   * When [newProjectId] is non-null (Import as new), a new project identity is
   * created and all IDs/references are remapped to avoid collisions. When null
   * (Overwrite), source IDs are preserved.
   */
  fun toProject(snapshot: ExportSnapshot, newProjectId: String? = null): Project {
    val importNow = now()
    val projectId = newProjectId ?: snapshot.projectId
    val remap = newProjectId != null

    val roundIdMap = mutableMapOf<String, String>()
    val questionIdMap = mutableMapOf<String, String>()

    if (remap) {
      snapshot.rounds.forEach { roundIdMap[it.id] = randomUUID() }
      snapshot.phases.forEach { phase ->
        phase.questions.forEach { q -> questionIdMap[q.id] = randomUUID() }
      }
    }

    fun mapRoundId(id: String): String =
      if (!remap) id else roundIdMap[id] ?: randomUUID().also { roundIdMap[id] = it }

    fun mapQuestionId(id: String): String =
      if (!remap) id else questionIdMap[id] ?: randomUUID().also { questionIdMap[id] = it }

    val rounds = snapshot.rounds.map { round ->
      val phase = Phase.fromKeyOrUnassigned(round.phaseKey)
      val origin = runCatching { RoundOrigin.valueOf(round.origin) }.getOrElse { RoundOrigin.Initial }
      val outcome = runCatching { RoundOutcome.valueOf(round.outcome) }.getOrElse { RoundOutcome.Pending }
      val safeOutcome = if (outcome == RoundOutcome.Failed && round.outcomeDetail.isNullOrBlank()) {
        RoundOutcome.Pending
      } else {
        outcome
      }
      val detail = if (safeOutcome == RoundOutcome.Failed) round.outcomeDetail else null
      Round(
        id = mapRoundId(round.id),
        projectId = projectId,
        phase = phase,
        roundNumber = round.roundNumber.coerceAtLeast(1),
        origin = origin,
        startedAt = Instant.fromEpochMilliseconds(round.startedAtEpochMs),
        completedAt = round.completedAtEpochMs?.let { Instant.fromEpochMilliseconds(it) },
        outcome = safeOutcome,
        outcomeDetail = detail,
      )
    }

    val knownRoundIds = rounds.map { it.id }.toSet()
    // Ensure every question references an existing round (create a synthetic one if needed).
    val extraRounds = mutableListOf<Round>()
    val questions = snapshot.phases.flatMap { phaseSnapshot ->
      val fallbackPhase = Phase.fromKeyOrUnassigned(phaseSnapshot.phaseKey)
      phaseSnapshot.questions.map { q ->
        val newQuestionId = mapQuestionId(q.id)
        val mappedRoundId = mapRoundId(q.roundId)
        if (mappedRoundId !in knownRoundIds && extraRounds.none { it.id == mappedRoundId }) {
          extraRounds += Round(
            id = mappedRoundId,
            projectId = projectId,
            phase = fallbackPhase,
            roundNumber = (rounds + extraRounds).maxOfOrNull { it.roundNumber }?.plus(1) ?: 1,
            origin = RoundOrigin.Initial,
            startedAt = Instant.fromEpochMilliseconds(q.timestampEpochMs),
            outcome = RoundOutcome.Pending,
          )
        }

        val answerIdMap = mutableMapOf<String, String>()
        val answers = q.answers.map { a ->
          val newAnswerId = if (remap) randomUUID() else a.id
          if (remap) answerIdMap[a.id] = newAnswerId
          Answer(
            id = newAnswerId,
            questionId = newQuestionId,
            text = a.text,
            createdAt = Instant.fromEpochMilliseconds(a.createdAtEpochMs),
          )
        }.toMutableList()
        val currentAnswerId = q.answerId?.let { old ->
          if (remap) answerIdMap[old] else old
        }?.takeIf { id -> answers.any { it.id == id } }

        // Prefer canonical state/answers; fall back to legacy draftText for older exports.
        // A question cannot be both committed and draft.
        val snapshotState = AnswerState.fromStringOrNull(q.answerState)
        val draftSnapshot = when {
          currentAnswerId != null -> null
          snapshotState == AnswerState.Draft -> {
            answers.maxByOrNull { it.createdAt }?.id ?: run {
              if (!q.draftText.isNullOrBlank()) {
                val draft = Answer(
                  id = if (remap) randomUUID() else "${newQuestionId}:draft",
                  questionId = newQuestionId,
                  text = q.draftText,
                  createdAt = q.draftUpdatedAtEpochMs?.let { Instant.fromEpochMilliseconds(it) }
                    ?: Instant.fromEpochMilliseconds(q.timestampEpochMs),
                )
                answers += draft
                draft.id
              } else {
                null
              }
            }
          }
          !q.draftText.isNullOrBlank() -> {
            val draft = Answer(
              id = if (remap) randomUUID() else "${newQuestionId}:draft",
              questionId = newQuestionId,
              text = q.draftText,
              createdAt = q.draftUpdatedAtEpochMs?.let { Instant.fromEpochMilliseconds(it) }
                ?: Instant.fromEpochMilliseconds(q.timestampEpochMs),
            )
            answers += draft
            draft.id
          }
          else -> null
        }

        val activeId = draftSnapshot ?: currentAnswerId
        val answerState = when {
          draftSnapshot != null -> AnswerState.Draft
          currentAnswerId != null -> AnswerState.Answered
          snapshotState == AnswerState.Answered && answers.isNotEmpty() -> AnswerState.Answered
          else -> AnswerState.Unanswered
        }

        // The model treats the newest snapshot by (createdAt, id) as active when Draft/Answered.
        val normalizedAnswers = run {
          val sorted = answers.sortedWith(ANSWER_NEWEST_FIRST)
          val latest = sorted.firstOrNull()
          if (activeId == null || latest == null || latest.id == activeId) return@run sorted
          val active = sorted.firstOrNull { it.id == activeId } ?: return@run sorted
          val bumped = active.copy(
            createdAt = Instant.fromEpochMilliseconds(latest.createdAt.toEpochMilliseconds() + 1)
          )
          listOf(bumped) + sorted.filter { it.id != activeId }
        }

        Question(
          id = newQuestionId,
          text = q.text,
          timestamp = Instant.fromEpochMilliseconds(q.timestampEpochMs),
          roundId = mappedRoundId,
          ignoredAt = q.ignoredAtEpochMs?.let { Instant.fromEpochMilliseconds(it) },
          answerState = answerState,
          answers = normalizedAnswers,
          compacted = false, // never persisted as compacted
        )
      }
    }

    val allRounds = (rounds + extraRounds).sortedBy { it.roundNumber }
    val createdAt = if (snapshot.createdAtEpochMs > 0) {
      Instant.fromEpochMilliseconds(snapshot.createdAtEpochMs)
    } else {
      Instant.fromEpochMilliseconds(snapshot.revisionUpdatedAtEpochMs)
    }
    val updatedAt = if (remap) {
      importNow
    } else {
      Instant.fromEpochMilliseconds(snapshot.revisionUpdatedAtEpochMs)
    }

    return Project(
      id = projectId,
      synopsis = snapshot.synopsis,
      title = snapshot.title,
      status = snapshot.status.ifBlank { "Draft" },
      questions = questions,
      rounds = allRounds,
      createdAt = createdAt,
      updatedAt = updatedAt,
    )
  }

  /** True when [bytes] look like gzip-compressed data. */
  fun isGzip(bytes: ByteArray): Boolean =
    bytes.size >= 2 && bytes[0] == 0x1f.toByte() && bytes[1] == 0x8b.toByte()
}
