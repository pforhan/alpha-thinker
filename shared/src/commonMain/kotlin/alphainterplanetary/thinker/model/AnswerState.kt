package alphainterplanetary.thinker.model

import kotlinx.serialization.Serializable

/**
 * Current answer state for a question. Ignored is orthogonal ([Question.isIgnored]).
 *
 * Enum names are persisted/exported; avoid renaming them. If a rename is ever needed,
 * add synonym support in [fromStringOrNull] (and any external format readers) so
 * historic DB/snapshot/import values still resolve.
 */
@Serializable
enum class AnswerState {
  Unanswered,
  Draft,
  Answered;

  companion object {
    fun fromStringOrNull(value: String): AnswerState? = entries.firstOrNull { it.name == value }
  }
}
