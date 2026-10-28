package alphainterplanetary.thinker.engine

import kotlin.math.roundToInt

/**
 * The derivations behind a model's identity: the name it is shown under, and
 * the size its metadata declares.
 *
 * They live apart from the catalogue that fills them because they are pure —
 * the same rules apply whatever source answered — and because the evaluation
 * harness (`tools/eval_ollama_models.py`) is the reference for both.
 */

/** The tag Ollama serves a model's newest build under. */
private const val LatestTag = "latest"

/** A size label as a source declares it: a number and a unit ("3B", "873.44M"). */
private val SizeLabel = Regex("^([0-9]+(?:\\.[0-9]+)?)\\s*([bBmM])?$")

/** A size that is only "8.0B" written out: the zero after the point is noise. */
private val RedundantZero = Regex("^[0-9]+\\.0$")

/**
 * The id a model is *shown* under, with the `latest` alias resolved to the
 * size tag it stands for.
 *
 * Ollama treats `latest` as a plain tag alias, so `llama3.2:latest` is really
 * `llama3.2:3b` — and a browser that shows the bare alias hides the one thing
 * that tells the model from its siblings. The registry call that would confirm
 * the alias is not available offline, so the size is distilled from the model's
 * own metadata instead: the size the source declares for it.
 *
 * The canonical id is untouched — that is what is asked of the provider — so a
 * model whose size cannot be read keeps its alias, and a model with a real tag
 * keeps that tag.
 */
internal fun resolveLatestAlias(modelId: String, sizeLabel: String?): String {
  val base = modelId.substringBefore(':')
  val tag = modelId.substringAfter(':', missingDelimiterValue = LatestTag)
  if (tag != LatestTag) return modelId
  val token = sizeTagToken(sizeLabel) ?: return modelId
  return "$base:$token"
}

/**
 * The tag a declared size stands for: the token Ollama itself uses ("3b",
 * "7.6b", "8b"). A redundant trailing zero is dropped ("8.0B" is "8b") and a
 * meaningful fraction is kept ("7.6B" is "7.6b").
 */
private fun sizeTagToken(sizeLabel: String?): String? {
  // A declared size must actually look like one ("3B", "7.6B", "873.44M"):
  // a descriptive label such as "mini" is not a size and must not be turned
  // into a bogus tag like "minib".
  val match = sizeLabel?.trim()?.let { SizeLabel.matchEntire(it) } ?: return null
  val amount = match.groupValues[1]
  val unit = match.groupValues[2].lowercase().ifEmpty { "b" }
  if (unit !in setOf("b", "m")) return null
  val withoutRedundantZero = if (RedundantZero.matches(amount)) amount.dropLast(2) else amount
  return "${withoutRedundantZero}${unit}"
}

/**
 * The parameter size a label declares, in billions: "3B" is 3 and "873.44M" is
 * 1. A label the app cannot read is no size at all rather than a guess.
 */
internal fun parseSizeLabel(label: String?): Int? {
  val match = label?.trim()?.let { SizeLabel.matchEntire(it) } ?: return null
  val amount = match.groupValues[1].toDoubleOrNull() ?: return null
  return when (match.groupValues[2].uppercase()) {
    "", "B" -> amount.roundToInt()
    "M" -> (amount / 1000.0).roundToInt()
    else -> null
  }
}

/**
 * The parameter size an exact count declares, in billions. The count is the
 * model's own, so it answers where no size label is declared.
 */
internal fun sizeFromCount(count: Long?): Int? {
  if (count == null || count <= 0) return null
  return (count / 1_000_000_000.0).roundToInt()
}
