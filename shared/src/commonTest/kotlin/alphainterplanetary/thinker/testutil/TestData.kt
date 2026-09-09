package alphainterplanetary.thinker.testutil

import alphainterplanetary.thinker.model.Answer
import alphainterplanetary.thinker.model.Question
import kotlin.time.Instant

val defaultTestInstant: Instant = Instant.fromEpochMilliseconds(0)

fun question(
  id: String,
  text: String = id,
  ignoredAt: Instant? = null,
  answers: List<Answer> = emptyList(),
  timestamp: Instant = defaultTestInstant,
  contextId: String = "ctx",
  draftText: String? = null,
  draftUpdatedAt: Instant? = null,
): Question = Question(
  id = id,
  text = text,
  timestamp = timestamp,
  contextId = contextId,
  ignoredAt = ignoredAt,
  answerId = answers.lastOrNull()?.id,
  draftText = draftText,
  draftUpdatedAt = draftUpdatedAt,
  answers = answers,
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