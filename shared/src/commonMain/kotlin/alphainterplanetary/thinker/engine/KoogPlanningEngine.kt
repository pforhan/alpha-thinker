package alphainterplanetary.thinker.engine

import ai.koog.prompt.Prompt
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.message.Message
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
 *
 * Each request is built once and reported into the ambient [LogScope] as it is
 * sent ([logRequest]), so the `prompt:` row is read back off the very [Prompt]
 * the backend received and the reply is filed verbatim on that request's own
 * outcome row — including the ones we could not read, where the failing message
 * alone says what went wrong and not what the model actually said.
 */
class KoogPlanningEngine(
  private val backend: KoogPlanningBackend,
) : PlanningEngine {

  override val source: LogSource
    get() = backend.source

  override suspend fun recommendTitle(synopsis: String, activityId: String): String {
    val built = prompt(PromptRecommendTitle) {
      system(TitleSystemPrompt)
      user(titleUserPrompt(synopsis))
    }
    val request = logRequest(built.asLogText())
    val text = send(built, request)
    if (text.isEmpty()) {
      val message = "The model returned an empty title"
      request?.failed(message, text)
      throw PlanningEngine.AnalysisFailure(message)
    }
    request?.responded(text, text)
    return text
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
    val built = prompt(promptId) {
      system(QuestionsSystemPrompt)
      user(questionsUserPrompt(title, synopsis, phase, previousQuestions))
    }
    val request = logRequest(built.asLogText())
    val text = send(built, request)
    val batch = try {
      batch(parseQuestions(text), roundId)
    } catch (e: Exception) {
      request?.failed(e.message ?: e.toString(), text)
      throw e
    }
    request?.responded(batch.summary(), text)
    return batch
  }

  /**
   * The model's verbatim reply to [built], or the failure it raised — which
   * [request] has already recorded, since a send that never got a reply is
   * exactly the case whose row would otherwise stand empty.
   */
  private suspend fun send(built: Prompt, request: LogRequest?): String = try {
    backend.executor.execute(built, backend.model).textContent().trim()
  } catch (e: CancellationException) {
    request?.cancelled()
    throw e
  } catch (e: Exception) {
    val message = e.message ?: e.toString()
    request?.failed(message)
    throw PlanningEngine.AnalysisFailure(message)
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

  /**
   * A built prompt as one log row: each message under its role. Reading it off
   * the [Prompt] rather than re-rendering its arguments is the whole point —
   * the row and the request cannot drift apart, however the prompt grows.
   */
  private fun Prompt.asLogText(): String =
    messages.joinToString("\n\n") { message ->
      "${message.role()}\n${message.textContent()}"
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

/**
 * The role a prompt message is logged under, e.g. `SYSTEM` (see `asLogText`).
 * Exhaustive on purpose: a new Koog message type has to say how it reads in the
 * log rather than fall through to a placeholder.
 */
private fun Message.role(): String = when (this) {
  is Message.System -> "SYSTEM"
  is Message.User -> "USER"
  is Message.Assistant -> "ASSISTANT"
}

