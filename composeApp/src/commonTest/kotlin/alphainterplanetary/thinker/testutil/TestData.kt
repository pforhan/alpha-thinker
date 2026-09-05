package alphainterplanetary.thinker.testutil

import alphainterplanetary.thinker.model.Answer
import alphainterplanetary.thinker.model.Question
import kotlinx.datetime.Instant

val defaultTestInstant: Instant = Instant.fromEpochMilliseconds(0)

fun question(
  id: String,
  text: String = id,
  ignoredAt: Instant? = null,
  answers: List<Answer> = emptyList(),
  timestamp: Instant = defaultTestInstant,
  contextId: String = "ctx",
): Question = Question(
  id = id,
  text = text,
  timestamp = timestamp,
  contextId = contextId,
  ignoredAt = ignoredAt,
  answers = answers,
)

fun answeredQuestion(id: String): Question = question(id, answers = listOf(answer(id, "a")))

fun ignoredQuestion(id: String): Question = question(id, ignoredAt = defaultTestInstant)

fun draftQuestion(id: String): Question =
  question(id, answers = listOf(answer(id, "draft", answeredAt = null)))

fun answer(
  questionId: String,
  text: String,
  answeredAt: Instant? = defaultTestInstant,
  id: Long = 0,
  deletedAt: Instant? = null,
): Answer = Answer(
  id = id,
  questionId = questionId,
  text = text,
  answeredAt = answeredAt,
  deletedAt = deletedAt,
)