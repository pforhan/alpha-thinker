package alphainterplanetary.thinker.eval

import alphainterplanetary.thinker.eval.EvalFixtures.Interaction
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive

/**
 * A score is a pure function of the interaction and the model's raw reply, never
 * of the timing or the row it came from. That is what lets the report re-score
 * old rows when a rule tightens instead of invalidating them.
 */
data class EvalScore(
  val ok: Boolean = false,
  val clean: Boolean = false,
  val error: String? = null,
  val jsonOk: Boolean? = null,
  val arrayStrings: Boolean? = null,
  val wordCount: Int = 0,
  val chars: Int = 0,
)

/**
 * The app's port of the Python harness's `score_result`.
 *
 * The three interactions have different bars on purpose: a title is a short
 * piece of prose with syntactic rules, a question batch is only useful when it
 * is a JSON array of non-empty strings, and a summary is prose with a length
 * and formatting budget. `ok` is basic usability; `clean` is usability plus the
 * formatting rules the app's own prompts ask for.
 */
object EvalScoring {
  private const val TitleMaxChars = 60
  private const val SummaryMaxWords = 150

  private val titlePreambles = listOf(
    "the title is",
    "title:",
    "suggested title",
    "here is",
    "here's",
    "i suggest",
  )

  private val summaryPreambles = listOf(
    "here is",
    "here's",
    "summary:",
    "in summary",
  )

  private val json = Json { ignoreUnknownKeys = true }

  fun score(interaction: Interaction, text: String): EvalScore {
    val trimmed = text.trim()
    return when (interaction) {
      Interaction.Title -> scoreTitle(trimmed)
      Interaction.InitialQuestions -> scoreQuestions(trimmed)
      Interaction.Summarize -> scoreSummary(trimmed)
    }
  }

  private fun scoreTitle(text: String): EvalScore {
    if (text.isEmpty()) return EvalScore(error = "empty title")
    val lowered = text.lowercase()
    val problems = buildList {
      if (text.length > TitleMaxChars) add(">$TitleMaxChars chars")
      if (text.endsWith(".")) add("trailing period")
      val first = text.take(1)
      if ((first == "\"" || first == "'") && text.takeLast(1) == first) {
        add("wrapped in quotes")
      }
      if (titlePreambles.any(lowered::startsWith)) add("preamble")
      if ('\n' in text) add("multi-line")
    }
    return EvalScore(
      ok = true,
      clean = problems.isEmpty(),
      error = problems.takeIf { it.isNotEmpty() }?.joinToString(", ", prefix = "title: "),
      wordCount = text.wordCount(),
      chars = text.length,
    )
  }

  private fun scoreQuestions(text: String): EvalScore {
    // The Python harness considers everything from the first `[` to the last
    // `]` one candidate array, so a reply with two arrays fails to parse rather
    // than having its first one extracted. Kept as-is so a rate measured in the
    // app means the same thing as one measured on the desktop.
    val start = text.indexOf('[')
    val end = text.lastIndexOf(']')
    val body = if (start >= 0 && end > start) text.substring(start, end + 1) else text
    val parsed = runCatching { json.parseToJsonElement(body) }
    if (parsed.isFailure) {
      return EvalScore(
        jsonOk = false,
        arrayStrings = false,
        error = "json parse failed: ${parsed.exceptionOrNull()?.message ?: "unreadable"}",
        wordCount = text.wordCount(),
        chars = text.length,
      )
    }
    val element = parsed.getOrNull()
    val arrayOfStrings = element is JsonArray && element.all { item ->
      val primitive = item as? JsonPrimitive
      primitive?.isString == true && primitive.content.isNotBlank()
    }
    return EvalScore(
      ok = arrayOfStrings,
      clean = arrayOfStrings,
      error = if (arrayOfStrings) null else "not array of non-empty strings",
      jsonOk = true,
      arrayStrings = arrayOfStrings,
      wordCount = text.wordCount(),
      chars = text.length,
    )
  }

  private fun scoreSummary(text: String): EvalScore {
    if (text.isEmpty()) return EvalScore(error = "empty summary")
    val words = text.wordCount()
    val problems = buildList {
      if (words > SummaryMaxWords) add(">$SummaryMaxWords words")
      if (hasBulletsOrHeadings(text)) add("bullets/headings")
      if (summaryPreambles.any(text.lowercase()::startsWith)) add("preamble")
    }
    return EvalScore(
      ok = true,
      clean = problems.isEmpty(),
      error = problems.takeIf { it.isNotEmpty() }?.joinToString(", ", prefix = "summary: "),
      wordCount = words,
      chars = text.length,
    )
  }

  private fun hasBulletsOrHeadings(text: String): Boolean =
    text.lineSequence().any { line ->
      val stripped = line.trim()
      if (stripped.isEmpty()) {
        false
      } else if (stripped.startsWith("-") || stripped.startsWith("*") ||
        stripped.startsWith("#") || stripped.startsWith("•")
      ) {
        true
      } else {
        val head = stripped.substringBefore(' ')
        (head.endsWith(".") || head.endsWith(")")) && head.dropLast(1).isNumeric()
      }
    }

  private fun String.isNumeric(): Boolean = isNotEmpty() && all { it in '0'..'9' }

  private fun String.wordCount(): Int = when {
    isEmpty() -> 0
    else -> trim().split(Regex("\\s+")).count { it.isNotEmpty() }
  }
}
