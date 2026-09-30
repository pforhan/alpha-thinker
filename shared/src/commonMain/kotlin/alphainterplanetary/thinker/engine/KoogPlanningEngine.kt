package alphainterplanetary.thinker.engine

import ai.koog.prompt.dsl.PromptBuilder
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.executor.model.PromptExecutor
import alphainterplanetary.thinker.activitylog.LogSource
import alphainterplanetary.thinker.model.Question
import alphainterplanetary.thinker.model.RoundOutcome
import alphainterplanetary.thinker.phases.Phase
import alphainterplanetary.thinker.util.jsonArray
import alphainterplanetary.thinker.util.now
import alphainterplanetary.thinker.util.randomUUID
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
 * Both question interactions ask the model for a JSON array of question strings
 * and parse it with kotlinx.serialization, and no other shape is accepted: a
 * reply we can't read fails the generation rather than being partially
 * recovered (see [parseQuestions]). A well-formed empty array reports `done` —
 * the LLM's "nothing more to produce" signal.
 */
class KoogPlanningEngine(
  private val backend: KoogPlanningBackend,
) : PlanningEngine, PromptRenderer {

  override val source: LogSource
    get() = backend.source

  override fun titlePrompt(synopsis: String): String =
    render(TitleSystemPrompt, titleUserPrompt(synopsis))

  override fun questionsPrompt(
    title: String,
    synopsis: String,
    previousQuestions: List<Question>,
    phase: Phase,
  ): String = render(QuestionsSystemPrompt, questionsUserPrompt(title, synopsis, phase, previousQuestions))

  override suspend fun recommendTitle(synopsis: String, activityId: String): String {
    val text = ask(PromptRecommendTitle) {
      system(TitleSystemPrompt)
      user(titleUserPrompt(synopsis))
    }
    return text.ifEmpty {
      throw PlanningEngine.AnalysisFailure("The model returned an empty title")
    }
  }

  override suspend fun generateQuestions(
    title: String,
    synopsis: String,
    previousQuestions: List<Question>,
    roundId: String,
    phase: Phase,
    activityId: String,
  ): QuestionBatch {
    // An empty list is only ever a brand-new project's opening round, so it is
    // the one thing that still distinguishes the two prompt shapes — kept in
    // the prompt id alone, where it separates their traces for free.
    val promptId =
      if (previousQuestions.isEmpty()) PromptInitialQuestions else PromptFollowUpQuestions
    val text = ask(promptId) {
      system(QuestionsSystemPrompt)
      user(questionsUserPrompt(title, synopsis, phase, previousQuestions))
    }
    return batch(parseQuestions(text), roundId)
  }

  private fun render(system: String, user: String): String =
    "SYSTEM\n$system\n\nUSER\n$user"

  private suspend fun ask(promptId: String, content: PromptBuilder.() -> Unit): String {
    val assistant = try {
      backend.executor.execute(prompt(promptId) { content() }, backend.model)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      throw PlanningEngine.AnalysisFailure(e.message ?: e.toString())
    }
    return assistant.textContent().trim()
  }

  /**
   * A batch from the parsed questions, or a failure when the reply couldn't be
   * read. Only a well-formed empty array means "done" — reporting an unreadable
   * reply as [QuestionBatch.done] would latch the round exhausted, and that state
   * is storage-backed, so the phase would read as finished (and "Get more
   * questions" would stay disabled) until the project was recreated. Failing
   * instead leaves the round [RoundOutcome.Failed] and the phase open to retry.
   */
  private fun batch(drafts: List<String>?, roundId: String): QuestionBatch {
    if (drafts == null) {
      throw PlanningEngine.AnalysisFailure(
        "The model didn't reply with a JSON array of question strings",
      )
    }
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
   * The questions in a model reply, or null when the reply carries no JSON array
   * of quoted strings — the only shape the prompt asks for, and the only one
   * worth reading. The array is taken from wherever it appears, so fences,
   * surrounding prose, and an object wrapping it are all fine; everything else
   * is refused rather than guessed at, because a half-recovered batch is
   * indistinguishable from a real one downstream (see [batch]).
   */
  private fun parseQuestions(text: String): List<String>? {
    val body = text.jsonArray() ?: return null
    return runCatching {
      questionJson.decodeFromString<List<String>>(body)
    }.getOrNull()
      ?.map { it.trim() }
      ?.filter { it.isNotEmpty() }
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
      "planning interview. The user message names the planning phase the project is currently in; " +
      "ask focused, concrete questions that move the project forward within that phase's subject " +
      "matter. It also carries the interview so far, one line per question with its answer, its " +
      "draft, or a note: \"skipped\", \"not yet answered\", or \"A: omitted\". An omitted answer " +
      "means the user answered the question, but the text was left out to save room — treat it as " +
      "answered, not open. Never ask a question that already appears in that list, and build on " +
      "the answers that are there. Reply with only valid JSON: a plain array of question strings, " +
      "e.g. [\"What is the MVP?\",\"Who is this for?\"]."

    /**
     * Strict on purpose. The default is already strict, but stating it pins the
     * reason: a lenient decode would read a bracketed bullet list like
     * `[P1] What is the MVP?` as a one-element array of unquoted strings instead
     * of refusing it.
     */
    val questionJson: Json = Json {
      isLenient = false
    }

    fun titleUserPrompt(synopsis: String): String =
      "Project synopsis:\n$synopsis\n\nReturn the project title."

    /**
     * One prompt for every question round. The interview-so-far block is the
     * whole transcript — every prior question with whatever the user has done
     * with it (see [PlanningContext.line]) — and is always present, reading
     * "(none)" on a project's opening round, so a first batch and a later one
     * cannot drift into different shapes. The title reaches every round, not
     * just the first.
     */
    fun questionsUserPrompt(
      editableTitle: String,
      synopsis: String,
      phase: Phase,
      previousQuestions: List<Question>,
    ): String =
      "Planning a project titled \"$editableTitle\".\n" +
        "Phase: ${phase.label} — ${phase.description}\n" +
        "Project synopsis:\n$synopsis\n\n" +
        "The interview so far:\n" +
        PlanningContext.render(previousQuestions).ifEmpty { "(none)" } + "\n\n" +
        "Propose exactly $DraftCount new questions for this phase. Go deeper on what has been " +
        "answered, and leave the rest of the interview alone."
  }
}

