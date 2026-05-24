package com.pforhan.alphathinker.model

import java.time.Instant

data class ExchangeRound(
    val round: Int,
    val questions: List<Question>,
    val createdAt: Instant
)

data class Answer(
    val questionId: String,
    val text: String,
    val answeredAt: Instant,
    val modifiedAt: Instant? = null
) {
    val isAnswered: Boolean
        get() = text.isNotBlank()
}
