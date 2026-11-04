package alphainterplanetary.thinker.eval

/**
 * Watches streamed output for the degenerate repetition that makes a slow model
 * unusable long before its output buffer fills.
 *
 * The test is deliberately conservative: a short unit must repeat at least six
 * times *and* cover at least [MinRepeatChars] characters, or the same non-empty
 * line must repeat at least six times. A normal answer can repeat a word or a
 * short phrase; it does not repeat one for hundreds of characters. Detecting the
 * loop lets the harness stop the runtime instead of waiting for the request
 * timeout, which is the difference between a cell that costs seconds and one
 * that costs two minutes.
 */
internal class EvalRepetitionDetector(
  private val minPeriod: Int = 4,
  private val maxPeriod: Int = 128,
  private val minRepeats: Int = 6,
  private val minRepeatChars: Int = 240,
  private val windowChars: Int = 8_192,
) {
  private val captured = StringBuilder()
  private val window = StringBuilder()

  /** Everything seen so far, capped so a runaway reply cannot exhaust memory. */
  val text: String
    get() = captured.toString()

  /**
   * Adds [chunk] and returns true once the tail is a repetition loop.
   *
   * The caller passes token deltas, not the whole reply, so the check runs as
   * the text arrives rather than after the model has already spent the time.
   */
  fun append(chunk: String): Boolean {
    if (chunk.isEmpty()) return false
    captured.append(chunk)
    if (captured.length > CapturedChars) {
      captured.delete(0, captured.length - CapturedChars)
    }
    window.append(chunk)
    if (window.length > windowChars) {
      window.delete(0, window.length - windowChars)
    }
    if (captured.length < minRepeatChars) return false
    return repeatsAUnit() || repeatsALine()
  }

  private fun repeatsAUnit(): Boolean {
    val end = window.length
    // A period longer than end / minRepeats cannot occur minRepeats times.
    val largest = minOf(maxPeriod, (end - 1) / minRepeats)
    for (period in minPeriod..largest) {
      if (!regionEquals(end - period, end - 2 * period, period)) continue
      var repeats = 2
      var start = end - 2 * period
      while (start - period >= 0 && regionEquals(start - period, start, period)) {
        repeats++
        start -= period
      }
      if (repeats >= minRepeats && period * repeats >= minRepeatChars) return true
    }
    return false
  }

  private fun repeatsALine(): Boolean {
    val lines = window.toString().trimEnd().split('\n')
    val last = lines.lastOrNull()?.trimEnd().orEmpty()
    if (last.length < 16) return false
    var repeats = 1
    for (index in lines.size - 2 downTo 0) {
      if (lines[index].trimEnd() != last) break
      repeats++
    }
    return repeats >= minRepeats && last.length * repeats >= minRepeatChars
  }

  private fun regionEquals(first: Int, second: Int, length: Int): Boolean {
    if (first < 0 || second < 0) return false
    for (offset in 0 until length) {
      if (window[first + offset] != window[second + offset]) return false
    }
    return true
  }

  private companion object {
    /** A reply is at most ~1k tokens today; this is only a runaway guard. */
    const val CapturedChars = 64 * 1024
  }
}
