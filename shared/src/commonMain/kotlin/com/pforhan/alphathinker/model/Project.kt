package com.pforhan.alphathinker.model

import kotlinx.datetime.Instant

data class Project(
    val id: String,
    val synopsis: String,
    val editableTitle: String,
    val status: String,
    val questions: List<Question>,
    val exchangeRounds: List<ExchangeRound>,
    val createdAt: Instant,
    val updatedAt: Instant
)
