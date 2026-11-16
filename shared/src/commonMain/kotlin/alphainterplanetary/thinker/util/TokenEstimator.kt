package alphainterplanetary.thinker.util

object TokenEstimator {
  /** Chars per token for the estimate, the usual ~4:1 rule of thumb. */
  const val CHARS_PER_TOKEN: Int = 4

  /**
   * The estimated token cost of [text]. An estimate, not a count: the heuristic
   * is length, which is what the budget is protecting against and is close
   * enough to keep a prompt inside a model's window.
   */
  fun estimateTokens(text: String): Int =
    (text.length + CHARS_PER_TOKEN - 1) / CHARS_PER_TOKEN
}
