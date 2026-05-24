package com.pforhan.alphathinker.model

import java.time.Instant

data class ExchangeRound(
    val round: Int,
    val questions: List<Question>,
    val contextId: String,
    val createdAt: Instant,
    val answers: List<Answer> = emptyList(),
    val questionsCount: Int
) {
    val isActive: Boolean
        get() = questions.any { !it.isArchived }

    val isArchived: Boolean
        get() = !isActive
}

data class Answer(
    val questionId: String,
    val text: String,
    val answeredAt: Instant,
    val modifiedAt: Instant? = null
) {
    val isAnswered: Boolean
        get() = text.isNotBlank()
}
