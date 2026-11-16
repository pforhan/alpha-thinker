package alphainterplanetary.thinker.engine.core

import alphainterplanetary.thinker.phases.Phase

/**
 * One phase's answers replaced by a model-written [summary], for a transcript
 * that no longer fits them verbatim. [coversQuestionIds] names the questions
 * whose answers the summary stands in for — the transcript keeps their `Q:`
 * lines and reads [PlanningContext.SummarizedNote] for the answer, so nothing
 * looks unanswered.
 */
data class SummarizedPhase(
  val phase: Phase,
  val summary: String,
  val coversQuestionIds: Set<String> = emptySet(),
)
