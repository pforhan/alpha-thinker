package com.pforhan.alphathinker.model

import java.time.Instant

data class Project(
    val id: String,
    val synopsis: String,
    val questions: List<Question>,
    val exchangeRounds: List<ExchangeRound>,
    val createdAt: Instant,
    val updatedAt: Instant
)
