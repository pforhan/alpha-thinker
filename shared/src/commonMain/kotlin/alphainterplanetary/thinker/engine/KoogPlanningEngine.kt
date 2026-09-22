package alphainterplanetary.thinker.engine

import ai.koog.prompt.dsl.PromptBuilder
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.llm.LLModel
import alphainterplanetary.thinker.model.Question
import alphainterplanetary.thinker.phases.Phase
import alphainterplanetary.thinker.util.jsonObject
import alphainterplanetary.thinker.util.now
import alphainterplanetary.thinker.util.randomUUID
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Instant

/**
 * The LLM-hosted [PlanningEngine], implemented over Koog's [PromptExecutor]
 * seam (a `MultiLLMPromptExecutor` over whichever LLM client the active
 * backend supplies — on-device, remote, or downloaded; ENG-DESIGN.md
 * "LLM Inference Layer"). It is invoked statelessly, exactly like
 * [HardcodedPlanningEngine], so DI can swap Lite for an LLM backend without
 * touching the repository or tasks.
 *
 * Both question interactions ask the model for a JSON `{"questions":[{...}]}`
 * payload and parse it with kotlinx.serialization; a malformed or empty reply
 * degrades to a line-by-line extraction (so a chatty model that ignores the
 * format still yields questions), and an empty reply reports `done` — the
 * LLM's "nothing more to produce" signal.
 */
class KoogPlanningEngine(
  private val executor: PromptExecutor,
  private val model: LLModel,
) : PlanningEngine {

  override suspend fun recommendTitle(synopsis: String, activityId: String): String {
    val text = ask(PromptRecommendTitle) {
      system(TitleSystemPrompt)
      user(titleUserPrompt(synopsis))
    }
    return text.ifEmpty {
      throw PlanningEngine.AnalysisFailure("The model returned an empty title")
    }
  }

  override suspend fun generateInitialQuestions(
    editableTitle: String,
    synopsis: String,
    roundId: String,
    phase: Phase,
    activityId: String,
  ): QuestionBatch {
    val text = ask(PromptInitialQuestions) {
      system(QuestionsSystemPrompt)
      user(initialUserPrompt(editableTitle, synopsis, phase))
    }
    return batch(parseQuestions(text), roundId)
  }

  override suspend fun generateFollowUpQuestions(
    synopsis: String,
    previousQuestions: List<Question>,
    roundId: String,
    phase: Phase,
    activityId: String,
  ): QuestionBatch {
    val text = ask(PromptFollowUpQuestions) {
      system(QuestionsSystemPrompt)
      user(followUpUserPrompt(synopsis, phase, previousQuestions.map { it.text }))
    }
    return batch(parseQuestions(text), roundId)
  }

  /**
   * An LLM can nearly always compose a fresh question for a phase, so the
   * conservative answer is "yes" — the phase only reads exhausted when an
   * interaction itself reports no new questions via [QuestionBatch.done].
   */
  override suspend fun canProduceMoreInPhase(
    synopsis: String,
    previousQuestions: List<Question>,
    phase: Phase,
    activityId: String,
  ): Boolean = true

  private suspend fun ask(promptId: String, content: PromptBuilder.() -> Unit): String {
    val assistant = try {
      executor.execute(prompt(promptId) { content() }, model)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      throw PlanningEngine.AnalysisFailure(e.message ?: e.toString())
    }
    return assistant.textContent().trim()
  }

  private fun batch(drafts: List<String>, roundId: String): QuestionBatch {
    val timestamp = now()
    return QuestionBatch(
      questions = drafts.map { text -> newQuestion(text, roundId, timestamp) },
      done = drafts.isEmpty(),
    )
  }

  private fun newQuestion(text: String, roundId: String, timestamp: Instant): Question =
    Question(
      id = randomUUID(),
      text = text,
      timestamp = timestamp,
      roundId = roundId,
    )

  /**
   * Extracts question texts from a model reply. Prefers a JSON
   * `{"questions":[...]}` payload (with or without a markdown fence around it),
   * then falls back to a line-by-line bullet / numbered extraction.
   */
  private fun parseQuestions(text: String): List<String> {
    val jsonBody = text.jsonObject()
    if (jsonBody != null) {
      runCatching {
        questionJson.decodeFromString<GeneratedQuestions>(jsonBody)
      }.getOrNull()?.let { decoded ->
        return decoded.questions.map { it.text.trim() }.filter { it.isNotEmpty() }
      }
    }
    return text.lineSequence()
      .map { it.trim() }
      .mapNotNull { line ->
        when {
          line.isEmpty() -> null
          line.endsWith(":") -> null
          line.startsWith("-") -> line.removePrefix("-").trim()
          line.startsWith("•") -> line.removePrefix("•").trim()
          line.startsWith("*") -> line.removePrefix("*").trim()
          line.endsWith("?") -> line
          else -> null
        }
      }
      .filter { it.isNotEmpty() }
      .toList()
  }

  companion object {
    const val DraftCount: Int = 5

    const val PromptRecommendTitle = "alpha-thinker-recommend-title"
    const val PromptInitialQuestions = "alpha-thinker-initial-questions"
    const val PromptFollowUpQuestions = "alpha-thinker-follow-up-questions"

    const val TitleSystemPrompt = "You are a project-planning assistant. Recommend a short, " +
      "memorable project title from the user's synopsis. Reply with only the title — no quotes, " +
      "no explanation, no trailing period. Keep it under 60 characters."

    const val QuestionsSystemPrompt = "You are a project-planning assistant that runs a guided " +
      "planning interview. Ask focused, concrete questions that help the user scope their project " +
      "in the current planning phase. Do not ask about things already covered. Reply with only " +
      "valid JSON of the form {\"questions\":[{\"text\":\"...\"}]}, one object per question."

    val questionJson: Json = Json {
      ignoreUnknownKeys = true
      coerceInputValues = true
    }

    fun titleUserPrompt(synopsis: String): String =
      "Project synopsis:\n$synopsis\n\nReturn the project title."

    fun initialUserPrompt(editableTitle: String, synopsis: String, phase: Phase): String =
      "Planning a project titled \"$editableTitle\".\n" +
        "Phase: ${phase.label} — ${phase.description}\n" +
        "Project synopsis:\n$synopsis\n\n" +
        "Propose exactly $DraftCount distinct questions for this phase."

    fun followUpUserPrompt(
      synopsis: String,
      phase: Phase,
      previousQuestions: List<String>,
    ): String =
      "Continuing a planning interview for the project described below.\n" +
        "Phase: ${phase.label} — ${phase.description}\n" +
        "Project synopsis:\n$synopsis\n\n" +
        "These questions were already asked and may contain answers:\n" +
        previousQuestions.joinToString("\n") { "- $it" }.ifEmpty { "(none)" } + "\n\n" +
        "Propose exactly $DraftCount new questions for this phase. Do not repeat any question " +
        "already asked."
  }
}

/** The JSON shape [KoogPlanningEngine.parseQuestions] expects from the model. */
@Serializable
private data class GeneratedQuestions(
  val questions: List<QuestionDraft> = emptyList(),
)

@Serializable
private data class QuestionDraft(
  val text: String,
)