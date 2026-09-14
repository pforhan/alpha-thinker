package alphainterplanetary.thinker.testutil

import alphainterplanetary.thinker.model.Answer
import alphainterplanetary.thinker.model.Question
import alphainterplanetary.thinker.model.Round
import alphainterplanetary.thinker.model.RoundOrigin
import alphainterplanetary.thinker.phases.Phase
import kotlin.time.Instant

val defaultTestInstant: Instant = Instant.fromEpochMilliseconds(0)

fun question(
  id: String,
  text: String = id,
  ignoredAt: Instant? = null,
  answers: List<Answer> = emptyList(),
  timestamp: Instant = defaultTestInstant,
  roundId: String = "ctx",
  draftText: String? = null,
  draftUpdatedAt: Instant? = null,
): Question = Question(
  id = id,
  text = text,
  timestamp = timestamp,
  roundId = roundId,
  ignoredAt = ignoredAt,
  answerId = answers.lastOrNull()?.id,
  draftText = draftText,
  draftUpdatedAt = draftUpdatedAt,
  answers = answers,
)

fun round(
  id: String,
  projectId: String = "p1",
  phase: Phase = Phase.ScopeGoals,
  roundNumber: Int = 1,
  origin: RoundOrigin = RoundOrigin.Initial,
  startedAt: Instant = defaultTestInstant,
  completedAt: Instant? = null,
): Round = Round(
  id = id,
  projectId = projectId,
  phase = phase,
  roundNumber = roundNumber,
  origin = origin,
  startedAt = startedAt,
  completedAt = completedAt,
)

fun answeredQuestion(id: String): Question =
  question(id, answers = listOf(answer(id, "a", id = "a1")))

fun ignoredQuestion(id: String): Question = question(id, ignoredAt = defaultTestInstant)

fun draftQuestion(id: String): Question =
  question(id, draftText = "draft", draftUpdatedAt = defaultTestInstant)

fun answer(
  questionId: String,
  text: String,
  id: String = "",
  createdAt: Instant = defaultTestInstant,
): Answer = Answer(
  id = id,
  questionId = questionId,
  text = text,
  createdAt = createdAt,
)