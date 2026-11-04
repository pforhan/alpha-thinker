package alphainterplanetary.thinker.eval

/**
 * The prompt fixtures the evaluation harness runs against a model.
 *
 * These are a direct port of `tools/eval_ollama_models.py` and are deliberately
 * kept byte-for-byte comparable with it: a row measured in the app has to be
 * poolable with the rows already in `docs/model-evals`, and those rows are only
 * comparable while the prompts and the fixture version are the same. Adding a
 * fixture or changing a prompt is therefore a fixtures-version bump, not a local
 * edit.
 */
object EvalFixtures {
  const val Version: Int = 1
  const val Schema: Int = 1

  /** A single planning interaction a model is asked to perform. */
  enum class Interaction(val wire: String) {
    Title("title"),
    InitialQuestions("initial_questions"),
    Summarize("summarize"),
    ;

    companion object {
      fun fromWire(wire: String): Interaction? = entries.firstOrNull { it.wire == wire }
    }
  }

  /** One project-shaped input, with the interactions that exercise it. */
  data class Fixture(
    val name: String,
    val titleInput: String,
    val synopsis: String,
    val transcript: String,
    val interactions: Set<Interaction> = Interaction.entries.toSet(),
  )

  val fixtures: List<Fixture> = listOf(
    Fixture(
      name = "simple-product",
      titleInput = "A mobile app to track daily water intake",
      synopsis = "Build a simple iOS/Android app that reminds users to drink water, " +
        "logs intake, and shows progress toward a daily goal.",
      transcript = "(none)",
    ),
    Fixture(
      name = "home-project",
      titleInput = "Kitchen cabinet refinish plan",
      synopsis = "Refinish existing kitchen cabinets to match a new color scheme with " +
        "minimal cost and low VOC materials.",
      transcript = "Q: What is the current cabinet material?\n" +
        "A: committed: solid oak with factory finish\n" +
        "Q: What is the desired timeline?\n" +
        "A: committed: 2–3 weekends",
    ),
    Fixture(
      name = "software-plan",
      titleInput = "Project planning app",
      synopsis = "An edge-LLM powered project planning app with iterative question " +
        "synthesis to help users plan projects.",
      transcript = "Q: Who is the target user?\n" +
        "A: committed: solo builders and small teams\n" +
        "Q: Offline first?\n" +
        "A: committed: yes",
    ),
  )

  /**
   * Every scored cell, in the order the Python harness runs them: each
   * interaction across each fixture that exercises it. The order is part of the
   * comparison: it keeps two sweeps of the same model comparable cell-for-cell.
   */
  val groups: List<Pair<Interaction, Fixture>> = Interaction.entries.flatMap { interaction ->
    fixtures.filter { interaction in it.interactions }.map { interaction to it }
  }

  /** The unscored request that pays the model's cold-load cost. */
  const val WarmupPrompt: String = "Reply with the single word: ready"

  /** The prompt for [interaction] over [fixture], matching the Python harness. */
  fun prompt(interaction: Interaction, fixture: Fixture): String {
    val title = fixture.titleInput
    val synopsis = fixture.synopsis
    val transcript = fixture.transcript
    return when (interaction) {
      Interaction.Title ->
        "You are a project-planning assistant. Recommend a short, memorable project " +
          "title from the user's synopsis. Reply with only the title — no quotes, " +
          "no explanation, no trailing period. Keep it under 60 characters.\n\n" +
          "Project synopsis:\n$synopsis\n\nReturn the project title."

      Interaction.InitialQuestions ->
        "You are a project-planning assistant. The user provides the current planning " +
          "phase and the interview history (format: question | status/answer). Ask 3-5 " +
          "new, focused questions for this phase. Reply with ONLY a valid JSON array of " +
          "strings, e.g. [\"Question 1?\", \"Question 2?\"]\n\n" +
          "Planning a project titled \"$title\".\n" +
          "Project synopsis:\n$synopsis\n\n" +
          "The interview so far:\n$transcript\n\n" +
          "Propose new questions for this phase. Focus on Phase: Scope & Goals — Define " +
          "the project's purpose, boundaries, and success criteria.\n" +
          "but if there are crucial details missing that block this phase you may go " +
          "deeper on what has been answered."

      Interaction.Summarize ->
        "You are a project-planning assistant. Condense a finished planning phase of a " +
          "project interview into a short summary that a later planning round can rely " +
          "on. Keep the decisions the user made, the facts and constraints they committed " +
          "to, and anything they explicitly ruled out — those are what must not be " +
          "re-asked. Drop the reasoning, the restatement, and the questions with nothing " +
          "behind them. Write plain prose under 150 words, no headings, no bullets, no " +
          "preamble.\n\n" +
          "Planning a project titled \"$title\".\n" +
          "Project synopsis:\n$synopsis\n\n" +
          "This planning phase (Scope & Goals) is finished:\n$transcript\n\n" +
          "Summarize what this phase settled, so a later round does not ask again."
    }
  }
}
