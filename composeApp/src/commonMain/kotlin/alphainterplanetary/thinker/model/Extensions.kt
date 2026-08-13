package alphainterplanetary.thinker.model

import kotlinx.datetime.Instant

val Question.isAnswered: Boolean
    get() = currentAnswer?.isComplete == true

val Question.isIgnored: Boolean
    get() = ignoredAt != null

val Question.isUnanswered: Boolean
    get() = !isAnswered && !isIgnored

val Answer.isComplete: Boolean
    get() = text.isNotBlank() && !isDraft

val Answer.isDraft: Boolean
    get() = answeredAt == null
