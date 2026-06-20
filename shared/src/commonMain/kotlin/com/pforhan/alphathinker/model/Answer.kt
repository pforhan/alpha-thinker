package com.pforhan.alphathinker.model

import kotlinx.datetime.Instant

data class Answer(
  val questionId: String,
  val text: String,
  val answeredAt: Instant,
  val modifiedAt: Instant? = null
) {
  val isAnswered: Boolean
    get() = text.isNotBlank()
}