package alphainterplanetary.thinker.downloads

/**
 * Tracks transfer speed as an exponential moving average so jittery byte
 * samples produce a stable rate and ETA.
 *
 * Feed it each progress sample with a monotonic timestamp; it returns the
 * smoothed bytes-per-second rate once it has at least one usable interval.
 * Samples that do not advance time or bytes are ignored and keep the current
 * rate, so a burst of identical updates does not zero the estimate.
 */
class EmaThroughput(
  private val alpha: Double = DEFAULT_ALPHA,
) {
  private var lastBytes: Long? = null
  private var lastNanos: Long? = null
  private var emaBps: Double? = null

  /**
   * Records [bytesDone] observed at [nowNanos] (a monotonic clock, such as
   * [kotlin.time.TimeSource.Monotonic]) and returns the smoothed throughput in
   * bytes per second, or `null` until a usable interval has been seen.
   */
  fun sample(bytesDone: Long, nowNanos: Long): Long? {
    val prevBytes = lastBytes
    val prevNanos = lastNanos
    lastBytes = bytesDone
    lastNanos = nowNanos
    if (prevBytes == null || prevNanos == null) return emaBps?.toLong()
    val deltaNanos = nowNanos - prevNanos
    val deltaBytes = bytesDone - prevBytes
    if (deltaNanos <= 0L || deltaBytes <= 0L) return emaBps?.toLong()
    val instantBps = deltaBytes.toDouble() * NANOS_PER_SECOND / deltaNanos.toDouble()
    val current = emaBps
    emaBps = if (current == null) instantBps else alpha * instantBps + (1.0 - alpha) * current
    return emaBps?.toLong()
  }

  companion object {
    /** Weights recent samples enough to react, while damping short spikes. */
    const val DEFAULT_ALPHA: Double = 0.3
    private const val NANOS_PER_SECOND: Double = 1_000_000_000.0
  }
}
