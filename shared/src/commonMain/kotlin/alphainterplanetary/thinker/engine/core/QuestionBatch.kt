package alphainterplanetary.thinker.engine.core

import alphainterplanetary.thinker.model.Question

/**
 * The result of one question-generation interaction: the produced [questions]
 * plus the engine's explicit [QuestionBatch.done] "stop condition" — "I'm done
 * with this phase". Carrying [done] openly means exhaustion is never inferred
 * from an empty question list (an `emptyList()` is indistinguishable from
 * "nothing surfaced yet" or a soft failure); the hardcoded engine reports done
 * once its phase pool is exhausted, a remote LLM backend reports it when it
 * decides it has nothing more to produce.
 */
data class QuestionBatch(
  val questions: List<Question>,
  val done: Boolean,
) {
  /**
   * The log line for a produced batch: `N questions, done=<flag>`, then one line
   * per question. Both writers of that row phrase it here — the request that
   * generated it, and the [LoggingPlanningEngine] fallback for an engine that
   * generated nothing — and the activity's collapsed headline parses the count
   * off that same text, so the phrasing is one contract rather than two.
   */
  fun summary(): String = buildString {
    val count = questions.size
    append(if (count == 1) "1 question" else "$count questions")
    append(", done=$done")
    questions.forEach { question ->
      append("\n• ")
      append(question.text)
    }
  }
}
