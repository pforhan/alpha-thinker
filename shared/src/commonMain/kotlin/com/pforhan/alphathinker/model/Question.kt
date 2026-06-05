package com.pforhan.alphathinker.model

import kotlinx.datetime.Instant

data class Question(
    val id: String,
    val text: String,
    val timestamp: Instant,
    val contextId: String,
    val archivedAt: Instant? = null
) {
    val isArchived: Boolean
        get() = archivedAt != null
}
