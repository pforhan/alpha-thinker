package alphainterplanetary.thinker.database

import alphainterplanetary.thinker.model.Answer
import alphainterplanetary.thinker.model.Project
import alphainterplanetary.thinker.model.Question
import alphainterplanetary.thinker.model.Round
import alphainterplanetary.thinker.model.RoundOrigin
import alphainterplanetary.thinker.phases.Phase
import kotlin.time.Instant

fun Project.toEntity() = ProjectEntity(
  id = id,
  synopsis = synopsis,
  editableTitle = editableTitle,
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

fun ProjectWithQuestions.toDomainModel(
  answers: List<AnswerEntity>,
  rounds: List<RoundEntity>,
): Project =
  answers.groupBy { it.questionId }.let { answersByQuestionId ->
    Project(
      id = project.id,
      synopsis = project.synopsis,
      editableTitle = project.editableTitle,
      status = project.status,
      questions = questions
        .sortedBy { it.sortOrder }
        .map { question -> question.toDomainModel(answersByQuestionId[question.id].orEmpty()) },
      rounds = rounds.map { it.toDomainModel() },
      createdAt = Instant.fromEpochMilliseconds(project.createdAt),
      updatedAt = Instant.fromEpochMilliseconds(project.updatedAt)
    )
  }

fun QuestionEntity.toDomainModel(answers: List<AnswerEntity>): Question = Question(
  id = id,
  text = text,
  timestamp = Instant.fromEpochMilliseconds(createdAt),
  roundId = roundId,
  ignoredAt = ignoredAt?.let { Instant.fromEpochMilliseconds(it) },
  answerId = answerId,
  draftText = draftText,
  draftUpdatedAt = draftUpdatedAt?.let { Instant.fromEpochMilliseconds(it) },
  answers = answers.map { it.toDomainModel() }
)

fun AnswerEntity.toDomainModel() = Answer(
  id = id,
  questionId = questionId,
  text = text,
  createdAt = Instant.fromEpochMilliseconds(createdAt)
)

fun Round.toEntity() = RoundEntity(
  id = id,
  projectId = projectId,
  phase = phase.key,
  roundNumber = roundNumber,
  origin = origin.name,
  startedAt = startedAt.toEpochMilliseconds(),
  completedAt = completedAt?.toEpochMilliseconds(),
)

fun RoundEntity.toDomainModel() = Round(
  id = id,
  projectId = projectId,
  phase = requireNotNull(Phase.fromKey(phase)) { "Unknown phase key: $phase" },
  roundNumber = roundNumber,
  origin = RoundOrigin.valueOf(origin),
  startedAt = Instant.fromEpochMilliseconds(startedAt),
  completedAt = completedAt?.let { Instant.fromEpochMilliseconds(it) },
)