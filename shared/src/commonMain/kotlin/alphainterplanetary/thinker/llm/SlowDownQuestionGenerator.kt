package alphainterplanetary.thinker.llm

import alphainterplanetary.thinker.model.Question
import alphainterplanetary.thinker.phases.Phase
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow

/**
 * Decorates a [QuestionGenerator] with an optional artificial delay applied
 * before each interaction, driven by [config] (the Testing setting). While the
 * config's [GeneratorDelayConfig.enabled] is on, each interaction is held for
 * its own chosen [GeneratorDelayConfig.durationFor] so generation tasks linger
 * on the Task Manager long enough to exercise and inspect its UI and code paths.
 */
class SlowDownQuestionGenerator(
  private val delegate: QuestionGenerator,
  private val config: StateFlow<GeneratorDelayConfig>,
) : QuestionGenerator {

  override suspend fun recommendTitle(synopsis: String): String {
    maybeDelay(GeneratorInteraction.RecommendTitle)
    return delegate.recommendTitle(synopsis)
  }

  override suspend fun generateInitialQuestions(
    editableTitle: String,
    synopsis: String,
    roundId: String,
    phase: Phase,
  ): List<Question> {
    maybeDelay(GeneratorInteraction.InitialQuestions)
    return delegate.generateInitialQuestions(editableTitle, synopsis, roundId, phase)
  }

  override suspend fun generateFollowUpQuestions(
    synopsis: String,
    previousQuestions: List<Question>,
    roundId: String,
    phase: Phase,
  ): List<Question> {
    maybeDelay(GeneratorInteraction.FollowUpQuestions)
    return delegate.generateFollowUpQuestions(synopsis, previousQuestions, roundId, phase)
  }

  override suspend fun remainingInPhase(
    synopsis: String,
    previousQuestions: List<Question>,
    phase: Phase,
  ): Int {
    maybeDelay(GeneratorInteraction.RemainingInPhase)
    return delegate.remainingInPhase(synopsis, previousQuestions, phase)
  }

  private suspend fun maybeDelay(interaction: GeneratorInteraction) {
    config.value.durationFor(interaction)?.let { duration ->
      delay(duration)
    }
  }
}