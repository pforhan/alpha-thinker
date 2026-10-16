package alphainterplanetary.thinker.database

import alphainterplanetary.thinker.activitylog.LogCategory
import alphainterplanetary.thinker.activitylog.LogEntry
import alphainterplanetary.thinker.activitylog.LogSource
import alphainterplanetary.thinker.engine.EngineInstance
import alphainterplanetary.thinker.engine.EngineKind
import alphainterplanetary.thinker.engine.EngineRef
import alphainterplanetary.thinker.engine.EngineState
import alphainterplanetary.thinker.engine.RemoteProvider
import alphainterplanetary.thinker.model.Answer
import alphainterplanetary.thinker.model.Project
import alphainterplanetary.thinker.model.Question
import alphainterplanetary.thinker.model.Round
import alphainterplanetary.thinker.model.RoundOrigin
import alphainterplanetary.thinker.model.RoundOutcome
import alphainterplanetary.thinker.phases.Phase
import kotlin.time.Instant

fun Project.toEntity() = ProjectEntity(
  id = id,
  synopsis = synopsis,
  title = title,
  createdAt = createdAt.toEpochMilliseconds(),
  updatedAt = updatedAt.toEpochMilliseconds(),
  status = status
)

fun Question.toEntity(projectId: String, index: Int) = QuestionEntity(
  id = id,
  projectId = projectId,
  text = text,
  roundId = roundId,
  createdAt = timestamp.toEpochMilliseconds(),
  sortOrder = index,
  ignoredAt = ignoredAt?.toEpochMilliseconds(),
  answerId = answerId,
  draftText = draftText,
  draftUpdatedAt = draftUpdatedAt?.toEpochMilliseconds(),
)

fun Answer.toEntity() = AnswerEntity(
  id = id,
  questionId = questionId,
  text = text,
  createdAt = createdAt.toEpochMilliseconds(),
)

fun Round.toEntity() = RoundEntity(
  id = id,
  projectId = projectId,
  phase = phase.key,
  roundNumber = roundNumber,
  origin = origin.name,
  startedAt = startedAt.toEpochMilliseconds(),
  completedAt = completedAt?.toEpochMilliseconds(),
  outcome = outcome.name,
  outcomeDetail = outcomeDetail,
)

fun LogEntry.toEntity(): LogEntryEntity = LogEntryEntity(
  id = id ?: 0,
  projectId = projectId,
  activityId = activityId,
  category = category.name,
  source = source?.name,
  log = log,
  timestampMillis = timestamp.toEpochMilliseconds(),
  raw = raw,
)

fun ProjectWithQuestions.toModel(
  answers: List<AnswerEntity>,
  rounds: List<RoundEntity>,
): Project =
  answers.groupBy { it.questionId }.let { answersByQuestionId ->
    Project(
      id = project.id,
      synopsis = project.synopsis,
      title = project.title,
      status = project.status,
      questions = questions
        .sortedBy { it.sortOrder }
        .map { question -> question.toModel(answersByQuestionId[question.id].orEmpty()) },
      rounds = rounds.map { it.toModel() },
      createdAt = Instant.fromEpochMilliseconds(project.createdAt),
      updatedAt = Instant.fromEpochMilliseconds(project.updatedAt)
    )
  }

fun QuestionEntity.toModel(answers: List<AnswerEntity>): Question = Question(
  id = id,
  text = text,
  timestamp = Instant.fromEpochMilliseconds(createdAt),
  roundId = roundId,
  ignoredAt = ignoredAt?.let { Instant.fromEpochMilliseconds(it) },
  answerId = answerId,
  draftText = draftText,
  draftUpdatedAt = draftUpdatedAt?.let { Instant.fromEpochMilliseconds(it) },
  answers = answers.map { it.toModel() }
)

fun AnswerEntity.toModel() = Answer(
  id = id,
  questionId = questionId,
  text = text,
  createdAt = Instant.fromEpochMilliseconds(createdAt)
)

fun RoundEntity.toModel() = Round(
  id = id,
  projectId = projectId,
  phase = requireNotNull(Phase.fromKey(phase)) { "Unknown phase key: $phase" },
  roundNumber = roundNumber,
  origin = RoundOrigin.valueOf(origin),
  startedAt = Instant.fromEpochMilliseconds(startedAt),
  completedAt = completedAt?.let { Instant.fromEpochMilliseconds(it) },
  outcome = RoundOutcome.valueOf(outcome),
  outcomeDetail = outcomeDetail,
)

fun LogEntryEntity.toModel(): LogEntry = LogEntry(
  id = id,
  projectId = projectId,
  activityId = activityId,
  category = parseCategory(category),
  source = source?.let(::parseSource),
  log = log,
  timestamp = Instant.fromEpochMilliseconds(timestampMillis),
  raw = raw,
)

/** Unknown stored categories degrade to [LogCategory.Info] rather than crash. */
private fun parseCategory(name: String): LogCategory =
  runCatching { LogCategory.valueOf(name) }.getOrElse { LogCategory.Info }

private fun parseSource(name: String): LogSource? =
  runCatching { LogSource.valueOf(name) }.getOrNull()

fun EngineInstance.toEntity() = EngineInstanceEntity(
  id = id,
  kind = kind.key,
  provider = provider?.key,
  label = label,
  baseUrl = baseUrl,
  apiKey = apiKey,
  model = model,
  contextTokens = contextTokens,
  localPath = localPath,
)

/** An unknown stored kind is a corrupt row; [EngineKind.fromKey] returning null here is a bug. */
fun EngineInstanceEntity.toModel() = EngineInstance(
  id = id,
  kind = requireNotNull(EngineKind.fromKey(kind)) { "Unknown engine kind key: $kind" },
  provider = provider?.let(RemoteProvider::fromKey),
  label = label,
  baseUrl = baseUrl,
  apiKey = apiKey,
  model = model,
  contextTokens = contextTokens,
  localPath = localPath,
)

fun EngineState.toEntity() = EngineStateEntity(
  refKey = ref.storageKey,
  selected = selected,
  lastUsedAt = lastUsedAt,
)

fun EngineStateEntity.toModel() = EngineState(
  ref = EngineRef.fromStorageKey(refKey),
  selected = selected,
  lastUsedAt = lastUsedAt,
)
